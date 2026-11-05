package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v1.4.2 新增 50 个附魔的集中实现。
 */
public final class NewEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    /** momentum/echo_strike：攻击者 -> 目标连续命中计数 */
    private final Map<UUID, Map<UUID, Integer>> combo = new HashMap<>();
    /** marking：被标记的目标 -> 标记者 */
    private final Map<UUID, UUID> marked = new HashMap<>();
    private final NamespacedKey furyKey, enhanceSpd, enhanceMove, enhanceArm, ironKey, graniteKey, celerityKey;
    private final Map<UUID, String> lastModCache = new HashMap<>();

    public NewEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.furyKey = new NamespacedKey(plugin, "n_fury");
        this.enhanceSpd = new NamespacedKey(plugin, "n_espd");
        this.enhanceMove = new NamespacedKey(plugin, "n_emove");
        this.enhanceArm = new NamespacedKey(plugin, "n_earm");
        this.ironKey = new NamespacedKey(plugin, "n_iron");
        this.graniteKey = new NamespacedKey(plugin, "n_granite");
        this.celerityKey = new NamespacedKey(plugin, "n_celerity");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    private int held(Player p, String key) {
        return Enchants.level(p.getInventory().getItemInMainHand(), key);
    }

    private int armorTotal(Player p, String key) {
        int t = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            if (it != null) {
                t += Enchants.level(it, key);
            }
        }
        return t;
    }

    private static boolean chance(double c) {
        return Math.random() < EnchantManager.clamp01(c);
    }

    /** v1.4.6 被动药水：有等级则补药水，无则清除 */
    private void passiveBuff(Player p, String key, int lvl, PotionEffectType type, int ticks) {
        if (lvl > 0) {
            if (!p.hasPotionEffect(type)) {
                p.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, lvl - 1)));
            }
        } else if (p.hasPotionEffect(type)) {
            p.removePotionEffect(type);
        }
    }

    /** config 生效示范：读取附魔 yml 的 config.chance 作为基础概率（已 clamp 到 [0,1]），再乘等级 */
    private boolean chance(String key, double base, int level) {
        return chance(manager.chance(key, base) * level);
    }

    /** 判断目标是否带有任一正向药水效果 */
    private static final Set<PotionEffectType> BENEFICIAL = new HashSet<>(Arrays.asList(
            PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING,
            PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION,
            PotionEffectType.HEALTH_BOOST, PotionEffectType.ABSORPTION, PotionEffectType.SATURATION,
            PotionEffectType.LUCK, PotionEffectType.SLOW_FALLING));

    private boolean hasBeneficialEffect(LivingEntity e) {
        for (PotionEffect eff : e.getActivePotionEffects()) {
            if (BENEFICIAL.contains(eff.getType())) {
                return true;
            }
        }
        return false;
    }

    // ================= 每秒被动：属性 + 各持续效果 =================
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            // blade_fury 攻速
            int fury = Enchants.level(held, "blade_fury");
            setMod(p, Attribute.GENERIC_ATTACK_SPEED, furyKey,
                    fury > 0 ? 0.2 * fury : 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            // enhance 全属性
            int enh = Enchants.level(held, "enhance");
            setMod(p, Attribute.GENERIC_ATTACK_SPEED, enhanceSpd, enh > 0 ? 0.06 * enh : 0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            setMod(p, Attribute.GENERIC_MOVEMENT_SPEED, enhanceMove, enh > 0 ? 0.03 * enh : 0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            setMod(p, Attribute.GENERIC_ARMOR, enhanceArm, enh > 0 ? 0.05 * enh : 0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            // ironclad 护甲
            int iron = armorTotal(p, "ironclad");
            setMod(p, Attribute.GENERIC_ARMOR, ironKey, iron > 0 ? 0.12 * iron : 0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            // ore_vision：周围矿石发光
            int ov = Enchants.level(held, "ore_vision");
            if (ov > 0) {
                for (int dx = -6; dx <= 6; dx++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        for (int dz = -6; dz <= 6; dz++) {
                            Material m = p.getLocation().add(dx, dy, dz).getBlock().getType();
                            if (isOre(m)) {
                                p.getWorld().spawnParticle(Particle.END_ROD,
                                        p.getLocation().add(dx, dy, dz), 2, 0.5, 0.5, 0.5, 0);
                            }
                        }
                    }
                }
            }
            // hunter_vision：附近敌对生物发光
            if (Enchants.level(p.getInventory().getHelmet(), "hunter_vision") > 0) {
                for (Entity e : p.getNearbyEntities(20, 20, 20)) {
                    if (e instanceof org.bukkit.entity.Monster) {
                        e.setGlowing(true);
                    }
                }
            } else {
                for (Entity e : p.getNearbyEntities(1, 1, 1)) {
                    if (e instanceof org.bukkit.entity.Monster) {
                        e.setGlowing(false);
                    }
                }
            }
            // clear_mind：免疫失明
            if (Enchants.level(p.getInventory().getHelmet(), "clear_mind") > 0) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            // warmth：免疫缓慢/冻结
            if (armorTotal(p, "warmth") > 0) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            // ===== v1.4.6 新增附魔：被动效果 =====
            passiveBuff(p, "turtle_shell", armorTotal(p, "turtle_shell"), PotionEffectType.RESISTANCE, 25 * 20);
            passiveBuff(p, "swift_step", Enchants.level(p.getInventory().getBoots(), "swift_step"), PotionEffectType.SPEED, 25 * 20);
            passiveBuff(p, "nightguard", Enchants.level(p.getInventory().getHelmet(), "nightguard"), PotionEffectType.NIGHT_VISION, 25 * 20);
            passiveBuff(p, "deep_mine", Enchants.level(p.getInventory().getItemInMainHand(), "deep_mine"), PotionEffectType.HASTE, 25 * 20);
            int granite = armorTotal(p, "granite_skin");
            setMod(p, Attribute.GENERIC_ARMOR, graniteKey,
                    granite > 0 ? 1.5 * granite : 0, AttributeModifier.Operation.ADD_NUMBER);
            // echo：移动残影
            if (Enchants.level(p.getInventory().getBoots(), "echo") > 0 && p.isSprinting()) {
                p.getWorld().spawnParticle(Particle.DUST,
                        p.getLocation().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0,
                        new DustOptions(Color.fromRGB(180, 180, 220), 0.8f));
            }
            // mountain_climber：贴墙/攀爬加速
            if (Enchants.level(p.getInventory().getBoots(), "mountain_climber") > 0
                    && p.isClimbing()) {
                p.setVelocity(p.getVelocity().add(p.getLocation().getDirection().multiply(0.2)
                        .setY(0.15)));
            }
            // glider：下落缓慢
            if (Enchants.level(p.getInventory().getBoots(), "glider") > 0
                    && p.getVelocity().getY() < -0.1 && !p.isOnGround()) {
                p.setVelocity(p.getVelocity().multiply(0.5));
            }
            // frost_walker：脚下水冻结
            if (Enchants.level(p.getInventory().getBoots(), "frost_walker") > 0) {
                Block under = p.getLocation().subtract(0, 0.5, 0).getBlock();
                if (under.getType() == Material.WATER) {
                    under.setType(Material.FROSTED_ICE);
                }
            }
            // ===== v1.4.6.3 新增附魔：被动效果 =====
            // second_wind：低血量时再生
            int secondWind = armorTotal(p, "second_wind");
            if (secondWind > 0 && p.getHealth() < p.getMaxHealth() * 0.3) {
                if (!p.hasPotionEffect(PotionEffectType.REGENERATION)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                            25 * 20, Math.max(0, secondWind - 1)));
                }
            } else if (p.hasPotionEffect(PotionEffectType.REGENERATION)) {
                p.removePotionEffect(PotionEffectType.REGENERATION);
            }
            // speed_dash：疾跑加速
            if (Enchants.level(p.getInventory().getBoots(), "speed_dash") > 0 && p.isSprinting()) {
                p.setVelocity(p.getVelocity().add(p.getLocation().getDirection().multiply(0.05).setY(0)));
            }
            // ===== v1.4.6.4 新增附魔：被动效果 =====
            // cleansing：每秒清除负面药水效果
            if (armorTotal(p, "cleansing") > 0) {
                for (PotionEffectType pt : new PotionEffectType[]{PotionEffectType.POISON,
                        PotionEffectType.WITHER, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
                        PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
                        PotionEffectType.MINING_FATIGUE}) {
                    if (p.hasPotionEffect(pt)) {
                        p.removePotionEffect(pt);
                    }
                }
            }
            // celerity：疾跑时移动速度提升
            int celerity = Enchants.level(p.getInventory().getBoots(), "celerity");
            setMod(p, Attribute.GENERIC_MOVEMENT_SPEED, celerityKey,
                    (celerity > 0 && p.isSprinting()) ? 0.12 * celerity : 0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            // ===== v1.4.6.6 对照 EcoEnchants 新增附魔：被动效果 =====
            // fire_warding：着火时概率灭火
            int fireWard = armorTotal(p, "fire_warding");
            if (fireWard > 0 && p.getFireTicks() > 0 && chance("fire_warding", 0.5, fireWard)) {
                p.setFireTicks(0);
            }
            // stamina：疾跑时恢复饱食
            int stamina = Enchants.level(p.getInventory().getBoots(), "stamina");
            if (stamina > 0 && p.isSprinting()) {
                p.setFoodLevel(Math.min(20, p.getFoodLevel() + 1));
            }
            // ===== v1.4.6.8 对标 EcoEnchants 新增 =====
            // suction：把附近掉落物吸过来
            int suction = Enchants.level(p.getInventory().getBoots(), "suction");
            if (suction > 0) {
                double r = 3.0 + suction;
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), r, r, r)) {
                    if (e instanceof Item it) {
                        org.bukkit.util.Vector pull = p.getLocation().toVector()
                                .subtract(it.getLocation().toVector());
                        if (pull.lengthSquared() > 1.0) {
                            it.setVelocity(it.getVelocity()
                                    .add(pull.normalize().multiply(0.35).setY(0.15)));
                        }
                    }
                }
            }
        }
    }

    private void setMod(Player p, Attribute attr, NamespacedKey key, double amount,
                        AttributeModifier.Operation op) {
        AttributeInstance ai = p.getAttribute(attr);
        if (ai == null) {
            return;
        }
        ai.removeModifier(key);
        if (amount != 0) {
            ai.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
    }

    private boolean isOre(Material m) {
        return m.name().contains("COAL_ORE") || m.name().contains("IRON_ORE")
                || m.name().contains("GOLD_ORE") || m.name().contains("DIAMOND_ORE")
                || m.name().contains("EMERALD_ORE") || m.name().contains("REDSTONE_ORE")
                || m.name().contains("LAPIS_ORE") || m.name().contains("COPPER_ORE")
                || m.name().contains("QUARTZ_ORE") || m.name().contains("ANCIENT_DEBRIS");
    }

    /** water_aspect：火系生物 / 末影人 */
    private boolean isFiery(LivingEntity e) {
        org.bukkit.entity.EntityType t = e.getType();
        return t == org.bukkit.entity.EntityType.BLAZE
                || t == org.bukkit.entity.EntityType.MAGMA_CUBE
                || t == org.bukkit.entity.EntityType.GHAST
                || t == org.bukkit.entity.EntityType.ENDERMAN;
    }

    // ================= 近战命中 =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        double dmg = event.getDamage();
        boolean changed = false;
        int gale = held(p, "gale_strike");
        if (gale > 0 && chance("gale_strike", 0.3, gale)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    manager.duration("gale_strike", 60), 0));
            victim.setVelocity(victim.getLocation().getDirection().multiply(1.5).setY(0.6));
        }
        int thunder = held(p, "thunder_clap");
        if (thunder > 0 && chance("thunder_clap", 0.15, thunder)) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    manager.duration("thunder_clap", 100), 3));
            dmg *= 1.3;
            changed = true;
        }
        int sunder = held(p, "sunder");
        if (sunder > 0 && chance("sunder", 0.25, sunder)) {
            dmg += 2.0 * sunder; // 破甲重击
            changed = true;
        }
        // momentum：连续命中增伤
        int momentum = held(p, "momentum");
        if (momentum > 0) {
            Map<UUID, Integer> cm = combo.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            int c = cm.getOrDefault(victim.getUniqueId(), 0) + 1;
            cm.put(victim.getUniqueId(), c);
            dmg *= (1 + 0.08 * momentum * Math.min(c, 5));
            changed = true;
        }
        int rend = held(p, "rend");
        if (rend > 0 && chance("rend", 0.3, rend)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    manager.duration("rend", 80), 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
        }
        int stagger = held(p, "stagger");
        if (stagger > 0 && chance("stagger", 0.25, stagger)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
        }
        int ghost = held(p, "ghost_blade");
        if (ghost > 0) {
            dmg *= (1 + 0.15 * ghost); // 部分无视护甲
            changed = true;
        }
        int echo = held(p, "echo_strike");
        if (echo > 0 && chance("echo_strike", 0.2, echo)) {
            dmg *= (1 + 0.4 * echo); // 多段爆发
            changed = true;
        }
        int pursuit = held(p, "pursuit");
        if (pursuit > 0 && victim instanceof Player && ((Player) victim).isSprinting()) {
            dmg *= (1 + 0.25 * pursuit);
            changed = true;
        }
        if (changed) {
            event.setDamage(dmg);
        }
        // ===== v1.4.6 新增附魔：命中效果 =====
        int soul = held(p, "soul_rend");
        if (soul > 0 && chance("soul_rend", 0.35, soul)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
        }
        int hexLvl = held(p, "hex");
        if (hexLvl > 0 && chance("hex", 0.4, hexLvl)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
        }
        int thunderFury = held(p, "thunder_fury");
        if (thunderFury > 0 && chance("thunder_fury", 0.15, thunderFury)) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            dmg *= 1.2;
            event.setDamage(dmg);
        }
        int siphon = held(p, "siphoning");
        if (siphon > 0) {
            double heal = event.getDamage() * 0.05 * siphon;
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
        }
        // ===== v1.4.6.3 新增附魔：命中效果 =====
        int frostBrand = held(p, "frost_brand");
        if (frostBrand > 0 && chance("frost_brand", 0.35, frostBrand)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    manager.duration("frost_brand", 60), 1));
            dmg *= 1.15;
            event.setDamage(dmg);
        }
        int culling = held(p, "culling_blade");
        if (culling > 0 && victim.getHealth() < victim.getMaxHealth() * 0.3) {
            dmg *= (1 + 0.3 * culling);
            event.setDamage(dmg);
        }
        int arcane = held(p, "arcane_strike");
        if (arcane > 0 && chance("arcane_strike", 0.15, arcane)) {
            victim.damage(3.0 * arcane, p);
        }
        int crushing = held(p, "crushing_blow");
        if (crushing > 0 && chance("crushing_blow", 0.3, crushing)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
            dmg *= 1.2;
            event.setDamage(dmg);
        }
        // ===== v1.4.6.4 新增附魔：命中效果 =====
        // glacial_grip：强减速
        int glacial = held(p, "glacial_grip");
        if (glacial > 0 && chance("glacial_grip", 0.35, glacial)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    manager.duration("glacial_grip", 100), Math.max(0, glacial)));
            dmg *= 1.15;
            event.setDamage(dmg);
        }
        // punisher：对带正向药水效果的目标增伤
        int punish = held(p, "punisher");
        if (punish > 0 && hasBeneficialEffect(victim)) {
            dmg *= (1 + 0.25 * punish);
            event.setDamage(dmg);
        }
        // interrupt：命中清除目标正向药水效果
        int interrupt = held(p, "interrupt");
        if (interrupt > 0 && chance("interrupt", 0.4, interrupt)) {
            for (PotionEffect eff : new ArrayList<>(victim.getActivePotionEffects())) {
                if (BENEFICIAL.contains(eff.getType())) {
                    victim.removePotionEffect(eff.getType());
                }
            }
        }
        // marking：命中标记目标（下一次增伤）
        int mark = held(p, "marking");
        if (mark > 0) {
            marked.put(victim.getUniqueId(), p.getUniqueId());
        }
        // ===== v1.4.6.6 对照 EcoEnchants 新增附魔：命中效果 =====
        // goliath：对血量高于自己的敌人增伤
        int goliath = held(p, "goliath");
        if (goliath > 0 && victim.getHealth() > p.getHealth()) {
            event.setDamage(event.getDamage() * (1 + 0.1 * goliath));
        }
        // water_aspect：对燃烧生物与火系/末影人增伤
        int waterAspect = held(p, "water_aspect");
        if (waterAspect > 0 && (victim.getFireTicks() > 0 || isFiery(victim))) {
            event.setDamage(event.getDamage() * (1 + 0.15 * waterAspect));
        }
        // confusion：概率打乱对手快捷栏
        int confusion = held(p, "confusion");
        if (confusion > 0 && victim instanceof Player vp
                && chance("confusion", 0.3, confusion)) {
            for (int i = 0; i < 4; i++) {
                int a = (int) (Math.random() * 9), b = (int) (Math.random() * 9);
                org.bukkit.inventory.PlayerInventory inv = vp.getInventory();
                ItemStack tmp = inv.getItem(a);
                inv.setItem(a, inv.getItem(b));
                inv.setItem(b, tmp);
            }
        }
        // valiant：贴脸近身时增伤
        int valiant = held(p, "valiant");
        if (valiant > 0 && p.getLocation().distance(victim.getLocation()) < 3) {
            event.setDamage(event.getDamage() * (1 + 0.1 * valiant));
        }
        // ===== v1.4.6.8 对标 EcoEnchants 新增 =====
        // cubism：对史莱姆/岩浆怪增伤
        int cubism = held(p, "cubism");
        if (cubism > 0) {
            EntityType vt = victim.getType();
            if (vt == EntityType.SLIME || vt == EntityType.MAGMA_CUBE) {
                event.setDamage(event.getDamage() * (1 + 0.15 * cubism));
            }
        }
        // insecticide：对虫类增伤
        int insecticide = held(p, "insecticide");
        if (insecticide > 0) {
            EntityType vt = victim.getType();
            if (vt == EntityType.SPIDER || vt == EntityType.CAVE_SPIDER
                    || vt == EntityType.SILVERFISH || vt == EntityType.ENDERMITE) {
                event.setDamage(event.getDamage() * (1 + 0.15 * insecticide));
            }
        }
        // plague：命中概率让周围敌人中毒
        int plague = held(p, "plague");
        if (plague > 0 && chance("plague", 0.25, plague)) {
            for (Entity e : victim.getWorld().getNearbyEntities(victim.getLocation(), 4, 4, 4)) {
                if (e instanceof LivingEntity le && !le.equals(p) && !le.equals(victim)) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                            manager.duration("plague", 100), plague - 1));
                }
            }
        }
        // overload：概率重击，代价是自身掉血
        int overload = held(p, "overload");
        if (overload > 0 && chance("overload", 0.2, overload)) {
            event.setDamage(event.getDamage() * 1.5);
            p.damage(1.0 * overload);
        }
        // voltaic：命中概率电击周围敌人
        int voltaic = held(p, "voltaic");
        if (voltaic > 0 && chance("voltaic", 0.15, voltaic)) {
            for (Entity e : victim.getWorld().getNearbyEntities(victim.getLocation(), 2, 2, 2)) {
                if (e instanceof LivingEntity le && !le.equals(p) && !le.equals(victim)) {
                    le.damage(2.0 * voltaic, p);
                }
            }
        }
    }

    // ================= 受击/护甲/防具 =================
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        double finalDmg = event.getFinalDamage();
        // 标记增伤：若被标记者攻击（在 onHit 无法直接联动，这里只做减伤防御）
        // parry / counter：格挡时免疫并反击
        int parry = Enchants.level(held, "parry");
        int counter = Enchants.level(held, "counter");
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (p.isBlocking()) {
            if (parry > 0) {
                event.setDamage(0);
                if (damager instanceof LivingEntity le && le != p) {
                    le.damage(finalDmg * 0.5, p);
                }
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1, 1);
            } else if (counter > 0 && chance("counter", 0.2, counter)) {
                event.setDamage(event.getDamage() * 0.5);
                if (damager instanceof LivingEntity le && le != p) {
                    le.damage(finalDmg * 0.5, p);
                }
            }
        }
        // ghost_blade：概率闪避
        if (held(p, "ghost_blade") > 0 && chance("ghost_blade", 0.12, held(p, "ghost_blade"))) {
            event.setDamage(0);
            return;
        }
        // 护甲附魔
        int fortress = armorTotal(p, "fortress");
        if (fortress > 0 && chance("fortress", 0.25, fortress)) {
            event.setDamage(event.getDamage() * 0.4);
        }
        int aegis = armorTotal(p, "aegis");
        if (aegis > 0) {
            event.setDamage(event.getDamage() * (1 - 0.12 * aegis));
        }
        int lastStand = armorTotal(p, "last_stand");
        if (lastStand > 0 && p.getHealth() < p.getMaxHealth() * 0.3) {
            event.setDamage(event.getDamage() * 0.4);
        }
        int bloodArmor = armorTotal(p, "blood_armor");
        if (bloodArmor > 0 && finalDmg > 0) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + finalDmg * 0.2 * bloodArmor));
        }
        int divine = armorTotal(p, "divine_protection");
        if (divine > 0 && chance("divine_protection", 0.08, divine)) {
            event.setDamage(0);
        }
        int blessing = armorTotal(p, "blessing");
        if (blessing > 0 && chance("blessing", 0.2, blessing)) {
            for (PotionEffectType t : new PotionEffectType[]{
                    PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.WEAKNESS,
                    PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS}) {
                p.removePotionEffect(t);
            }
        }
        // curse_break：诅咒惩罚减少
        int cb = Enchants.level(held, "curse_break");
        if (cb > 0) {
            event.setDamage(event.getDamage() * 0.9);
        }
        // 靴：stomper 落地震击 / glider 减摔落
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (Enchants.level(p.getInventory().getBoots(), "glider") > 0) {
                event.setDamage(event.getDamage() * 0.5);
            }
            if (Enchants.level(p.getInventory().getBoots(), "stomper") > 0) {
                for (Entity e : p.getNearbyEntities(3, 1, 3)) {
                    if (e != p && e instanceof LivingEntity le) {
                        le.damage(4.0, p);
                        le.setVelocity(le.getVelocity().add(new org.bukkit.util.Vector(0, 1, 0)));
                    }
                }
            }
        }
        // warmth：减免冰冻
        if (armorTotal(p, "warmth") > 0
                && event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            event.setDamage(event.getDamage() * 0.5);
        }
        // ===== v1.4.6.3 新增附魔：护甲 =====
        // frost_guard：大幅减免冻结伤害
        if (armorTotal(p, "frost_guard") > 0
                && event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            event.setDamage(event.getDamage() * 0.2);
        }
        // ===== v1.4.6.4 新增附魔：护甲 =====
        // stalwart：高血量时减免伤害
        int stalwart = armorTotal(p, "stalwart");
        if (stalwart > 0 && p.getHealth() > p.getMaxHealth() * 0.6) {
            event.setDamage(event.getDamage() * (1 - 0.08 * stalwart));
        }
        // retribution：受击概率反击
        int retribution = armorTotal(p, "retribution");
        if (retribution > 0 && chance("retribution", 0.3, retribution)) {
            org.bukkit.damage.DamageSource ds = event.getDamageSource();
            Entity dm = ds == null ? null : ds.getCausingEntity();
            if (dm instanceof LivingEntity le && le != p) {
                le.damage(event.getDamage() * 0.4, p);
            }
        }
        // ===== v1.4.6.6 对照 EcoEnchants 新增附魔：受击效果 =====
        // blazing：受击概率让攻击者着火
        int blazing = armorTotal(p, "blazing");
        if (blazing > 0 && damager instanceof LivingEntity le && le != p
                && chance("blazing", 0.3, blazing)) {
            le.setFireTicks(60);
        }
    }

    // ================= 射箭 =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null) {
            return;
        }
        int rapid = Enchants.level(bow, "rapid_fire");
        if (rapid > 0) {
            org.bukkit.util.Vector v = event.getProjectile().getVelocity();
            event.getProjectile().setVelocity(v.multiply(1.3));
        }
        int scat = Enchants.level(bow, "scattershot");
        if (scat > 0) {
            for (int i = 0; i < scat; i++) {
                org.bukkit.entity.Projectile pr = (Projectile) p.launchProjectile(Arrow.class);
                org.bukkit.util.Vector v = p.getLocation().getDirection()
                        .add(new org.bukkit.util.Vector(
                                (Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.2,
                                (Math.random() - 0.5) * 0.4)).normalize();
                pr.setVelocity(v.multiply(2.0));
            }
        }
        int rain = Enchants.level(bow, "arrow_rain");
        if (rain > 0 && chance("arrow_rain", 0.15, rain)) {
            for (int i = 0; i < rain * 3; i++) {
                org.bukkit.Location loc = p.getTargetBlock(null, 30).getLocation()
                        .add(Math.random() * 4 - 2, 20, Math.random() * 4 - 2);
                Arrow ar = loc.getWorld().spawn(loc, Arrow.class);
                ar.setShooter(p);
                ar.setVelocity(new org.bukkit.util.Vector(0, -1.5, 0));
                ar.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            }
        }
        // ===== v1.4.6.6 对照 EcoEnchants 新增附魔：射出效果 =====
        // juggler：箭矢无视重力
        if (Enchants.level(bow, "juggler") > 0) {
            event.getProjectile().setGravity(false);
        }
    }

    // ================= 箭矢命中 =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player p)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack bow = p.getInventory().getItemInMainHand();
        int chilling = Enchants.level(bow, "chilling");
        if (chilling > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
        }
        int spark = Enchants.level(bow, "spark");
        if (spark > 0 && chance("spark", 0.3, spark)) {
            victim.setFireTicks(manager.duration("spark", 60));
            for (Entity e : victim.getNearbyEntities(2, 2, 2)) {
                if (e instanceof LivingEntity le) {
                    le.setFireTicks(40);
                }
            }
        }
        int voidShot = Enchants.level(bow, "void_shot");
        if (voidShot > 0 && victim.getHealth() < victim.getMaxHealth() * 0.2
                && chance(0.05 + 0.05 * voidShot)) {
            victim.damage(victim.getHealth() + 1.0, p);
            event.setDamage(0);
        }
        int trajectory = Enchants.level(bow, "trajectory");
        if (trajectory > 0) {
            projectile.setGravity(false);
        }
        // ===== v1.4.6 新增附魔：余烬（箭点燃灵魂火） =====
        int ember = Enchants.level(bow, "ember");
        if (ember > 0 && chance("ember", 0.3, ember)) {
            victim.setFireTicks(80);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
        }
        // ===== v1.4.6.3 新增附魔：箭矢效果 =====
        int heavy = Enchants.level(bow, "heavy_arrow");
        if (heavy > 0) {
            event.setDamage(event.getDamage() * (1 + 0.2 * heavy));
            victim.setVelocity(victim.getLocation().getDirection().multiply(0.6).setY(0.4));
        }
        int iceTip = Enchants.level(bow, "ice_tip");
        if (iceTip > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    manager.duration("ice_tip", 100), 1));
        }
        // ===== v1.4.6.4 新增附魔：箭矢效果 =====
        // debilitating_shot：箭命中虚弱 + 缓慢
        int debilitate = Enchants.level(bow, "debilitating_shot");
        if (debilitate > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
        }
        // corrosive_arrow：箭命中削弱目标护甲
        int corrosive = Enchants.level(bow, "corrosive_arrow");
        if (corrosive > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
        }
        // marking：被标记者被近战/箭攻击增伤（此处箭也触发）
        if (marked.containsKey(victim.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.3);
            marked.remove(victim.getUniqueId());
        }
        // ===== v1.4.6.6 对照 EcoEnchants 新增附魔：箭矢命中效果 =====
        // sniper：射程越远伤害越高
        int sniper = Enchants.level(bow, "sniper");
        if (sniper > 0) {
            double dist = p.getLocation().distance(victim.getLocation());
            if (dist > 8) {
                event.setDamage(event.getDamage() * (1 + 0.05 * sniper * Math.min(3, dist / 8)));
            }
        }
        // ===== v1.4.6.8 对标 EcoEnchants 新增 =====
        // precision：概率暴击
        int precision = Enchants.level(bow, "precision");
        if (precision > 0 && chance("precision", 0.15, precision)) {
            event.setDamage(event.getDamage() * 1.5);
        }
        // ricochet：命中后弹射伤害附近另一敌人
        int ricochet = Enchants.level(bow, "ricochet");
        if (ricochet > 0 && chance("ricochet", 0.2, ricochet)) {
            LivingEntity second = null;
            double best = Double.MAX_VALUE;
            for (Entity e : victim.getWorld().getNearbyEntities(victim.getLocation(), 5, 5, 5)) {
                if (e instanceof LivingEntity le && !le.equals(victim) && !le.equals(p)
                        && !(le instanceof Player)) {
                    double d = le.getLocation().distance(victim.getLocation());
                    if (d < best) {
                        best = d;
                        second = le;
                    }
                }
            }
            if (second != null) {
                second.damage(event.getDamage() * 0.5, p);
            }
        }
        // turbulence：命中产生的气流击退周围敌人
        int turbulence = Enchants.level(bow, "turbulence");
        if (turbulence > 0 && chance("turbulence", 0.15, turbulence)) {
            for (Entity e : victim.getWorld().getNearbyEntities(victim.getLocation(), 3, 3, 3)) {
                if (e instanceof LivingEntity le && !le.equals(p) && !le.equals(victim)) {
                    org.bukkit.util.Vector push = le.getLocation().toVector()
                            .subtract(victim.getLocation().toVector()).normalize().multiply(0.6)
                            .setY(0.3);
                    le.setVelocity(le.getVelocity().add(push));
                }
            }
        }
    }

    // ================= 挖掘 =================
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        Block b = event.getBlock();
        Material m = b.getType();
        int greed = Enchants.level(held, "greed");
        if (greed > 0 && chance("greed", 0.2, greed) && m.isItem()) {
            for (ItemStack it : b.getDrops(held)) {
                b.getWorld().dropItemNaturally(b.getLocation(), it.clone());
            }
        }
        int resource = Enchants.level(held, "resourceful");
        if (resource > 0 && isOre(m) && chance("resourceful", 0.15, resource)) {
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(m));
        }
        int fury = Enchants.level(held, "mining_fury");
        if (fury > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, fury - 1));
        }
        int compact = Enchants.level(held, "compact");
        if (compact > 0) {
            p.giveExp(2 * compact);
        }
        int xpb = Enchants.level(held, "xp_boost");
        if (xpb > 0) {
            p.giveExp(1 + xpb);
        }
        int tunnel = Enchants.level(held, "tunneling");
        if (tunnel > 0) {
            org.bukkit.util.Vector dir = p.getLocation().getDirection();
            for (int i = 0; i < tunnel + 1; i++) {
                Block nb = b.getRelative(dir.getBlockX() * (i + 1),
                        dir.getBlockY() * (i + 1), dir.getBlockZ() * (i + 1));
                if (nb.getType() != Material.AIR && nb.getType() != Material.BEDROCK) {
                    nb.breakNaturally(held);
                }
            }
        }
        int quarry = Enchants.level(held, "quarry");
        if (quarry > 0) {
            for (int i = 1; i <= quarry + 1; i++) {
                Block nb = b.getRelative(0, -i, 0);
                if (nb.getType() != Material.AIR && nb.getType() != Material.BEDROCK) {
                    nb.breakNaturally(held);
                }
            }
        }
        // ===== v1.4.6.3 新增附魔：挖掘效果 =====
        // ore_magnet：挖掘矿石时吸引附近掉落物
        int oreMag = Enchants.level(held, "ore_magnet");
        if (oreMag > 0 && isOre(m)) {
            for (Entity e : b.getWorld().getNearbyEntities(b.getLocation(), 6, 6, 6)) {
                if (e instanceof Item it) {
                    org.bukkit.util.Vector pull = b.getLocation().toVector()
                            .subtract(e.getLocation().toVector()).normalize().multiply(0.5);
                    e.setVelocity(e.getVelocity().add(pull).setY(0.4));
                }
            }
        }
        // mining_expert：挖掘额外经验
        int mineExpert = Enchants.level(held, "mining_expert");
        if (mineExpert > 0) {
            p.giveExp(1 + mineExpert);
        }
        // ===== v1.4.6.4 新增附魔：挖掘效果 =====
        // timberlord：斧砍原木概率双倍掉落
        int timber = Enchants.level(held, "timberlord");
        if (timber > 0 && held.getType().name().contains("_AXE")
                && m.name().endsWith("_LOG") && chance("timberlord", 0.3, timber)) {
            for (ItemStack it : b.getDrops(held)) {
                b.getWorld().dropItemNaturally(b.getLocation(), it.clone());
            }
        }
        // prospecting：挖掘矿石概率额外掉落金粒
        int prospecting = Enchants.level(held, "prospecting");
        if (prospecting > 0 && isOre(m) && chance("prospecting", 0.2, prospecting)) {
            b.getWorld().dropItemNaturally(b.getLocation(),
                    new ItemStack(Material.GOLD_NUGGET, 1 + prospecting));
        }
        // ===== v1.4.6.6 对照 EcoEnchants 新增附魔：挖掘效果 =====
        // oxygenate：水下挖矿恢复氧气
        int oxygenate = Enchants.level(held, "oxygenate");
        if (oxygenate > 0 && p.isInWater()) {
            p.setRemainingAir(p.getMaximumAir());
        }
        // ===== v1.4.6.8 对标 EcoEnchants 新增 =====
        // replenish：收获成熟作物后自动补种（延后 1 tick，等原方块真正被移除）
        int replenish = Enchants.level(held, "replenish");
        if (replenish > 0 && b.getBlockData() instanceof org.bukkit.block.data.Ageable age
                && age.getAge() == age.getMaximumAge()) {
            Material cropType = m;
            ItemStack seed = null;
            if (cropType == Material.WHEAT) {
                seed = new ItemStack(Material.WHEAT_SEEDS);
            } else if (cropType == Material.CARROTS) {
                seed = new ItemStack(Material.CARROT);
            } else if (cropType == Material.POTATOES) {
                seed = new ItemStack(Material.POTATO);
            } else if (cropType == Material.BEETROOTS) {
                seed = new ItemStack(Material.BEETROOT_SEEDS);
            }
            if (seed != null && p.getInventory().containsAtLeast(seed, 1)) {
                p.getInventory().removeItem(seed);
                org.bukkit.Location cropLoc = b.getLocation();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Block rb = cropLoc.getBlock();
                    if (rb.getType() == Material.AIR) {
                        rb.setType(cropType);
                    }
                });
            }
        }
    }

    // ================= 击杀 =================
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity dead)) {
            return;
        }
        Player p = dead.getKiller();
        if (p == null) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        int gold = Enchants.level(held, "gold_rush");
        if (gold > 0) {
            p.giveExp(5 * gold);
            p.getWorld().dropItemNaturally(dead.getLocation(),
                    new ItemStack(Material.GOLD_NUGGET, gold));
        }
        int lucky = Enchants.level(held, "lucky");
        if (lucky > 0 && chance("lucky", 0.1, lucky)) {
            for (ItemStack drop : new ArrayList<>(event.getDrops())) {
                p.getWorld().dropItemNaturally(dead.getLocation(), drop.clone());
            }
        }
        int prosperity = Enchants.level(held, "prosperity");
        if (prosperity > 0) {
            p.giveExp(3 * prosperity);
        }
        int enhance = Enchants.level(held, "enhance");
        if (enhance > 0) {
            p.giveExp(2 * enhance);
        }
    }

    // ================= time_ward：死亡保留经验 =================
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (Enchants.level(held, "time_ward") > 0) {
            event.setKeepLevel(true);
            event.setKeepInventory(true);
            p.sendMessage(manager.prefix() + manager.color("&e时间护符护住了你的经验。"));
        }
    }

    /** 热重载时清理运行时状态（连击计数 / 标记目标），避免 reload 后残留旧数据 */
    public void clearState() {
        combo.clear();
        marked.clear();
    }
}
