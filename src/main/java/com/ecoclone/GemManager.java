package com.ecoclone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宝石 + 槽位系统。
 *
 * 槽位：普通装备可通过 /aeath socket 开槽（上限 MAX_SLOTS=100），
 *       已镶宝石数量记录在 PDC "gems_list"（格式 "ruby:2,sapphire:1"），
 *       槽位上限记录在 PDC "gems_slots"。
 * 宝石：独立物品，PDC "gem_key" + "gem_lv"，右键 / 背包点击可镶入装备空槽，也可取出。
 * lore：槽位区用两条标记行（起始/结束）包裹，refresh() 会裁剪旧区并重建，不影响原 lore。
 */
public final class GemManager {

    public static final int MAX_SLOTS = 100;
    /** 宝石最高等级 */
    public static final int MAX_LV = 100;

    /** 宝石等级段（每 10 级一档，共 10 档） */
    public static int tierOf(int lv) {
        return Math.max(1, Math.min(10, (Math.max(1, lv) + 9) / 10));
    }

    /** 十档等级段名称 */
    public static String tierName(int lv) {
        String[] names = {"凡品", "精良", "稀有", "史诗", "传说", "神话", "不朽", "神王", "混沌", "究极·超越"};
        return names[tierOf(lv) - 1];
    }

    private final EcoClonePlugin plugin;
    private final NamespacedKey kSlots;
    private final NamespacedKey kGems;
    private final NamespacedKey kGemKey;
    private final NamespacedKey kGemLv;

    private final Map<String, GemDef> defs = new LinkedHashMap<>();

    public static final class GemDef {
        public final String key;
        public final String name;      // 中文名
        public final Material mat;     // 物品材质
        public final String color;     // 显示色码
        public final String target;    // weapon / armor / both
        public final int maxLevel;
        public final String desc;
        public final String fx;        // 通用效果模板，null 走 GemListener 旧 switch

        public GemDef(String key, String name, Material mat, String color, String target, int maxLevel, String desc) {
            this(key, name, mat, color, target, maxLevel, desc, null);
        }

        public GemDef(String key, String name, Material mat, String color, String target, int maxLevel, String desc, String fx) {
            this.key = key; this.name = name; this.mat = mat; this.color = color;
            this.target = target; this.maxLevel = maxLevel; this.desc = desc; this.fx = fx;
        }
    }

    /** 颜色转换：支持 &#RRGGBB（16 进制，转成 §x§R§R§G§G§B§B）与 &x legacy 码 */
    private static String col(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && s.charAt(i + 1) == '#') {
                if (i + 8 <= s.length()) {
                    String hex = s.substring(i + 1, i + 8); // "#RRGGBB"
                    if (hex.matches("#[0-9a-fA-F]{6}")) {
                        sb.append('\u00a7').append('x');
                        for (int k = 1; k < hex.length(); k++) {
                            sb.append('\u00a7').append(hex.charAt(k));
                        }
                        i += 8;
                        continue;
                    }
                }
            }
            if (c == '&') sb.append('\u00a7');
            else sb.append(c);
            i++;
        }
        return sb.toString();
    }

    public GemManager(EcoClonePlugin plugin) {
        this.plugin = plugin;
        this.kSlots = new NamespacedKey(plugin, "gems_slots");
        this.kGems = new NamespacedKey(plugin, "gems_list");
        this.kGemKey = new NamespacedKey(plugin, "gem_key");
        this.kGemLv = new NamespacedKey(plugin, "gem_lv");
        registerDefaults();
    }

    private void registerDefaults() {
        defs.put("ruby", new GemDef("ruby", "红宝石", Material.REDSTONE, "&#FF5555", "weapon", 5,
                "命中伤害 +2%×Lv，30% 概率点燃 Lv 秒"));
        defs.put("sapphire", new GemDef("sapphire", "蓝宝石", Material.LAPIS_LAZULI, "&#55AFFF", "weapon", 5,
                "命中追加 Lv 点伤害，命中后自身获得速度 Lv（1 秒）"));
        defs.put("emerald", new GemDef("emerald", "翡翠", Material.EMERALD, "&#55FF55", "armor", 5,
                "穿戴时生命恢复 Lv"));
        defs.put("amethyst", new GemDef("amethyst", "紫晶", Material.AMETHYST_SHARD, "&#D0A5FF", "armor", 5,
                "穿戴时获得抗性提升 Lv"));
        defs.put("topaz", new GemDef("topaz", "黄玉", Material.GOLD_NUGGET, "&#FFD966", "both", 5,
                "武器：5%×Lv 概率暴击×2；穿戴：急迫 Lv"));
        defs.put("onyx", new GemDef("onyx", "黑曜石之心", Material.BLACK_DYE, "&#9E9E9E", "weapon", 5,
                "命中时吸血 5%×Lv 的伤害"));
        defs.put("diamond", new GemDef("diamond", "钻石之心", Material.DIAMOND, "&#E0F7FF", "armor", 5,
                "穿戴时伤害吸收 Lv，受击减免 2%×Lv"));
        defs.put("dragon", new GemDef("dragon", "龙鳞", Material.NETHERITE_SCRAP, "&#B388FF", "both", 5,
                "武器：全伤害 +3%×Lv；穿戴：力量+抗性 Lv"));
        defs.put("moonstone", new GemDef("moonstone", "月光石", Material.QUARTZ, "&#C0C0FF", "armor", 5,
                "穿戴时获得夜视 Lv 与速度 Lv"));
        defs.put("tiger_eye", new GemDef("tiger_eye", "虎眼石", Material.BROWN_DYE, "&#C98A2D", "weapon", 5,
                "命中追加 Lv 点伤害，命中后自身获得力量 Lv（1 秒）"));
        defs.put("aquamarine", new GemDef("aquamarine", "海蓝宝石", Material.LIGHT_BLUE_DYE, "&#7FE0FF", "armor", 5,
                "穿戴时获得水下呼吸与速度 Lv"));
        defs.put("sunstone", new GemDef("sunstone", "日耀石", Material.GLOWSTONE_DUST, "&#FFC94D", "both", 5,
                "武器：命中 30% 概率点燃 Lv 秒；穿戴：火抗+急迫 Lv"));
        defs.put("spinel", new GemDef("spinel", "尖晶石", Material.PINK_DYE, "&#FF4D8D", "armor", 5,
                "穿戴时受击反射 5%×Lv 的伤害给攻击者"));
        defs.put("malachite", new GemDef("malachite", "孔雀石", Material.GREEN_DYE, "&#3DDC8E", "armor", 5,
                "穿戴时急迫 Lv 与速度 Lv（挖矿提速）"));
        defs.put("shadowstone", new GemDef("shadowstone", "影石", Material.GRAY_DYE, "&#6B5BFF", "armor", 5,
                "穿戴时获得夜视与隐身 Lv"));
        defs.put("starstone", new GemDef("starstone", "星辰石", Material.PRISMARINE_CRYSTALS, "&#5B7BFF", "both", 5,
                "武器：命中 10%×Lv 概率引雷；穿戴：抗性 Lv+伤害吸收"));
        defs.put("vitality", new GemDef("vitality", "生命石", Material.REDSTONE_BLOCK, "&#FF6E6E", "armor", 5,
                "穿戴时生命恢复 Lv 与伤害吸收 Lv"));
        defs.put("arcane", new GemDef("arcane", "秘法石", Material.MAGENTA_DYE, "&#A55AFF", "both", 5,
                "武器：全伤 +1%×Lv，命中自身速度 Lv（1 秒）；穿戴：力量+速度 Lv"));
        defs.put("frost", new GemDef("frost", "霜晶石", Material.ICE, "&#9BE7FF", "weapon", 5,
                "命中追加 Lv 点伤害，并附加缓慢 Lv（2 秒）"));
        defs.put("venom", new GemDef("venom", "毒蛇石", Material.SLIME_BALL, "&#7CFC00", "weapon", 5,
                "命中附加中毒 Lv（3 秒）"));
        defs.put("guardian", new GemDef("guardian", "守护石", Material.SHIELD, "&#B0B7C0", "armor", 5,
                "穿戴时获得抗性提升 Lv 与伤害吸收 Lv"));
        defs.put("phoenix", new GemDef("phoenix", "凤凰石", Material.BLAZE_POWDER, "&#FF7F50", "armor", 5,
                "穿戴时获得火抗 Lv 与生命恢复 Lv"));
        defs.put("void", new GemDef("void", "虚空石", Material.ENDER_PEARL, "&#2F2F3D", "both", 5,
                "武器：命中 5%×Lv 概率造成 0.5× 真实伤害；穿戴：抗性+夜视 Lv"));
        // ================= 新宝石 50 种（fx 效果模板驱动） =================
        // 武器系 25
        defs.put("iron", new GemDef("iron", "钢铁之心", Material.IRON_INGOT, "&#D8D8D8", "weapon", 5, "命中追加 Lv 点伤害", "DMG_FLAT"));
        defs.put("gold", new GemDef("gold", "黄金意志", Material.GOLD_INGOT, "&#FFD700", "weapon", 5, "命中伤害 +2%×Lv", "DMG_PCT"));
        defs.put("copper", new GemDef("copper", "铜刃之心", Material.COPPER_INGOT, "&#D97744", "weapon", 5, "命中追加 Lv 点伤害", "DMG_FLAT"));
        defs.put("netherite", new GemDef("netherite", "下界合金意志", Material.NETHERITE_INGOT, "&#4A4444", "weapon", 5, "命中伤害 +2%×Lv", "DMG_PCT"));
        defs.put("blaze", new GemDef("blaze", "烈焰之心", Material.BLAZE_ROD, "&#FF8C1A", "weapon", 5, "命中点燃 Lv 秒", "FIRE"));
        defs.put("magma", new GemDef("magma", "熔岩之心", Material.MAGMA_CREAM, "&#FF6A33", "weapon", 5, "命中点燃 Lv 秒", "FIRE"));
        defs.put("ice", new GemDef("ice", "寒冰之心", Material.PACKED_ICE, "&#9BE7FF", "weapon", 5, "命中附加缓慢 Lv（2 秒）", "SLOWNESS"));
        defs.put("snow", new GemDef("snow", "雪原之心", Material.SNOWBALL, "&#EAF7FF", "weapon", 5, "命中附加缓慢 Lv（2 秒）", "SLOWNESS"));
        defs.put("wither", new GemDef("wither", "凋零之心", Material.WITHER_ROSE, "&#3A3A3A", "weapon", 5, "命中附加凋零 Lv（3 秒）", "WITHER"));
        defs.put("skull", new GemDef("skull", "骷髅之心", Material.BONE, "&#E0E0E0", "weapon", 5, "命中附加凋零 Lv（3 秒）", "WITHER"));
        defs.put("storm", new GemDef("storm", "风暴之心", Material.LIGHTNING_ROD, "&#6A9BFF", "weapon", 5, "命中 10%×Lv 概率引雷", "LIGHTNING"));
        defs.put("thunder", new GemDef("thunder", "雷霆之心", Material.CRYING_OBSIDIAN, "&#5A6BFF", "weapon", 5, "命中 10%×Lv 概率引雷", "LIGHTNING"));
        defs.put("ghast", new GemDef("ghast", "恶魂之泪", Material.GHAST_TEAR, "&#FF7B7B", "weapon", 5, "命中吸血 5%×Lv 伤害", "LIFESTEAL"));
        defs.put("soul", new GemDef("soul", "灵魂之心", Material.SOUL_SAND, "&#6B5BFF", "weapon", 5, "命中吸血 5%×Lv 伤害", "LIFESTEAL"));
        defs.put("obsidian", new GemDef("obsidian", "虚空之刃", Material.OBSIDIAN, "&#3A2A4A", "weapon", 5, "命中 5%×Lv 概率造成 0.5× 真实伤害", "TRUE_DMG"));
        defs.put("ender", new GemDef("ender", "末影之刃", Material.ENDER_PEARL, "&#2F2F3D", "weapon", 5, "命中 5%×Lv 概率造成 0.5× 真实伤害", "TRUE_DMG"));
        defs.put("phantom", new GemDef("phantom", "幻影之翼", Material.PHANTOM_MEMBRANE, "&#7FE0FF", "weapon", 5, "命中后自身速度 Lv（1 秒）", "SPEED_ON_HIT"));
        defs.put("echo", new GemDef("echo", "回响之石", Material.ECHO_SHARD, "&#6BD5E1", "weapon", 5, "命中后自身速度 Lv（1 秒）", "SPEED_ON_HIT"));
        defs.put("fury", new GemDef("fury", "狂怒之石", Material.NETHER_WART, "&#C90000", "weapon", 5, "命中后自身力量 Lv（1 秒）", "STRENGTH_ON_HIT"));
        defs.put("wrath", new GemDef("wrath", "暴怒之石", Material.GUNPOWDER, "&#FF5555", "weapon", 5, "命中后自身力量 Lv（1 秒）", "STRENGTH_ON_HIT"));
        defs.put("quartz", new GemDef("quartz", "精粹石英", Material.QUARTZ_BLOCK, "&#E0E0E0", "weapon", 5, "命中伤害 +2%×Lv", "DMG_PCT"));
        defs.put("amethyst_blade", new GemDef("amethyst_blade", "紫晶之刃", Material.AMETHYST_BLOCK, "&#D0A5FF", "weapon", 5, "命中伤害 +2%×Lv", "DMG_PCT"));
        defs.put("diamond_blade", new GemDef("diamond_blade", "钻石之刃", Material.DIAMOND_BLOCK, "&#E0F7FF", "weapon", 5, "命中追加 Lv 点伤害", "DMG_FLAT"));
        defs.put("emerald_blade", new GemDef("emerald_blade", "翡翠之刃", Material.EMERALD_BLOCK, "&#55FF55", "weapon", 5, "命中追加 Lv 点伤害", "DMG_FLAT"));
        defs.put("lapis_blade", new GemDef("lapis_blade", "青金之刃", Material.LAPIS_BLOCK, "&#4C8DFF", "weapon", 5, "命中追加 Lv 点伤害", "DMG_FLAT"));
        // 护甲系 25
        defs.put("golden_apple", new GemDef("golden_apple", "金苹果之光", Material.GOLDEN_APPLE, "&#FFD700", "armor", 5, "穿戴时生命恢复 Lv", "REGEN"));
        defs.put("glow", new GemDef("glow", "光辉浆果", Material.GLOW_BERRIES, "&#FF8C8C", "armor", 5, "穿戴时生命恢复 Lv", "REGEN"));
        defs.put("melon", new GemDef("melon", "甘露之石", Material.GLISTERING_MELON_SLICE, "&#FF6E6E", "armor", 5, "穿戴时生命恢复 Lv", "REGEN"));
        defs.put("deepslate", new GemDef("deepslate", "泰坦之石", Material.DEEPSLATE, "&#5A5A5A", "armor", 5, "穿戴时抗性提升 Lv", "RESISTANCE"));
        defs.put("stone", new GemDef("stone", "磐石之心", Material.STONE, "&#7A7A7A", "armor", 5, "穿戴时抗性提升 Lv", "RESISTANCE"));
        defs.put("andesite", new GemDef("andesite", "安山岩壁", Material.ANDESITE, "&#8A8A8A", "armor", 5, "穿戴时抗性提升 Lv", "RESISTANCE"));
        defs.put("basalt", new GemDef("basalt", "玄武岩壁", Material.BASALT, "&#4A4A4A", "armor", 5, "穿戴时抗性提升 Lv", "RESISTANCE"));
        defs.put("sugar", new GemDef("sugar", "疾风之石", Material.SUGAR, "&#FFE28C", "armor", 5, "穿戴时速度 Lv", "SPEED"));
        defs.put("feather", new GemDef("feather", "羽翼之石", Material.FEATHER, "&#FFFFFF", "armor", 5, "穿戴时速度 Lv", "SPEED"));
        defs.put("ink", new GemDef("ink", "夜行之石", Material.INK_SAC, "&#3A3A3A", "armor", 5, "穿戴时夜视 Lv", "NIGHT_VISION"));
        defs.put("charcoal", new GemDef("charcoal", "暗影之石", Material.CHARCOAL, "&#3A3A3A", "armor", 5, "穿戴时夜视 Lv", "NIGHT_VISION"));
        defs.put("netherrack", new GemDef("netherrack", "余烬之石", Material.NETHERRACK, "&#FF5A5A", "armor", 5, "穿戴时火抗 Lv", "FIRE_RESIST"));
        defs.put("sculk", new GemDef("sculk", "守望之石", Material.SCULK, "&#0B6E6E", "armor", 5, "穿戴时伤害吸收 Lv", "ABSORB"));
        defs.put("shulker", new GemDef("shulker", "壁垒之石", Material.SHULKER_SHELL, "&#C77BFF", "armor", 5, "穿戴时伤害吸收 Lv", "ABSORB"));
        defs.put("egg", new GemDef("egg", "幽灵之石", Material.EGG, "&#F0F0F0", "armor", 5, "穿戴时隐身 Lv", "INVIS"));
        defs.put("spore", new GemDef("spore", "迷雾之石", Material.SPORE_BLOSSOM, "&#FF7F8C", "armor", 5, "穿戴时隐身 Lv", "INVIS"));
        defs.put("cod", new GemDef("cod", "碧波之石", Material.COD, "&#4C8DFF", "armor", 5, "穿戴时水下呼吸 Lv", "WATER_BREATH"));
        defs.put("coral", new GemDef("coral", "珊瑚之石", Material.BRAIN_CORAL, "&#FF6E8C", "armor", 5, "穿戴时水下呼吸 Lv", "WATER_BREATH"));
        defs.put("rabbit_foot", new GemDef("rabbit_foot", "跃迁之石", Material.RABBIT_FOOT, "&#FFE28C", "armor", 5, "穿戴时跳跃提升 Lv", "JUMP"));
        defs.put("honeycomb", new GemDef("honeycomb", "弹簧之石", Material.HONEYCOMB, "&#FFC94D", "armor", 5, "穿戴时跳跃提升 Lv", "JUMP"));
        defs.put("beef", new GemDef("beef", "熊之力", Material.BEEF, "&#FF8C8C", "armor", 5, "穿戴时力量 Lv", "STRENGTH"));
        defs.put("pork", new GemDef("pork", "蛮牛之石", Material.PORKCHOP, "&#FF9999", "armor", 5, "穿戴时力量 Lv", "STRENGTH"));
        defs.put("iron_pickaxe", new GemDef("iron_pickaxe", "矿工之石", Material.IRON_PICKAXE, "&#D8D8D8", "armor", 5, "穿戴时急迫 Lv", "HASTE"));
        defs.put("cobble", new GemDef("cobble", "石拳之石", Material.COBBLESTONE, "&#7A7A7A", "armor", 5, "穿戴时急迫 Lv", "HASTE"));
        defs.put("bark", new GemDef("bark", "树皮之石", Material.OAK_LOG, "&#7A6A4A", "armor", 5, "穿戴时急迫 Lv", "HASTE"));
        // ================= 新宝石 22 种（v1.4.6，fx 效果模板驱动） =================
        // 武器系 12
        defs.put("sakura", new GemDef("sakura", "樱花之心", Material.CHERRY_SAPLING, "&#FFB3C7", "weapon", 5, "命中伤害 +2%×Lv", "DMG_PCT"));
        defs.put("bamboo", new GemDef("bamboo", "青竹之刃", Material.BAMBOO, "&#7FFF6E", "weapon", 5, "命中追加 Lv 点伤害", "DMG_FLAT"));
        defs.put("crimson", new GemDef("crimson", "绯红之心", Material.CRIMSON_FUNGUS, "&#C9001F", "weapon", 5, "命中点燃 Lv 秒", "FIRE"));
        defs.put("warped", new GemDef("warped", "诡谲之石", Material.WARPED_FUNGUS, "&#3A9BDC", "weapon", 5, "命中附加缓慢 Lv（2 秒）", "SLOWNESS"));
        defs.put("toxic", new GemDef("toxic", "剧毒之心", Material.SPIDER_EYE, "&#7CFC00", "weapon", 5, "命中附加中毒 Lv（3 秒）", "POISON"));
        defs.put("necro", new GemDef("necro", "亡灵之石", Material.ZOMBIE_HEAD, "&#6B6B6B", "weapon", 5, "命中附加凋零 Lv（3 秒）", "WITHER"));
        defs.put("volt", new GemDef("volt", "电涌之石", Material.PRISMARINE_SHARD, "&#5A6BFF", "weapon", 5, "命中 10%×Lv 概率引雷", "LIGHTNING"));
        defs.put("lifefang", new GemDef("lifefang", "生命獠牙", Material.FERMENTED_SPIDER_EYE, "&#FF7B7B", "weapon", 5, "命中吸血 5%×Lv 伤害", "LIFESTEAL"));
        defs.put("critstone", new GemDef("critstone", "暴击之石", Material.GLOW_INK_SAC, "&#FFD966", "weapon", 5, "命中 5%×Lv 概率暴击×2", "CRIT"));
        defs.put("swift", new GemDef("swift", "迅捷之石", Material.RABBIT_HIDE, "&#FFE28C", "weapon", 5, "命中后自身速度 Lv（1 秒）", "SPEED_ON_HIT"));
        defs.put("berserk", new GemDef("berserk", "狂暴之石", Material.FIREWORK_STAR, "&#FF8C1A", "weapon", 5, "命中后自身力量 Lv（1 秒）", "STRENGTH_ON_HIT"));
        defs.put("truecut", new GemDef("truecut", "真伤之刃", Material.SHEARS, "&#E0E0E0", "weapon", 5, "命中 5%×Lv 概率造成 0.5× 真实伤害", "TRUE_DMG"));
        // 护甲系 10
        defs.put("pearl", new GemDef("pearl", "珍珠之心", Material.NAUTILUS_SHELL, "&#B388FF", "armor", 5, "穿戴时生命恢复 Lv", "REGEN"));
        defs.put("prism", new GemDef("prism", "棱镜之石", Material.PRISMARINE_BRICKS, "&#6BE7FF", "armor", 5, "穿戴时抗性提升 Lv", "RESISTANCE"));
        defs.put("sensor", new GemDef("sensor", "感应之石", Material.SCULK_SENSOR, "&#C0C0FF", "armor", 5, "穿戴时速度 Lv", "SPEED"));
        defs.put("honey", new GemDef("honey", "蜜糖之石", Material.HONEY_BOTTLE, "&#FFC94D", "armor", 5, "穿戴时跳跃提升 Lv", "JUMP"));
        defs.put("moss", new GemDef("moss", "苔藓之石", Material.MOSS_BLOCK, "&#7FFF6E", "armor", 5, "穿戴时急迫 Lv", "HASTE"));
        defs.put("radiance", new GemDef("radiance", "辉光之石", Material.GLOWSTONE, "&#FFC94D", "armor", 5, "穿戴时火抗 Lv", "FIRE_RESIST"));
        defs.put("shieldcore", new GemDef("shieldcore", "盾心之石", Material.TURTLE_SCUTE, "&#7FE0FF", "armor", 5, "穿戴时伤害吸收 Lv", "ABSORB"));
        defs.put("ghost", new GemDef("ghost", "幽魂之石", Material.POINTED_DRIPSTONE, "&#B0B7C0", "armor", 5, "穿戴时隐身 Lv", "INVIS"));
        defs.put("tide", new GemDef("tide", "潮汐之石", Material.KELP, "&#4C8DFF", "armor", 5, "穿戴时水下呼吸 Lv", "WATER_BREATH"));
        defs.put("oxheart", new GemDef("oxheart", "蛮牛之心", Material.COOKED_BEEF, "&#FF8C8C", "armor", 5, "穿戴时力量 Lv", "STRENGTH"));
    }

    // ---------- 访问器 ----------

    public Map<String, GemDef> defs() {
        return defs;
    }

    public GemDef get(String key) {
        return defs.get(key.toLowerCase());
    }

    public boolean isGem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(kGemKey, PersistentDataType.STRING);
    }

    public GemDef gemDef(ItemStack item) {
        if (!isGem(item)) return null;
        String k = item.getItemMeta().getPersistentDataContainer().get(kGemKey, PersistentDataType.STRING);
        return get(k);
    }

    public int gemLevel(ItemStack item) {
        if (!isGem(item)) return 0;
        Integer lv = item.getItemMeta().getPersistentDataContainer().get(kGemLv, PersistentDataType.INTEGER);
        return lv == null ? 1 : Math.max(1, lv);
    }

    /** 创建宝石物品 */
    public ItemStack gemOf(String key, int lv, int amount) {
        GemDef d = get(key);
        if (d == null) return null;
        int level = Math.max(1, Math.min(lv, MAX_LV));
        ItemStack gem = new ItemStack(d.mat, Math.max(1, amount));
        ItemMeta meta = gem.getItemMeta();
        meta.displayName(Component.text(col(d.color + "◆ " + d.name + " §7Lv." + level + " §7[" + tierName(level) + "]")));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(col("&7" + d.desc)));
        lore.add(Component.text(col(
                "&8目标：&7" + ("weapon".equals(d.target) ? "武器" : "armor".equals(d.target) ? "护甲" : "通用"))));
        lore.add(Component.text(col("&8等级段：&f" + tierName(level) + " &7(1-10=" + tierName(level) + " 档)")));
        lore.add(Component.text(col("&7手持装备+副手此宝石右键，或背包点击装备即可镶嵌")));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kGemKey, PersistentDataType.STRING, d.key);
        pdc.set(kGemLv, PersistentDataType.INTEGER, level);
        gem.setItemMeta(meta);
        return gem;
    }

    // ---------- 槽位 ----------

    public int slotsOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer n = item.getItemMeta().getPersistentDataContainer().get(kSlots, PersistentDataType.INTEGER);
        return n == null ? 0 : n;
    }

    public ItemStack setSlots(ItemStack item, int slots) {
        ItemStack c = item.clone();
        c.editMeta(meta -> meta.getPersistentDataContainer().set(kSlots, PersistentDataType.INTEGER,
                Math.max(0, Math.min(slots, MAX_SLOTS))));
        return refresh(c);
    }

    public static final class SlotEntry {
        public final String key;
        public final int level;
        public SlotEntry(String key, int level) { this.key = key; this.level = level; }
    }

    /** 已镶宝石列表（顺序=镶嵌顺序） */
    public List<SlotEntry> gemsOf(ItemStack item) {
        List<SlotEntry> out = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return out;
        String raw = item.getItemMeta().getPersistentDataContainer().get(kGems, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try {
                out.add(new SlotEntry(kv[0], Integer.parseInt(kv[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public boolean hasGems(ItemStack item) {
        return !gemsOf(item).isEmpty();
    }

    public boolean canSocket(ItemStack item) {
        return slotsOf(item) > 0 && gemsOf(item).size() < slotsOf(item);
    }

    /** 不允许开槽的物品（书/卷轴/宝石本身） */
    public boolean locked(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        Material t = item.getType();
        return t == Material.BOOK || t == Material.ENCHANTED_BOOK
                || t == Material.WRITTEN_BOOK || isGem(item);
    }

    // ---------- 宝石与装备部位匹配 ----------

    private static boolean isArmorType(Material t) {
        if (t == Material.ELYTRA) return true;
        String n = t.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private static boolean isWeaponType(Material t) {
        String n = t.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_TRIDENT")
                || n.endsWith("_BOW") || n.endsWith("_CROSSBOW") || n.endsWith("_MACE")
                || n.endsWith("_SHIELD") || n.endsWith("_HOE");
    }

    /** 装备部位：weapon / armor / both（工具等通用装备只接受通用宝石） */
    public String targetOf(ItemStack item) {
        if (item == null) return "both";
        Material t = item.getType();
        if (isArmorType(t)) return "armor";
        if (isWeaponType(t)) return "weapon";
        return "both";
    }

    /** 该宝石能否镶嵌进这件装备（部位匹配） */
    public boolean compatible(ItemStack equip, ItemStack gem) {
        GemDef d = gemDef(gem);
        if (d == null || equip == null) return false;
        return d.target.equals("both") || d.target.equals(targetOf(equip));
    }

    private ItemStack writeGems(ItemStack item, List<SlotEntry> gems) {
        ItemStack c = item.clone();
        StringBuilder sb = new StringBuilder();
        for (SlotEntry e : gems) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.key).append(":").append(e.level);
        }
        final String data = sb.toString();
        c.editMeta(meta -> meta.getPersistentDataContainer().set(kGems, PersistentDataType.STRING, data));
        return refresh(c);
    }

    /** 镶嵌：成功返回新装备，失败返回 null */
    public ItemStack insert(ItemStack equip, ItemStack gem) {
        if (!canSocket(equip) || !compatible(equip, gem)) return null;
        GemDef d = gemDef(gem);
        if (d == null) return null;
        List<SlotEntry> gems = gemsOf(equip);
        gems.add(new SlotEntry(d.key, gemLevel(gem)));
        return writeGems(equip, gems);
    }

    /** 取出最后一颗：返回 [弹出的宝石, 剩余装备]，无宝石返回 null */
    public ItemStack[] extractLast(ItemStack equip) {
        List<SlotEntry> gems = gemsOf(equip);
        if (gems.isEmpty()) return null;
        SlotEntry last = gems.remove(gems.size() - 1);
        ItemStack gem = gemOf(last.key, last.level, 1);
        ItemStack rest = writeGems(equip, gems);
        return new ItemStack[]{gem, rest};
    }

    // ---------- Lore 刷新 ----------

    private static final String MARKER_START = "§8━━━ 宝石槽位 ━━━";
    private static final String MARKER_END = "§8━━━ 宝石槽位结束 ━━━";

    /** 重写装备的槽位区（裁剪旧区，追加新区），不影响原 lore */
    public ItemStack refresh(ItemStack item) {
        ItemStack c = item.clone();
        ItemMeta meta = c.getItemMeta();
        if (meta == null) return c;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        List<Component> kept = new ArrayList<>();
        boolean skipping = false;
        for (Component line : lore) {
            String s = plain.serialize(line);
            if (s.contains("宝石槽位") && s.contains("━")) { skipping = true; continue; }
            if (skipping) {
                if (s.contains("宝石槽位结束")) { skipping = false; }
                continue;
            }
            kept.add(line);
        }
        List<Component> section = gemSection(c);
        if (!section.isEmpty()) {
            kept.addAll(section);
        }
        meta.lore(kept);
        c.setItemMeta(meta);
        return c;
    }

    private List<Component> gemSection(ItemStack item) {
        List<Component> out = new ArrayList<>();
        out.add(Component.text(col(MARKER_START)));
        List<SlotEntry> gems = gemsOf(item);
        int used = gems.size();
        int slots = slotsOf(item);
        if (slots <= 0) {
            out.add(Component.text(col("&7尚未开槽，使用 &e/aeath socket &7为装备开槽")));
        } else {
            out.add(Component.text(col("&8◆ 宝石槽位 &7(&f" + used + "&7/&f" + slots + "&7)")));
            if (used > 0) {
                for (SlotEntry e : gems) {
                    GemDef d = get(e.key);
                    String nm = d == null ? e.key : d.name;
                    String colorHex = d == null ? "&#FFFFFF" : d.color;
                    out.add(Component.text(col(
                            colorHex + "◆ " + nm + " §7Lv." + e.level + " §8[" + tierName(e.level) + "]")));
                }
            }
            int empty = slots - used;
            if (empty > 0) {
                out.add(Component.text(col("&8◇ 空槽 &7×" + empty)));
            }
        }
        out.add(Component.text(col(MARKER_END)));
        return out;
    }
}
