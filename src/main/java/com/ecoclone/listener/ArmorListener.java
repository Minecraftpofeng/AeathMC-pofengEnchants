package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 护甲附魔效果：Evasion / Preservation / Freerunner（抗摔）。
 */
public final class ArmorListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public ArmorListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int evasion = total(player, "evasion");
        int preservation = total(player, "preservation");
        int freerunner = total(player, "freerunner");
        int flame = total(player, "flame_guard");
        int magic = total(player, "magic_shield");
        int fortitude = total(player, "fortitude");
        int phoenix = total(player, "phoenix");

        // Phantom（虚影）：手持附有虚影的剑时概率闪避伤害
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held != null && !held.getType().isAir()) {
            int phantom = Enchants.level(held, "phantom");
            if (phantom > 0 && Math.random() < Math.min(0.05 * phantom, 0.5)) {
                event.setDamage(0);
                event.setCancelled(true);
                player.sendMessage(manager.prefix() + manager.color("&d虚影闪过，你避开了这次攻击。"));
                return;
            }
        }

        // Evasion：概率无视伤害
        if (evasion > 0 && Math.random() < 0.08 * evasion) {
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        // Freerunner：抗摔
        if (freerunner > 0 && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && Math.random() < 0.2 * freerunner) {
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        // Preservation：大幅减伤
        if (preservation > 0) {
            event.setDamage(event.getDamage() * (1 - 0.15 * preservation));
        }

        // Flame Guard：减少火焰/岩浆伤害
        if (flame > 0 && (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            event.setDamage(event.getDamage() * (1 - 0.25 * flame));
        }

        // Magic Shield：减免魔法/药水/凋零伤害
        if (magic > 0 && (event.getCause() == EntityDamageEvent.DamageCause.MAGIC
                || event.getCause() == EntityDamageEvent.DamageCause.POISON
                || event.getCause() == EntityDamageEvent.DamageCause.WITHER)) {
            event.setDamage(event.getDamage() * (1 - 0.2 * magic));
        }

        // Fortitude：减免爆炸伤害
        if (fortitude > 0 && (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            event.setDamage(event.getDamage() * (1 - 0.2 * fortitude));
        }

        // Phoenix：概率免死
        if (phoenix > 0 && player.getHealth() - event.getDamage() <= 0 && Math.random() < 0.4) {
            event.setDamage(0);
            event.setCancelled(true);
            player.setHealth(1.0);
            player.setFireTicks(0);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 200, 0));
        }
    }

    private int total(Player player, String key) {
        int sum = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null) {
                sum += Enchants.level(piece, key);
            }
        }
        return sum;
    }
}
