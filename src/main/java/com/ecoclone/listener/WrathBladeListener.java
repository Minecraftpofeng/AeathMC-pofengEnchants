package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 七宗罪 · 暴怒「嗜血重生」：
 *   嗜血重生（血<15%回满+抗性+20黄心）/ 狂怒（血<60%伤害×3.7+燃烧+速度急迫）
 *   嗜血（攻击吸血+抗性）/ §k受伤（受伤回血+抗性+吸收；<10%更强）
 *   赎罪（死亡复活）/ 禁咒（固定30伤+削5%生命）/ 诅咒之刃（×2+免疫5类debuff）
 *   暴戾（每附魔+攻速/护甲/击退抗性；每诅咒+实体范围/护甲韧性）
 *   绑定 OP_KL / Minecraft_pofeng，他人持有即为破烂。
 */
public final class WrathBladeListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private final NamespacedKey spdKey, armKey, knockKey, toughKey, rangeKey;

    public WrathBladeListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.spdKey = new NamespacedKey(plugin, "wrath_speed");
        this.armKey = new NamespacedKey(plugin, "wrath_armor");
        this.knockKey = new NamespacedKey(plugin, "wrath_knock");
        this.toughKey = new NamespacedKey(plugin, "wrath_tough");
        this.rangeKey = new NamespacedKey(plugin, "wrath_range");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    // ---------------- 每秒循环：暴戾属性 + 状态被动 + 免疫 + 破烂显示 ----------------
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            boolean blade = manager.isWrathBlade(held);
            boolean owner = blade && manager.isWrathOwner(p.getName());
            if (owner) {
                // 免疫：中毒/凋零/虚弱/缓慢/反胃
                removeDebuffs(p);
                // 狂怒状态：血量<60% 速度2 + 急迫3
                if (p.getHealth() < p.getMaxHealth() * 0.60) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 2));
                }
                // 暴戾：按附魔/诅咒数加成
                applyRage(p, held);
                // 恢复神器外观（防被改）
                held.editMeta(meta -> {
                    meta.displayName(manager.wrathNameComponent());
                    meta.lore(manager.wrathLore(true));
                });
            } else if (blade) {
                // 非绑定持有：变成破烂
                held.editMeta(meta -> {
                    meta.displayName(net.kyori.adventure.text.Component.text(manager.color("&7破烂")));
                    meta.lore(java.util.List.of(
                            net.kyori.adventure.text.Component.text(manager.color("&8被抛弃的废物……"))));
                });
                clearRage(p);
            } else {
                clearRage(p);
            }
        }
    }

    private void removeDebuffs(Player p) {
        for (PotionEffectType t : new PotionEffectType[]{
                PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS, PotionEffectType.NAUSEA}) {
            p.removePotionEffect(t);
        }
    }

    // ---------------- 暴戾：每附魔+4%攻速/+3%护甲/+0.25击退抗性；每诅咒+2%实体范围/+2护甲韧性 ----------------
    private void applyRage(Player p, ItemStack held) {
        int ench = held.getEnchantments().size();
        int curses = countCurses(held);
        setMod(p, Attribute.GENERIC_ATTACK_SPEED, spdKey, 0.04 * ench,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        setMod(p, Attribute.GENERIC_ARMOR, armKey, 0.03 * ench,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        setMod(p, Attribute.GENERIC_KNOCKBACK_RESISTANCE, knockKey, 0.25 * ench,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, Attribute.GENERIC_ARMOR_TOUGHNESS, toughKey, 2.0 * curses,
                AttributeModifier.Operation.ADD_NUMBER);
    }

    private int countCurses(ItemStack held) {
        int c = 0;
        for (Enchantment e : held.getEnchantments().keySet()) {
            if (e == Enchantment.VANISHING_CURSE || e == Enchantment.BINDING_CURSE
                    || e.getKey().getKey().contains("curse")) {
                c++;
            }
        }
        return c;
    }

    private void setMod(Player p, Attribute attr, NamespacedKey key, double amount,
                        AttributeModifier.Operation op) {
        AttributeInstance ai = p.getAttribute(attr);
        if (ai == null) {
            return;
        }
        ai.removeModifier(key);
        if (amount != 0) {
            ai.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.MAINHAND));
        }
    }

    private void clearRage(Player p) {
        for (Attribute a : new Attribute[]{
                Attribute.GENERIC_ATTACK_SPEED, Attribute.GENERIC_ARMOR,
                Attribute.GENERIC_KNOCKBACK_RESISTANCE, Attribute.GENERIC_ARMOR_TOUGHNESS}) {
            AttributeInstance ai = p.getAttribute(a);
            if (ai != null) {
                ai.removeModifier(spdKey); ai.removeModifier(armKey);
                ai.removeModifier(knockKey); ai.removeModifier(toughKey);
            }
        }
    }

    // ---------------- 攻击：诅咒之刃×2 + 狂怒×3.7+燃烧 + 禁咒+30削5% + 嗜血吸血 ----------------
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        if (!manager.isWrathOwner(p.getName())) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isWrathBlade(held)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        double dmg = event.getDamage() * 2.0; // 诅咒之刃：常驻×2
        if (p.getHealth() < p.getMaxHealth() * 0.60) { // 狂怒
            dmg *= 3.7;
        }
        dmg += 30.0; // 禁咒：固定30点伤害
        // 凋零 + 持续伤害（常驻燃烧6秒）
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
        victim.setFireTicks(Math.max(victim.getFireTicks(), 120));
        // 禁咒：每次攻击削减对方5%最大生命
        double cut = victim.getMaxHealth() * 0.05;
        victim.setHealth(Math.max(1.0, victim.getHealth() - cut));
        event.setDamage(dmg);
        // 嗜血：攻击恢复10%生命 + 抗性1
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + p.getMaxHealth() * 0.10));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.6f);
    }

    // ---------------- 受伤：嗜血重生(<15%) + §k受伤(回血/抗性/吸收) ----------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (!manager.isWrathOwner(p.getName())) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isWrathBlade(held)) {
            return;
        }
        double finalDmg = event.getFinalDamage();
        double post = p.getHealth() - finalDmg;
        // 反伤（暴怒专属）：反弹 30% 伤害给攻击者
        org.bukkit.damage.DamageSource src = event.getDamageSource();
        Entity damager = src == null ? null : src.getCausingEntity();
        if (damager instanceof LivingEntity le && le != p && finalDmg > 0) {
            le.damage(finalDmg * 0.3, p);
            le.getWorld().spawnParticle(Particle.DUST, le.getLocation().add(0, 1, 0), 10,
                    0.4, 0.4, 0.4, 0, new DustOptions(Color.fromRGB(255, 30, 30), 1.4f));
        }
        // §k受伤：回血2 + 抗性1 + 吸收2；生命<10%时抗性3 + 生命恢复5
        if (post < p.getMaxHealth() * 0.10) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 4));
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, 1));
        }
        // 嗜血重生：血量<15% 回复100% + 20秒抗性1 + 20颗黄心
        if (post < p.getMaxHealth() * 0.15) {
            p.setHealth(p.getMaxHealth());
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 19)); // 20颗黄心
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.4, 0), 40,
                    0.5, 0.5, 0.5, 0, new DustOptions(Color.fromRGB(255, 30, 30), 1.6f));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }
    }

    // ---------------- 赎罪：死亡复活 ----------------
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!manager.isWrathOwner(p.getName())) {
            return;
        }
        if (!manager.isWrathBlade(p.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        event.setDeathMessage(null);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 30,
                    0.4, 0.4, 0.4, 0.02);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
            p.sendTitle("赎罪 · ovO", "", 10, 40, 20);
        }, 40L);
    }
}
