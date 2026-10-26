package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 晨曦破晓剑（Dawnbreaker）专属机制：
 *   - 破晓之光：命中点燃目标，迸发太阳金辉
 *   - 太阳灼烧：命中致盲并迟缓目标
 *   - 黎明庇护：受击反弹 30% 伤害
 *   - 曙光冲锋：持有者攻速 +75%、移速 +20%
 * 与寰宇支配之剑、创世神刃、彩虹猫之刃同一级别。
 */
public final class DawnBladeListener implements Listener {

    private static final Color[] DAWN = {
            Color.fromRGB(255, 215, 0),
            Color.fromRGB(255, 170, 60),
            Color.fromRGB(255, 255, 240)
    };

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public DawnBladeListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    boolean on = manager.isDawnBlade(p.getInventory().getItemInMainHand());
                    applySpeed(p, on);
                    applyMove(p, on);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isDawnBlade(held)) {
            return;
        }
        // 破晓之光：点燃
        victim.setFireTicks(120);
        // 太阳灼烧：致盲 + 迟缓
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
        // 太阳金辉
        Location center = victim.getLocation().add(0, 1, 0);
        for (int i = 0; i < DAWN.length; i++) {
            victim.getWorld().spawnParticle(Particle.DUST, center, 10,
                    0.6, 0.6, 0.6, 0, new DustOptions(DAWN[i], 1.3f));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (!manager.isDawnBlade(p.getInventory().getItemInMainHand())) {
            return;
        }
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (damager instanceof LivingEntity le && le != p) {
            le.damage(event.getDamage() * 0.3, p);
            le.getWorld().spawnParticle(Particle.DUST,
                    le.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0,
                    new DustOptions(DAWN[(int) (System.currentTimeMillis() / 100) % DAWN.length], 1.2f));
        }
    }

    private void applySpeed(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "aeathmc_dawn_speed");
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new AttributeModifier(
                    key, 3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
    }

    private void applyMove(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "aeathmc_dawn_move");
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new AttributeModifier(
                    key, 0.02, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
    }
}
