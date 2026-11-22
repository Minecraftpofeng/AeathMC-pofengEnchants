package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 钓鱼发现渠道：钓上鱼时有 loot.fishing-chance 概率额外钓到一本附魔书。
 * 只抽 discoverable.fishing 为 true 的附魔，标签池与权重沿用 loot 段。
 */
public final class FishingEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public FishingEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        double chance = manager.channelChance("fishing", 0.05);
        if (chance <= 0 || Math.random() >= chance) {
            return;
        }
        Map.Entry<Enchantment, Integer> roll = manager.randomEnchant(manager.lootTypes(),
                manager.lootTypeWeights(), k -> manager.discoverableChannel(k, "fishing"));
        if (roll == null) {
            return;
        }
        ItemStack book = manager.bookOf(roll.getKey(), roll.getValue());
        Map<Integer, ItemStack> left = player.getInventory().addItem(book);
        if (!left.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), book);
        }
        player.sendMessage(manager.prefix() + manager.color("&b你钓到了一本附魔书。"));
    }
}
