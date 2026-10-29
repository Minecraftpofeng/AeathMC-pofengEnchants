package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 概念性防具 · 无尽系列（4 件套，专属绑定 ClowmPierce）：
 *  通用特性：巫师袍2（命中附加魔法伤害）/ 概念性防御3（-30%承受）/
 *           无尽反应胸甲（存储50%受击伤害，空手右键释放AOE）/
 *           衔尾蛇2（35%反弹）/ 龙之愤怒（受击叠层增伤+龙息）/
 *           无尽力量（+20%伤害）/ 魔童（低血崩飞AOE）
 *  部位专属：寰宇支配（持剑+10%）/ 世界崩解（潜行+左键开潜影箱）/
 *           天堂陨落（弓+100%）/ 自然荒芜（潜行+右键植物枯萎）/ 星球吞噬（铲子3x3）
 *  全套4件：无尽药水buff（30s）+ 全套抗性减免（免疫除虚空/魔法外伤害）+ 不死图腾5次
 *  ClowmPierce 专属：怒火中烧（头盔，受击后增伤）/ 终结（胸甲，处决低血）/
 *            审判之眼（护腿，概率真伤）/ 暗黑戒指（鞋子，夜间增伤+移速）
 */
public final class ArmorArtifactListener implements Listener {

    private static final double WIZARD_CHANCE = 0.45;   // 巫师袍2
    private static final double CONCEPT_REDUCE = 0.30;  // 概念性防御3
    private static final double OROBOROS_CHANCE = 0.35; // 衔尾蛇2
    private static final double STORE_RATIO = 0.50;
    private static final double STORE_MAX = 50.0;
    private static final int    DRAGON_CAP = 5;
    private static final long   DRAGON_MS = 15000;
    private static final long   WITHER_CD_MS = 180000;

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private final Random rnd = new Random();

    /** 无尽反应胸甲：存储伤害（UUID -> 已存储） */
    private final Map<UUID, Double> stored = new HashMap<>();
    /** 龙之愤怒层数（UUID -> 层数） */
    private final Map<UUID, Integer> dragonLayer = new HashMap<>();
    private final Map<UUID, Long> dragonExpire = new HashMap<>();
    /** 魔童冷却 */
    private final Map<UUID, Long> magickCd = new HashMap<>();
    /** 自然荒芜冷却 */
    private final Map<UUID, Long> witherCd = new HashMap<>();
    /** 怒火中烧状态（受击后 3 秒） */
    private final Map<UUID, Long> furyExpire = new HashMap<>();
    /** 不死图腾剩余次数 */
    private final Map<UUID, Integer> totem = new HashMap<>();
    /** 世界崩解潜影箱（内存） */
    private final Map<UUID, Inventory> shulkers = new HashMap<>();
    /** 本插件开启飞行的玩家（非创造，需还原） */
    private final Set<UUID> armorFlight = new HashSet<>();

    public ArmorArtifactListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 40L);
    }

    // ---------- 穿戴判定 ----------

    private boolean wearing(Player p, String piece) {
        ItemStack it = switch (piece) {
            case "helmet" -> p.getInventory().getHelmet();
            case "chest" -> p.getInventory().getChestplate();
            case "legs" -> p.getInventory().getLeggings();
            default -> p.getInventory().getBoots();
        };
        return it != null && manager.isArmorArtifact(it) && piece.equals(manager.armorPiece(it));
    }

    private int countSet(Player p) {
        int c = 0;
        for (String s : new String[]{"helmet", "chest", "legs", "boots"}) {
            if (wearing(p, s)) c++;
        }
        return c;
    }

    private boolean fullSet(Player p) {
        return countSet(p) == 4;
    }

    // ---------- 每秒循环：地蕴复生 / 全套buff / 专属移速 / 状态清理 ----------

    private void tick() {
        long now = System.currentTimeMillis();
        dragonExpire.entrySet().removeIf(e -> e.getValue() < now);
        furyExpire.entrySet().removeIf(e -> e.getValue() < now);
        dragonLayer.keySet().removeIf(u -> !dragonExpire.containsKey(u));
        for (Player p : Bukkit.getOnlinePlayers()) {
            int set = countSet(p);
            if (set == 0) {
                continue;
            }
            // 地蕴复生：生命恢复加速
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1, false, false, false));
            if (fullSet(p)) {
                applySetBuff(p);
                applyFlight(p);
            } else if (armorFlight.remove(p.getUniqueId())) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
            // ClowmPierce 专属：暗黑戒指移速
            if (manager.isArmorOwner(p.getName()) && wearing(p, "boots")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false, false));
            }
        }
    }

    private void applySetBuff(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 9, false, false, false));        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 600, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 9, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 4, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 600, 9, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 2, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 9, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 600, 1, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 600, 4, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 600, 4, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 600, 1, false, false, false));
    }

    /** 全套飞行：生存/冒险自动 /fly，创造与旁观不干预 */
    private void applyFlight(Player p) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return;
        }
        armorFlight.add(p.getUniqueId());
        p.setAllowFlight(true);
        if (!p.isFlying()) {
            p.setFlying(true);
        }
    }

    // ---------- 命中：寰宇支配 / 天堂陨落 / 无尽力量 / 龙之愤怒 / 巫师袍 / 专属 ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity vic)) {
            return;
        }
        if (countSet(p) == 0) {
            return;
        }
        double dmg = e.getDamage();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean sword = hand != null && !hand.getType().isAir() && hand.getType().name().endsWith("_SWORD");
        boolean bow = hand != null && (hand.getType() == Material.BOW || hand.getType() == Material.CROSSBOW);
        // 寰宇支配（头盔）：持剑 +30%
        if (sword && wearing(p, "helmet")) {
            dmg *= 1.30;
        }
        // 天堂陨落（护腿/鞋）：弓 +100%
        if (bow && (wearing(p, "legs") || wearing(p, "boots"))) {
            dmg *= 2.0;
        }
        // 无尽力量：+20%
        dmg *= 1.20;
        // 龙之愤怒层数
        int layer = dragonLayer.getOrDefault(p.getUniqueId(), 0);
        if (layer > 0) {
            dmg *= (1 + 0.10 * layer);
        }
        boolean owner = manager.isArmorOwner(p.getName());
        if (owner) {
            // 终结（胸甲）：目标生命 <30% 处决
            if (wearing(p, "chest") && vic.getHealth() < vic.getMaxHealth() * 0.30) {
                dmg *= 2.5;
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, vic.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.01);
            }
            // 暗黑戒指（鞋子）：夜间 +40%
            long t = p.getWorld().getTime();
            if (wearing(p, "boots") && t >= 13000 && t < 23000) {
                dmg *= 1.4;
            }
            // 审判之眼（护腿）：20% 概率真实伤害
            if (wearing(p, "legs") && rnd.nextDouble() < 0.20) {
                vic.damage(dmg * 0.30, p);
                p.getWorld().spawnParticle(Particle.CRIT, vic.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.01);
            }
            // 怒火中烧（头盔）：受击后 3 秒内 +25%
            if (wearing(p, "helmet")
                    && furyExpire.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
                dmg *= 1.25;
            }
        }
        e.setDamage(dmg);
        // 巫师袍2：45% 概率附加魔法伤害（真伤）
        if (rnd.nextDouble() < WIZARD_CHANCE) {
            vic.damage(dmg * 0.45, p);
            p.getWorld().spawnParticle(Particle.WITCH, vic.getLocation().add(0, 1, 0), 10, 0.4, 0.4, 0.4, 0.01);
        }
    }

    // ---------- 受击：概念性防御 / 全套抗性 / 衔尾蛇 / 无尽反应胸甲 / 龙之愤怒 / 魔童 / 怒火中烧 ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        if (countSet(p) == 0) {
            return;
        }
        double dmg = e.getDamage();
        // 无尽反应胸甲：存储 50% 受击伤害（全套免疫时也存储，用原始伤害）
        if (wearing(p, "chest")) {
            double st = stored.getOrDefault(p.getUniqueId(), 0.0) + dmg * STORE_RATIO;
            stored.put(p.getUniqueId(), Math.min(st, STORE_MAX));
        }
        // 龙之愤怒：受击叠层 + 脚底龙息
        int dl = dragonLayer.getOrDefault(p.getUniqueId(), 0);
        dragonLayer.put(p.getUniqueId(), Math.min(DRAGON_CAP, dl + 1));
        dragonExpire.put(p.getUniqueId(), System.currentTimeMillis() + DRAGON_MS);
        spawnDragonBreath(p);
        long now = System.currentTimeMillis();
        // 魔童：低血崩飞
        if (p.getHealth() < p.getMaxHealth() * 0.25
                && magickCd.getOrDefault(p.getUniqueId(), 0L) < now) {
            magickCd.put(p.getUniqueId(), now + 20000);
            for (Entity en : p.getWorld().getNearbyEntities(p.getLocation(), 30, 30, 30)) {
                if (en instanceof LivingEntity le && le != p) {
                    le.damage(40, p);
                    le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector())
                            .normalize().multiply(2.2).setY(1.2));
                }
            }
            p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        }
        // 怒火中烧（头盔）：受击后 3 秒增伤
        if (manager.isArmorOwner(p.getName()) && wearing(p, "helmet")) {
            furyExpire.put(p.getUniqueId(), now + 3000);
        }
        // 全套抗性：免疫除虚空/魔法/龙息/自定义外伤害
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (fullSet(p)
                && cause != DamageCause.VOID && cause != DamageCause.MAGIC
                && cause != DamageCause.DRAGON_BREATH && cause != DamageCause.CUSTOM) {
            e.setDamage(0);
            p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.01);
            return;
        }
        // 概念性防御3：-30% 承受伤害
        dmg *= (1 - CONCEPT_REDUCE);
        e.setDamage(dmg);
        Entity src = e.getDamageSource() == null ? null : e.getDamageSource().getCausingEntity();
        // 衔尾蛇2：35% 反弹
        if (src instanceof LivingEntity le && le != p && rnd.nextDouble() < OROBOROS_CHANCE) {
            le.damage(dmg * 0.5, p);
            le.getWorld().spawnParticle(Particle.ENCHANT, le.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0);
        }
    }

    private void spawnDragonBreath(Player p) {
        try {
            AreaEffectCloud cloud = (AreaEffectCloud) p.getWorld()
                    .spawnEntity(p.getLocation().add(0, 0.4, 0), EntityType.AREA_EFFECT_CLOUD);
            cloud.setRadius(2.5f);
            cloud.setRadiusPerTick(-0.01f);
            cloud.setDuration(60);
            cloud.setWaitTime(5);
            cloud.setParticle(Particle.DRAGON_BREATH);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0), true);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0), true);
        } catch (Throwable ignored) {
        }
    }

    // ---------- 无尽反应胸甲释放（空手右键）+ 自然荒芜（潜行+右键） ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        boolean owner = manager.isArmorOwner(p.getName());
        // 自然荒芜（护腿）：潜行 + 右键
        if (p.isSneaking() && owner && wearing(p, "legs")) {
            long now = System.currentTimeMillis();
            if (witherCd.getOrDefault(p.getUniqueId(), 0L) < now) {
                witherCd.put(p.getUniqueId(), now + WITHER_CD_MS);
                int count = witherPlants(p);
                p.sendMessage(manager.prefix()
                        + manager.color("&a自然荒芜已释放，枯萎 &f" + count + "&a 株植物（冷却 180s）。"));
            } else {
                long left = (witherCd.getOrDefault(p.getUniqueId(), 0L) - now) / 1000;
                p.sendMessage(manager.prefix() + manager.color("&c自然荒芜冷却中，还需 &f" + left + "&c 秒。"));
            }
            e.setCancelled(true);
            return;
        }
        // 无尽反应胸甲释放：空手右键
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand != null && !hand.getType().isAir()) {
            return;
        }
        if (!wearing(p, "chest")) {
            return;
        }
        Double st = stored.get(p.getUniqueId());
        if (st == null || st <= 0) {
            p.sendMessage(manager.prefix() + manager.color("&7无尽反应胸甲当前无存储伤害。"));
            return;
        }
        stored.remove(p.getUniqueId());
        int hit = 0;
        for (Entity en : p.getWorld().getNearbyEntities(p.getLocation(), 4.5, 4.5, 4.5)) {
            if (en instanceof LivingEntity le && le != p) {
                le.damage(st, p);
                hit++;
            }
        }
        p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 20, 1.5, 1, 1.5, 0.1);
        p.sendMessage(manager.prefix() + manager.color("&a释放存储伤害 &f" + st + "&a 点，命中 &f" + hit + "&a 个目标。"));
        e.setCancelled(true);
    }

    private int witherPlants(Player p) {
        Location c = p.getLocation();
        int count = 0;
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    Block b = c.getBlock().getRelative(dx, dy, dz);
                    if (b.getBlockData() instanceof Ageable a) {
                        a.setAge(0);
                        b.setBlockData(a, false);
                        count++;
                    } else if (isPlant(b.getType())) {
                        b.setType(Material.AIR);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isPlant(Material t) {
        return switch (t) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH,
                    DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP,
                    ORANGE_TULIP, WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, CORNFLOWER,
                    LILY_OF_THE_VALLEY, WITHER_ROSE, SUNFLOWER, LILAC, ROSE_BUSH,
                    PEONY, VINE, TALL_SEAGRASS, SEAGRASS, KELP_PLANT, SUGAR_CANE,
                    BAMBOO, MANGROVE_PROPAGULE, SWEET_BERRY_BUSH, CAVE_VINES,
                    SPORE_BLOSSOM, GLOW_LICHEN -> true;
            default -> false;
        };
    }

    // ---------- 世界崩解（胸甲）：潜行 + 左键 打开额外潜影箱 ----------

    @EventHandler(ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.isSneaking() || !manager.isArmorOwner(p.getName()) || !wearing(p, "chest")) {
            return;
        }
        Inventory inv = shulkers.computeIfAbsent(p.getUniqueId(), k ->
                Bukkit.createInventory(null, 54, "无尽潜影箱 · 世界崩解"));
        p.openInventory(inv);
        e.setCancelled(true);
    }

    // ---------- 星球吞噬（鞋子）：铲子挖掘 3x3 ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!wearing(p, "boots")) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        String n = hand.getType().name();
        if (!(n.endsWith("_SHOVEL") || n.endsWith("_SPADE"))) {
            return;
        }
        Block b = e.getBlock();
        double hardness = b.getType().getHardness();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block nb = b.getRelative(dx, 0, dz);
                if (nb.getType().isAir() || nb.getType() == Material.BEDROCK) {
                    continue;
                }
                if (nb.getType().getHardness() < 0 || nb.getType().getHardness() > hardness) {
                    continue;
                }
                nb.breakNaturally(hand);
            }
        }
    }

    // ---------- 不死图腾（全套）：死亡复活 5 次 ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!fullSet(p)) {
            return;
        }
        int left = totem.getOrDefault(p.getUniqueId(), 5);
        if (left <= 0) {
            return;
        }
        totem.put(p.getUniqueId(), left - 1);
        e.setCancelled(true);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(10);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.2);
        p.sendMessage(manager.prefix()
                + manager.color("&a不死图腾守护！剩余次数 &f" + (left - 1)));
    }

    // ---------- 无法破坏：无尽防具永不损耗耐久 ----------

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent e) {
        ItemStack it = e.getItem();
        if (it != null && manager.isArmorArtifact(it)) {
            e.setCancelled(true);
        }
    }
}
