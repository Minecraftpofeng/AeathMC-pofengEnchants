package com.ecoclone;

import io.papermc.paper.registry.TypedKey;
import org.bukkit.inventory.ItemType;

import java.util.List;

/**
 * 单个附魔的静态定义（用于注册阶段与运行时引用）。
 */
public final class EnchantDef {

    public final String key;          // 附魔键名，如 beheading
    public final String name;         // 显示名
    public final String description;  // 描述
    public final int maxLevel;
    public final int weight;          // 抽取权重
    public final boolean curse;
    public final boolean treasure;    // 是否宝藏附魔（无法在附魔台自然出现）
    public final String rarity;       // 稀有度（对应 rarity.yml 分组）
    public final List<TypedKey<ItemType>> targets;
    public final int minBase, minPerLevel, maxBase, maxPerLevel;
    public final int anvilCost;

    public EnchantDef(String key, String name, String description, int maxLevel, int weight,
                      boolean curse, boolean treasure, String rarity,
                      List<TypedKey<ItemType>> targets,
                      int minBase, int minPerLevel, int maxBase, int maxPerLevel, int anvilCost) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.maxLevel = maxLevel;
        this.weight = weight;
        this.curse = curse;
        this.treasure = treasure;
        this.rarity = rarity;
        this.targets = targets;
        this.minBase = minBase;
        this.minPerLevel = minPerLevel;
        this.maxBase = maxBase;
        this.maxPerLevel = maxPerLevel;
        this.anvilCost = anvilCost;
    }
}
