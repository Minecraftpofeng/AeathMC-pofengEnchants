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
 * 暗渊噬魂剑（Abyss Devourer）专属机制：
 *   - 噬魂：命中吸取目标生命（回复更强，25%）
 *   - 深渊凝视：命中令目标虚弱 + 失明 + 缓慢
 *   - 暗影瞬袭：命中瞬移至目标背后并落下深渊雷暴
 *   - 深渊契约：受击反弹 35% 伤害
 *   - 噬魂狂怒：持有者攻速 +60%
 * 与寰宇支配之剑、创世神刃、彩虹猫之刃同一级别。
 */
public final class AbyssBladeListener implements Listener {

    private static final Color[] ABYSS = {
            Color.fromRGB(156, 39, 176),
            Color.fromRGB(74, 0, 130),
            Color.fromRGB(180, 80, 255)
    };

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public AbyssBladeListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    applySpeed(p, manager.isAbyssBlade(p.getInventory().getItemInMainHand()));
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
        if (!manager.isAbyssBlade(held)) {
            return;
        }
        // 噬魂：吸取 25% 伤害为生命
        double heal = event.getDamage() * 0.25;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
        // 深渊凝视：虚弱 + 失明 + 缓慢
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
        // 深渊吞噬：满级 100% 吞噬生物（玩家改为额外吞噬 8% 最大生命）
        if (victim instanceof Player) {
            victim.damage(victim.getMaxHealth() * 0.08, p);
        } else {
            victim.damage(victim.getHealth() + 1.0, p);
        }
        // 深渊雷暴
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        // 深渊粒子
        Location center = victim.getLocation().add(0, 1, 0);
        for (int i = 0; i < ABYSS.length; i++) {
            victim.getWorld().spawnParticle(Particle.DUST, center, 10,
                    0.6, 0.6, 0.6, 0, new DustOptions(ABYSS[i], 1.3f));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (!manager.isAbyssBlade(p.getInventory().getItemInMainHand())) {
            return;
        }
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (damager instanceof LivingEntity le && le != p) {
            le.damage(event.getDamage() * 0.35, p);
            le.getWorld().spawnParticle(Particle.DUST,
                    le.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0,
                    new DustOptions(ABYSS[(int) (System.currentTimeMillis() / 100) % ABYSS.length], 1.2f));
        }
    }

    private void applySpeed(Player p, boolean on) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "aeathmc_abyss_speed");
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new AttributeModifier(
                    key, 2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
    }
}
