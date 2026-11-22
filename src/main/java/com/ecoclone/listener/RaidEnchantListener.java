package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 袭击结算发现渠道：守住村庄后按 loot.raid-chance 概率给每位胜利者一本附魔书。
 * 只抽 discoverable.raids 为 true 的附魔。
 */
public final class RaidEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public RaidEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaidFinish(RaidFinishEvent event) {
        double chance = manager.channelChance("raid", 0.02);
        if (chance <= 0) {
            return;
        }
        for (Player player : event.getWinners()) {
            if (Math.random() >= chance) {
                continue;
            }
            Map.Entry<Enchantment, Integer> roll = manager.randomEnchant(manager.lootTypes(),
                    manager.lootTypeWeights(), k -> manager.discoverableChannel(k, "raids"));
            if (roll == null) {
                continue;
            }
            ItemStack book = manager.bookOf(roll.getKey(), roll.getValue());
            Map<Integer, ItemStack> left = player.getInventory().addItem(book);
            if (!left.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), book);
            }
        }
    }
}
