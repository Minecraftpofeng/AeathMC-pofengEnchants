package com.ecoclone;

import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 运行时配置与工具管理器。
 * 负责加载 config.yml / rarity.yml / targets.yml / en.yml / enchantments/*.yml，
 * 提供冷却、随机抽取、附魔应用与消息翻译。
 */
public final class EnchantManager {

    private final EcoClonePlugin plugin;
    private YamlConfiguration config;
    private YamlConfiguration rarity;
    private YamlConfiguration targets;
    private YamlConfiguration messages;
    private final Map<String, YamlConfiguration> enchantConfigs = new HashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Set<ItemType>> keyTargets = new HashMap<>();

    public EnchantManager(EcoClonePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveDefaults();
        config = loadYml("config.yml");
        rarity = loadYml("rarity.yml");
        targets = loadYml("targets.yml");
        messages = loadYml(messagesName());

        enchantConfigs.clear();
        File dir = new File(plugin.getDataFolder(), "enchantments");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                enchantConfigs.put(f.getName().replace(".yml", ""), YamlConfiguration.loadConfiguration(f));
            }
        }

        buildTargets();
    }

    private void buildTargets() {
        keyTargets.clear();
        Registry<ItemType> registry = Registry.ITEM;
        for (EnchantDef def : Enchants.definitions()) {
            List<ItemType> types = new ArrayList<>();
            for (TypedKey<ItemType> tk : def.targets) {
                ItemType t = registry.get(tk);
                if (t != null) {
                    types.add(t);
                }
            }
            keyTargets.put(def.key, Set.copyOf(types));
        }
    }

    // ---------- 配置加载 ----------

    private void saveDefaults() {
        saveResource("config.yml");
        saveResource("rarity.yml");
        saveResource("targets.yml");
        saveResource(messagesName());
        File dir = new File(plugin.getDataFolder(), "enchantments");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (EnchantDef def : Enchants.definitions()) {
            File f = new File(dir, def.key + ".yml");
            if (!f.exists()) {
                plugin.saveResource("enchantments/" + def.key + ".yml", false);
            }
        }
    }

    private void saveResource(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private YamlConfiguration loadYml(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (f.exists()) {
            return YamlConfiguration.loadConfiguration(f);
        }
        InputStream in = plugin.getResource(name);
        return in == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public String messagesName() {
        return config != null ? config.getString("language", "en.yml") : "en.yml";
    }

    // ---------- 附魔配置读取 ----------

    private YamlConfiguration ench(String key) {
        return enchantConfigs.get(key);
    }

    public boolean isEnabled(String key) {
        YamlConfiguration c = ench(key);
        return c == null || c.getBoolean("enabled", true);
    }

    public String name(String key) {
        YamlConfiguration c = ench(key);
        if (c != null && c.getString("name") != null) {
            return c.getString("name");
        }
        for (EnchantDef d : Enchants.definitions()) {
            if (d.key.equals(key)) {
                return d.name;
            }
        }
        return key;
    }

    public String description(String key) {
        YamlConfiguration c = ench(key);
        if (c != null && c.getString("description") != null) {
            return c.getString("description");
        }
        for (EnchantDef d : Enchants.definitions()) {
            if (d.key.equals(key)) {
                return d.description;
            }
        }
        return "";
    }

    public int maxLevel(String key) {
        YamlConfiguration c = ench(key);
        if (c != null && c.contains("general-config.maximum-level")) {
            return c.getInt("general-config.maximum-level");
        }
        for (EnchantDef d : Enchants.definitions()) {
            if (d.key.equals(key)) {
                return d.maxLevel;
            }
        }
        return 1;
    }

    public String rarity(String key) {
        YamlConfiguration c = ench(key);
        if (c != null && c.getString("obtaining.rarity") != null) {
            return c.getString("obtaining.rarity");
        }
        for (EnchantDef d : Enchants.definitions()) {
            if (d.key.equals(key)) {
                return d.rarity;
            }
        }
        return "common";
    }

    public boolean isCurse(String key) {
        for (EnchantDef d : Enchants.definitions()) {
            if (d.key.equals(key)) {
                return d.curse;
            }
        }
        return false;
    }

    public boolean isTreasure(String key) {
        for (EnchantDef d : Enchants.definitions()) {
            if (d.key.equals(key)) {
                return d.treasure;
            }
        }
        return false;
    }

    /** 该附魔是否允许从附魔台获得 */
    public boolean fromTable(String key) {
        YamlConfiguration c = ench(key);
        if (c != null && c.contains("obtaining.table")) {
            return c.getBoolean("obtaining.table");
        }
        return true;
    }

    public boolean canEnchant(ItemType type, String key) {
        Set<ItemType> set = keyTargets.get(key);
        return set != null && set.contains(type);
    }

    // ---------- 冷却 ----------

    public boolean isOnCooldown(UUID uuid, String key) {
        Long until = cooldowns.get(uuid + ":" + key);
        return until != null && System.currentTimeMillis() < until;
    }

    public void startCooldown(UUID uuid, String key, double seconds) {
        cooldowns.put(uuid + ":" + key, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public double cooldownSeconds() {
        return config.getDouble("default-cooldown", 3.0);
    }

    // ---------- 消息 ----------

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /**
     * 支持 16 进制颜色码 &#RRGGBB; 的染色：先转成 &x&R&R&G&G&B&B 旧式码，再按 & 转义。
     */
    public String colorHex(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 9 <= s.length()
                    && s.charAt(i) == '&' && s.charAt(i + 1) == '#'
                    && s.charAt(i + 8) == ';' && isHex6(s, i + 2)) {
                String rgb = s.substring(i + 2, i + 8);
                sb.append("&x");
                for (char c : rgb.toCharArray()) {
                    sb.append('&').append(c);
                }
                i += 9;
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private static boolean isHex6(String s, int from) {
        for (int j = 0; j < 6; j++) {
            char c = s.charAt(from + j);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    public String msg(String path) {
        String raw = messages.getString(path);
        if (raw == null) {
            return path;
        }
        return color(raw.replace("%player%", "%player%"));
    }

    public String prefix() {
        return color(messages.getString("prefix", "&8[&6AeathMC&8] "));
    }

    // ---------- 附魔应用 / 随机 ----------

    public ItemStack apply(ItemStack item, Enchantment enchantment, int level) {
        item.editMeta(meta -> meta.addEnchant(enchantment, level, true));
        return item;
    }

    public ItemStack bookOf(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, true));
        return book;
    }

    /** 按稀有度权重随机抽取一个附魔（用于 randomenchant / giverandombook）。 */
    public Map.Entry<Enchantment, Integer> randomEnchant() {
        List<Map.Entry<Enchantment, Integer>> pool = new ArrayList<>();
        for (EnchantDef def : Enchants.definitions()) {
            if (!isEnabled(def.key)) {
                continue;
            }
            Enchantment e = Enchants.get(def.key);
            if (e == null) {
                continue;
            }
            double rarityWeight = rarity.getDouble(def.rarity + ".weight", 1);
            int totalWeight = Math.max(1, (int) (rarityWeight * def.weight));
            int maxLvl = maxLevel(def.key);
            int level = 1 + (int) (Math.random() * maxLvl);
            for (int i = 0; i < totalWeight; i++) {
                pool.add(Map.entry(e, level));
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get((int) (Math.random() * pool.size()));
    }

    // ---------- 卷轴 / 附魔槽 / 洗附魔 ----------

    /** 每件物品的自定义附魔槽上限 */
    public int enchantmentSlots() {
        return config.getInt("enchantment-slots", 5);
    }

    /** 物品上已有的自定义附魔（ecoclone 命名空间）数量 */
    public int customCount(ItemStack item) {
        if (item == null) {
            return 0;
        }
        int n = 0;
        for (Enchantment e : item.getEnchantments().keySet()) {
            if (e.getKey().getNamespace().equals("ecoclone")) {
                n++;
            }
        }
        return n;
    }

    /** 是否还能在物品上再添加该自定义附魔（已拥有则不占新槽） */
    public boolean canAddCustom(ItemStack item, Enchantment e) {
        if (item == null) {
            return false;
        }
        if (item.getEnchantments().containsKey(e)) {
            return true;
        }
        return customCount(item) < enchantmentSlots();
    }

    /** 制作一张附魔卷轴（PAPER + NBT 存附魔键/等级） */
    public ItemStack scrollOf(Enchantment e, int level) {
        ItemStack s = new ItemStack(Material.PAPER);
        s.editMeta(meta -> {
            meta.displayName(Component.text(color("&d[AeathMC] 附魔卷轴")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(color("&7附魔：&f" + name(e.getKey().getKey()) + " &7Lv." + level)));
            lore.add(Component.text(color("&7描述：&f" + description(e.getKey().getKey()))));
            lore.add(Component.text(color("&7在附魔工作台中与物品一起放入即可上附魔")));
            meta.lore(lore);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, "scroll_key"), PersistentDataType.STRING, e.getKey().getKey());
            pdc.set(new NamespacedKey(plugin, "scroll_level"), PersistentDataType.INTEGER, level);
        });
        return s;
    }

    /** 是否为有效附魔卷轴 */
    public boolean isScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "scroll_key"), PersistentDataType.STRING);
    }

    /** 从卷轴读取附魔，返回 {附魔, 等级}；不是有效卷轴返回 null */
    public Map.Entry<Enchantment, Integer> scrollContent(ItemStack item) {
        if (!isScroll(item)) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String key = pdc.get(new NamespacedKey(plugin, "scroll_key"), PersistentDataType.STRING);
        Integer level = pdc.get(new NamespacedKey(plugin, "scroll_level"), PersistentDataType.INTEGER);
        Enchantment e = Enchants.get(key);
        if (e == null || level == null || level < 1) {
            return null;
        }
        return Map.entry(e, level);
    }

    /** 清除物品上的全部自定义附魔（洗附魔），返回新物品 */
    public ItemStack stripCustom(ItemStack item) {
        if (item == null) {
            return item;
        }
        ItemStack clone = item.clone();
        clone.editMeta(meta -> {
            Map<Enchantment, Integer> toRemove = new HashMap<>();
            for (Enchantment e : meta.getEnchants().keySet()) {
                if (e.getKey().getNamespace().equals("ecoclone")) {
                    toRemove.put(e, meta.getEnchantLevel(e));
                }
            }
            toRemove.keySet().forEach(meta::removeEnchant);
        });
        return clone;
    }

    /** 神器主人 */
    public String artifactOwner() {
        return "ClowmPierce";
    }

    /** 是否为神器（寰宇支配之剑） */
    public boolean isArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "artifact"), PersistentDataType.BOOLEAN);
    }

    /** 神器：寰宇支配之剑·最终幻想 */
    public ItemStack artifactSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(Component.text(color("&d&l寰宇支配之剑&f&l•&b&l最终幻想")));
            meta.lore(artifactLore(false));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "artifact"),
                    PersistentDataType.BOOLEAN, true);
            // 移除攻击属性修饰符，隐藏"在主手时"属性提示
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        // 附魔（强制叠加，忽略冲突与等级上限）
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 256);
        ench.put(Enchantment.BANE_OF_ARTHROPODS, 50);
        ench.put(Enchantment.SMITE, 50);
        ench.put(Enchantment.UNBREAKING, 50);
        ench.put(Enchantment.SWEEPING_EDGE, 20);
        ench.put(Enchantment.FIRE_ASPECT, 20);
        putIf(ench, "leeching", 10);       // 吸血
        putIf(ench, "frost", 5);           // 冻结
        putIf(ench, "armor_piercing", 10); // 破甲
        putIf(ench, "criticals", 1);       // 会心一击
        putIf(ench, "king_descent", 2);    // 王的降临（虚空恐惧）
        putIf(ench, "nausea", 2);          // 反胃
        putIf(ench, "wither_aspect", 2);   // 凋零
        putIf(ench, "toxic", 2);           // 中毒
        putIf(ench, "insomnia", 2);        // 失眠
        putIf(ench, "conductive", 2);      // 导电
        putIf(ench, "explosive", 2);       // 爆炸
        putIf(ench, "phantom", 10);        // 虚影
        putIf(ench, "sneak_attack", 2);    // 偷袭
        putIf(ench, "eclipse", 1);         // 日食
        sword.addUnsafeEnchantments(ench);
        return sword;
    }

    /** 是否为管理员之刃（创世神刃） */
    public boolean isAdminSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "admin_sword"), PersistentDataType.BOOLEAN);
    }

    /** 管理员之刃：创世神刃 */
    public ItemStack adminSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(gradient("创世神刃", 230, 20, 20, 255, 200, 0)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.lore(adminLore());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "admin_sword"),
                    PersistentDataType.BOOLEAN, true);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        sword.addUnsafeEnchantments(java.util.Map.of(Enchantment.SHARPNESS, 256));
        return sword;
    }

    /** 管理员之刃 lore（创世神刃） */
    public List<Component> adminLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&6特性&7:&c管理之怒")));
        lore.add(Component.text(color("&8[&7仅 OP 持有触发&8]&7 瞬间血量为0，释放扣血动画")));
        lore.add(Component.text(color("&7秒杀前召唤闪电 &8(不起火)")));
        lore.add(Component.text(color("&7击杀后半径 &f1000 &7格内玩家 &c反胃&7+&9失明&7 5秒")));
        lore.add(Component.text(color("&7放大标题:"))
                .append(Component.text("让整个世界为之颤抖吧!",
                                net.kyori.adventure.text.format.TextColor.color(255, 40, 40))
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)));
        lore.add(Component.text(color("&dHim神力&7:&a主手自动开飞行+午夜&7 | 离手关闭飞行+白天")));
        lore.add(Component.text("Admin", net.kyori.adventure.text.format.TextColor.color(255, 170, 0))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        lore.add(gradient("+Infinity", 255, 60, 60, 255, 200, 0)
                .append(Component.text(color(" &7攻击伤害")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(gradient("+Infinity", 0, 200, 255, 160, 60, 255)
                .append(Component.text(color(" &7攻击速度")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        return lore;
    }

    private void putIf(java.util.Map<org.bukkit.enchantments.Enchantment, Integer> map,
                       String key, int level) {
        org.bukkit.enchantments.Enchantment e = Enchants.get(key);
        if (e != null) {
            map.put(e, level);
        }
    }

    /** 神器 lore（owner 为 true 时显示"王已归位..."，否则"寻找它的主人"） */
    public List<Component> artifactLore(boolean owner) {
        List<Component> lore = new ArrayList<>();
        // 精简：王的降临 + 特性 合并为一行
        lore.add(Component.text(color(
                "&8王的降临 &7特性：&f虚空恐惧/反胃/凋零/中毒/失眠/导电/爆炸/虚影/偷袭/日食")));
        // 更鲜艳醒目的渐变（攻击伤害：红→金；攻击速度：青→紫），加粗
        lore.add(gradient("Infinity", 255, 60, 60, 255, 200, 0)
                .append(Component.text(color(" &7攻击伤害")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(gradient("Infinity", 0, 200, 255, 160, 60, 255)
                .append(Component.text(color(" &7攻击速度")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        if (owner) {
            lore.add(gradient("王已归位...", 255, 220, 120, 190, 90, 255));
        } else {
            lore.add(Component.text(color("&7一把十分精致的剑 但它似乎正在寻找它的主人")));
        }
        return lore;
    }

    /** 字符级彩色渐变 */
    public Component gradient(String text, int sr, int sg, int sb, int er, int eg, int eb) {
        java.util.List<Component> parts = new ArrayList<>();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            double t = n <= 1 ? 0 : (double) i / (n - 1);
            int r = sr + (int) ((er - sr) * t);
            int g = sg + (int) ((eg - sg) * t);
            int b = sb + (int) ((eb - sb) * t);
            parts.add(Component.text(String.valueOf(text.charAt(i)))
                    .color(net.kyori.adventure.text.format.TextColor.color(
                            Math.max(0, Math.min(255, r)),
                            Math.max(0, Math.min(255, g)),
                            Math.max(0, Math.min(255, b)))));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.noSeparators(), parts);
    }

    private void addIf(org.bukkit.inventory.meta.ItemMeta meta, String key, int level) {
        Enchantment e = Enchants.get(key);
        if (e != null) {
            meta.addEnchant(e, level, true);
        }
    }

    public YamlConfiguration rawConfig() {
        return config;
    }

    public YamlConfiguration rawTargets() {
        return targets;
    }

    public YamlConfiguration rawRarity() {
        return rarity;
    }
}
