package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soulbound：死亡时保留拥有该附魔的物品，重生后归还。
 */
public final class DeathListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private final Map<UUID, List<ItemStack>> kept = new ConcurrentHashMap<>();

    public DeathListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        List<ItemStack> saved = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            if (Enchants.has(item, "soulbound")) {
                saved.add(item.clone());
                return true;
            }
            return false;
        });
        if (!saved.isEmpty()) {
            kept.put(id, saved);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> items = kept.remove(event.getPlayer().getUniqueId());
        if (items != null) {
            items.forEach(item -> event.getPlayer().getInventory().addItem(item));
        }
    }
}
