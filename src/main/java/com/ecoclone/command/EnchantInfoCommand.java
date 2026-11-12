package com.ecoclone.command;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /enchantinfo <enchant> — 查看某个附魔的信息（描述、最大等级、稀有度、适用物品、冲突）。
 */
public final class EnchantInfoCommand implements CommandExecutor {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public EnchantInfoCommand(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecoenchants.command.enchantinfo")) {
            sender.sendMessage(manager.prefix() + manager.msg("commands.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(manager.prefix() + manager.color("&c用法：/enchantinfo <附魔键名>"));
            return true;
        }

        String key = args[0].toLowerCase();
        if (Enchants.get(key) == null) {
            sender.sendMessage(manager.prefix() + manager.msg("commands.enchant-not-found")
                    .replace("%enchant%", key));
            return true;
        }

        sender.sendMessage(manager.color(manager.msg("enchantinfo.header").replace("%name%", manager.name(key))));
        sender.sendMessage(manager.color(manager.msg("enchantinfo.description")
                .replace("%description%", manager.description(key))));
        sender.sendMessage(manager.color(manager.msg("enchantinfo.max-level")
                .replace("%level%", String.valueOf(manager.maxLevel(key)))));
        sender.sendMessage(manager.color(manager.msg("enchantinfo.rarity")
                .replace("%rarity%", manager.rarity(key))));
        // v1.4.8.0 高级配置
        com.ecoclone.EnchantType type = manager.type(key);
        sender.sendMessage(manager.color("&7类型 Type: &f" + type.id() + " &8(" + type.cn() + ")"));
        if (type == com.ecoclone.EnchantType.SPELL) {
            sender.sendMessage(manager.color("&7激活方式 Activation: &f右键 Right-click"
                    + " &8(弓/弩/三叉戟/鱼竿为左键) &7冷却 Cooldown: &f"
                    + manager.spellCooldown(key) + "s"));
        }
        java.util.List<String> required = manager.requiredList(key);
        if (!required.isEmpty()) {
            sender.sendMessage(manager.color("&7前置 Required: &f" + String.join(", ", required)));
        }
        java.util.List<String> requirements = manager.requirements(key);
        if (!requirements.isEmpty()) {
            sender.sendMessage(manager.color("&7条件 Requirements: &f" + String.join(", ", requirements)));
        }
        java.util.List<String> notMet = manager.notMetLore(key);
        if (!notMet.isEmpty()) {
            sender.sendMessage(manager.color("&7未满足提示 Not-met: &f" + String.join(" / ", notMet)));
        }
        java.util.List<String> noWorlds = manager.disabledWorlds(key);
        if (!noWorlds.isEmpty()) {
            sender.sendMessage(manager.color("&7禁用世界 Disabled worlds: &f" + String.join(", ", noWorlds)));
        }
        if (manager.typeLimit(type) > 0) {
            sender.sendMessage(manager.color("&7单件上限 Per-item limit: &f" + manager.typeLimit(type)));
        }
        return true;
    }
}
