package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.List;
import java.util.Map;

/**
 * 附魔工作台 GUI：
 *   [10] 待附魔物品  [11] 附魔来源(卷轴/附魔书)  [13] 信息
 *   [15] 应用附魔      [16] 洗附魔
 * 依赖附魔槽系统（enchantment-slots）。
 */
public final class GuiListener implements Listener {

    private static final int SLOT_ITEM = 10;
    private static final int SLOT_SOURCE = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_APPLY = 15;
    private static final int SLOT_WASH = 16;

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public GuiListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 27, manager.color("&5[AeathMC] 附魔工作台"));
        inv.setItem(SLOT_APPLY, button(Material.EMERALD_BLOCK, "&a应用附魔",
                "&7把卷轴/附魔书放到来源槽，点击应用"));
        inv.setItem(SLOT_WASH, button(Material.BARRIER, "&c洗附魔",
                "&7清除待附魔物品的全部自定义附魔"));
        inv.setItem(SLOT_INFO, button(Material.PAPER, "&e附魔槽上限：" + manager.enchantmentSlots(),
                "&710 号槽：放要附魔的物品",
                "&711 号槽：放附魔卷轴或附魔书"));
        // 不用的槽位用淡灰色玻璃板填满
        for (int i = 0; i < 27; i++) {
            if (i == SLOT_ITEM || i == SLOT_SOURCE || i == SLOT_INFO
                    || i == SLOT_APPLY || i == SLOT_WASH) {
                continue;
            }
            inv.setItem(i, filler());
        }
        p.openInventory(inv);
    }

    private ItemStack filler() {
        ItemStack f = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        f.editMeta(m -> m.displayName(Component.text(manager.color("&7不要戳我哦喵~o(>﹏<)o"))));
        return f;
    }

    private ItemStack button(Material mat, String name, String... lore) {
        ItemStack b = new ItemStack(mat);
        b.editMeta(m -> {
            m.displayName(Component.text(manager.color(name)));
            List<Component> l = new java.util.ArrayList<>();
            for (String s : lore) {
                l.add(Component.text(manager.color(s)));
            }
            m.lore(l);
        });
        return b;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTitle().equals(manager.color("&5[AeathMC] 附魔工作台")))) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        // 只处理顶栏按钮区（13/15/16）的点击动作；10/11 槽交给玩家正常摆放
        if (slot == SLOT_INFO) {
            event.setCancelled(true);
            return;
        }
        if (slot == SLOT_APPLY) {
            event.setCancelled(true);
            apply(player, top);
            return;
        }
        if (slot == SLOT_WASH) {
            event.setCancelled(true);
            wash(player, top);
            return;
        }
        // 顶栏其它槽：10/11 允许正常摆放，其余（信息槽+玻璃填充槽）禁止点击
        if (slot < 27) {
            if (slot == SLOT_ITEM || slot == SLOT_SOURCE) {
                return;
            }
            event.setCancelled(true);
        }
    }

    private void apply(Player player, Inventory top) {
        ItemStack item = top.getItem(SLOT_ITEM);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(manager.prefix() + manager.msg("gui-no-item"));
            return;
        }
        ItemStack src = top.getItem(SLOT_SOURCE);
        Map.Entry<Enchantment, Integer> content = sourceContent(src);
        if (content == null) {
            player.sendMessage(manager.prefix() + manager.msg("gui-need-source"));
            return;
        }
        Enchantment ench = content.getKey();
        int level = content.getValue();
        if (!manager.canAddCustom(item, ench)) {
            player.sendMessage(manager.prefix()
                    + manager.msg("gui-no-slot").replace("%slots%", String.valueOf(manager.enchantmentSlots())));
            return;
        }
        manager.apply(item, ench, level);
        top.setItem(SLOT_ITEM, item);
        top.setItem(SLOT_SOURCE, null);
        player.sendMessage(manager.prefix()
                + manager.msg("gui-applied").replace("%enchant%", ench.getKey().getKey())
                .replace("%level%", String.valueOf(level)));
    }

    private void wash(Player player, Inventory top) {
        ItemStack item = top.getItem(SLOT_ITEM);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(manager.prefix() + manager.msg("gui-no-item"));
            return;
        }
        top.setItem(SLOT_ITEM, manager.stripCustom(item));
        player.sendMessage(manager.prefix() + manager.msg("gui-washed"));
    }

    /** 从来源槽读取附魔：卷轴 或 附魔书（取第一个自定义附魔） */
    private Map.Entry<Enchantment, Integer> sourceContent(ItemStack src) {
        if (src == null) {
            return null;
        }
        if (manager.isScroll(src)) {
            return manager.scrollContent(src);
        }
        if (src.getType() == Material.ENCHANTED_BOOK && src.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            for (Enchantment e : meta.getStoredEnchants().keySet()) {
                if (e.getKey().getNamespace().equals("ecoclone")) {
                    return Map.entry(e, meta.getStoredEnchantLevel(e));
                }
            }
        }
        return null;
    }
}
