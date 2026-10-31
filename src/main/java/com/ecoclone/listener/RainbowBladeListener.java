package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 彩虹猫之刃（Rainbow Cat Blade / Nyan Blade）专属机制：
 *   - 彩虹尾迹：手持时移动，身后拖出七彩彩虹粒子
 *   - 喵喵弹射：命中击退目标 + 喵叫 + 引下彩虹闪电
 *   - 彩虹怒涛：命中目标周身迸发七彩虹光粒子
 *   - 霓虹狂飙：持有者攻速 +100%、移动速度 +40%
 *   - 彩虹护盾：受击时反弹 30% 伤害给攻击者
 * 与寰宇支配之剑、创世神刃同一级别（独立神器、独立识别、独立指令）。
 */
public final class RainbowBladeListener implements Listener {

    private static final String SPD_KEY = "aeathmc_rainbow_speed";
    private static final String ATK_KEY = "aeathmc_rainbow_attack";

    private static final Color[] RAINBOW = {
            Color.RED,
            Color.fromRGB(255, 140, 0),
            Color.YELLOW,
            Color.LIME,
            Color.fromRGB(0, 200, 255),
            Color.BLUE,
            Color.fromRGB(200, 0, 255)
    };

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public RainbowBladeListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        // 彩虹尾迹：每 0.1 秒（2 tick）在持剑者脚下拖一条彩虹色粒子
        new BukkitRunnable() {
            @Override
            public void run() {
                int idx = (int) (System.currentTimeMillis() / 100) % RAINBOW.length;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    ItemStack held = p.getInventory().getItemInMainHand();
                    if (!manager.isRainbowBlade(held)) {
                        continue;
                    }
                    Location loc = p.getLocation().add(0, 0.15, 0);
                    for (int i = 0; i < RAINBOW.length; i++) {
                        p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(
                                (i - 3) * 0.12, 0, 0), 1, 0, 0, 0, 0,
                                new DustOptions(RAINBOW[(idx + i) % RAINBOW.length], 1.0f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 霓虹狂飙：每 1 秒维护攻速 +100%、移速 +40%（主手持剑生效，离手移除）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    boolean on = manager.isRainbowBlade(p.getInventory().getItemInMainHand());
                    applySpeed(p, on);
                    applyMove(p, on);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ---------- 喵喵弹射 + 彩虹闪电 + 彩虹怒涛（命中触发） ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isRainbowBlade(held)) {
            return;
        }
        // 喵喵弹射：击退目标
        Vector dir = victim.getLocation().toVector().subtract(p.getLocation().toVector());
        if (dir.lengthSquared() < 0.001) {
            dir = p.getLocation().getDirection();
        } else {
            dir.normalize();
        }
        dir.multiply(2.2).setY(0.7);
        victim.setVelocity(dir);
        // 喵叫
        p.getWorld().playSound(victim.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1.0f, 1.3f);
        // 彩虹闪电
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        // 彩虹怒涛：周身迸发七彩虹光
        Location center = victim.getLocation().add(0, 1, 0);
        for (int i = 0; i < RAINBOW.length; i++) {
            victim.getWorld().spawnParticle(Particle.DUST, center, 8,
                    0.6, 0.6, 0.6, 0, new DustOptions(RAINBOW[i], 1.4f));
        }
    }

    // ---------- 彩虹护盾（受击反弹 30%） ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isRainbowBlade(held)) {
            return;
        }
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (damager instanceof LivingEntity le && le != p) {
            double back = event.getDamage() * 0.3;
            le.damage(back, p);
            le.getWorld().spawnParticle(Particle.DUST,
                    le.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0,
                    new DustOptions(RAINBOW[(int) (System.currentTimeMillis() / 100) % RAINBOW.length], 1.2f));
        }
    }

    // ---------- 霓虹狂飙修饰符 ----------
    private void applySpeed(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, ATK_KEY);
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new AttributeModifier(
                    key, 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
    }

    private void applyMove(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, SPD_KEY);
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new AttributeModifier(
                    key, 0.04, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
    }
}
