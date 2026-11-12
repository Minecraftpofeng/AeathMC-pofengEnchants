package com.ecoclone;

/**
 * 附魔类型（对应 EcoEnchants 的 type 字段）。
 *
 * normal   普通附魔，可正常叠加
 * curse    诅咒附魔，负面效果
 * special  强力附魔，默认每件物品只能有 1 个（config.yml type-limits.special）
 * artifact 装饰附魔，只产生粒子，默认每件物品只能有 1 个（type-limits.artifact）
 * spell    主动技能，右键（弓/弩/三叉戟/鱼竿为左键）激活，随后进入冷却
 */
public enum EnchantType {

    NORMAL("normal", "普通"),
    CURSE("curse", "诅咒"),
    SPECIAL("special", "强力"),
    ARTIFACT("artifact", "装饰"),
    SPELL("spell", "主动");

    private final String id;
    private final String cn;

    EnchantType(String id, String cn) {
        this.id = id;
        this.cn = cn;
    }

    public String id() {
        return id;
    }

    public String cn() {
        return cn;
    }

    /** 无法识别时回落到 NORMAL，避免配置笔误让附魔直接失效 */
    public static EnchantType parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return NORMAL;
        }
        String s = raw.trim().toLowerCase();
        for (EnchantType t : values()) {
            if (t.id.equals(s) || t.name().toLowerCase().equals(s)) {
                return t;
            }
        }
        if (s.equals("artifact-enchant")) {
            return ARTIFACT;
        }
        if (s.equals("spell-enchant") || s.equals("ability")) {
            return SPELL;
        }
        return NORMAL;
    }
}
