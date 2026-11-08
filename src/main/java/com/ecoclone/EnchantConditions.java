package com.ecoclone;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.4.7 高级功能：附魔自定义条件（conditions / filters）与触发器（triggers）。
 *
 * yml 写法（两种格式都支持）：
 *   conditions:
 *     - "permission:aeath.vip"
 *     - "world:world_nether"
 *     - "y-above:60"
 *     - type: biome
 *       value: plains,desert
 *   filters:              # EcoEnchants 兼容别名，等价 conditions
 *     permission: aeath.vip
 *
 * 支持的类型：
 *   permission / no-permission     权限节点
 *   world / world-not              世界名（逗号分隔可多值）
 *   world-env                       normal / nether / the_end
 *   biome                          生物群系（逗号分隔，忽略 minecraft: 前缀）
 *   y-above / y-below              坐标
 *   time                           day / night / dawn / dusk 或 13000-23000
 *   weather                        clear / rain / thunder
 *   health-above / health-below    血量，可用 50% 表示比例
 *   food-above / food-below        饱食度
 *   level-above / level-below      玩家等级
 *   gamemode                       survival / creative / adventure / spectator
 *   sneaking / sprinting / flying / on-ground / in-water / vehicle   布尔（true）
 *   entities-nearby                MONSTER>=3 / ANIMAL>=2 / PLAYER>=1 / ANY>=5
 *   placeholder                    %placeholder%>=10（需要 PlaceholderAPI，未装时视为通过）
 */
public final class EnchantConditions {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private final Map<String, List<String[]>> conditions = new ConcurrentHashMap<>();
    private final Map<String, Set<TriggerType>> triggers = new ConcurrentHashMap<>();

    private static final Pattern PAPI_EXPR = Pattern.compile("(%[^%]+%)\\s*(>=|<=|==|!=|>|<)\\s*(.+)");
    private static final Set<String> BOOLEAN_TYPES = Set.of("sneaking", "sprinting", "flying",
            "on-ground", "in-water", "vehicle", "gliding", "swimming");

    public EnchantConditions(EcoClonePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
    }

    public void reload() {
        conditions.clear();
        triggers.clear();
    }

    /** 条件是否全部满足；没有配置条件时恒为 true */
    public boolean matches(String key, Player p) {
        if (p == null || !manager.conditionsEnabled()) {
            return true;
        }
        List<String[]> conds = conditions.computeIfAbsent(key, k -> parse(manager.enchantConfig(k)));
        for (String[] c : conds) {
            try {
                if (!test(c[0], c[1], p)) {
                    return false;
                }
            } catch (Throwable ignored) {
                // 条件格式错误时不阻断附魔，避免配置笔误让附魔静默失效
            }
        }
        return true;
    }

    /** 触发器是否允许；triggers 为空表示全部允许 */
    public boolean triggerAllowed(String key, TriggerType type) {
        if (!manager.triggersEnabled() || type == null) {
            return true;
        }
        Set<TriggerType> allowed = triggers.computeIfAbsent(key, k -> parseTriggers(manager.enchantConfig(k)));
        return allowed.isEmpty() || allowed.contains(type);
    }

    // ---------- 解析 ----------

    private List<String[]> parse(YamlConfiguration c) {
        List<String[]> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        Object raw = c.get("conditions");
        if (raw == null) {
            raw = c.get("filters");
        }
        if (raw == null) {
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    addPair(out, s, null);
                } else if (o instanceof Map<?, ?> m) {
                    String type = str(m.get("type"));
                    if (type == null) {
                        type = str(m.get("condition"));
                    }
                    Object value = m.containsKey("value") ? m.get("value") : m.get("values");
                    if (type != null && value != null) {
                        addValue(out, type, value);
                    }
                }
            }
        } else if (raw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String type = str(e.getKey());
                if (type != null) {
                    addValue(out, type, e.getValue());
                }
            }
        } else if (raw instanceof String s) {
            addPair(out, s, null);
        }
        return out;
    }

    private void addValue(List<String[]> out, String type, Object value) {
        if (value instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) {
                    out.add(new String[]{type.toLowerCase(), String.valueOf(o)});
                }
            }
        } else {
            out.add(new String[]{type.toLowerCase(), String.valueOf(value)});
        }
    }

    private void addPair(List<String[]> out, String line, String ignored) {
        String s = line.trim();
        if (s.isEmpty()) {
            return;
        }
        int idx = s.indexOf(':');
        if (idx <= 0) {
            return;
        }
        out.add(new String[]{s.substring(0, idx).trim().toLowerCase(), s.substring(idx + 1).trim()});
    }

    private Set<TriggerType> parseTriggers(YamlConfiguration c) {
        Set<TriggerType> out = EnumSet.noneOf(TriggerType.class);
        if (c == null) {
            return out;
        }
        List<String> raw = c.getStringList("general-config.triggers");
        if (raw.isEmpty()) {
            raw = c.getStringList("triggers");
        }
        for (String s : raw) {
            TriggerType t = TriggerType.parse(s);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    // ---------- 判定 ----------

    private boolean test(String type, String rawValue, Player p) {
        String v = rawValue == null ? "" : rawValue.trim();
        if (BOOLEAN_TYPES.contains(type)) {
            boolean expect = !"false".equalsIgnoreCase(v) && !"no".equalsIgnoreCase(v) && !"0".equals(v);
            return switch (type) {
                case "sneaking" -> p.isSneaking();
                case "sprinting" -> p.isSprinting();
                case "flying" -> p.isFlying() || p.isGliding();
                case "on-ground" -> p.isOnGround();
                case "in-water" -> p.isInWater();
                case "vehicle" -> p.isInsideVehicle();
                case "gliding" -> p.isGliding();
                case "swimming" -> p.isSwimming();
                default -> true;
            } == expect;
        }
        switch (type) {
            case "permission":
            case "has-permission":
                return p.hasPermission(v);
            case "no-permission":
            case "permission-not":
                return !p.hasPermission(v);
            case "world":
                return anyMatch(v, String.valueOf(p.getWorld().getName()));
            case "world-not":
                return !anyMatch(v, String.valueOf(p.getWorld().getName()));
            case "world-env": {
                World.Environment env = p.getWorld().getEnvironment();
                for (String s : split(v)) {
                    if (s.equalsIgnoreCase(env.name())
                            || (s.equalsIgnoreCase("the_end") && env == World.Environment.THE_END)
                            || (s.equalsIgnoreCase("end") && env == World.Environment.THE_END)
                            || (s.equalsIgnoreCase("nether") && env == World.Environment.NETHER)
                            || (s.equalsIgnoreCase("overworld") && env == World.Environment.NORMAL)) {
                        return true;
                    }
                }
                return false;
            }
            case "biome":
            case "biomes": {
                String biome = String.valueOf(p.getLocation().getBlock().getBiome().getKey());
                return anyMatch(stripNamespace(v), stripNamespace(biome));
            }
            case "y-above":
                return p.getLocation().getY() >= num(v, 0);
            case "y-below":
                return p.getLocation().getY() <= num(v, 0);
            case "time": {
                long t = p.getWorld().getTime();
                for (String s : split(v)) {
                    switch (s.toLowerCase()) {
                        case "day" -> {
                            if (t >= 0 && t < 13000) return true;
                        }
                        case "night" -> {
                            if (t >= 13000 && t <= 23000) return true;
                        }
                        case "dawn" -> {
                            if (t >= 23000 || t < 2000) return true;
                        }
                        case "dusk" -> {
                            if (t >= 12000 && t < 14000) return true;
                        }
                        default -> {
                            String[] range = s.split("-");
                            if (range.length == 2 && t >= num(range[0], 0) && t <= num(range[1], 24000)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
            case "weather": {
                for (String s : split(v)) {
                    if (s.equalsIgnoreCase("clear") && !p.getWorld().hasStorm() && !p.getWorld().isThundering()) {
                        return true;
                    }
                    if (s.equalsIgnoreCase("rain") && p.getWorld().hasStorm()) {
                        return true;
                    }
                    if (s.equalsIgnoreCase("thunder") && p.getWorld().isThundering()) {
                        return true;
                    }
                }
                return false;
            }
            case "health-above":
                return p.getHealth() >= ratio(v, p.getMaxHealth());
            case "health-below":
                return p.getHealth() <= ratio(v, p.getMaxHealth());
            case "food-above":
                return p.getFoodLevel() >= num(v, 0);
            case "food-below":
                return p.getFoodLevel() <= num(v, 0);
            case "level-above":
                return p.getLevel() >= num(v, 0);
            case "level-below":
                return p.getLevel() <= num(v, 0);
            case "gamemode":
            case "game-mode":
                for (String s : split(v)) {
                    if (s.equalsIgnoreCase(p.getGameMode().name())
                            || (s.equalsIgnoreCase("survival") && p.getGameMode() == GameMode.SURVIVAL)
                            || (s.equalsIgnoreCase("creative") && p.getGameMode() == GameMode.CREATIVE)
                            || (s.equalsIgnoreCase("adventure") && p.getGameMode() == GameMode.ADVENTURE)
                            || (s.equalsIgnoreCase("spectator") && p.getGameMode() == GameMode.SPECTATOR)) {
                        return true;
                    }
                }
                return false;
            case "entities-nearby":
            case "entity-nearby":
                return nearby(v, p);
            case "placeholder":
            case "papi":
                return placeholder(v, p);
            default:
                return true;
        }
    }

    private boolean nearby(String expr, Player p) {
        String[] parts = expr.split(">=|<=|>|<");
        if (parts.length < 2) {
            return true;
        }
        String kind = parts[0].trim().toUpperCase();
        int need = (int) num(expr.substring(parts[0].length() + 2).trim(), 0);
        int count = 0;
        for (Entity e : p.getNearbyEntities(8, 8, 8)) {
            if (e instanceof Player) {
                if (kind.equals("PLAYER") || kind.equals("ANY")) {
                    count++;
                }
            } else if (kind.equals("MONSTER") || kind.equals("HOSTILE")) {
                if (e instanceof Monster) {
                    count++;
                }
            } else if (kind.equals("ANIMAL") || kind.equals("ANIMALS")) {
                if (e instanceof Animals) {
                    count++;
                }
            } else if (kind.equals("ANY")) {
                count++;
            }
        }
        return count >= need;
    }

    private boolean placeholder(String expr, Player p) {
        Matcher m = PAPI_EXPR.matcher(expr);
        if (!m.find()) {
            return true;
        }
        String value = papi(p, m.group(1));
        if (value == null) {
            return true;
        }
        String op = m.group(2);
        String right = m.group(3).trim();
        double l = num(value, Double.NaN);
        double r = num(right, Double.NaN);
        if (!Double.isNaN(l) && !Double.isNaN(r)) {
            return switch (op) {
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                case ">" -> l > r;
                case "<" -> l < r;
                case "!=" -> l != r;
                default -> l == r;
            };
        }
        return switch (op) {
            case "!=" -> !value.equalsIgnoreCase(right);
            default -> value.equalsIgnoreCase(right);
        };
    }

    /** 反射调用 PlaceholderAPI，未安装时返回 null（视为通过） */
    private String papi(Player p, String placeholder) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return null;
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            for (Method m : api.getMethods()) {
                if (!m.getName().equals("setPlaceholders") || m.getParameterCount() != 2) {
                    continue;
                }
                Class<?> first = m.getParameterTypes()[0];
                if (!first.isAssignableFrom(Player.class)) {
                    continue;
                }
                Object result = m.invoke(null, p, placeholder);
                return result == null ? null : String.valueOf(result);
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- 工具 ----------

    private static List<String> split(String v) {
        List<String> out = new ArrayList<>();
        for (String s : v.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean anyMatch(String values, String actual) {
        for (String s : split(values)) {
            if (s.equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    private static String stripNamespace(String s) {
        return s.contains(":") ? s.substring(s.indexOf(':') + 1) : s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static double num(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** "50%" 按最大值比例换算，纯数字按绝对值 */
    private static double ratio(String s, double max) {
        String t = s.trim();
        if (t.endsWith("%")) {
            return num(t.substring(0, t.length() - 1), 0) / 100.0 * max;
        }
        return num(t, 0);
    }

    /** 清理：热重载后重建缓存 */
    public static Set<String> knownTypes() {
        Set<String> s = new HashSet<>(Set.of("permission", "no-permission", "world", "world-not",
                "world-env", "biome", "y-above", "y-below", "time", "weather", "health-above",
                "health-below", "food-above", "food-below", "level-above", "level-below", "gamemode",
                "sneaking", "sprinting", "flying", "on-ground", "in-water", "vehicle", "gliding",
                "swimming", "entities-nearby", "placeholder"));
        return s;
    }

    public List<String> conditionsOf(String key) {
        List<String> out = new ArrayList<>();
        for (String[] c : conditions.computeIfAbsent(key, k -> parse(manager.enchantConfig(k)))) {
            out.add(c[0] + ":" + c[1]);
        }
        return out;
    }

    /** 该附魔配置的触发器（空列表表示全部场景允许） */
    public List<String> triggersOf(String key) {
        List<String> out = new ArrayList<>();
        for (TriggerType t : triggers.computeIfAbsent(key, k -> parseTriggers(manager.enchantConfig(k)))) {
            out.add(t.name().toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }
}
