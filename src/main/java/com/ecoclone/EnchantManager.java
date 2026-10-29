package com.ecoclone;

import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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

    /** 热重载时清空全部冷却记录 */
    public void clearCooldowns() {
        cooldowns.clear();
    }

    // ---------- 核心数值（config 生效：附魔 yml 的 config 段） ----------

    /** 概率 clamp 到 [0,1]，防止配置里写入超界值导致必触发或永不触发 */
    public static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** 读取附魔触发概率（config.chance），缺省用 def，已 clamp 到 [0,1] */
    public double chance(String key, double def) {
        YamlConfiguration c = ench(key);
        return c != null && c.contains("config.chance")
                ? clamp01(c.getDouble("config.chance", def))
                : clamp01(def);
    }

    /** 读取附魔持续时长（config.duration，单位 tick），缺省用 def，下限 0 */
    public int duration(String key, int def) {
        YamlConfiguration c = ench(key);
        return c != null && c.contains("config.duration")
                ? Math.max(0, c.getInt("config.duration", def))
                : def;
    }

    /** 读取附魔独立冷却（config.cooldown 秒），缺省用 def，下限 0 */
    public double cooldown(String key, double def) {
        YamlConfiguration c = ench(key);
        double v = c != null && c.contains("config.cooldown")
                ? c.getDouble("config.cooldown", def) : def;
        return Math.max(0, v);
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

    /** 是否为彩虹猫之刃 */
    public boolean isRainbowBlade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "rainbow_blade"), PersistentDataType.BOOLEAN);
    }

    /** 彩虹猫之刃（Rainbow Cat Blade / Nyan Blade） */
    public ItemStack rainbowBlade() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(gradient("彩虹猫之刃", 255, 60, 60, 200, 0, 255)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.lore(rainbowLore());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rainbow_blade"),
                    PersistentDataType.BOOLEAN, true);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 256);
        putIf(ench, "leeching", 10);       // 吸血
        putIf(ench, "frost", 5);           // 冻结
        putIf(ench, "conductive", 2);      // 导电
        putIf(ench, "explosive", 2);       // 爆炸
        putIf(ench, "toxic", 2);           // 中毒
        putIf(ench, "wither_aspect", 2);   // 凋零
        putIf(ench, "nausea", 2);          // 反胃
        putIf(ench, "phantom", 10);        // 虚影
        putIf(ench, "sneak_attack", 2);    // 偷袭
        putIf(ench, "eclipse", 1);         // 日食
        putIf(ench, "criticals", 1);       // 会心一击
        sword.addUnsafeEnchantments(ench);
        return sword;
    }

    /** 彩虹猫之刃 lore */
    public List<Component> rainbowLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&6特性&f：彩虹尾迹 / 喵喵弹射 / 彩虹闪电 / 霓虹狂飙 / 彩虹护盾")));
        lore.add(Component.text(color("&7移动时拖出七彩彩虹尾迹")));
        lore.add(Component.text(color("&7命中击退并喵叫，引下彩虹闪电")));
        lore.add(Component.text(color("&7攻速 +100% &7· &7移速 +40% &7· &7受击反弹 30%")));
        lore.add(Component.text("Nyan", net.kyori.adventure.text.format.TextColor.color(255, 120, 220))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        lore.add(gradient("+Infinity", 255, 60, 60, 255, 200, 0)
                .append(Component.text(color(" &7攻击伤害")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(gradient("+Infinity", 0, 200, 255, 160, 60, 255)
                .append(Component.text(color(" &7攻击速度")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(Component.text(color("&7喵~ (ฅ'ω'ฅ)")));
        return lore;
    }

    // ==================== 以太之剑 · 升级链（阶段 I~V） ====================

    /** 是否为以太之剑 */
    public boolean isAetherBlade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "aether_blade"), PersistentDataType.BOOLEAN);
    }

    /** 以太之剑当前阶段（1~5，默认 1） */
    public int aetherStage(ItemStack item) {
        if (!isAetherBlade(item)) {
            return 1;
        }
        Integer s = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "aether_stage"), PersistentDataType.INTEGER);
        return s == null ? 1 : Math.max(1, Math.min(5, s));
    }

    /** 以太之剑累计击杀数 */
    public int aetherKills(ItemStack item) {
        if (!isAetherBlade(item)) {
            return 0;
        }
        Integer k = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "aether_kills"), PersistentDataType.INTEGER);
        return k == null ? 0 : k;
    }

    /** 写入以太之剑击杀数 */
    public void setAetherKills(ItemStack item, int kills) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "aether_kills"), PersistentDataType.INTEGER, kills));
    }

    /** 觉醒下一阶段的击杀阈值 */
    public int aetherThreshold(int stage) {
        switch (stage) {
            case 2: return 50;
            case 3: return 150;
            case 4: return 350;
            case 5: return 750;
            default: return 50;
        }
    }

    /** 以太之剑各阶段名称 */
    public String aetherName(int stage) {
        switch (stage) {
            case 2: return "星穹以太 · 裁决之刃";
            case 3: return "永恒以太 · 虚空裁决";
            case 4: return "以太洪荒 · 弑神之刃";
            case 5: return "以太湮灭 · 万象归墟";
            default: return "以太 · 溯洄终焉";
        }
    }

    /** 以太之剑：按阶段创建 */
    public ItemStack aetherBlade(int stage) {
        final int s = Math.max(1, Math.min(5, stage));
        int[] c = stageColors(s);
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(gradient(aetherName(s), c[0], c[1], c[2], c[3], c[4], c[5])
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.lore(aetherLore(s, 0));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, "aether_blade"), PersistentDataType.BOOLEAN, true);
            pdc.set(new NamespacedKey(plugin, "aether_stage"), PersistentDataType.INTEGER, s);
            pdc.set(new NamespacedKey(plugin, "aether_kills"), PersistentDataType.INTEGER, 0);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 256);
        putIf(ench, "criticals", 1);
        putIf(ench, "leeching", s >= 4 ? 10 : 5);
        if (s >= 2) {
            putIf(ench, "conductive", 2);
        }
        if (s >= 3) {
            putIf(ench, "explosive", 2);
            putIf(ench, "toxic", 2);
        }
        if (s >= 4) {
            ench.put(Enchantment.FIRE_ASPECT, 20);
            putIf(ench, "wither_aspect", 2);
            putIf(ench, "insomnia", 2);
        }
        if (s >= 5) {
            putIf(ench, "phantom", 10);
            putIf(ench, "eclipse", 1);
            putIf(ench, "sneak_attack", 2);
            putIf(ench, "nausea", 2);
        }
        sword.addUnsafeEnchantments(ench);
        return sword;
    }

    /** 以太之剑 lore（含击杀进度） */
    public List<Component> aetherLore(int stage, int kills) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&d阶段 &f" + roman(stage)
                + " &8· &7" + aetherName(stage))));
        lore.add(Component.text(color("&6特性&f：星辰爆裂 / 以太吸灵 / 深渊吞噬 / 以太护体")));
        if (stage >= 2) {
            lore.add(Component.text(color("&7觉醒：&c以太爆裂")));
        }
        if (stage >= 4) {
            lore.add(Component.text(color("&7觉醒：&4以太烈焰")));
        }
        if (stage >= 5) {
            lore.add(Component.text(color("&7觉醒：&5三重雷暴 · 万象归墟 &c· 吞噬必至")));
        }
        if (stage < 5) {
            lore.add(Component.text(color("&8击杀进度：&f" + kills + "&8 / &f"
                    + aetherThreshold(stage + 1) + " &8（觉醒下一阶段）")));
        } else {
            lore.add(Component.text(color("&7已觉醒至最终形态，万象尽归墟")));
        }
        lore.add(gradient("+Infinity", 255, 60, 60, 255, 200, 0)
                .append(Component.text(color(" &7攻击伤害")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(gradient("+Infinity", 0, 200, 255, 160, 60, 255)
                .append(Component.text(color(" &7攻击速度")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        return lore;
    }

    private int[] stageColors(int stage) {
        switch (stage) {
            case 2: return new int[]{0, 229, 255, 255, 215, 0};
            case 3: return new int[]{255, 215, 0, 180, 60, 255};
            case 4: return new int[]{180, 60, 255, 255, 40, 40};
            case 5: return new int[]{255, 40, 40, 60, 0, 120};
            default: return new int[]{0, 229, 255, 30, 90, 255};
        }
    }

    private String roman(int stage) {
        switch (stage) {
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return "I";
        }
    }

    // ==================== 晨曦破晓剑 ====================

    /** 是否为晨曦破晓剑 */
    public boolean isDawnBlade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "dawn_blade"), PersistentDataType.BOOLEAN);
    }

    /** 晨曦破晓剑 */
    public ItemStack dawnBlade() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(gradient("晨曦破晓剑", 255, 215, 0, 255, 170, 60)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.lore(dawnLore());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "dawn_blade"),
                    PersistentDataType.BOOLEAN, true);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 256);
        ench.put(Enchantment.FIRE_ASPECT, 20);
        putIf(ench, "criticals", 1);
        putIf(ench, "leeching", 10);
        putIf(ench, "frost", 5);
        putIf(ench, "conductive", 2);
        sword.addUnsafeEnchantments(ench);
        return sword;
    }

    /** 晨曦破晓剑 lore */
    public List<Component> dawnLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&6特性&f：破晓之光 / 太阳灼烧 / 黎明庇护 / 曙光冲锋")));
        lore.add(Component.text(color("&7命中点燃并致盲目标，迸发太阳金辉")));
        lore.add(Component.text(color("&7攻速 +75% &7· &7移速 +20% &7· &7受击反弹 30%")));
        lore.add(Component.text(color("&6黎明将至，破晓即裁决")));
        lore.add(gradient("+Infinity", 255, 60, 60, 255, 200, 0)
                .append(Component.text(color(" &7攻击伤害")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(gradient("+Infinity", 0, 200, 255, 160, 60, 255)
                .append(Component.text(color(" &7攻击速度")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        return lore;
    }

    // ==================== 暗渊噬魂剑 ====================

    /** 是否为暗渊噬魂剑 */
    public boolean isAbyssBlade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "abyss_blade"), PersistentDataType.BOOLEAN);
    }

    /** 暗渊噬魂剑 */
    public ItemStack abyssBlade() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(gradient("暗渊噬魂剑", 156, 39, 176, 74, 0, 130)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.lore(abyssLore());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "abyss_blade"),
                    PersistentDataType.BOOLEAN, true);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 256);
        putIf(ench, "toxic", 2);
        putIf(ench, "wither_aspect", 2);
        putIf(ench, "nausea", 2);
        putIf(ench, "insomnia", 2);
        putIf(ench, "leeching", 10);
        putIf(ench, "criticals", 1);
        putIf(ench, "phantom", 10);
        putIf(ench, "sneak_attack", 2);
        sword.addUnsafeEnchantments(ench);
        return sword;
    }

    /** 暗渊噬魂剑 lore */
    public List<Component> abyssLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&6特性&f：噬魂 / 深渊凝视 / 深渊吞噬 / 深渊契约")));
        lore.add(Component.text(color("&7命中吸取 25% 伤害为生命，&5满级 100% 吞噬生物")));
        lore.add(Component.text(color("&7攻速 +60% &7· &7受击反弹 35%")));
        lore.add(Component.text(color("&8深渊之底，唯有吞噬")));
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

    // ==================== 神器锄头 · 万象生息 · 丰饶之锄 ====================

    /** 是否为神器锄头 */
    public boolean isHoeArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "hoe_artifact"), PersistentDataType.BOOLEAN);
    }

    /** 绑定玩家判断 */
    public boolean isHoeOwner(String name) {
        return name != null && (name.equals("7gzztrgv") || name.equals("Minecraft_pofeng"));
    }

    /** 锄头名字（统一为「大楚兴，陈胜王」之锄，渐变金→红） */
    public Component hoeNameComponent(boolean owner) {
        return gradient("「大楚兴，陈胜王」之锄", 255, 215, 0, 255, 40, 40)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
    }

    /** 神器锄头（主特性为武器） */
    public ItemStack hoeArtifact() {
        ItemStack hoe = new ItemStack(Material.NETHERITE_HOE);
        hoe.editMeta(meta -> {
            meta.displayName(hoeNameComponent(false));
            meta.lore(hoeLore(false));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoe_artifact"),
                    PersistentDataType.BOOLEAN, true);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 10);
        ench.put(Enchantment.FIRE_ASPECT, 10);
        putIf(ench, "criticals", 1);       // 会心
        putIf(ench, "leeching", 10);       // 吸血
        putIf(ench, "conductive", 2);      // 导电
        putIf(ench, "explosive", 2);       // 爆炸
        putIf(ench, "toxic", 2);           // 中毒
        putIf(ench, "wither_aspect", 2);   // 凋零
        putIf(ench, "phantom", 5);         // 虚影
        putIf(ench, "insomnia", 2);        // 失眠
        hoe.addUnsafeEnchantments(ench);
        return hoe;
    }

    /** 锄头 lore（owner 时狐狸行变为渐变名言） */
    public List<Component> hoeLore(boolean owner) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&8专属绑定：&f7gzztrgv &8/ &fMinecraft_pofeng")));
        lore.add(Component.text(color("&6主特性&f（武器）：大地之怒 / 灵狐易伤 / 神农庇护 / 万象生息 / 灵狐赐福")));
        lore.add(Component.text(color("&eMinecraft_pofeng&7 持有时：命中伤害翻倍 · 目标 1 秒内无受击无敌帧")));
        lore.add(Component.text(color("&d7gzztrgv &7持有时：&b冰冻 · 剧毒 · 召唤灵狐 · 高攻速 · 无视无敌帧")));
        lore.add(Component.text(color("&7受击减免并反弹 · 击杀回复生命")));
        lore.add(Component.text(color("&8农耕：丰饶催熟 / 丰收翻倍 / 大地滋养 / 沃土永固")));
        if (owner) {
            lore.add(gradient("大楚兴，陈胜王", 255, 215, 0, 255, 40, 40));
        } else {
            lore.add(Component.text(color("&8这把锄头似乎是某个狐狸落下的")));
        }
        lore.add(gradient("+Infinity", 255, 60, 60, 255, 200, 0)
                .append(Component.text(color(" &7攻击伤害")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(gradient("+Infinity", 0, 200, 255, 160, 60, 255)
                .append(Component.text(color(" &7攻击速度")))
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(Component.text(color("&8万物有灵，一锄生息")));
        return lore;
    }

    // ==================== 七宗罪 · 暴怒「嗜血重生」 ====================

    public boolean isWrathBlade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "wrath_blade"), PersistentDataType.BOOLEAN);
    }

    public boolean isWrathOwner(String name) {
        return name != null && (name.equals("OP_KL") || name.equals("Minecraft_pofeng"));
    }

    public Component wrathNameComponent() {
        return gradient("七宗罪 · 暴怒「嗜血重生」", 255, 30, 30, 255, 150, 0)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
    }

    /** 暴怒神器（绑定 OP_KL / Minecraft_pofeng，他人持之为破烂） */
    public ItemStack wrathBlade() {
        ItemStack blade = new ItemStack(Material.NETHERITE_SWORD);
        blade.editMeta(meta -> {
            meta.displayName(wrathNameComponent());
            meta.lore(wrathLore(true));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "wrath_blade"),
                    PersistentDataType.BOOLEAN, true);
        });
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.SHARPNESS, 10);
        ench.put(Enchantment.FIRE_ASPECT, 10);
        ench.put(Enchantment.VANISHING_CURSE, 1);
        ench.put(Enchantment.BINDING_CURSE, 1);
        putIf(ench, "criticals", 1);      // 会心
        putIf(ench, "leeching", 10);      // 吸血
        putIf(ench, "explosive", 2);      // 爆炸
        putIf(ench, "conductive", 2);     // 导电
        putIf(ench, "razor", 5);          // 利刃
        blade.addUnsafeEnchantments(ench);
        return blade;
    }

    /** 暴怒神器 lore（owner 为 true 时完整展示，否则破烂） */
    public List<Component> wrathLore(boolean owner) {
        List<Component> lore = new ArrayList<>();
        if (!owner) {
            lore.add(Component.text(color("&7破烂")));
            lore.add(Component.text(color("&8被抛弃的废物……")));
            return lore;
        }
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&6专属绑定：&fOP_KL &8/ &fMinecraft_pofeng")));
        lore.add(Component.text(color("&8灵魂契约之物，只忠于契约之人，身死，此物不离")));
        lore.add(Component.text(color("&c嗜血重生&f：生命低于&415%&f时回复全部生命，获得&e20秒抗性提升1&f与&e20颗黄心")));
        lore.add(Component.text(color("&c狂怒&f：生命低于&460%&f时伤害&c×3.7&f，&b速度2&f、&b命中燃烧6秒&f、&b急迫3")));
        lore.add(Component.text(color("&c嗜血&f：攻击恢复&c10%&f生命，获得&e抗性1")));
        lore.add(Component.text(color("&c受伤&f：生命恢复2、抗性提升1、伤害吸收2（低于&410%&f：抗性3、生命恢复5）")));
        lore.add(Component.text(color("&c赎罪&f：死亡时以最大生命复活&e(ovo)")));
        lore.add(Component.text(color("&c禁咒&f：固定&c30&f点伤害，每次攻击削减对方&c5%&f生命值")));
        lore.add(Component.text(color("&c诅咒之刃&f：常驻伤害&c×2&f，免疫中毒/凋零/虚弱/缓慢/反胃")));
        lore.add(Component.text(color("&c暴戾&f：每附魔&a+4%攻速&7/&a+3%护甲&7/&a+0.25击退抗性&f；每诅咒&a+2%实体范围&7/&a+2护甲韧性")));
        lore.add(Component.empty());
        lore.add(Component.text("暴怒", TextColor.fromHexString("#FF4444"))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        lore.add(Component.text("愤怒是不加掩饰的原罪。", TextColor.fromHexString("#9AA0A6"))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        lore.add(Component.text("它会撕碎冷静，纵容杀戮，", TextColor.fromHexString("#FF4444"))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        lore.add(Component.text("使持有者沉溺于复仇与狂暴之中，", TextColor.fromHexString("#9AA0A6"))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        lore.add(Component.text("在无休止的厮杀里，沉沦不返。", TextColor.fromHexString("#FF4444"))
                .decorate(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED));
        return lore;
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

    // ==================== 概念性防具 · 无尽系列（4 件套，绑定 ClowmPierce） ====================

    public static final String ARMOR_OWNER = "ClowmPierce";

    /** 是否为无尽系列防具 */
    public boolean isArmorArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "armor_artifact"), PersistentDataType.BOOLEAN);
    }

    /** 部位：helmet / chest / legs / boots */
    public String armorPiece(ItemStack item) {
        if (!isArmorArtifact(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "armor_piece"), PersistentDataType.STRING);
    }

    /** 绑定判断：本人 ClowmPierce / Minecraft_pofeng */
    public boolean isArmorOwner(String name) {
        return name != null && (name.equals(ARMOR_OWNER) || name.equals("Minecraft_pofeng"));
    }

    public String armorName(String piece) {
        return switch (piece) {
            case "helmet" -> "无尽头盔·寰宇支配";
            case "chest" -> "无尽胸甲·世界崩解";
            case "legs" -> "无尽护腿·自然荒芜";
            default -> "无尽鞋子·星球吞噬";
        };
    }

    public Material armorMat(String piece) {
        return switch (piece) {
            case "helmet" -> Material.NETHERITE_HELMET;
            case "chest" -> Material.NETHERITE_CHESTPLATE;
            case "legs" -> Material.NETHERITE_LEGGINGS;
            default -> Material.NETHERITE_BOOTS;
        };
    }

    public String ownerBonusName(String piece) {
        return switch (piece) {
            case "helmet" -> "怒火中烧";
            case "chest" -> "终结";
            case "legs" -> "审判之眼";
            default -> "暗黑戒指";
        };
    }

    public Component armorNameComponent(String piece) {
        return gradient(armorName(piece), 120, 220, 255, 200, 120, 255)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
    }

    /** 创建单件无尽防具 */
    public ItemStack armorArtifact(String piece) {
        ItemStack it = new ItemStack(armorMat(piece));
        it.editMeta(meta -> {
            meta.displayName(armorNameComponent(piece));
            meta.lore(armorLore(piece));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, "armor_artifact"), PersistentDataType.BOOLEAN, true);
            pdc.set(new NamespacedKey(plugin, "armor_piece"), PersistentDataType.STRING, piece);
            meta.setUnbreakable(true);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE,
                    new org.bukkit.attribute.AttributeModifier(
                            new NamespacedKey(plugin, "armor_kb_" + piece), 1.0,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.ANY));
        });
        java.util.Map<Enchantment, Integer> ench = new java.util.HashMap<>();
        ench.put(Enchantment.PROTECTION, 50);
        ench.put(Enchantment.BLAST_PROTECTION, 50);
        ench.put(Enchantment.FIRE_PROTECTION, 50);
        ench.put(Enchantment.PROJECTILE_PROTECTION, 50);
        ench.put(Enchantment.FEATHER_FALLING, 50);
        ench.put(Enchantment.RESPIRATION, 50);
        ench.put(Enchantment.DEPTH_STRIDER, 50);
        ench.put(Enchantment.FROST_WALKER, 5);
        ench.put(Enchantment.SWIFT_SNEAK, 10);
        ench.put(Enchantment.THORNS, 80);
        ench.put(Enchantment.UNBREAKING, 50);
        ench.put(Enchantment.LUCK_OF_THE_SEA, 50);
        it.addUnsafeEnchantments(ench);
        return it;
    }

    public List<Component> armorLore(String piece) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(color("&7Lv.&fInfinity")));
        lore.add(Component.text(color("&8无尽防具系列 · 概念性防御")));
        lore.add(Component.text(color("&6专属绑定：&fClowmPierce / Minecraft_pofeng")));
        lore.add(Component.text(color("&8特性：&f巫师袍2 / 概念性防御3 / 无尽反应胸甲 / 衔尾蛇2 / 龙之愤怒 / 无尽力量 / 魔童 / 狂热 / 震撼")));
        lore.add(Component.text(color("&e专属 " + armorName(piece) + "：")));
        switch (piece) {
            case "helmet" -> lore.add(Component.text(color("  &d寰宇支配&7：持剑者伤害+30%（无视强制限伤）")));
            case "chest" -> lore.add(Component.text(color("  &d世界崩解&7：空手左键秒挖单方块；潜行+左键 32x32 区域挖掘（掉落整理入潜影箱）")));
            case "legs" -> lore.add(Component.text(color("  &d自然荒芜&7：潜行+右键，15格内植物枯萎")));
            default -> lore.add(Component.text(color("  &d星球吞噬&7：铲子挖掘范围提升至3x3")));
        }
        if ("helmet".equals(piece) || "chest".equals(piece)) {
            lore.add(Component.text(color("  &d地蕴复生&7：生命恢复速度+15%")));
        }
        if ("legs".equals(piece) || "boots".equals(piece)) {
            lore.add(Component.text(color("  &d天堂陨落&7：弓造成的伤害+100%")));
        }
        lore.add(Component.text(color("&8穿齐4件：全套无尽药水buff（30s）+ 全套抗性减免 + 生存/冒险自动飞行")));
        lore.add(Component.text(color("&8狂热&7：命中后自身攻击速度提升（3秒） / &8震撼&7：空手右键震飞周围生物并眩晕")));
        lore.add(Component.text(color("&6✦ 王的降临 ✦")));
        lore.add(Component.text(color("&7绑定者装备时解锁专属：&d" + ownerBonusName(piece))));
        return lore;
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
