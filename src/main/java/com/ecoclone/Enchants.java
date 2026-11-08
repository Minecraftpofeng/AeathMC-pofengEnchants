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

    /** 该附魔在物品上的等级（v1.4.7.0 起同时套 conditions / triggers 闸门） */
    public static int level(ItemStack item, String key) {
        Enchantment e = REGISTERED.get(key);
        int value = e == null || item == null ? 0 : item.getEnchantmentLevel(e);
        if (value <= 0) {
            return 0;
        }
        return EnchantGate.allows(item, key) ? value : 0;
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
        list.add(def("corrosion", "Corrosion", "Melts your opponent, dealing damage over time", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 8));
        list.add(def("laceration", "Laceration", "Tears open wounds that bleed heavily over time", 4, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("scorching", "Scorching", "Burns your opponent with soul fire over time", 5, 4, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("rotting", "Rotting", "Inflicts a rotting decay that drains health over time", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("leeching", "Leeching", "Heals a portion of damage dealt", 6, 3, false, false, "epic", swAx(), 15, 10, 30, 10, 8));
        list.add(def("thor", "Thor", "Chance of striking lightning on your opponent", 6, 3, false, false, "epic", swords(), 20, 10, 35, 10, 8));
        list.add(def("supercritical", "Supercritical", "Small chance to deal extreme damage", 3, 2, false, false, "mythic", swords(), 20, 10, 40, 10, 10));
        list.add(def("toxic", "Toxic", "Chance to apply poison to your opponent", 2, 6, false, false, "common", swords(), 5, 5, 10, 5, 3));
        list.add(def("wither_aspect", "Wither Aspect", "Chance to apply wither to your opponent", 2, 4, false, false, "uncommon", swords(), 10, 5, 20, 5, 4));
        list.add(def("razor", "Razor", "Massively increases damage dealt", 8, 1, false, true, "mythic", swords(), 30, 10, 60, 10, 12));
        list.add(def("frost", "Frost", "Chance to freeze your opponent with slowness", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("executioner", "Executioner", "Deals bonus damage to low health enemies", 4, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("armor_piercing", "Armor Piercing", "Ignores a portion of enemy armor", 3, 4, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("undead_bane", "Undead Bane", "Deals bonus damage against undead mobs", 6, 4, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("frenzy", "Frenzy", "Gain strength and speed after a kill", 5, 3, false, false, "epic", swords(), 10, 10, 20, 10, 8));
        list.add(def("greedy", "Greedy", "Earn extra experience from kills", 5, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("berserker", "Berserker", "Deals more damage the lower your health is", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("chain_lightning", "Chain Lightning", "Chance to strike lightning on nearby enemies", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("blind", "Blind", "Chance to blind your opponent", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("nausea", "Nausea", "Chance to give your opponent nausea", 3, 3, false, false, "common", swords(), 10, 10, 20, 10, 4));
        list.add(def("insomnia", "Insomnia", "Chance to fatigue your opponent", 2, 4, false, false, "epic", swords(), 10, 10, 25, 10, 6));
        list.add(def("king_descent", "King's Descent", "Deal more damage in the Overworld and End", 2, 1, false, false, "mythic", swords(), 25, 10, 50, 10, 12));
        list.add(def("conductive", "Conductive", "Strike lightning above your target", 2, 2, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("explosive", "Explosive", "Strikes cause an explosion", 2, 2, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("phantom", "Phantom", "Chance to dodge incoming damage", 10, 2, false, false, "rare", swords(), 10, 10, 20, 10, 5));
        list.add(def("sneak_attack", "Sneak Attack", "Deal bonus damage when attacking from behind", 2, 4, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("eclipse", "Eclipse", "Chance to plunge your target into darkness", 2, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("execute", "Execute", "Chance to instantly kill enemies at low health", 1, 1, false, true, "mythic", swords(), 20, 10, 40, 10, 10));
        list.add(def("bloodlust", "Bloodlust", "Heal and gain speed after a kill", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("soul_siphon", "Soul Siphon", "Restore hunger after a kill", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
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
        list.add(def("explosive_arrow", "Explosive Arrow", "Arrows explode on impact", 2, 3, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        list.add(def("blaze", "Blaze", "Arrows set targets on fire", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("shockwave", "Shockwave", "Arrows knock back nearby enemies", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("homing", "Homing", "Arrows home toward the nearest enemy", 3, 2, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        // 护甲
        list.add(def("evasion", "Evasion", "Chance to ignore incoming damage", 3, 3, false, false, "epic", armor(), 15, 10, 30, 10, 8));
        list.add(def("preservation", "Preservation", "Massively reduces incoming damage", 4, 1, false, true, "mythic", armor(), 30, 10, 60, 10, 12));
        list.add(def("thrive", "Thrive", "Increases maximum health", 2, 4, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("flame_guard", "Flame Guard", "Reduces fire and lava damage", 4, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("magic_shield", "Magic Shield", "Reduces magic and potion damage", 4, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("fortitude", "Fortitude", "Reduces explosion damage", 4, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("phoenix", "Phoenix", "Chance to survive a fatal blow", 1, 1, false, true, "mythic", armor(), 20, 10, 40, 10, 10));
        list.add(def("regeneration", "Regeneration", "Regenerate health while worn", 3, 2, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        // 头盔
        list.add(def("night_vision", "Night Vision", "Grants night vision while worn", 1, 2, false, false, "uncommon", helmets(), 10, 10, 20, 10, 4));
        // 靴子
        list.add(def("magnetic", "Magnetic", "Items and XP go towards you", 4, 4, false, false, "uncommon", boots(), 5, 5, 15, 5, 4));
        list.add(def("spring", "Spring", "Increases jump height and distance", 3, 1, false, true, "mythic", boots(), 20, 10, 40, 10, 10));
        list.add(def("freerunner", "Freerunner", "Chance to ignore fall damage", 6, 4, false, false, "rare", boots(), 5, 5, 15, 5, 5));
        list.add(def("adrenaline", "Adrenaline", "Gain speed when low on health", 3, 2, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("diver", "Diver", "Breathe and see underwater", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("swift", "Swift", "Grants a speed boost while worn", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        // 斧
        list.add(def("lumberjack", "Lumberjack", "Instantly chop entire trees", 1, 3, false, false, "rare", axes(), 10, 10, 20, 10, 6));
        list.add(def("scavenger", "Scavenger", "Chance of double drops from blocks", 5, 3, false, false, "rare", tools(), 10, 10, 20, 10, 6));
        list.add(def("drill", "Drill", "Grants haste while mining", 3, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        // 通用
        list.add(def("soulbound", "Soulbound", "Keep this item on death", 1, 1, false, true, "mythic", everything(), 30, 10, 60, 10, 10));
        list.add(def("indestructibility", "Indestructibility", "Massively increases item durability", 3, 1, false, true, "mythic", everything(), 30, 10, 60, 10, 10));
        list.add(def("repairing", "Repairing", "Automatically repairs items when not held", 3, 1, false, true, "mythic", everything(), 30, 10, 60, 10, 10));
        // 诅咒
        list.add(def("curse_of_fragility", "Curse of Fragility", "Decreases item durability", 1, 4, true, false, "common", everything(), 5, 5, 10, 5, 5));
        list.add(def("curse_of_misfortune", "Curse of Misfortune", "Chance of blocks to not drop items", 1, 4, true, false, "common", tools(), 5, 5, 10, 5, 5));
        list.add(def("curse_of_weakness", "Curse of Weakness", "Reduces damage you deal while equipped", 3, 4, true, false, "common", everything(), 5, 5, 10, 5, 5));
        // ===== 新增 40 个附魔 =====
        // 剑/近战
        list.add(def("frost_aura", "Frost Aura", "Chance to freeze your opponent", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("cripple", "Cripple", "Reduces your opponent's attack speed", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("vampiric", "Vampiric", "Greatly heals a portion of damage dealt", 5, 3, false, false, "epic", swords(), 15, 10, 30, 10, 8));
        list.add(def("guardian", "Guardian", "Reduces damage taken while blocking", 3, 3, false, false, "rare", swAx(), 10, 10, 20, 10, 6));
        list.add(def("venomous", "Venomous", "Chance to apply strong poison", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("storm_call", "Storm Call", "Strike lightning on your opponent", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("ender_shift", "Ender Shift", "Teleport behind your target on hit", 2, 2, false, false, "mythic", swords(), 20, 10, 40, 10, 10));
        list.add(def("dual_strike", "Dual Strike", "Chance to strike a second time", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("fear", "Fear", "Chance to make your opponent flee", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("cleave", "Cleave", "Deal splash damage to nearby enemies", 5, 3, false, false, "rare", swAx(), 10, 10, 20, 10, 6));
        // 弓/远程
        list.add(def("pierce_shot", "Pierce Shot", "Arrows pierce through enemies", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("arrow_volley", "Arrow Volley", "Shoot extra arrows", 3, 3, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        list.add(def("gravitational", "Gravitational", "Arrows pull enemies toward you", 3, 3, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        list.add(def("frost_arrow", "Frost Arrow", "Arrows slow enemies", 3, 3, false, false, "uncommon", ranged(), 10, 10, 20, 10, 5));
        list.add(def("venom_arrow", "Venom Arrow", "Arrows poison enemies", 3, 3, false, false, "uncommon", ranged(), 10, 10, 20, 10, 5));
        list.add(def("blood_arrow", "Blood Arrow", "Arrows heal you on hit", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("penetrate", "Penetrate", "Arrows ignore enemy armor", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("explosive_tip", "Explosive Tip", "Arrows explode on impact", 2, 3, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        // 护甲/防具
        list.add(def("thorns_reflect", "Thorns Reflect", "Reflect damage back to attackers", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("iron_body", "Iron Body", "Reduces incoming damage", 4, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("vitality", "Vitality", "Increases maximum health", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("fire_ward", "Fire Ward", "Reduces fire damage", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("poison_ward", "Poison Ward", "Cures poison", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("wither_ward", "Wither Ward", "Reduces wither damage", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("explosion_ward", "Explosion Ward", "Reduces explosion damage", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("undying", "Undying", "Chance to survive a fatal blow", 1, 1, false, true, "mythic", armor(), 20, 10, 40, 10, 10));
        list.add(def("aura_light", "Aura of Light", "Light up your surroundings", 1, 2, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("resistance", "Resistance", "Reduces all incoming damage", 4, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        // 靴子/头盔
        list.add(def("aqua_step", "Aqua Step", "Walk on water with a frozen path", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("rocket_boots", "Rocket Boots", "Boost forward when sprinting", 3, 3, false, false, "epic", boots(), 10, 10, 25, 10, 8));
        list.add(def("speed_boots", "Speed Boots", "Grants speed while worn", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        list.add(def("feather_fall", "Feather Fall", "Negate fall damage", 3, 3, false, false, "common", boots(), 5, 5, 15, 5, 4));
        list.add(def("night_eye", "Night Eye", "Grants night vision while worn", 1, 2, false, false, "uncommon", helmets(), 10, 10, 20, 10, 4));
        list.add(def("water_breath", "Water Breath", "Breathe underwater", 2, 2, false, false, "common", helmets(), 10, 10, 20, 10, 5));
        // 工具
        list.add(def("excavation", "Excavation", "Mines blocks in a large area", 5, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("prospector", "Prospector", "Chance to reveal ores", 3, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("harvest", "Harvest", "Harvest crops in a wide area", 5, 3, false, false, "rare", tools(), 10, 10, 20, 10, 6));
        list.add(def("auto_repair", "Auto Repair", "Slowly repairs the tool", 3, 2, false, true, "mythic", tools(), 20, 10, 40, 10, 10));
        list.add(def("smelter", "Smelter", "Automatically smelt mined blocks", 1, 3, false, false, "rare", tools(), 10, 10, 20, 10, 8));
        list.add(def("lucky_mining", "Lucky Mining", "Chance of double drops", 5, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        // ===== v1.4.2 新增 50 个附魔 =====
        // 近战
        list.add(def("gale_strike", "Gale Strike", "Knocks back and slows your opponent", 4, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("thunder_clap", "Thunder Clap", "Chance to stun with a thunder shock", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("sunder", "Sunder", "Shatters enemy armor on hit", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("momentum", "Momentum", "Consecutive hits deal increasing damage", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("parry", "Parry", "Blocking negates damage and counters", 3, 3, false, false, "rare", swAx(), 10, 10, 20, 10, 6));
        list.add(def("rend", "Rend", "Slows and bleeds your opponent", 4, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("stagger", "Stagger", "Chance to weaken your opponent's attacks", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("ghost_blade", "Ghost Blade", "Attacks partially ignore armor and dodge", 3, 2, false, false, "mythic", swords(), 20, 10, 40, 10, 10));
        list.add(def("echo_strike", "Echo Strike", "Chance to strike multiple times", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("pursuit", "Pursuit", "Deal more damage to sprinting targets", 4, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("counter", "Counter", "Counterattack when hit", 3, 3, false, false, "epic", swAx(), 10, 10, 20, 10, 8));
        list.add(def("blade_fury", "Blade Fury", "Increases attack speed", 4, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        // 弓
        list.add(def("rapid_fire", "Rapid Fire", "Draws and fires arrows faster", 3, 3, false, false, "rare", bows(), 10, 10, 20, 10, 6));
        list.add(def("scattershot", "Scattershot", "Shoots a spread of extra arrows", 3, 3, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        list.add(def("chilling", "Chilling", "Arrows chill and slow enemies", 3, 3, false, false, "uncommon", ranged(), 10, 10, 20, 10, 5));
        list.add(def("marking", "Marking", "Marked targets take more damage", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("trajectory", "Trajectory", "Arrows fly further with less drop", 3, 3, false, false, "uncommon", ranged(), 10, 10, 20, 10, 5));
        list.add(def("spark", "Spark", "Arrows ignite a small area", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("void_shot", "Void Shot", "Chance to instantly kill low-health targets", 1, 1, false, true, "mythic", ranged(), 20, 10, 40, 10, 10));
        list.add(def("arrow_rain", "Arrow Rain", "Strike arrows from above", 2, 2, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        // 工具
        list.add(def("greed", "Greed", "Extra drops from mining", 5, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        list.add(def("resourceful", "Resourceful", "Chance for bonus ores while mining", 3, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("mining_fury", "Mining Fury", "Gain strength after breaking blocks", 3, 3, false, false, "epic", tools(), 10, 10, 25, 10, 8));
        list.add(def("ore_vision", "Ore Vision", "Nearby ores glow", 1, 2, false, false, "epic", pickaxes(), 10, 10, 25, 10, 8));
        list.add(def("compact", "Compact", "Mined blocks grant bonus experience", 4, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        list.add(def("tunneling", "Tunneling", "Mine a horizontal tunnel", 2, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("quarry", "Quarry", "Mine downward in a shaft", 2, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("xp_boost", "XP Boost", "Bonus experience from mining", 3, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        // 护甲
        list.add(def("fortress", "Fortress", "Chance to block incoming attacks", 4, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("aegis", "Aegis", "Reduces incoming damage", 4, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("blessing", "Blessing", "Chance to remove negative effects", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("warmth", "Warmth", "Immune to slowness and freeze", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("last_stand", "Last Stand", "Greatly reduce damage at low health", 3, 2, false, false, "epic", armor(), 10, 10, 25, 10, 8));
        list.add(def("ironclad", "Ironclad", "Increases armor", 4, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("blood_armor", "Blood Armor", "Heal a portion of damage taken", 3, 3, false, false, "epic", armor(), 10, 10, 25, 10, 8));
        list.add(def("divine_protection", "Divine Protection", "Chance to completely negate damage", 2, 1, false, true, "mythic", armor(), 20, 10, 40, 10, 10));
        // 靴/盔
        list.add(def("stomper", "Stomper", "Landing stomp damages enemies", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("long_jump", "Long Jump", "Sprint jump launches further", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("echo", "Echo", "Leave a ghostly trail while moving", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        list.add(def("mountain_climber", "Mountain Climber", "Move faster while climbing", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        list.add(def("glider", "Glider", "Descend slowly", 3, 2, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("clear_mind", "Clear Mind", "Immune to blindness", 2, 3, false, false, "uncommon", helmets(), 10, 10, 20, 10, 5));
        list.add(def("hunter_vision", "Hunter Vision", "Highlight nearby hostile mobs", 2, 2, false, false, "epic", helmets(), 10, 10, 25, 10, 8));
        list.add(def("frost_walker", "Frost Walker", "Freeze water beneath your feet", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        // 通用
        list.add(def("curse_break", "Curse Break", "Reduces the penalty of curses", 3, 3, false, false, "rare", everything(), 10, 10, 20, 10, 6));
        list.add(def("enhance", "Enhance", "Slightly boosts all attributes", 3, 3, false, false, "epic", everything(), 10, 10, 25, 10, 8));
        list.add(def("gold_rush", "Gold Rush", "Kills grant bonus experience and drops", 4, 3, false, false, "uncommon", everything(), 10, 10, 20, 10, 5));
        list.add(def("time_ward", "Time Ward", "Keep experience on death", 1, 1, false, true, "mythic", everything(), 20, 10, 40, 10, 10));
        list.add(def("prosperity", "Prosperity", "Increases all earned experience", 3, 3, false, false, "rare", everything(), 10, 10, 20, 10, 6));
        list.add(def("lucky", "Lucky", "Small chance for bonus loot", 3, 3, false, false, "uncommon", everything(), 10, 10, 20, 10, 5));
        // ===== v1.4.6 新增附魔书 10 个 =====
        list.add(def("soul_rend", "Soul Rend", "Hits wither your opponent", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("hex", "Hex", "Hits weaken your opponent", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("thunder_fury", "Thunder Fury", "Hits strike lightning above your opponent", 2, 2, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("siphoning", "Siphoning", "Hits heal a portion of damage dealt", 5, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("ember", "Ember", "Arrows ignite targets with soul fire", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("deep_mine", "Deep Mine", "Grants haste while holding a pickaxe", 3, 3, false, false, "uncommon", pickaxes(), 10, 10, 20, 10, 5));
        list.add(def("turtle_shell", "Turtle Shell", "Grants resistance while worn", 3, 2, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("granite_skin", "Granite Skin", "Increases armor while worn", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("swift_step", "Swift Step", "Grants speed while worn", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        list.add(def("nightguard", "Nightguard", "Grants night vision and glowing sight", 1, 2, false, false, "rare", helmets(), 10, 10, 20, 10, 6));
        // ===== v1.4.6.3 新增附魔书 11 个 =====
        list.add(def("frost_brand", "Frost Brand", "Hits freeze and chill your opponent", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("culling_blade", "Culling Blade", "Bonus damage to low-health enemies", 4, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("arcane_strike", "Arcane Strike", "Chance to deal bonus magic damage", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("crushing_blow", "Crushing Blow", "Hits weaken armor and deal bonus damage", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("heavy_arrow", "Heavy Arrow", "Arrows deal bonus damage and knock back", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("ice_tip", "Ice Tip", "Arrows freeze targets", 3, 3, false, false, "uncommon", ranged(), 10, 10, 20, 10, 5));
        list.add(def("ore_magnet", "Ore Magnet", "Mined ores pull nearby drops toward you", 3, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        list.add(def("mining_expert", "Mining Expert", "Extra experience from mining", 3, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        list.add(def("second_wind", "Second Wind", "Regenerate at low health while worn", 3, 2, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("frost_guard", "Frost Guard", "Nearly negate freeze damage", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("speed_dash", "Speed Dash", "Sprint faster while worn", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        // ===== v1.4.6.4 新增附魔书 11 个 =====
        list.add(def("glacial_grip", "Glacial Grip", "Hits deeply freeze your opponent", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("punisher", "Punisher", "Bonus damage against buffed enemies", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("interrupt", "Interrupt", "Hits strip positive effects from your opponent", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("debilitating_shot", "Debilitating Shot", "Arrows weaken and slow targets", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("corrosive_arrow", "Corrosive Arrow", "Arrows weaken enemy armor", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("timberlord", "Timberlord", "Chance of double logs when chopping trees", 3, 3, false, false, "epic", axes(), 10, 10, 25, 10, 8));
        list.add(def("stalwart", "Stalwart", "Reduce damage while at high health", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("cleansing", "Cleansing", "Periodically purge negative effects while worn", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("retribution", "Retribution", "Chance to counterattack when hit", 3, 3, false, false, "epic", armor(), 10, 10, 25, 10, 8));
        list.add(def("celerity", "Celerity", "Sprint faster while worn", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("prospecting", "Prospecting", "Mined ores may drop extra gold nuggets", 3, 3, false, false, "rare", pickaxes(), 10, 10, 20, 10, 6));
        // ===== v1.4.6.6 对照 EcoEnchants 新增附魔 10 个 =====
        list.add(def("goliath", "Goliath", "Extra damage against enemies with more health than you", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("water_aspect", "Water Aspect", "Bonus damage against fiery mobs and endermen", 3, 3, false, false, "uncommon", swAx(), 10, 10, 20, 10, 5));
        list.add(def("confusion", "Confusion", "Chance to shuffle your opponent's hotbar", 3, 3, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("valiant", "Valiant", "Bonus damage when close to your target", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("sniper", "Sniper", "More damage the further the arrow travels", 5, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("fire_warding", "Fire Warding", "Chance to remove fire when burning", 3, 3, false, false, "uncommon", armor(), 10, 10, 20, 10, 5));
        list.add(def("blazing", "Blazing", "Chance to set your attacker on fire", 3, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("stamina", "Stamina", "Restore hunger while sprinting", 3, 3, false, false, "uncommon", boots(), 10, 10, 20, 10, 5));
        list.add(def("oxygenate", "Oxygenate", "Gain oxygen when mining underwater", 3, 3, false, false, "uncommon", pickaxes(), 10, 10, 20, 10, 5));
        list.add(def("juggler", "Juggler", "Arrows ignore gravity", 1, 1, false, false, "rare", bows(), 10, 10, 20, 10, 6));
        // ===== v1.4.6.8 对标 EcoEnchants 新增 =====
        list.add(def("cubism", "Cubism", "Extra damage against slimes and magma cubes", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("insecticide", "Insecticide", "Extra damage against spiders, silverfish and endermites", 3, 4, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("plague", "Plague", "Chance to poison nearby enemies on hit", 3, 2, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("overload", "Overload", "Chance to deal heavy damage but hurt yourself", 3, 2, false, false, "epic", swords(), 10, 10, 25, 10, 8));
        list.add(def("precision", "Precision", "Chance for arrows to deal critical damage", 3, 3, false, false, "rare", ranged(), 10, 10, 20, 10, 6));
        list.add(def("ricochet", "Ricochet", "Arrows bounce and damage a nearby enemy", 3, 2, false, false, "epic", ranged(), 10, 10, 25, 10, 8));
        list.add(def("turbulence", "Turbulence", "Arrows kick nearby enemies back", 3, 3, false, false, "uncommon", ranged(), 10, 10, 20, 10, 5));
        list.add(def("replenish", "Replenish", "Replants harvested crops automatically", 1, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        list.add(def("suction", "Suction", "Pulls nearby dropped items toward you", 3, 3, false, false, "rare", boots(), 10, 10, 20, 10, 6));
        list.add(def("voltaic", "Voltaic", "Chance to shock nearby enemies on hit", 3, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        // ===== v1.4.6.9 对标 EcoEnchants 新增 =====
        list.add(def("abrasion", "Abrasion", "Chance to damage your opponent's armor", 3, 3, false, false, "uncommon", swAx(), 10, 10, 20, 10, 5));
        list.add(def("antidote", "Antidote", "Chance to be immune to potion damage", 6, 3, false, false, "rare", armor(), 10, 10, 20, 10, 6));
        list.add(def("butcher", "Butcher", "Extra damage against passive animals", 5, 3, false, false, "uncommon", swAx(), 10, 10, 20, 10, 5));
        list.add(def("defusion", "Defusion", "Extra damage against creepers", 5, 3, false, false, "uncommon", swAx(), 10, 10, 20, 10, 5));
        list.add(def("ender_slayer", "Ender Slayer", "Extra damage against end creatures", 5, 3, false, false, "rare", swAx(), 10, 10, 20, 10, 6));
        list.add(def("nether_dictator", "Nether Dictator", "Extra damage while in the Nether", 6, 2, false, false, "epic", swAx(), 10, 10, 25, 10, 8));
        list.add(def("moonlight", "Moonlight", "Extra damage at night", 6, 3, false, false, "rare", swords(), 10, 10, 20, 10, 6));
        list.add(def("paladin", "Paladin", "Extra damage while riding", 3, 3, false, false, "uncommon", swords(), 10, 10, 20, 10, 5));
        list.add(def("diet", "Diet", "Chance to prevent hunger loss", 6, 3, false, false, "rare", helmets(), 10, 10, 20, 10, 6));
        list.add(def("launch", "Launch", "Chance to launch your enemy into the air", 3, 2, false, false, "epic", swAx(), 10, 10, 25, 10, 8));
        list.add(def("splash", "Splash", "Tridents deal area damage on landing", 4, 3, false, false, "rare", tridents(), 10, 10, 20, 10, 6));
        list.add(def("haste", "Haste", "Gain haste after mining blocks", 3, 3, false, false, "uncommon", tools(), 10, 10, 20, 10, 5));
        list.add(def("shield_bash", "Shield Bash", "Knock back and damage attackers while blocking", 3, 3, false, false, "uncommon", shields(), 10, 10, 20, 10, 5));
        list.add(def("spikes", "Spikes", "Reflect contact damage to attackers while blocking", 3, 3, false, false, "common", shields(), 10, 10, 20, 10, 4));
        list.add(def("mirror", "Mirror", "Chance to fully reflect damage while blocking", 2, 2, false, false, "epic", shields(), 10, 10, 20, 10, 8));
        list.add(def("lucky_catch", "Lucky Catch", "Chance to catch an extra fish", 3, 3, false, false, "uncommon", fishingRods(), 10, 10, 20, 10, 5));
        list.add(def("grappling", "Grappling", "Reel in hooked entities", 3, 3, false, false, "rare", fishingRods(), 10, 10, 20, 10, 6));
        list.add(def("conservation", "Conservation", "Chance to save elytra durability while gliding", 3, 3, false, false, "rare", elytras(), 10, 10, 20, 10, 6));
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

    private static List<TypedKey<ItemType>> helmets() {
        return List.of(ItemTypeKeys.LEATHER_HELMET, ItemTypeKeys.CHAINMAIL_HELMET, ItemTypeKeys.IRON_HELMET,
                ItemTypeKeys.GOLDEN_HELMET, ItemTypeKeys.DIAMOND_HELMET, ItemTypeKeys.NETHERITE_HELMET);
    }

    private static List<TypedKey<ItemType>> tridents() {
        return List.of(ItemTypeKeys.TRIDENT);
    }

    private static List<TypedKey<ItemType>> shields() {
        return List.of(ItemTypeKeys.SHIELD);
    }

    private static List<TypedKey<ItemType>> elytras() {
        return List.of(ItemTypeKeys.ELYTRA);
    }

    private static List<TypedKey<ItemType>> fishingRods() {
        return List.of(ItemTypeKeys.FISHING_ROD);
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

    /**
     * v1.4.7 高级功能：静态清单 + 市场导入的附魔。
     * 导入的 yml 落在 plugins/&lt;插件&gt;/enchantments/&lt;key&gt;.yml，bootstrap 阶段据此注册为真实附魔，
     * 因此新导入的附魔重启服务端后即可用。
     */
    public static List<EnchantDef> withImported(java.nio.file.Path dataDir) {
        List<EnchantDef> base = definitions();
        List<EnchantDef> out = new ArrayList<>(base);
        if (dataDir == null) {
            return out;
        }
        java.io.File dir = dataDir.resolve("enchantments").toFile();
        java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) {
            return out;
        }
        java.util.Set<String> known = new java.util.HashSet<>();
        for (EnchantDef d : base) {
            known.add(d.key);
        }
        for (java.io.File f : files) {
            String key = f.getName().substring(0, f.getName().length() - 4);
            if (known.contains(key)) {
                continue;
            }
            try {
                org.bukkit.configuration.file.YamlConfiguration y =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                String name = y.getString("name", key);
                String desc = y.getString("description", "");
                int max = Math.max(1, y.getInt("general-config.maximum-level", 1));
                String rarity = y.getString("obtaining.rarity", "common");
                java.util.List<String> groups = y.getStringList("general-config.targets");
                out.add(new EnchantDef(key, name, desc, max, 3, false, false, rarity,
                        targetGroups(groups), 10, 10, 20, 10, 5));
                known.add(key);
            } catch (Throwable ignored) {
                // 导入文件损坏时跳过，不影响启动
            }
        }
        return out;
    }

    /** 把 yml 的 targets 分组名翻译成物品类型集合 */
    private static List<TypedKey<ItemType>> targetGroups(java.util.List<String> groups) {
        List<TypedKey<ItemType>> out = new ArrayList<>();
        for (String raw : groups) {
            if (raw == null) {
                continue;
            }
            switch (raw.toLowerCase().trim()) {
                case "swords", "sword" -> out.addAll(swords());
                case "axes", "axe" -> out.addAll(axes());
                case "swax", "swords-axes" -> out.addAll(swAx());
                case "armor", "armour" -> out.addAll(armor());
                case "boots" -> out.addAll(boots());
                case "helmets", "helmet" -> out.addAll(helmets());
                case "pickaxes", "pickaxe" -> out.addAll(pickaxes());
                case "tools", "tool" -> out.addAll(tools());
                case "bows", "bow" -> out.addAll(bows());
                case "ranged" -> out.addAll(ranged());
                case "tridents", "trident" -> out.addAll(tridents());
                case "shields", "shield" -> out.addAll(shields());
                case "elytras", "elytra" -> out.addAll(elytras());
                case "fishing-rods", "fishing_rods", "fishingrods", "rods", "rods-fishing" -> out.addAll(fishingRods());
                case "everything", "all" -> out.addAll(everything());
                default -> {
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(everything());
        }
        return out;
    }
}
