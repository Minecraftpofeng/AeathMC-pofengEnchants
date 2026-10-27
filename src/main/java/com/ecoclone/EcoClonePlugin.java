package com.ecoclone;

import com.ecoclone.command.EcoEnchantsCommand;
import com.ecoclone.command.EnchantInfoCommand;
import com.ecoclone.listener.ArmorListener;
import com.ecoclone.listener.AdminSwordListener;
import com.ecoclone.listener.ArtifactListener;
import com.ecoclone.listener.CombatListener;
import com.ecoclone.listener.DeathListener;
import com.ecoclone.listener.GuiListener;
import com.ecoclone.listener.MiningListener;
import com.ecoclone.listener.ProjectileListener;
import com.ecoclone.listener.RainbowBladeListener;
import com.ecoclone.listener.AetherBladeListener;
import com.ecoclone.listener.DawnBladeListener;
import com.ecoclone.listener.AbyssBladeListener;
import com.ecoclone.listener.HoeArtifactListener;
import com.ecoclone.listener.WrathBladeListener;
import com.ecoclone.listener.NewEnchantListener;
import com.ecoclone.listener.UtilityListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EcoClonePlugin extends JavaPlugin {

    private EnchantManager manager;
    private GuiListener guiListener;
    private ArtifactListener artifactListener;
    private NewEnchantListener newEnchantListener;
    private GemManager gemManager;
    private GemListener gemListener;

    public GuiListener guiListener() {
        return guiListener;
    }

    public ArtifactListener artifactListener() {
        return artifactListener;
    }

    public GemManager gemManager() {
        return gemManager;
    }

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
            sendSplash();
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
            guiListener = new GuiListener(this, manager);
            getServer().getPluginManager().registerEvents(guiListener, this);
            artifactListener = new ArtifactListener(this, manager);
            getServer().getPluginManager().registerEvents(artifactListener, this);
            getServer().getPluginManager().registerEvents(new AdminSwordListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new RainbowBladeListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new AetherBladeListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new DawnBladeListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new AbyssBladeListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new HoeArtifactListener(this, manager), this);
            getServer().getPluginManager().registerEvents(new WrathBladeListener(this, manager), this);
            newEnchantListener = new NewEnchantListener(this, manager);
            getServer().getPluginManager().registerEvents(newEnchantListener, this);

            gemManager = new GemManager(this);
            gemListener = new GemListener(this);
            getServer().getPluginManager().registerEvents(gemListener, this);
            gemListener.startTick();
        } catch (Throwable t) {
            getLogger().severe("AeathMC-pofengEnchants 启用失败，原因如下:");
            t.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        UtilityListener.clearAll(Bukkit.getServer());
    }

    /** 启动鸣谢弹幕：控制台 + 在线玩家各弹两行（Adventure 组件，16 进制颜色，中文后附英文） */
    private void sendSplash() {
        Component line1 = Component.text("鸣谢 / Thanks to: ", TextColor.fromHexString("#FFD700"))
                .append(Component.text("SKY_qwq  clowm贺  shadow_agent  H1111111  7gzztrgv", TextColor.fromHexString("#00E5FF")));
        Component line2 = Component.text("——长风破浪会有时 ", TextColor.fromHexString("#7DF9FF"))
                .append(Component.text("(Someday I'll ride the wind and break the waves) ", TextColor.fromHexString("#AAAAAA")))
                .append(Component.text("[AeathMC 总开发 / Lead Developer ", TextColor.fromHexString("#FFD700")))
                .append(Component.text("Minecraft_pofeng", TextColor.fromHexString("#FFAA00")))
                .append(Component.text("]", TextColor.fromHexString("#FFD700")));
        getServer().getConsoleSender().sendMessage(line1);
        getServer().getConsoleSender().sendMessage(line2);
        getServer().getScheduler().runTaskLater(this, () -> {
            Bukkit.broadcast(line1);
            Bukkit.broadcast(line2);
        }, 20L);
    }

    public EnchantManager manager() {
        return manager;
    }

    public void reload() {
        manager.load();
        manager.clearCooldowns();
        if (newEnchantListener != null) {
            newEnchantListener.clearState();
        }
        UtilityListener.reapply(Bukkit.getServer(), this);
    }
}
