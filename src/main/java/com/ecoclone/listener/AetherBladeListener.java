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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 以太之剑·升级链（Aether Blade Progression）：
 *   5 个形态（阶段 I~V），每杀怪累计计数，达到阈值自动觉醒为下一形态：
 *     I   以太 · 溯洄终焉          50  击杀
 *     II  星穹以太 · 裁决之刃     150  击杀
 *     III 永恒以太 · 虚空裁决     350  击杀
 *     IV  以太洪荒 · 弑神之刃     750  击杀
 *     V   以太湮灭 · 万象归墟     满级
 *   各形态技能递进觉醒：虚空瞬移/雷暴/吸灵 → 爆炸 → 烈焰 → 三重雷暴
 */
public final class AetherBladeListener implements Listener {

    private static final Color[][] STAGE_COLORS = {
            {Color.fromRGB(0, 229, 255), Color.fromRGB(30, 90, 255)},   // I 青
            {Color.fromRGB(0, 229, 255), Color.fromRGB(255, 215, 0)},   // II 青金
            {Color.fromRGB(255, 215, 0), Color.fromRGB(180, 60, 255)},  // III 金紫
            {Color.fromRGB(180, 60, 255), Color.fromRGB(255, 40, 40)},  // IV 紫红
            {Color.fromRGB(255, 40, 40), Color.fromRGB(60, 0, 120)}     // V 红黑紫
    };

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public AetherBladeListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        // 以太加持：攻速随阶段提升（I:+1.0 ~ V:+4.0）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    ItemStack held = p.getInventory().getItemInMainHand();
                    boolean on = manager.isAetherBlade(held);
                    double bonus = on ? 1.0 + 0.75 * manager.aetherStage(held) : 0.0;
                    applySpeed(p, on, bonus);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ---------- 命中：按阶段觉醒技能 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isAetherBlade(held)) {
            return;
        }
        int stage = manager.aetherStage(held);
        // 星辰爆裂（I+）：落雷，阶段越高雷越多
        int bolts = 1 + (stage >= 5 ? 2 : stage >= 2 ? 1 : 0);
        for (int i = 0; i < bolts; i++) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }
        // 以太吸灵（I+）：吸取生命
        double heal = event.getDamage() * (0.15 + 0.03 * stage);
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
        // 以太爆裂（III+）：爆炸
        if (stage >= 3) {
            victim.getWorld().createExplosion(victim.getLocation(), 0.8f + 0.3f * stage, false, false);
        }
        // 以太烈焰（IV+）：点燃
        if (stage >= 4) {
            victim.setFireTicks(100);
        }
        // 深渊吞噬：按阶段与目标剩余血量概率吞噬生物；满级（V）100% 必吞
        if (!(victim instanceof Player)) {
            double rate = stage >= 5 ? 1.0
                    : new double[]{0.20, 0.35, 0.50, 0.70, 1.0}[stage - 1];
            double chance = rate * (1.0 - victim.getHealth() / victim.getMaxHealth());
            if (Math.random() < chance) {
                victim.damage(victim.getHealth() + 1.0, p);
            }
        }
        // 星辰粒子
        Location center = victim.getLocation().add(0, 1, 0);
        Color[] colors = STAGE_COLORS[stage - 1];
        for (int i = 0; i < colors.length; i++) {
            victim.getWorld().spawnParticle(Particle.DUST, center, 10,
                    0.7, 0.7, 0.7, 0, new DustOptions(colors[i], 1.3f));
        }
    }

    // ---------- 以太护体：受击反弹 ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isAetherBlade(held)) {
            return;
        }
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (damager instanceof LivingEntity le && le != p) {
            le.damage(event.getDamage() * 0.2, p);
            Color[] colors = STAGE_COLORS[manager.aetherStage(held) - 1];
            le.getWorld().spawnParticle(Particle.DUST,
                    le.getLocation().add(0, 1, 0), 10, 0.4, 0.4, 0.4, 0,
                    new DustOptions(colors[0], 1.2f));
        }
    }

    // ---------- 杀怪升级 ----------
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
        if (!manager.isAetherBlade(held)) {
            return;
        }
        int stage = manager.aetherStage(held);
        int kills = manager.aetherKills(held) + 1;
        if (stage < 5 && kills >= manager.aetherThreshold(stage + 1)) {
            p.getInventory().setItemInMainHand(manager.aetherBlade(stage + 1));
            p.sendMessage(manager.prefix() + manager.color(
                    "&d以太之剑已觉醒 → &f" + manager.aetherName(stage + 1)));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
        } else {
            ItemStack update = held.clone();
            manager.setAetherKills(update, kills);
            update.editMeta(meta -> meta.lore(manager.aetherLore(stage, kills)));
            p.getInventory().setItemInMainHand(update);
        }
    }

    private void applySpeed(Player p, boolean on, double bonus) {
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "aeathmc_aether_speed");
        attr.removeModifier(key);
        if (on) {
            attr.addTransientModifier(new AttributeModifier(
                    key, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
    }
}
