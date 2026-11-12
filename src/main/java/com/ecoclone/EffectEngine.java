package com.ecoclone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.4.8.0 逐级效果引擎（对齐 EcoEnchants 的 effects / levels）。
 *
 * 单个附魔 yml 新增两种写法，二者等价，levels 优先：
 *
 *   effects:                       # 所有等级共用
 *     - id: damage_multiplier
 *       args: { multiplier: "1 + 0.1 * %level%" }
 *       triggers: [attack]
 *
 *   levels:                        # 按等级分别配置（也支持 levels: {1: [...]}）
 *     1:
 *       effects: [...]
 *     2:
 *       effects: [...]
 *
 * 每条效果字段：id（必填）、args（参数，支持 %level% 表达式）、triggers（不写 = 全部触发）、
 * args.chance（可选，0-1 触发概率）。
 *
 * 已实现的效果 id：
 *   damage_multiplier / damage_add   改攻击伤害（attack、defend 触发时有效）
 *   potion_effect                    药水效果（effect / level / duration / apply_to_victim）
 *   heal / feed                      回血 / 回饱食度
 *   ignite                           点燃目标（seconds）
 *   knock_away / pull_in             击退 / 拉近（strength）
 *   drop_xp                          给玩家经验（amount）
 *   play_sound                       播放音效（sound / volume / pitch）
 *   particle                         粒子（particle / count）
 *   run_command                      执行指令（command，支持 %player%）
 *   msg                              发消息（message，支持 %player%、%level%）
 *   remove_this_enchant              用掉后移除自身附魔（一次性附魔）
 *   set_item_level                   修改自身附魔等级（level）
 */
public final class EffectEngine {

    /** 一次触发的事件上下文（damage 可被效果改写） */
    public static final class Ctx {
        public double damage;
        public LivingEntity victim;
        public org.bukkit.block.Block block;

        public Ctx() {
        }

        public Ctx(double damage) {
            this.damage = damage;
        }
    }

    private final EnchantManager manager;
    private final Map<String, Boolean> hasCache = new HashMap<>();

    public EffectEngine(EnchantManager manager) {
        this.manager = manager;
    }

    public void reload() {
        hasCache.clear();
    }

    /** 该附魔是否配置了效果（用于被动 tick 与扫描时跳过纯 Java 附魔） */
    public boolean hasEffects(String key) {
        Boolean cached = hasCache.get(key);
        if (cached != null) {
            return cached;
        }
        YamlConfiguration c = manager.enchantConfig(key);
        boolean has = c != null && (c.get("levels") != null || c.get("effects") != null);
        hasCache.put(key, has);
        return has;
    }

    /**
     * 执行该附魔在当前等级、当前触发器下的全部效果。
     *
     * @return 是否至少执行了一条效果
     */
    public boolean fire(Player p, ItemStack item, String key, int level, TriggerType trigger, Ctx ctx) {
        if (p == null || item == null || key == null || level <= 0) {
            return false;
        }
        boolean ran = false;
        for (Map<?, ?> spec : specs(key, level)) {
            String id = str(spec.get("id"));
            if (id == null) {
                continue;
            }
            Map<?, ?> args = spec.get("args") instanceof Map<?, ?> m ? m : Map.of();
            List<String> triggers = strings(spec.get("triggers"));
            if (!triggers.isEmpty() && !triggerMatches(triggers, trigger)) {
                continue;
            }
            double chance = num(args, "chance", 1.0, level);
            if (chance < 1.0 && Math.random() > chance) {
                continue;
            }
            try {
                apply(p, item, key, level, trigger, ctx, id.trim().toLowerCase(), args);
                ran = true;
            } catch (Throwable ignored) {
                // 单条效果配置错误不影响其它效果
            }
        }
        return ran;
    }

    // ---------- 效果实现 ----------

    private void apply(Player p, ItemStack item, String key, int level, TriggerType trigger,
                       Ctx ctx, String id, Map<?, ?> args) {
        switch (id) {
            case "damage_multiplier" -> {
                if (ctx != null) {
                    ctx.damage *= num(args, "multiplier", 1.0, level);
                }
            }
            case "damage_add" -> {
                if (ctx != null) {
                    ctx.damage += num(args, "amount", 0.0, level);
                }
            }
            case "potion_effect" -> {
                PotionEffectType type = potion(str(args, "effect", "speed"));
                if (type == null) {
                    return;
                }
                int amp = Math.max(0, (int) num(args, "level", 1.0, level) - 1);
                int ticks = Math.max(1, (int) num(args, "duration", 200.0, level));
                boolean toVictim = bool(args, "apply_to_victim", false);
                LivingEntity target = toVictim && ctx != null && ctx.victim != null ? ctx.victim : p;
                target.addPotionEffect(new PotionEffect(type, ticks, amp, true, true, true));
            }
            case "heal" -> {
                double max = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
                        ? p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
                        : 20.0;
                p.setHealth(Math.min(max, p.getHealth() + num(args, "amount", 2.0, level)));
            }
            case "feed" -> p.setFoodLevel(Math.min(20,
                    p.getFoodLevel() + (int) num(args, "amount", 2.0, level)));
            case "ignite" -> {
                LivingEntity target = ctx != null && ctx.victim != null ? ctx.victim : p;
                target.setFireTicks((int) (num(args, "seconds", 3.0, level) * 20));
            }
            case "knock_away", "pull_in" -> {
                LivingEntity target = ctx != null && ctx.victim != null ? ctx.victim : null;
                if (target == null) {
                    return;
                }
                double strength = num(args, "strength", 0.6, level);
                Location from = p.getLocation();
                org.bukkit.util.Vector v = target.getLocation().toVector().subtract(from.toVector());
                if (v.lengthSquared() < 0.01) {
                    v = from.getDirection();
                }
                v.normalize().multiply(id.equals("pull_in") ? -strength : strength);
                v.setY(Math.max(v.getY(), 0.25));
                target.setVelocity(target.getVelocity().add(v));
            }
            case "drop_xp" -> p.giveExp((int) num(args, "amount", 5.0, level));
            case "play_sound" -> p.playSound(p.getLocation(),
                    str(args, "sound", "entity.experience_orb.pickup"),
                    (float) num(args, "volume", 1.0, level),
                    (float) num(args, "pitch", 1.0, level));
            case "particle" -> {
                Particle particle = particle(str(args, "particle", "end_rod"));
                if (particle == null) {
                    return;
                }
                p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1, 0),
                        Math.max(1, (int) num(args, "count", 10.0, level)),
                        0.3, 0.4, 0.3, 0.02);
            }
            case "run_command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    str(args, "command", "").replace("%player%", p.getName()));
            case "msg" -> p.sendMessage(manager.color(str(args, "message", "")
                    .replace("%player%", p.getName())
                    .replace("%level%", String.valueOf(level))));
            case "remove_this_enchant" -> {
                org.bukkit.enchantments.Enchantment ench = Enchants.get(key);
                if (ench != null) {
                    item.editMeta(meta -> meta.removeEnchant(ench));
                }
            }
            case "set_item_level" -> {
                org.bukkit.enchantments.Enchantment ench = Enchants.get(key);
                if (ench != null) {
                    int lv = Math.max(1, (int) num(args, "level", level, level));
                    item.editMeta(meta -> meta.addEnchant(ench, lv, true));
                }
            }
            default -> {
                // 未实现的效果 id 静默跳过，保持与 EcoEnchants 一致的容错行为
            }
        }
    }

    // ---------- 配置解析 ----------

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> specs(String key, int level) {
        List<Map<?, ?>> out = new ArrayList<>();
        YamlConfiguration c = manager.enchantConfig(key);
        if (c == null) {
            return out;
        }
        Object levels = c.get("levels");
        if (levels instanceof Map<?, ?> map) {
            Object raw = map.get(String.valueOf(level));
            collect(out, raw instanceof Map<?, ?> m ? m.get("effects") : raw);
        } else if (levels instanceof List<?> list) {
            if (level >= 1 && level <= list.size()) {
                Object raw = list.get(level - 1);
                collect(out, raw instanceof Map<?, ?> m ? m.get("effects") : raw);
            }
        }
        if (out.isEmpty()) {
            collect(out, c.get("effects"));
        }
        return out;
    }

    private void collect(List<Map<?, ?>> out, Object raw) {
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(m);
                }
            }
        }
    }

    private boolean triggerMatches(List<String> triggers, TriggerType trigger) {
        for (String raw : triggers) {
            TriggerType t = TriggerType.parse(raw);
            if (t != null && t == trigger) {
                return true;
            }
            if (t == null && raw.trim().equalsIgnoreCase("any")) {
                return true;
            }
        }
        return false;
    }

    // ---------- 参数工具 ----------

    private static String str(Map<?, ?> args, String key, String def) {
        Object o = args.get(key);
        return o == null ? def : String.valueOf(o);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean bool(Map<?, ?> args, String key, boolean def) {
        Object o = args.get(key);
        if (o == null) {
            return def;
        }
        return String.valueOf(o).equalsIgnoreCase("true");
    }

    private static double num(Map<?, ?> args, String key, double def, int level) {
        Object o = args.get(key);
        if (o == null) {
            return def;
        }
        try {
            return eval(String.valueOf(o).replace("%level%", String.valueOf(level)));
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else if (raw != null) {
            out.add(String.valueOf(raw));
        }
        return out;
    }

    // ---------- 小表达式求值（数字、+ - * / % ^、括号） ----------

    /** 计算 arg 表达式，非法表达式返回 0 */
    static double eval(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return new Parser(expr).parse();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s.replace(" ", "");
        }

        double parse() {
            double v = expr();
            if (i < s.length()) {
                throw new IllegalArgumentException("trailing");
            }
            return v;
        }

        private double expr() {
            double v = term();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '+') {
                    i++;
                    v += term();
                } else if (c == '-') {
                    i++;
                    v -= term();
                } else {
                    break;
                }
            }
            return v;
        }

        private double term() {
            double v = power();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '*') {
                    i++;
                    v *= power();
                } else if (c == '/') {
                    i++;
                    double d = power();
                    v = d == 0 ? 0 : v / d;
                } else if (c == '%') {
                    i++;
                    double d = power();
                    v = d == 0 ? 0 : v % d;
                } else {
                    break;
                }
            }
            return v;
        }

        private double power() {
            double base = unary();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                return Math.pow(base, power());
            }
            return base;
        }

        private double unary() {
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
                return -unary();
            }
            if (i < s.length() && s.charAt(i) == '+') {
                i++;
                return unary();
            }
            return atom();
        }

        private double atom() {
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                double v = expr();
                if (i >= s.length() || s.charAt(i) != ')') {
                    throw new IllegalArgumentException("paren");
                }
                i++;
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("number");
            }
            return Double.parseDouble(s.substring(start, i));
        }
    }

    // ---------- 名字映射 ----------

    private static Particle particle(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase().replace(' ', '_'));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType potion(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toLowerCase().replace("minecraft:", "").replace(' ', '_');
        return switch (n) {
            case "speed", "swiftness" -> PotionEffectType.SPEED;
            case "slowness" -> PotionEffectType.SLOWNESS;
            case "haste" -> PotionEffectType.HASTE;
            case "mining_fatigue" -> PotionEffectType.MINING_FATIGUE;
            case "strength" -> PotionEffectType.STRENGTH;
            case "weakness" -> PotionEffectType.WEAKNESS;
            case "regeneration" -> PotionEffectType.REGENERATION;
            case "resistance" -> PotionEffectType.RESISTANCE;
            case "fire_resistance" -> PotionEffectType.FIRE_RESISTANCE;
            case "water_breathing" -> PotionEffectType.WATER_BREATHING;
            case "invisibility" -> PotionEffectType.INVISIBILITY;
            case "night_vision" -> PotionEffectType.NIGHT_VISION;
            case "jump_boost" -> PotionEffectType.JUMP_BOOST;
            case "absorption" -> PotionEffectType.ABSORPTION;
            case "health_boost" -> PotionEffectType.HEALTH_BOOST;
            case "saturation" -> PotionEffectType.SATURATION;
            case "luck" -> PotionEffectType.LUCK;
            case "levitation" -> PotionEffectType.LEVITATION;
            case "slow_falling" -> PotionEffectType.SLOW_FALLING;
            case "glowing" -> PotionEffectType.GLOWING;
            case "darkness" -> PotionEffectType.DARKNESS;
            case "blindness" -> PotionEffectType.BLINDNESS;
            case "nausea" -> PotionEffectType.NAUSEA;
            case "hunger" -> PotionEffectType.HUNGER;
            case "poison" -> PotionEffectType.POISON;
            case "wither" -> PotionEffectType.WITHER;
            case "dolphins_grace" -> PotionEffectType.DOLPHINS_GRACE;
            case "conduit_power" -> PotionEffectType.CONDUIT_POWER;
            default -> null;
        };
    }
}
