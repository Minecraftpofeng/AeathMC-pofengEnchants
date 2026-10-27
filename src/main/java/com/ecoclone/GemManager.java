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
 * 槽位：普通装备可通过 /ecoenchants socket 开槽（上限 MAX_SLOTS=6），
 *       已镶宝石数量记录在 PDC "gems_list"（格式 "ruby:2,sapphire:1"），
 *       槽位上限记录在 PDC "gems_slots"。
 * 宝石：独立物品，PDC "gem_key" + "gem_lv"，右键 / 背包点击可镶入装备空槽，也可取出。
 * lore：槽位区用两条标记行（起始/结束）包裹，refresh() 会裁剪旧区并重建，不影响原 lore。
 */
public final class GemManager {

    public static final int MAX_SLOTS = 6;

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

        public GemDef(String key, String name, Material mat, String color, String target, int maxLevel, String desc) {
            this.key = key; this.name = name; this.mat = mat; this.color = color;
            this.target = target; this.maxLevel = maxLevel; this.desc = desc;
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
        int level = Math.max(1, Math.min(lv, d.maxLevel));
        ItemStack gem = new ItemStack(d.mat, Math.max(1, amount));
        ItemMeta meta = gem.getItemMeta();
        meta.displayName(Component.text(col(d.color + "◆ " + d.name + " §7Lv." + level)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(col("&7" + d.desc)));
        lore.add(Component.text(col(
                "&8目标：&7" + ("weapon".equals(d.target) ? "武器" : "armor".equals(d.target) ? "护甲" : "通用"))));
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
        String n = t.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private static boolean isWeaponType(Material t) {
        String n = t.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_TRIDENT")
                || n.endsWith("_BOW") || n.endsWith("_CROSSBOW") || n.endsWith("_MACE")
                || n.endsWith("_SHIELD");
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
            out.add(Component.text(col("&7尚未开槽，使用 &e/ecoenchants socket &7为装备开槽")));
        } else {
            out.add(Component.text(col("&8◆ 宝石槽位 &7(&f" + used + "&7/&f" + slots + "&7)")));
            if (used > 0) {
                for (SlotEntry e : gems) {
                    GemDef d = get(e.key);
                    String nm = d == null ? e.key : d.name;
                    String colorHex = d == null ? "&#FFFFFF" : d.color;
                    out.add(Component.text(col(
                            colorHex + "◆ " + nm + " §7Lv." + e.level)));
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
