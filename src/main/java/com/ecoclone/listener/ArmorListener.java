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
