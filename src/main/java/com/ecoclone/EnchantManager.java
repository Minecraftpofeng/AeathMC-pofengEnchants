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
