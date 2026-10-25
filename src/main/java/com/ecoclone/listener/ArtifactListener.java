package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 神器（寰宇支配之剑·最终幻想）专属机制：
 *   - 主人 ClowmPierce 持剑：常驻攻速+50%
 *   - 狂热：攻击生物增快攻速，持续3秒后逐渐衰减，攻速上限250%
 *   - 震慑：右键连击蓄力 1~3 档，释放震飞半径 7.5/10/15 内生物并造成2秒眩晕，冷却120秒
 *   - 动态 lore：本人手持显示"王已归位..."，他人显示"寻找它的主人"
 */
public final class ArtifactListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private static final String MODIFIER = "aeathmc_artifact_speed";

    // 狂热：furyLevel 0..3，每级 +2.0 攻速，上限 +6.0（250%）
    private final Map<UUID, Integer> fury = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    // 震慑
    private final Map<UUID, Integer> stunCharge = new HashMap<>();
    private final Map<UUID, Long> stunChargeTime = new HashMap<>();
    private final Map<UUID, Long> stunCooldown = new HashMap<>();

    public ArtifactListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        // 攻速衰减 + 动态 lore + 震慑自动释放（每1秒）
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    decayFury(p, now);
                    updateLore(p);
                    checkStun(p, now);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** 攻击命中时触发狂热 */
    public void onAttack(Player p) {
        if (!manager.isArtifact(p.getInventory().getItemInMainHand())) {
            return;
        }
        int level = Math.min(fury.getOrDefault(p.getUniqueId(), 0) + 1, 3);
        fury.put(p.getUniqueId(), level);
        lastHit.put(p.getUniqueId(), System.currentTimeMillis());
        applySpeed(p);
    }

    private void decayFury(Player p, long now) {
        Integer level = fury.get(p.getUniqueId());
        if (level == null) {
            return;
        }
        if (now - lastHit.getOrDefault(p.getUniqueId(), 0L) > 3000) {
            int next = level - 1;
            if (next <= 0) {
                fury.remove(p.getUniqueId());
                lastHit.remove(p.getUniqueId());
            } else {
                fury.put(p.getUniqueId(), next);
            }
            applySpeed(p);
        }
    }

    /** 攻速：主人常驻+50%(+2.0) + 狂热(+2.0/级)，封顶+6.0（250%） */
    private void applySpeed(Player p) {
        double bonus = 0.0;
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean owner = manager.isArtifact(held) && p.getName().equals(manager.artifactOwner());
        if (owner) {
            bonus += 2.0;
        }
        Integer f = fury.get(p.getUniqueId());
        if (f != null) {
            bonus += 2.0 * f;
        }
        bonus = Math.min(bonus, 6.0);
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, MODIFIER);
        attr.removeModifier(key);
        if (bonus <= 0) {
            return;
        }
        AttributeModifier mod = new AttributeModifier(
                key, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        attr.addTransientModifier(mod);
    }

    // ---------- 震慑（右键连击蓄力） ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isArtifact(held)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < stunCooldown.getOrDefault(p.getUniqueId(), 0L)) {
            p.sendMessage(manager.prefix() + manager.color(
                    "&c震慑蓄力中/冷却中，请稍候。"));
            return;
        }
        Integer charge = stunCharge.getOrDefault(p.getUniqueId(), 0);
        long since = stunChargeTime.getOrDefault(p.getUniqueId(), 0L);
        if (charge > 0 && since > 0 && now - since > 2000) {
            // 上一档超时，先释放
            releaseStun(p, charge);
            return;
        }
        int next = charge + 1;
        if (next > 3) {
            releaseStun(p, next == 4 ? 3 : charge);
            return;
        }
        stunCharge.put(p.getUniqueId(), next);
        stunChargeTime.put(p.getUniqueId(), now);
        p.sendMessage(manager.prefix() + manager.color(
                "&d震慑蓄力 &e" + next + "/3&d（半径 "
                + (7.5 * next) + " 格），2秒内再次右键提升蓄力。"));
    }

    private void checkStun(Player p, long now) {
        Integer charge = stunCharge.get(p.getUniqueId());
        if (charge == null) {
            return;
        }
        if (now - stunChargeTime.getOrDefault(p.getUniqueId(), 0L) > 2000) {
            releaseStun(p, charge);
        }
    }

    private void releaseStun(Player p, int charge) {
        stunCharge.remove(p.getUniqueId());
        stunChargeTime.remove(p.getUniqueId());
        double radius = 7.5 * charge;
        // 释放震慑：震飞范围内生物 + 眩晕2秒
        for (org.bukkit.entity.Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || le == p) {
                continue;
            }
            Vector dir = le.getLocation().toVector().subtract(p.getLocation().toVector());
            double dist = dir.length();
            if (dist < 0.01) {
                dir = p.getLocation().getDirection();
            } else {
                dir.normalize();
            }
            double force = 1.5 + 0.5 * charge;
            le.setVelocity(dir.multiply(force).setY(0.6 * charge + 0.3));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 254));
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        }
        stunCooldown.put(p.getUniqueId(), System.currentTimeMillis() + 120_000);
        p.sendMessage(manager.prefix() + manager.color(
                "&b震慑已释放！震飞了半径 " + (int) radius + " 格内的所有生物（冷却 120 秒）。"));
    }

    // ---------- 动态 lore ----------

    private void updateLore(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!manager.isArtifact(held)) {
            return;
        }
        boolean owner = p.getName().equals(manager.artifactOwner());
        held.editMeta(meta -> meta.lore(manager.artifactLore(owner)));
    }

    // ---------- 工具 ----------
    public Component gradient(String text, int sr, int sg, int sb, int er, int eg, int eb) {
        return manager.gradient(text, sr, sg, sb, er, eg, eb);
    }
}
