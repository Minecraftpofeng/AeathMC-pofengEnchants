package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.loot.LootTable;

import java.util.Map;

/**
 * 战利品箱自动生成自定义附魔书。
 * 配置见 config.yml 的 loot 段（enabled / chance / max-books / level-multiplier / table-prefixes）。
 */
public final class LootInjectListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public LootInjectListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!manager.lootEnabled()) {
            return;
        }
        LootTable table = event.getLootTable();
        if (table == null || table.getKey() == null) {
            return;
        }
        String path = table.getKey().getKey();
        boolean matched = false;
        for (String prefix : manager.lootTablePrefixes()) {
            if (prefix != null && !prefix.isEmpty() && path.startsWith(prefix)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return;
        }
        if (Math.random() > manager.lootChance()) {
            return;
        }
        int count = 1 + (int) (Math.random() * manager.lootMaxBooks());
        double mult = manager.lootLevelMultiplier();
        for (int i = 0; i < count; i++) {
            // 按标签池 + 标签权重 + discoverable.chests 抽取：强力 3%，法术 1%，装饰不出
            Map.Entry<Enchantment, Integer> roll = manager.randomEnchant(manager.lootTypes(),
                    manager.lootTypeWeights(), k -> manager.discoverableChannel(k, "chests"));
            if (roll == null) {
                return;
            }
            int max = manager.maxLevel(roll.getKey().getKey().getKey());
            int level = Math.min(max, Math.max(1, (int) Math.round(roll.getValue() * mult)));
            event.getLoot().add(manager.bookOf(roll.getKey(), level));
        }
    }
}
