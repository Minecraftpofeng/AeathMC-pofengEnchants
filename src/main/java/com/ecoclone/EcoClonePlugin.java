package com.ecoclone;

import com.ecoclone.command.EcoEnchantsCommand;
import com.ecoclone.command.EnchantInfoCommand;
import com.ecoclone.listener.ArmorListener;
import com.ecoclone.listener.CombatListener;
import com.ecoclone.listener.DeathListener;
import com.ecoclone.listener.MiningListener;
import com.ecoclone.listener.ProjectileListener;
import com.ecoclone.listener.UtilityListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EcoClonePlugin extends JavaPlugin {

    private EnchantManager manager;

    private void registerCommand(String name, CommandExecutor executor) {
        CommandMap cm = getServer().getCommandMap();
        if (cm.getCommand(name) != null) {
            // 已被 paper-plugin.yml 或别处注册，直接换执行器
            getCommand(name).setExecutor(executor);
            return;
        }
        BukkitCommand cmd = new BukkitCommand(name) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return executor.onCommand(sender, this, commandLabel, args);
            }
        };
        cm.register("ecoclone", cmd);
        getLogger().info("已注册指令 /" + name);
    }

    @Override
    public void onEnable() {
        try {
            getLogger().info("正在启用 AeathMC-pofengEnchants...");
            manager = new EnchantManager(this);
            manager.load();
            int n = Enchants.loadFromRegistry();
            getLogger().info("已启用 " + n + " 个自定义附魔。");
            if (!EcoCloneBootstrap.registrationErrors().isEmpty()) {
                java.util.List<String> errs = EcoCloneBootstrap.registrationErrors();
                getLogger().severe("有 " + errs.size() + " 个附魔注册失败，详见 plugins/AeathMC-pofengEnchants/registration-errors.txt");
                getLogger().severe("第一条失败原因: " + errs.get(0));
                getServer().getScheduler().runTaskLater(this,
                        () -> Bukkit.broadcastMessage("§c[AeathMC] " + errs.size()
                                + " 个附魔注册失败，原因已写入 registration-errors.txt，请截图发给腐竹"), 20L);
            }

            registerCommand("ecoenchants", new EcoEnchantsCommand(this, manager));
            registerCommand("enchantinfo", new EnchantInfoCommand(this, manager));

            getServer().getPluginManager().registerEvents(new CombatListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new ProjectileListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new MiningListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new ArmorListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new UtilityListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new DeathListener(this, manager), this);
        } catch (Throwable t) {
            getLogger().severe("AeathMC-pofengEnchants 启用失败，原因如下:");
            t.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        UtilityListener.clearAll(Bukkit.getServer());
    }

    public EnchantManager manager() {
        return manager;
    }

    public void reload() {
        manager.load();
        UtilityListener.reapply(Bukkit.getServer(), this);
    }
}
