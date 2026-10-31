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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final NamespacedKey furyKey, enhanceSpd, enhanceMove, enhanceArm, ironKey, graniteKey;
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
        // marking：命中标记目标（下一次增伤）
        int mark = held(p, "marking");
        if (mark > 0) {
            marked.put(victim.getUniqueId(), p.getUniqueId());
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
        // marking：被标记者被近战/箭攻击增伤（此处箭也触发）
        if (marked.containsKey(victim.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.3);
            marked.remove(victim.getUniqueId());
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
