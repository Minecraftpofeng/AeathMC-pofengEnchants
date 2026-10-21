package com.ecoclone;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔清单 + 运行时访问。
 * 附魔注册逻辑位于 EcoCloneBootstrap（bootstrap 阶段，注册为真实附魔）。
 */
public final class Enchants {

    /** key -> 已注册的附魔对象 */
    public static final Map<String, Enchantment> REGISTERED = new LinkedHashMap<>();

    private Enchants() {
    }

    public static Enchantment get(String key) {
        return REGISTERED.get(key);
    }

    /** 该附魔在物品上的等级 */
    public static int level(ItemStack item, String key) {
        Enchantment e = REGISTERED.get(key);
        return e == null || item == null ? 0 : item.getEnchantmentLevel(e);
    }

    public static boolean has(ItemStack item, String key) {
        return level(item, key) > 0;
    }

    public static void store(String key, Enchantment enchantment) {
        REGISTERED.put(key, enchantment);
    }

    /** bootstrap 注册完成后，按 key 从 Registry 取回真实附魔对象（onEnable 时调用）。 */
    public static int loadFromRegistry() {
        REGISTERED.clear();
        // 遍历整个附魔注册表，筛出 ecoclone 命名空间的自定义附魔（比 get(单个key) 更可靠）
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (e.getKey().getNamespace().equals("ecoclone")) {
                REGISTERED.put(e.getKey().getKey(), e);
            }
        }
        // 兜底：如果遍历没找到，再按单个 key 取一次
        if (REGISTERED.isEmpty()) {
            for (EnchantDef def : definitions()) {
                TypedKey<Enchantment> k = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("ecoclone", def.key));
                Enchantment e = Registry.ENCHANTMENT.get(k);
                if (e != null) {
                    REGISTERED.put(def.key, e);
                }
            }
        }
        return REGISTERED.size();
    }

    /** 附魔完整清单（默认值，与 enchantments/*.yml 一一对应） */
    public static List<EnchantDef> definitions() {
        List<EnchantDef> list = new ArrayList<>();
        // 剑/斧
        list.add(def("beheading", "Beheading", "Chance of getting player or mob head", 3, 4, false, false, "rare", swAx(), 15, 10, 30, 10, 8));
        list.add(def("criticals", "Criticals", "Increases critical damage", 3, 5, false, false, "uncommon", swAx(), 10, 8, 20, 8, 5));
        list.add(def("first_strike", "First Strike", "Increases damage dealt when entity is on max health", 5, 4, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("bleed", "Bleed", "Causes your opponent to bleed, damaging them repeatedly", 7, 3, false, false, "rare", swords(), 10, 10, 20, 10, 8));
        list.add(def("leeching", "Leeching", "Heals a portion of damage dealt", 6, 3, false, false, "epic", swAx(), 15, 10, 30, 10, 8));
        list.add(def("thor", "Thor", "Chance of striking lightning on your opponent", 6, 3, false, false, "epic", swords(), 20, 10, 35, 10, 8));
        list.add(def("supercritical", "Supercritical", "Small chance to deal extreme damage", 3, 2, false, false, "mythic", swords(), 20, 10, 40, 10, 10));
        list.add(def("toxic", "Toxic", "Chance to apply poison to your opponent", 2, 6, false, false, "common", swords(), 5, 5, 10, 5, 3));
        list.add(def("wither_aspect", "Wither Aspect", "Chance to apply wither to your opponent", 2, 4, false, false, "uncommon", swords(), 10, 5, 20, 5, 4));
        list.add(def("razor", "Razor", "Massively increases damage dealt", 8, 1, false, true, "mythic", swords(), 30, 10, 60, 10, 12));
        // 挖掘
        list.add(def("blast_mining", "Blast Mining", "Mines blocks in a 3x3 area", 1, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("vein", "Vein", "Mine blocks in a vein", 5, 3, false, false, "epic", pickaxes(), 10, 10, 25, 10, 8));
        list.add(def("infernal_touch", "Infernal Touch", "Automatically smelt mined blocks", 1, 4, false, false, "rare", tools(), 10, 10, 20, 10, 8));
        list.add(def("telekinesis", "Telekinesis", "Drops and xp go directly into your inventory", 1, 3, false, false, "epic", tools(), 10, 10, 25, 10, 8));
        list.add(def("wisdom", "Wisdom", "Increases dropped experience", 3, 5, false, false, "uncommon", tools(), 10, 8, 20, 8, 5));
        list.add(def("energizing", "Energizing", "Receive a short burst of haste after breaking a block", 3, 1, false, true, "mythic", tools(), 20, 10, 40, 10, 10));
        // 弓
        list.add(def("boss_hunter", "Boss Hunter", "Increases damage against bosses", 8, 3, false, false, "epic", ranged(), 10, 10, 25, 10, 6));
        list.add(def("optics", "Optics", "Increases damage when further from target", 5, 4, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("tripleshot", "Tripleshot", "Shoots 3 arrows", 1, 1, false, false, "mythic", bows(), 20, 10, 40, 10, 10));
        list.add(def("force", "Force", "Massively increases arrow damage", 8, 1, false, true, "mythic", bows(), 30, 10, 60, 10, 12));
        list.add(def("zeus", "Zeus", "Chance to strike lightning on your opponent", 6, 3, false, false, "epic", ranged(), 20, 10, 35, 10, 8));
        // 护甲
        list.add(def("evasion", "Evasion", "Chance to ignore incoming damage", 3, 3, false, false, "epic", armor(), 15, 10, 30, 10, 8));
        list.add(def("preservation", "Preservation", "Massively reduces incoming damage", 4, 1, false, true, "mythic", armor(), 30, 10, 60, 10, 12));
        list.add(def("thrive", "Thrive", "Increases maximum health", 2, 4, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        // 靴子
        list.add(def("magnetic", "Magnetic", "Items and XP go towards you", 4, 4, false, false, "uncommon", boots(), 5, 5, 15, 5, 4));
        list.add(def("spring", "Spring", "Increases jump height and distance", 3, 1, false, true, "mythic", boots(), 20, 10, 40, 10, 10));
        list.add(def("freerunner", "Freerunner", "Chance to ignore fall damage", 6, 4, false, false, "rare", boots(), 5, 5, 15, 5, 5));
        // 通用
        list.add(def("soulbound", "Soulbound", "Keep this item on death", 1, 1, false, true, "mythic", everything(), 30, 10, 60, 10, 10));
        list.add(def("indestructibility", "Indestructibility", "Massively increases item durability", 3, 1, false, true, "mythic", everything(), 30, 10, 60, 10, 10));
        list.add(def("repairing", "Repairing", "Automatically repairs items when not held", 3, 1, false, true, "mythic", everything(), 30, 10, 60, 10, 10));
        // 诅咒
        list.add(def("curse_of_fragility", "Curse of Fragility", "Decreases item durability", 1, 4, true, false, "common", everything(), 5, 5, 10, 5, 5));
        list.add(def("curse_of_misfortune", "Curse of Misfortune", "Chance of blocks to not drop items", 1, 4, true, false, "common", tools(), 5, 5, 10, 5, 5));
        return list;
    }

    private static EnchantDef def(String key, String name, String desc, int maxLevel, int weight,
                                  boolean curse, boolean treasure, String rarity,
                                  List<TypedKey<ItemType>> targets,
                                  int minBase, int minPerLevel, int maxBase, int maxPerLevel, int anvilCost) {
        return new EnchantDef(key, name, desc, maxLevel, weight, curse, treasure, rarity,
                targets, minBase, minPerLevel, maxBase, maxPerLevel, anvilCost);
    }

    // ---------- 目标物品分组 ----------

    private static List<TypedKey<ItemType>> swords() {
        return List.of(ItemTypeKeys.WOODEN_SWORD, ItemTypeKeys.STONE_SWORD, ItemTypeKeys.IRON_SWORD,
                ItemTypeKeys.GOLDEN_SWORD, ItemTypeKeys.DIAMOND_SWORD, ItemTypeKeys.NETHERITE_SWORD);
    }

    private static List<TypedKey<ItemType>> axes() {
        return List.of(ItemTypeKeys.WOODEN_AXE, ItemTypeKeys.STONE_AXE, ItemTypeKeys.IRON_AXE,
                ItemTypeKeys.GOLDEN_AXE, ItemTypeKeys.DIAMOND_AXE, ItemTypeKeys.NETHERITE_AXE);
    }

    private static List<TypedKey<ItemType>> swAx() {
        List<TypedKey<ItemType>> l = new ArrayList<>(swords());
        l.addAll(axes());
        return l;
    }

    private static List<TypedKey<ItemType>> pickaxes() {
        return List.of(ItemTypeKeys.WOODEN_PICKAXE, ItemTypeKeys.STONE_PICKAXE, ItemTypeKeys.IRON_PICKAXE,
                ItemTypeKeys.GOLDEN_PICKAXE, ItemTypeKeys.DIAMOND_PICKAXE, ItemTypeKeys.NETHERITE_PICKAXE);
    }

    private static List<TypedKey<ItemType>> tools() {
        List<TypedKey<ItemType>> l = new ArrayList<>(pickaxes());
        l.addAll(axes());
        l.addAll(List.of(ItemTypeKeys.WOODEN_SHOVEL, ItemTypeKeys.STONE_SHOVEL, ItemTypeKeys.IRON_SHOVEL,
                ItemTypeKeys.GOLDEN_SHOVEL, ItemTypeKeys.DIAMOND_SHOVEL, ItemTypeKeys.NETHERITE_SHOVEL));
        l.addAll(List.of(ItemTypeKeys.WOODEN_HOE, ItemTypeKeys.STONE_HOE, ItemTypeKeys.IRON_HOE,
                ItemTypeKeys.GOLDEN_HOE, ItemTypeKeys.DIAMOND_HOE, ItemTypeKeys.NETHERITE_HOE));
        return l;
    }

    private static List<TypedKey<ItemType>> bows() {
        return List.of(ItemTypeKeys.BOW);
    }

    private static List<TypedKey<ItemType>> ranged() {
        return List.of(ItemTypeKeys.BOW, ItemTypeKeys.CROSSBOW);
    }

    private static List<TypedKey<ItemType>> armor() {
        return List.of(
                ItemTypeKeys.LEATHER_HELMET, ItemTypeKeys.LEATHER_CHESTPLATE, ItemTypeKeys.LEATHER_LEGGINGS, ItemTypeKeys.LEATHER_BOOTS,
                ItemTypeKeys.CHAINMAIL_HELMET, ItemTypeKeys.CHAINMAIL_CHESTPLATE, ItemTypeKeys.CHAINMAIL_LEGGINGS, ItemTypeKeys.CHAINMAIL_BOOTS,
                ItemTypeKeys.IRON_HELMET, ItemTypeKeys.IRON_CHESTPLATE, ItemTypeKeys.IRON_LEGGINGS, ItemTypeKeys.IRON_BOOTS,
                ItemTypeKeys.GOLDEN_HELMET, ItemTypeKeys.GOLDEN_CHESTPLATE, ItemTypeKeys.GOLDEN_LEGGINGS, ItemTypeKeys.GOLDEN_BOOTS,
                ItemTypeKeys.DIAMOND_HELMET, ItemTypeKeys.DIAMOND_CHESTPLATE, ItemTypeKeys.DIAMOND_LEGGINGS, ItemTypeKeys.DIAMOND_BOOTS,
                ItemTypeKeys.NETHERITE_HELMET, ItemTypeKeys.NETHERITE_CHESTPLATE, ItemTypeKeys.NETHERITE_LEGGINGS, ItemTypeKeys.NETHERITE_BOOTS);
    }

    private static List<TypedKey<ItemType>> boots() {
        return List.of(ItemTypeKeys.LEATHER_BOOTS, ItemTypeKeys.CHAINMAIL_BOOTS, ItemTypeKeys.IRON_BOOTS,
                ItemTypeKeys.GOLDEN_BOOTS, ItemTypeKeys.DIAMOND_BOOTS, ItemTypeKeys.NETHERITE_BOOTS);
    }

    private static List<TypedKey<ItemType>> everything() {
        List<TypedKey<ItemType>> l = new ArrayList<>();
        l.addAll(swords());
        l.addAll(axes());
        l.addAll(pickaxes());
        l.addAll(tools());
        l.addAll(armor());
        l.addAll(List.of(ItemTypeKeys.BOW, ItemTypeKeys.CROSSBOW, ItemTypeKeys.TRIDENT,
                ItemTypeKeys.SHIELD, ItemTypeKeys.ELYTRA, ItemTypeKeys.FISHING_ROD));
        return l;
    }
}
