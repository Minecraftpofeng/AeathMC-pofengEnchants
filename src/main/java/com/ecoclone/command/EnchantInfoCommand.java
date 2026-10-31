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
        return true;
    }
}
