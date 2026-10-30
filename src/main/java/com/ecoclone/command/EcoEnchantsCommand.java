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
 * /aeath 主指令，子命令与 EcoEnchants 保持一致：
 *   /aeath giverandombook <player>
 *   /aeath randomenchant [player]
 *   /aeath reload
 *   /aeath givebook <附魔键> <等级> [player]
 *   /aeath gui
 *   /aeath scroll <附魔键> <等级> [player]
 *   /aeath wipe [player]
 *   /aeath artifact [player]
 *   /aeath admin [player]   （管理员之刃：创世神刃）
 *   /aeath enchant <附魔> <等级> [player]  （可突破原版附魔等级上限）
 *   /aeath help             （全部指令与附魔/宝石语法）
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
                    sender.sendMessage(manager.prefix() + manager.color("&c用法：/aeath givebook <附魔键> <等级> [玩家]"));
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
            case "gui" -> {
                if (!sender.hasPermission("ecoenchants.command.gui")) {
                    noPerm(sender);
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(manager.prefix() + manager.msg("commands.player-only"));
                    return true;
                }
                plugin.guiListener().open(player);
                return true;
            }
            case "scroll" -> {
                if (!sender.hasPermission("ecoenchants.command.scroll")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(manager.prefix() + manager.color("&c用法：/aeath scroll <附魔键> <等级> [玩家]"));
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
                target.getInventory().addItem(manager.scrollOf(ench, level));
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已将附魔卷轴 " + args[1] + " " + level + " 给予 " + target.getName() + "。"));
                return true;
            }
            case "wipe" -> {
                if (!sender.hasPermission("ecoenchants.command.wipe")) {
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
                target.getInventory().setItemInMainHand(manager.stripCustom(held));
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已清除 " + target.getName() + " 手中物品的全部自定义附魔。"));
                return true;
            }
            case "artifact" -> {
                if (!sender.hasPermission("ecoenchants.command.artifact")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.artifactSword());
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已将寰宇支配之剑·最终幻想给予 " + target.getName() + "。"));
                return true;
            }
            case "admin" -> {
                if (!sender.hasPermission("ecoenchants.command.admin")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.adminSword());
                sender.sendMessage(manager.prefix()
                        + manager.color("&c已将创世神刃·管理之怒给予 " + target.getName() + "。"));
                return true;
            }
            case "rainbow" -> {
                if (!sender.hasPermission("ecoenchants.command.rainbow")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.rainbowBlade());
                sender.sendMessage(manager.prefix()
                        + manager.color("&b已将彩虹猫之刃给予 " + target.getName() + "。"));
                return true;
            }
            case "aether" -> {
                if (!sender.hasPermission("ecoenchants.command.aether")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                int stage = 1;
                if (args.length > 2) {
                    try {
                        stage = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                target.getInventory().addItem(manager.aetherBlade(stage));
                sender.sendMessage(manager.prefix()
                        + manager.color("&d已将以太之剑·" + manager.aetherName(stage)
                        + "（阶段 " + stage + "/5）给予 " + target.getName() + "。"));
                return true;
            }
            case "dawn" -> {
                if (!sender.hasPermission("ecoenchants.command.dawn")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.dawnBlade());
                sender.sendMessage(manager.prefix()
                        + manager.color("&6已将晨曦破晓剑给予 " + target.getName() + "。"));
                return true;
            }
            case "abyss" -> {
                if (!sender.hasPermission("ecoenchants.command.abyss")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.abyssBlade());
                sender.sendMessage(manager.prefix()
                        + manager.color("&5已将暗渊噬魂剑给予 " + target.getName() + "。"));
                return true;
            }
            case "hoe" -> {
                if (!sender.hasPermission("ecoenchants.command.hoe")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.hoeArtifact());
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已将万象生息 · 丰饶之锄给予 " + target.getName() + "。"));
                return true;
            }
            case "wrath" -> {
                if (!sender.hasPermission("ecoenchants.command.wrath")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 1 ? args[1] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(manager.wrathBlade());
                sender.sendMessage(manager.prefix()
                        + manager.color("&c已将七宗罪 · 暴怒「嗜血重生」给予 " + target.getName() + "。"));
                return true;
            }
            case "enchant" -> {                if (!sender.hasPermission("ecoenchants.command.enchant")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(manager.prefix() + manager.color(
                            "&c用法：/aeath enchant <附魔> <等级> [玩家]  （可突破原版上限，如 sharpness 256）"));
                    return true;
                }
                org.bukkit.enchantments.Enchantment ench = Enchants.get(args[1].toLowerCase());
                if (ench == null) {
                    ench = org.bukkit.enchantments.Enchantment.getByKey(
                            org.bukkit.NamespacedKey.minecraft(args[1].toLowerCase()));
                }
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
                if (level < 1) {
                    sender.sendMessage(manager.prefix() + manager.color("&c等级必须至少为 1。"));
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 3 ? args[3] : null);
                if (target == null) {
                    return true;
                }
                ItemStack held = target.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    sender.sendMessage(manager.prefix() + manager.msg("commands.no-item"));
                    return true;
                }
                held.addUnsafeEnchantment(ench, level);
                target.getInventory().setItemInMainHand(held);
                sender.sendMessage(manager.prefix() + manager.color(
                        "&a已为 " + target.getName() + " 手持物品附加 " + args[1] + " " + level + "。"));
                return true;
            }
            case "gem" -> {
                if (!sender.hasPermission("ecoenchants.command.gem")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(manager.prefix() + manager.color(
                            "&c用法：/aeath gem <宝石> [数量] [玩家]   （宝石清单见 /aeath gems）"));
                    return true;
                }
                com.ecoclone.GemManager.GemDef d = plugin.gemManager().get(args[1]);
                if (d == null) {
                    sender.sendMessage(manager.prefix() + manager.color("&c找不到宝石 " + args[1] + "。"));
                    return true;
                }
                int amount = args.length > 2 ? parseInt(args[2], 1) : 1;
                Player target = resolvePlayer(sender, args.length > 3 ? args[3] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(plugin.gemManager().gemOf(d.key, 1, amount));
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已将宝石 " + d.name + " Lv.1 ×" + amount + " 给予 " + target.getName() + "。"));
                return true;
            }
            case "gemlv" -> {
                if (!sender.hasPermission("ecoenchants.command.gem")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(manager.prefix() + manager.color(
                            "&c用法：/aeath gemlv <宝石> <等级> [玩家]   （等级 1-100）"));
                    return true;
                }
                com.ecoclone.GemManager.GemDef d = plugin.gemManager().get(args[1]);
                if (d == null) {
                    sender.sendMessage(manager.prefix() + manager.color("&c找不到宝石 " + args[1] + "。"));
                    return true;
                }
                int lv = parseInt(args[2], 1);
                lv = Math.max(1, Math.min(lv, com.ecoclone.GemManager.MAX_LV));
                Player target = resolvePlayer(sender, args.length > 3 ? args[3] : null);
                if (target == null) {
                    return true;
                }
                target.getInventory().addItem(plugin.gemManager().gemOf(d.key, lv, 1));
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已将宝石 " + d.name + " Lv." + lv + " §8[" + com.ecoclone.GemManager.tierName(lv)
                        + "]&a 给予 " + target.getName() + "。"));
                return true;
            }
            case "socket" -> {
                if (!sender.hasPermission("ecoenchants.command.socket")) {
                    noPerm(sender);
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 2 ? args[2] : null);
                if (target == null) {
                    return true;
                }
                ItemStack held = target.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    sender.sendMessage(manager.prefix() + manager.msg("commands.no-item"));
                    return true;
                }
                if (plugin.gemManager().locked(held)) {
                    sender.sendMessage(manager.prefix() + manager.color("&c该物品不支持开槽（书/卷轴/宝石）。"));
                    return true;
                }
                int count = args.length > 1 ? parseInt(args[1], 1) : 1;
                int base = plugin.gemManager().slotsOf(held);
                int add = Math.max(1, count);
                int total = Math.min(base + add, com.ecoclone.GemManager.MAX_SLOTS);
                target.getInventory().setItemInMainHand(plugin.gemManager().setSlots(held, total));
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已为装备开槽至 &f" + total + "&a/&f" + com.ecoclone.GemManager.MAX_SLOTS + "。"));
                return true;
            }
            case "gems" -> {
                if (!sender.hasPermission("ecoenchants.command.gems")) {
                    noPerm(sender);
                    return true;
                }
                sender.sendMessage(manager.prefix() + manager.color("&6宝石一览："));
                for (com.ecoclone.GemManager.GemDef g : plugin.gemManager().defs().values()) {
                    String tgt = "weapon".equals(g.target) ? "武器" : "armor".equals(g.target) ? "护甲" : "通用";
                    sender.sendMessage(manager.color(
                            " &f" + g.key + " &7- &6" + g.name + " §7(" + tgt + ", Lv.1-"
                                    + com.ecoclone.GemManager.MAX_LV + ")"));
                    sender.sendMessage(manager.color("   &8" + g.desc));
                }
                sender.sendMessage(manager.color("&7用 &e/aeath gem <宝石> [数量] [玩家] &7获得，右键或背包点击镶嵌。"));
                sender.sendMessage(manager.color("&7发指定等级用 &e/aeath gemlv <宝石> <等级1-100> [玩家]&7。"));
                return true;
            }
            case "version" -> {
                sender.sendMessage(manager.prefix() + manager.color("&6AeathMC-pofengEnchants 版本信息："));
                sender.sendMessage(manager.color("  &7插件版本 Plugin Version: &f" + plugin.getDescription().getVersion()));
                String bv = org.bukkit.Bukkit.getBukkitVersion();
                sender.sendMessage(manager.color("  &7服务端版本 Server Version: &f" + bv));
                sender.sendMessage(manager.color("  &7支持范围 Supported: &f1.21.x - 1.26.3"));
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(bv);
                if (m.find()) {
                    int[] v = { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            m.group(3) == null ? 0 : Integer.parseInt(m.group(3)) };
                    boolean ok = true;
                    if (v[0] < 1 || (v[0] == 1 && v[1] < 21)) ok = false;
                    if (v[0] > 1 || (v[0] == 1 && v[1] > 26)
                            || (v[0] == 1 && v[1] == 26 && v[2] > 3)) ok = false;
                    sender.sendMessage(manager.color("  &7检测结果 Result: &"
                            + (ok ? "a兼容 Compatible" : "c超出支持范围 Out of range")));
                } else {
                    sender.sendMessage(manager.color("  &c无法解析服务端版本。"));
                }
                return true;
            }
            case "update" -> {
                sender.sendMessage(manager.prefix()
                        + manager.color("&a已发起更新检查，结果请看控制台（[AeathMC-pofengEnchan] 前缀）。"));
                plugin.checkUpdate(true);
                return true;
            }
            case "armor" -> {
                if (!sender.hasPermission("ecoenchants.command.artifact")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(manager.prefix() + manager.color(
                            "&c用法：/aeath armor <helmet|chest|legs|boots|set> [玩家]"));
                    return true;
                }
                Player target = resolvePlayer(sender, args.length > 2 ? args[2] : null);
                if (target == null) {
                    return true;
                }
                if (args[1].equalsIgnoreCase("set")) {
                    for (String s : new String[]{"helmet", "chest", "legs", "boots"}) {
                        target.getInventory().addItem(manager.armorArtifact(s));
                    }
                    sender.sendMessage(manager.prefix()
                            + manager.color("&a已给予 " + target.getName() + " 整套无尽防具（4 件）。"));
                } else {
                    String p = args[1].toLowerCase();
                    if (!(p.equals("helmet") || p.equals("chest") || p.equals("legs") || p.equals("boots"))) {
                        sender.sendMessage(manager.prefix()
                                + manager.color("&c部位需为 helmet / chest / legs / boots / set。"));
                        return true;
                    }
                    target.getInventory().addItem(manager.armorArtifact(p));
                    sender.sendMessage(manager.prefix()
                            + manager.color("&a已给予 " + target.getName() + " " + manager.armorName(p) + "。"));
                }
                return true;
            }
            case "geminfo" -> {
                if (!sender.hasPermission("ecoenchants.command.geminfo")) {
                    noPerm(sender);
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(manager.prefix() + manager.msg("commands.player-only"));
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    sender.sendMessage(manager.prefix() + manager.msg("commands.no-item"));
                    return true;
                }
                int slots = plugin.gemManager().slotsOf(held);
                var gemsList = plugin.gemManager().gemsOf(held);
                sender.sendMessage(manager.prefix() + manager.color("&6宝石槽位：&f" + slots));
                if (gemsList.isEmpty()) {
                    sender.sendMessage(manager.color("&8◇ 未镶嵌任何宝石。"));
                } else {
                    for (com.ecoclone.GemManager.SlotEntry ge : gemsList) {
                        var gd = plugin.gemManager().get(ge.key);
                        sender.sendMessage(manager.color(" &f" + (gd == null ? ge.key : gd.name)
                                + " §7Lv." + ge.level + " §8[" + com.ecoclone.GemManager.tierName(ge.level) + "]"));
                    }
                }
                return true;
            }
            case "help" -> {
                help(sender);
                return true;
            }
            default -> sender.sendMessage(manager.prefix() + manager.msg("commands.invalid-args"));
        }
        return true;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Player resolvePlayer(CommandSender sender, String name) {        if (name != null) {
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

    /** /aeath help：列出全部指令语法与附魔/宝石键 */
    private void help(CommandSender sender) {
        sender.sendMessage(manager.prefix() + manager.color("&6AeathMC-pofengEnchants 指令帮助 / Help"));
        sender.sendMessage(manager.color("&7━━━━━━ 指令 / Commands ━━━━━━"));
        sender.sendMessage(manager.color(" &e/aeath help &7- 本帮助"));
        sender.sendMessage(manager.color(" &e/aeath giverandombook [玩家] &7- 随机附魔书"));
        sender.sendMessage(manager.color(" &e/aeath randomenchant [玩家] &7- 随机附魔手持物品"));
        sender.sendMessage(manager.color(" &e/aeath givebook <附魔键> <等级> [玩家] &7- 发放附魔书"));
        sender.sendMessage(manager.color(" &e/aeath scroll <附魔键> <等级> [玩家] &7- 发放附魔卷轴"));
        sender.sendMessage(manager.color(" &e/aeath enchant <附魔> <等级> [玩家] &7- 直接上附魔(可突破原版上限)"));
        sender.sendMessage(manager.color(" &e/aeath gui &7- 打开附魔 GUI"));
        sender.sendMessage(manager.color(" &e/aeath wipe [玩家] &7- 洗掉手持物品的自定义附魔"));
        sender.sendMessage(manager.color(" &e/aeath reload &7- 重载配置"));
        sender.sendMessage(manager.color(" &e/aeath artifact [玩家] &7- 寰宇支配之剑·最终幻想"));
        sender.sendMessage(manager.color(" &e/aeath admin [玩家] &7- 创世神刃·管理之怒"));
        sender.sendMessage(manager.color(" &e/aeath rainbow [玩家] &7- 彩虹猫之刃"));
        sender.sendMessage(manager.color(" &e/aeath aether [玩家] <阶段1-5> &7- 以太系列五阶剑"));
        sender.sendMessage(manager.color(" &e/aeath dawn [玩家] &7- 晨曦破晓剑"));
        sender.sendMessage(manager.color(" &e/aeath abyss [玩家] &7- 暗渊噬魂剑"));
        sender.sendMessage(manager.color(" &e/aeath hoe [玩家] &7- 大楚兴陈胜王之锄"));
        sender.sendMessage(manager.color(" &e/aeath wrath [玩家] &7- 七宗罪·暴怒「嗜血重生」"));
        sender.sendMessage(manager.color(" &e/aeath armor <helmet|chest|legs|boots|set> [玩家] &7- 无尽防具"));
        sender.sendMessage(manager.color(" &e/aeath gem <宝石> [数量] [玩家] &7- 获得宝石"));
        sender.sendMessage(manager.color(" &e/aeath gemlv <宝石> <等级1-100> [玩家] &7- 指定等级宝石"));
        sender.sendMessage(manager.color(" &e/aeath socket [数量] [玩家] &7- 为手持装备开槽"));
        sender.sendMessage(manager.color(" &e/aeath gems &7- 全部宝石一览"));
        sender.sendMessage(manager.color(" &e/aeath geminfo &7- 查看手持装备宝石槽"));
        sender.sendMessage(manager.color(" &e/aeath version &7- 版本信息"));
        sender.sendMessage(manager.color(" &e/aeath update &7- 检测更新"));
        sender.sendMessage(manager.color("&7━━━━━━ 附魔键 / Enchant Keys ━━━━━━"));
        sender.sendMessage(manager.color("&7用法：&e/aeath givebook <键> <等级> [玩家]&7 或 &e/aeath scroll <键> <等级> [玩家]"));
        StringBuilder melee = new StringBuilder(), range = new StringBuilder(),
                armor = new StringBuilder(), tool = new StringBuilder(), curse = new StringBuilder(), other = new StringBuilder();
        java.util.List<String> targets;
        for (com.ecoclone.EnchantDef def : Enchants.definitions()) {
            String line;
            if (def.curse) {
                line = curse.length() == 0 ? def.key : curse.append(" ").toString() + def.key;
                curse.setLength(0); curse.append(line);
            } else {
                targets = def.targets.stream().map(t -> t.toString()).collect(java.util.stream.Collectors.toList());
                String j = String.join(",", targets);
                StringBuilder b = j.contains("sword") ? melee : j.contains("bow") || j.contains("crossbow") ? range
                        : j.contains("helmet") || j.contains("chestplate") || j.contains("leggings") || j.contains("boots") ? armor
                        : (j.contains("pickaxe") || j.contains("axe") || j.contains("shovel") || j.contains("hoe")) ? tool : other;
                String cur = b.toString();
                b.setLength(0); b.append(cur.isEmpty() ? def.key : cur + " " + def.key);
            }
        }
        sender.sendMessage(manager.color(" &7近战 / Melee: &f" + melee));
        sender.sendMessage(manager.color(" &7弓弩 / Ranged: &f" + range));
        sender.sendMessage(manager.color(" &7护甲 / Armor: &f" + armor));
        sender.sendMessage(manager.color(" &7工具 / Tool: &f" + tool));
        sender.sendMessage(manager.color(" &7诅咒 / Curse: &f" + curse));
        sender.sendMessage(manager.color(" &7通用 / Other: &f" + other));
        sender.sendMessage(manager.color("&7━━━━━━ 宝石 / Gems ━━━━━━"));
        sender.sendMessage(manager.color("&7用法：&e/aeath gem <宝石> [数量] [玩家]&7，完整清单见 &e/aeath gems"));
        sender.sendMessage(manager.color("&7宝石等级上限 &f100&7，共 &f10&7 档（凡品/精良/稀有/史诗/传说/神话/不朽/神王/混沌/究极·超越）。"));
    }
}
