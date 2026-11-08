package com.ecoclone;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * v1.4.7.0 高级功能闸门：让老监听器（Combat / Mining / Projectile / Utility / Armor）里的
 * Enchants.level 也受 conditions 与 triggers 控制。
 *
 * 用法：监听器事件入口调用 enter(本次事件相关的玩家...)，随后所有 Enchants.level(item, key)
 * 会按物品归属找到对应玩家，再套条件与触发器；找不到归属或没有上下文时放行，保持原行为。
 */
public final class EnchantGate {

    /** 上下文有效期：事件同步处理，超过该时间视为残留，自动作废 */
    private static final long TTL_MS = 120L;

    private static final ThreadLocal<Player[]> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Long> STAMP = new ThreadLocal<>();
    private static volatile EnchantConditions conditions;

    private EnchantGate() {
    }

    /** 插件启用时注入条件判定器 */
    public static void bind(EnchantConditions c) {
        conditions = c;
    }

    /** 事件入口登记参与判定的玩家（可为 null，最多两位：攻击方与受击方） */
    public static void enter(Player... players) {
        CONTEXT.set(players);
        STAMP.set(System.currentTimeMillis());
    }

    /** 清理上下文（可选，残留也会因超时自动失效） */
    public static void exit() {
        CONTEXT.remove();
        STAMP.remove();
    }

    /** 该物品上的 key 附魔在当前上下文下是否允许生效 */
    public static boolean allows(ItemStack item, String key) {
        EnchantConditions c = conditions;
        if (c == null || item == null) {
            return true;
        }
        Player owner = ownerOf(item);
        if (owner == null) {
            return true;
        }
        return c.triggerAllowed(key, TriggerType.get()) && c.matches(key, owner);
    }

    /** 按物品归属解析玩家：单玩家直接用，多玩家按背包/护甲/手持匹配 */
    private static Player ownerOf(ItemStack item) {
        Player[] ps = CONTEXT.get();
        Long stamp = STAMP.get();
        if (ps == null || stamp == null) {
            return null;
        }
        if (System.currentTimeMillis() - stamp > TTL_MS) {
            exit();
            return null;
        }
        int count = 0;
        Player single = null;
        for (Player p : ps) {
            if (p == null) {
                continue;
            }
            count++;
            single = p;
            if (holds(p, item)) {
                return p;
            }
        }
        return count == 1 ? single : null;
    }

    private static boolean holds(Player p, ItemStack item) {
        PlayerInventory inv = p.getInventory();
        if (same(inv.getItemInMainHand(), item) || same(inv.getItemInOffHand(), item)) {
            return true;
        }
        for (ItemStack armor : inv.getArmorContents()) {
            if (same(armor, item)) {
                return true;
            }
        }
        for (ItemStack slot : inv.getStorageContents()) {
            if (same(slot, item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.equals(b);
    }
}
