package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantGate;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import com.ecoclone.TriggerType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * v1.4.7.0：把 FISH / TRADE / LOOT 三个触发器接到真实事件，
 * 并实现钓鱼系新附魔 Lucky Catch（额外鱼获）与 Grappling（钓中实体拉近）。
 */
public final class TriggerEventsListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public TriggerEventsListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        EnchantGate.enter(player);
        TriggerType.set(TriggerType.FISH);

        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType().isAir()) {
            rod = player.getInventory().getItemInOffHand();
        }
        if (rod == null || rod.getType().isAir()) {
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH
                && event.getCaught() instanceof Item caught) {
            int lucky = Enchants.level(rod, "lucky_catch");
            if (lucky > 0 && Math.random() < Math.min(0.15 * lucky, 0.5)) {
                ItemStack extra = caught.getItemStack().clone();
                caught.getWorld().dropItemNaturally(caught.getLocation(), extra);
            }
        } else if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            int grapple = Enchants.level(rod, "grappling");
            Entity caught = event.getCaught();
            if (grapple > 0 && caught != null && !caught.equals(player)) {
                Vector dir = player.getLocation().toVector()
                        .subtract(caught.getLocation().toVector());
                if (dir.lengthSquared() > 0.01) {
                    caught.setVelocity(dir.normalize().multiply(0.5 + 0.25 * grapple));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(io.papermc.paper.event.player.PlayerTradeEvent event) {
        EnchantGate.enter(event.getPlayer());
        TriggerType.set(TriggerType.TRADE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (event.getEntity() instanceof Player player) {
            EnchantGate.enter(player);
            TriggerType.set(TriggerType.LOOT);
        }
    }
}
