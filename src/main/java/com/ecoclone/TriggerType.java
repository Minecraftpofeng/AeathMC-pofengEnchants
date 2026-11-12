package com.ecoclone;

/**
 * 附魔触发器类型（对应 EcoEnchants 的 triggers 配置）。
 * yml 写法：
 *   general-config:
 *     triggers: [attack, defense]     # 不写 = 全部场景都允许
 *   triggers: [mining]                # 顶层写法同样生效
 */
public enum TriggerType {

    ATTACK("attack", "hit", "damage"),
    DEFEND("defense", "defend", "hurt", "damaged"),
    SHOOT("shoot", "shooting", "projectile", "bow", "arrow"),
    MINE("mine", "mining", "break", "block-break"),
    KILL("kill", "killing", "death"),
    PASSIVE("passive", "tick", "always"),
    FISH("fish", "fishing"),
    TRADE("trade", "villager"),
    LOOT("loot", "chest"),
    DEATH("death", "die", "on-death"),
    SPELL("spell", "cast", "activate", "use");

    private final String[] aliases;

    TriggerType(String... aliases) {
        this.aliases = aliases;
    }

    /** 事件处理时设置的当前触发器（服务端事件在主线程串行执行） */
    private static TriggerType current = PASSIVE;

    public static TriggerType get() {
        return current;
    }

    public static TriggerType set(TriggerType type) {
        TriggerType prev = current;
        current = type == null ? PASSIVE : type;
        return prev;
    }

    /** 将配置里的别名解析成触发器；无法识别返回 null */
    public static TriggerType parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String s = raw.trim().toLowerCase().replace('_', '-');
        for (TriggerType t : values()) {
            if (t.name().equalsIgnoreCase(s.replace('-', '_'))) {
                return t;
            }
            for (String a : t.aliases) {
                if (a.equals(s)) {
                    return t;
                }
            }
        }
        return null;
    }

    public String[] aliases() {
        return aliases;
    }
}
