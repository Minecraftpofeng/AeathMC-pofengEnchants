package com.ecoclone.command;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * /ecoenchants 主指令，子命令与 EcoEnchants 保持一致：
 *   /ecoenchants giverandombook <player>
 *   /ecoenchants randomenchant [player]
 *   /ecoenchants reload
 *   /ecoenchants givebook <附魔键> <等级> [player]
 */
public final class EcoEnchantsCommand implements CommandExecutor {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public EcoEnchantsCommand(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(manager.prefix() + manager.msg("commands.invalid-args"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "giverandombook" -> {
                if (!sender.hasPermission("ecoenchants.command.giverandombook")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                Map.Entry<org.bukkit.enchantments.Enchantment, Integer> roll = manager.randomEnchant();
                if (roll == null) {
                    sender.sendMessage(manager.prefix() + manager.color("&c没有可用的附魔。"));
                    return true;
                }
                target.getInventory().addItem(manager.bookOf(roll.getKey(), roll.getValue()));
                sender.sendMessage(manager.prefix()
                        + manager.msg("commands.giverandombook").replace("%player%", target.getName()));
                return true;
            }
            case "randomenchant" -> {
                if (!sender.hasPermission("ecoenchants.command.randomenchant")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                ItemStack held = target.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    sender.sendMessage(manager.prefix() + manager.msg("commands.no-item"));
                    return true;
                }
                Map.Entry<org.bukkit.enchantments.Enchantment, Integer> roll = manager.randomEnchant();
                if (roll == null) {
                    sender.sendMessage(manager.prefix() + manager.color("&c没有可用的附魔。"));
                    return true;
                }
                manager.apply(held, roll.getKey(), roll.getValue());
                sender.sendMessage(manager.prefix()
                        + manager.msg("commands.randomenchant").replace("%player%", target.getName()));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("ecoenchants.command.reload")) {
                    noPerm(sender);
                    return true;
                }
                plugin.reload();
                sender.sendMessage(manager.prefix() + manager.msg("commands.reload"));
                return true;
            }
            case "givebook" -> {
                if (!sender.hasPermission("ecoenchants.command.givebook")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(manager.prefix() + manager.color("&c用法：/ecoenchants givebook <附魔键> <等级> [玩家]"));
                    return true;
                }
                org.bukkit.enchantments.Enchantment ench = Enchants.get(args[1].toLowerCase());
                if (ench == null) {
                    sender.sendMessage(manager.prefix() + manager.color("&c找不到附魔 " + args[1] + "。"));
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(manager.prefix() + manager.color("&c等级必须是数字。"));
                    return true;
                }
                if (level < 1 || level > ench.getMaxLevel()) {
                    sender.sendMessage(manager.prefix() + manager.color("&c等级需在 1-" + ench.getMaxLevel() + " 之间。"));
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 3 ? args[3] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.bookOf(ench, level));
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已将附魔书 " + args[1] + " " + level + " 给予 " + target.getName() + "。"));
                return true;
            }
            default -> sender.sendMessage(manager.prefix() + manager.msg("commands.invalid-args"));
        }
        return true;
    }

    private Player resolvePlayer(CommandSender sender, String name) {
        if (name != null) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(manager.prefix() + manager.msg("commands.player-not-found")
                        .replace("%player%", name));
                return null;
            }
            return p;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(manager.prefix() + manager.msg("commands.player-only"));
        return null;
    }

    private void noPerm(CommandSender sender) {
        sender.sendMessage(manager.prefix() + manager.msg("commands.no-permission"));
    }
}
