package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;

/**
 * 生物掉落发现渠道：玩家击杀生物时按 loot.mob-chance 概率把附魔书塞进掉落物。
 * 只抽 discoverable.mob-drops 为 true 的附魔。
 */
public final class MobDropEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public MobDropEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        // 玩家之间击杀不产生
        if (event.getEntity() instanceof Player) {
            return;
        }
        double chance = manager.channelChance("mob", 0.03);
        if (chance <= 0 || Math.random() >= chance) {
            return;
        }
        Map.Entry<Enchantment, Integer> roll = manager.randomEnchant(manager.lootTypes(),
                manager.lootTypeWeights(), k -> manager.discoverableChannel(k, "mob-drops"));
        if (roll == null) {
            return;
        }
        event.getDrops().add(manager.bookOf(roll.getKey(), roll.getValue()));
    }
}
