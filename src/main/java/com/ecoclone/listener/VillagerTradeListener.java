package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.Map;

/**
 * v1.4.7 高级功能：村民交易自动生成自定义附魔书。
 * 配置见 config.yml 的 villager 段（enabled / chance / max-uses / price.<rarity>）。
 */
public final class VillagerTradeListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public VillagerTradeListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!manager.villagerEnabled()) {
            return;
        }
        if (event.getEntity().getType() != EntityType.VILLAGER
                && event.getEntity().getType() != EntityType.WANDERING_TRADER) {
            return;
        }
        if (Math.random() > manager.villagerChance()) {
            return;
        }
        Map.Entry<Enchantment, Integer> roll = manager.randomEnchant();
        if (roll == null) {
            return;
        }
        String key = roll.getKey().getKey().getKey();
        int level = roll.getValue();
        ItemStack book = manager.bookOf(roll.getKey(), level);
        int price = manager.villagerPrice(manager.rarity(key));

        MerchantRecipe recipe = new MerchantRecipe(book, manager.villagerMaxUses());
        recipe.addIngredient(new ItemStack(Material.EMERALD, price));
        recipe.setExperienceReward(true);
        event.setRecipe(recipe);
    }
}
