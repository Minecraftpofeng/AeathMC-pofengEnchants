package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.Map;

/**
 * 附魔台接入：让自定义附魔按 enchanting-table 配置真正上桌。
 * 原版先按自己的权重塞入自定义附魔，这里先清掉，再按我们的标签池、权重乘数重抽，
 * 同时套 enchantable / treasure / ecoenchants.fromtable.<key> 权限。
 */
public final class EnchantTableListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public EnchantTableListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!manager.tableEnabled()) {
            return;
        }
        Player player = event.getEnchanter();
        if (player == null) {
            return;
        }
        // 清掉原版直接塞进来的自定义附魔，避免绕过标签与开关
        event.getEnchantsToAdd().keySet().removeIf(e ->
                "ecoclone".equals(e.getKey().getNamespace()));
        int cost = Math.max(1, event.getExpLevelCost());
        int rolls = manager.tableRolls(cost);
        for (int i = 0; i < rolls; i++) {
            Map.Entry<Enchantment, Integer> roll =
                    manager.randomTableEnchant(player, cost, manager.tableTypes());
            if (roll == null) {
                continue;
            }
            Enchantment ench = roll.getKey();
            int level = roll.getValue();
            Enchantment already = null;
            for (Enchantment e : event.getEnchantsToAdd().keySet()) {
                if (e.getKey().getKey().equals(ench.getKey().getKey())) {
                    already = e;
                }
            }
            if (already != null) {
                continue;
            }
            // 槽位与类型上限：附魔台给的也不能突破
            if (!manager.typeLimitOk(event.getItem(), ench.getKey().getKey())) {
                continue;
            }
            event.getEnchantsToAdd().put(ench, level);
        }
    }
}
