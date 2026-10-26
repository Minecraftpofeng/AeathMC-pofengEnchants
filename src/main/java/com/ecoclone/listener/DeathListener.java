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
        Player player = event.getPlayer();
        List<ItemStack> items = kept.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        // 延迟到下一 tick 归还：复活瞬间物品栏可能尚未就绪，立即放入会被吞
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack item : items) {
                ItemStack leftover = player.getInventory().addItem(item).get(0);
                if (leftover != null) {
                    // 背包已满：剩余物品掉落而非消失
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        });
    }
}
