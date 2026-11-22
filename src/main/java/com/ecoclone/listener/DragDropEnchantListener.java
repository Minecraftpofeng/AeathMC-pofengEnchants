package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 拖拽应用：光标上拿着附魔书，直接点目标物品即可应用，不必摆进工作台。
 * 走 drag-and-drop 段的开关与价格；Vault 用反射调用，避免硬编译依赖。
 */
public final class DragDropEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    private Method hasAccount;
    private Method withdraw;
    private Method getBalance;
    private Class<?> economyClass;

    public DragDropEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        hookVault();
    }

    private void hookVault() {
        try {
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            hasAccount = economyClass.getMethod("hasAccount", Player.class);
            withdraw = economyClass.getMethod("withdrawPlayer", Player.class, double.class);
            getBalance = economyClass.getMethod("getBalance", Player.class);
        } catch (Throwable ignored) {
            economyClass = null;
        }
    }

    private Object economy() {
        if (economyClass == null) {
            return null;
        }
        var reg = Bukkit.getServicesManager().getRegistration(economyClass);
        return reg == null ? null : reg.getProvider();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getView().getCursor();
        if (cursor == null || cursor.getType() != Material.ENCHANTED_BOOK) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir()) {
            return;
        }
        if (!(cursor.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return;
        }
        Map.Entry<Enchantment, Integer> content = firstCustom(meta);
        if (content == null) {
            return;
        }
        String key = content.getKey().getKey().getKey();
        if (!manager.dragDropEnabled(key)) {
            return;
        }
        if (!manager.canEnchant(target.getType().asItemType(), key)
                && !manager.canAddCustom(target, content.getKey())) {
            return;
        }
        event.setCancelled(true);

        // 先校验前置 / 互斥 / 槽位，全部通过才扣费，避免白扣钱
        if (!manager.conflictsOk(target, key) || !manager.requiredOk(target, key)) {
            player.sendMessage(manager.prefix() + manager.color("&c目标物品不满足该附魔的前置或互斥条件。"));
            return;
        }
        if (!manager.canAddCustom(target, content.getKey())) {
            player.sendMessage(manager.prefix()
                    + manager.color("&c目标物品附魔槽已满（上限 " + manager.enchantmentSlots() + " 个）。"));
            return;
        }

        double price = manager.dragDropPrice(key, content.getValue());
        if (price > 0) {
            Object eco = economy();
            if (eco == null) {
                player.sendMessage(manager.prefix() + manager.color("&c拖拽应用需要 Vault 经济支持。"));
                return;
            }
            try {
                double balance = (Double) getBalance.invoke(eco, player);
                if (balance < price) {
                    player.sendMessage(manager.prefix()
                            + manager.color("&c金币不足，需要 " + EnchantManager.num(price) + "。"));
                    return;
                }
                withdraw.invoke(eco, player, price);
            } catch (Throwable t) {
                player.sendMessage(manager.prefix() + manager.color("&c扣费失败，请检查 Vault 经济。"));
                return;
            }
        }

        manager.apply(target, content.getKey(), content.getValue());
        event.setCurrentItem(target);
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
        } else {
            event.getView().setCursor(null);
        }
        player.sendMessage(manager.prefix() + manager.color("&a已应用 &e" + key + " &a" + content.getValue() + " 级。"));
    }

    private Map.Entry<Enchantment, Integer> firstCustom(EnchantmentStorageMeta meta) {
        for (Map.Entry<Enchantment, Integer> e : meta.getStoredEnchants().entrySet()) {
            var k = e.getKey().getKey();
            if ("ecoclone".equals(k.getNamespace())) {
                return e;
            }
        }
        return null;
    }
}
