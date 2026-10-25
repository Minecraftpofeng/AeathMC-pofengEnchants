package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 挖掘附魔效果：Blast Mining / Vein / Infernal Touch / Telekinesis /
 * Wisdom / Energizing / Curse of Misfortune。
 */
public final class MiningListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public MiningListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        // Auto Repair：缓慢修复带该附魔的工具（每 2 秒）
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                    for (ItemStack it : p.getInventory().getContents()) {
                        if (it == null) {
                            continue;
                        }
                        int ar = Enchants.level(it, "auto_repair");
                        if (ar > 0 && it.getType().getMaxDurability() > 0 && it.getDurability() > 0) {
                            it.setDurability((short) Math.max(0, it.getDurability() - ar));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        Block block = event.getBlock();

        // 诅咒：概率不掉落
        if (Enchants.has(held, "curse_of_misfortune") && Math.random() < 0.25) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        // Telekinesis：掉落直接进背包
        boolean tele = Enchants.has(held, "telekinesis");
        if (tele && event.isDropItems()) {
            List<ItemStack> drops = new ArrayList<>(block.getDrops(held));
            event.setDropItems(false);
            for (ItemStack d : drops) {
                player.getInventory().addItem(d).values().forEach(left ->
                        block.getWorld().dropItemNaturally(block.getLocation(), left));
            }
        }

        // Infernal Touch：自动熔炼
        if (Enchants.has(held, "infernal_touch") && event.isDropItems()) {
            for (ItemStack d : block.getDrops(held)) {
                Material smelted = smelt(d.getType());
                if (smelted != null) {
                    ItemStack out = d.clone();
                    out.setType(smelted);
                    event.setDropItems(false);
                    block.getWorld().dropItemNaturally(block.getLocation(), out);
                }
            }
        }

        // Wisdom：额外经验
        int wisdom = Enchants.level(held, "wisdom");
        if (wisdom > 0) {
            event.setExpToDrop(event.getExpToDrop() + 2 * wisdom);
        }

        // Energizing：获得短时急迫
        int ener = Enchants.level(held, "energizing");
        if (ener > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40 * ener, 0));
        }

        // Blast Mining：3x3 挖掘
        if (Enchants.has(held, "blast_mining")) {
            double hardness = block.getType().getHardness();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block b = block.getRelative(dx, dy, dz);
                        if (breakable(b, hardness)) {
                            b.breakNaturally(held);
                        }
                    }
                }
            }
        }

        // Vein：连锁挖掘矿脉
        int vein = Enchants.level(held, "vein");
        if (vein > 0 && block.getType().name().contains("ORE")) {
            int radius = 3 + vein;
            Set<Block> veinBlocks = new HashSet<>();
            Deque<Block> queue = new ArrayDeque<>();
            queue.add(block);
            Material type = block.getType();
            while (!queue.isEmpty() && veinBlocks.size() < 40) {
                Block cur = queue.poll();
                if (cur.getType() != type) {
                    continue;
                }
                if (!veinBlocks.add(cur)) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block nb = cur.getRelative(dx, dy, dz);
                            if (nb.getType() == type
                                    && nb.getLocation().distanceSquared(block.getLocation()) <= radius * radius) {
                                queue.add(nb);
                            }
                        }
                    }
                }
            }
            for (Block b : veinBlocks) {
                if (b.equals(block)) {
                    continue;
                }
                b.breakNaturally(held);
            }
        }

        // Lumberjack：整棵树砍伐
        if (Enchants.has(held, "lumberjack")
                && (block.getType().name().endsWith("_LOG") || block.getType().name().endsWith("_WOOD"))) {
            chopTree(block, held);
        }

        // Scavenger：概率双倍掉落
        int scav = Enchants.level(held, "scavenger");
        if (scav > 0 && event.isDropItems() && Math.random() < 0.1 * scav) {
            for (ItemStack d : new ArrayList<>(block.getDrops(held))) {
                block.getWorld().dropItemNaturally(block.getLocation(), d);
            }
        }

        // Drill：挖掘时获得急迫
        int drill = Enchants.level(held, "drill");
        if (drill > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, Math.min(2, drill - 1)));
        }

        // Excavation：大范围挖掘
        int exca = Enchants.level(held, "excavation");
        if (exca > 0) {
            int r = Math.min(3, 1 + exca);
            double hardness = block.getType().getHardness();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block b = block.getRelative(dx, dy, dz);
                        if (breakable(b, hardness)) {
                            b.breakNaturally(held);
                        }
                    }
                }
            }
        }

        // Smelter：自动熔炼
        if (Enchants.has(held, "smelter") && event.isDropItems()) {
            for (ItemStack d : block.getDrops(held)) {
                Material sm = smelt(d.getType());
                if (sm != null) {
                    ItemStack out = d.clone();
                    out.setType(sm);
                    event.setDropItems(false);
                    block.getWorld().dropItemNaturally(block.getLocation(), out);
                }
            }
        }

        // Lucky Mining：概率双倍掉落
        int lucky = Enchants.level(held, "lucky_mining");
        if (lucky > 0 && event.isDropItems() && Math.random() < 0.1 * lucky) {
            for (ItemStack d : new ArrayList<>(block.getDrops(held))) {
                block.getWorld().dropItemNaturally(block.getLocation(), d);
            }
        }

        // Prospector：探矿（概率额外掉落矿石）
        int prosp = Enchants.level(held, "prospector");
        if (prosp > 0 && block.getType().name().contains("ORE") && Math.random() < 0.15 * prosp) {
            for (ItemStack d : new ArrayList<>(block.getDrops(held))) {
                block.getWorld().dropItemNaturally(block.getLocation(), d);
            }
        }

        // Harvest：丰收（大范围收割作物）
        int harv = Enchants.level(held, "harvest");
        if (harv > 0 && isCrop(block.getType())) {
            int r = 1 + harv;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    Block b = block.getRelative(dx, 0, dz);
                    if (b.getType() == block.getType()) {
                        b.breakNaturally(held);
                    }
                }
            }
        }
    }

    private static boolean isCrop(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES
                || m == Material.BEETROOTS || m == Material.NETHER_WART;
    }

    private void chopTree(Block origin, ItemStack held) {
        Material type = origin.getType();
        Set<Block> logs = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty() && logs.size() < 80) {
            Block cur = queue.poll();
            if (cur.getType() != type) {
                continue;
            }
            if (!logs.add(cur)) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block nb = cur.getRelative(dx, dy, dz);
                        if (nb.getType() == type) {
                            queue.add(nb);
                        }
                    }
                }
            }
        }
        for (Block b : logs) {
            if (!b.equals(origin)) {
                b.breakNaturally(held);
            }
        }
    }

    private static boolean breakable(Block b, double hardness) {
        if (b.getType().isAir()) {
            return false;
        }
        if (b.getType() == Material.BEDROCK || b.getType() == Material.OBSIDIAN) {
            return false;
        }
        if (b.isLiquid()) {
            return false;
        }
        return b.getType().getHardness() <= hardness;
    }

    private static Material smelt(Material m) {
        return switch (m) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            case CLAY -> Material.BRICK;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }
}
