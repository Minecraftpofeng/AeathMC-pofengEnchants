package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 神器锄头 · 万象生息 · 丰饶之锄（绑定 7gzztrgv / Minecraft_pofeng）：
 *   - 万象生息 / 春回大地：持有获得生命恢复，耕作与击杀回复生命
 *   - 丰饶催熟：周围作物自动成熟
 *   - 大地滋养：破坏成熟作物自动补种
 *   - 丰收翻倍：收获掉落翻倍
 *   - 沃土永固：耕地永不退化
 *   - 灵狐赐福：移速提升
 *   - 神农庇护：受击减免并反弹
 *   - 大地之怒：可作为武器劈砍，命中伤害翻倍
 *   - 灵狐易伤（方案②）：命中目标挂 1 秒易伤，期内目标无受击无敌帧
 *   - 绑定觉醒：7gzztrgv / Minecraft_pofeng 持有时名字化作「大楚兴，陈胜王」，
 *     lore 狐狸行变为渐变名言，并唤醒日月交替（主手御空夜临，离手落地昼还）
 */
public final class HoeArtifactListener implements Listener {

    private static final long VULN_MS = 1000;
    /** 易伤目标（UUID -> 到期时间） */
    private final Map<UUID, Long> vulnerable = new HashMap<>();
    /** 由本插件开启飞行的玩家 */
    private final Set<UUID> pluginFlying = new HashSet<>();

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public HoeArtifactListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cropRipen, 0L, 40L);
    }

    // ---------- 每秒循环：日月交替 + 动态觉醒 + 生命恢复 + 移速 + 清理易伤 ----------
    private void tick() {
        long now = System.currentTimeMillis();
        vulnerable.entrySet().removeIf(e -> e.getValue() < now);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            boolean hoe = manager.isHoeArtifact(held);
            boolean owner = hoe && manager.isHoeOwner(p.getName());
            if (owner) {
                pluginFlying.add(p.getUniqueId());
                p.setAllowFlight(true);
                if (!p.isFlying()) {
                    p.setFlying(true);
                }
                World w = p.getWorld();
                if (w.getTime() != 18000) {
                    w.setTime(18000); // 午夜
                }
                // 春回大地：生命恢复
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0));
            } else if (pluginFlying.remove(p.getUniqueId())) {
                p.setFlying(false);
                p.setAllowFlight(false);
                World w = p.getWorld();
                if (w.getTime() != 1000) {
                    w.setTime(1000); // 白天
                }
            }
            // 灵狐赐福：移速
            applyMove(p, hoe);
            // 高攻速（7gzztrgv 专属）：攻速大幅提升
            applySpeed(p, hoe && p.getName().equals("7gzztrgv"));
            // 动态觉醒外观（绑定玩家持有时名字/lore 变为渐变名言）
            if (hoe) {
                ItemStack target = held.clone();
                boolean own = manager.isHoeOwner(p.getName());
                target.editMeta(meta -> {
                    meta.displayName(manager.hoeNameComponent(own));
                    meta.lore(manager.hoeLore(own));
                });
                p.getInventory().setItemInMainHand(target);
            }
        }
    }

    // ---------- 丰饶催熟：周围作物自动成熟 ----------
    private void cropRipen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (!manager.isHoeArtifact(held)) {
                continue;
            }
            org.bukkit.Location c = p.getLocation();
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        Block b = c.getBlock().getRelative(dx, dy, dz);
                        if (b.getBlockData() instanceof Ageable a && a.getAge() < a.getMaximumAge()) {
                            a.setAge(a.getMaximumAge());
                            b.setBlockData(a, false);
                        }
                    }
                }
            }
        }
    }

    // ---------- 命中：大地之怒 + 灵狐易伤（方案②） ----------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isHoeArtifact(held)) {
            return;
        }
        boolean is7g = p.getName().equals("7gzztrgv");
        if (is7g) {
            // 7gzztrgv 专属控制流：不高伤，改为冰冻 + 剧毒 + 召唤灵狐
            event.setDamage(event.getDamage());
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            summonFox(victim, p);
            victim.getWorld().spawnParticle(Particle.DUST,
                    victim.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0,
                    new DustOptions(Color.fromRGB(140, 210, 255), 1.3f));
        } else {
            // 大地之怒：伤害翻倍（Minecraft_pofeng 专属）
            event.setDamage(event.getDamage() * 2);
        }
        // 凋零 + 持续伤害（燃烧5秒）
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
        victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
        // 灵狐易伤：目标挂 1 秒易伤（无视无敌帧）
        vulnerable.put(victim.getUniqueId(), System.currentTimeMillis() + VULN_MS);
        // 凋零：命中凋零 5 秒
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
        victim.getWorld().spawnParticle(Particle.DUST,
                victim.getLocation().add(0, 1, 0), 10, 0.4, 0.4, 0.4, 0,
                new DustOptions(Color.fromRGB(255, 200, 80), 1.2f));
    }

    // ---------- 易伤穿透：期内目标受击清除无敌帧 ----------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (vulnerable.containsKey(victim.getUniqueId()) && victim.getNoDamageTicks() > 0) {
            victim.setNoDamageTicks(0);
        }
    }

    // ---------- 神农庇护：受击减免 + 反弹 ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (!manager.isHoeArtifact(p.getInventory().getItemInMainHand())) {
            return;
        }
        event.setDamage(event.getDamage() * 0.8); // 减免 20%
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (damager instanceof LivingEntity le && le != p) {
            le.damage(event.getDamage() * 0.3, p);
        }
    }

    // ---------- 万象生息：击杀回复生命 ----------
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity dead)) {
            return;
        }
        Player p = dead.getKiller();
        if (p == null) {
            return;
        }
        if (!manager.isHoeArtifact(p.getInventory().getItemInMainHand())) {
            return;
        }
        double heal = 4.0;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 8, 0.3, 0.3, 0.3, 0.01);
    }

    // ---------- 丰收翻倍（掉落翻倍）+ 大地滋养自动补种 ----------
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isHoeArtifact(held)) {
            return;
        }
        Block b = event.getBlock();
        if (!(b.getBlockData() instanceof Ageable ageable)) {
            return;
        }
        boolean mature = ageable.getAge() == ageable.getMaximumAge();
        // 大地滋养：破坏成熟作物后原地自动补种（重新长）
        if (mature) {
            Ageable replant = (Ageable) ageable.clone();
            replant.setAge(0);
            b.setBlockData(replant, false);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.01);
        }
    }

    // 丰收翻倍：成熟作物掉落翻倍
    @EventHandler(ignoreCancelled = true)
    public void onDrop(org.bukkit.event.block.BlockDropItemEvent event) {
        Player p = event.getPlayer();
        if (!manager.isHoeArtifact(p.getInventory().getItemInMainHand())) {
            return;
        }
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        if (ageable.getAge() == ageable.getMaximumAge()) {
            java.util.List<org.bukkit.entity.Item> drops = event.getItems();
            for (org.bukkit.entity.Item it : new java.util.ArrayList<>(drops)) {
                it.setItemStack(it.getItemStack().asQuantity(it.getItemStack().getAmount() * 2));
            }
        }
    }

    // ---------- 沃土永固：耕地永不退化 ----------
    @EventHandler(ignoreCancelled = true)
    public void onFarm(BlockPhysicsEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.FARMLAND) {
            return;
        }
        // 仅当附近有绑定玩家持锄头时生效
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!manager.isHoeArtifact(p.getInventory().getItemInMainHand())) {
                continue;
            }
            if (p.getLocation().distanceSquared(b.getLocation()) <= 100.0) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---------- 召唤灵狐（7gzztrgv 专属）：命中召唤灵狐攻击目标，20 秒后消失 ----------
    private void summonFox(LivingEntity victim, Player owner) {
        try {
            org.bukkit.entity.Mob fox = (org.bukkit.entity.Mob) victim.getWorld()
                    .spawnEntity(victim.getLocation().add(0.5, 1, 0.5), org.bukkit.entity.EntityType.FOX);
            fox.setTarget(victim);
            fox.setCustomName("灵狐 · 护锄");
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (fox.isValid()) {
                    fox.remove();
                }
            }, 400L);
        } catch (Throwable ignored) {
        }
    }

    // ---------- 高攻速（7gzztrgv 专属）：攻速 +200% ----------
    private void applySpeed(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "aeathmc_hoe_speed");
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new org.bukkit.attribute.AttributeModifier(
                    key, 8.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
    }

    // ---------- 灵狐赐福：移速 ----------
    private void applyMove(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "aeathmc_hoe_move");
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new org.bukkit.attribute.AttributeModifier(
                    key, 0.04, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
    }
}
