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
import com.ecoclone.listener.ArmorArtifactListener;
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
        org.bukkit.command.Command existing = cm.getCommand(name);
        if (existing != null) {
            if (existing instanceof org.bukkit.command.PluginCommand pc) {
                pc.setExecutor(executor);
                if (executor instanceof org.bukkit.command.TabCompleter tc) {
                    pc.setTabCompleter(tc);
                }
            }
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
            detectVersion();
            checkUpdate(true);
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

            EcoEnchantsCommand cmd = new EcoEnchantsCommand(this, manager);
            registerCommand("aeath", cmd);
            org.bukkit.command.PluginCommand pc = getCommand("aeath");
            if (pc != null) {
                pc.setTabCompleter(cmd);
            }
            registerCommand("enchantinfo", new EnchantInfoCommand(this, manager));
            hookSoftDependencies();

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
            getServer().getPluginManager().registerEvents(new ArmorArtifactListener(this, manager), this);
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

    /**
     * 软依赖集成主流插件：PlaceholderAPI / Vault / ProtocolLib。
     * 已在 paper-plugin.yml 的 softdepend 声明。此处启动时探测已装插件并打日志；
     * 未安装则静默跳过，不影响启动。
     */
    private void hookSoftDependencies() {
        String[] soft = {"PlaceholderAPI", "Vault", "ProtocolLib"};
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String name : soft) {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null) {
                found.add(name + " v" + p.getDescription().getVersion());
            }
        }
        if (found.isEmpty()) {
            getLogger().info("未检测到软依赖插件（PlaceholderAPI/Vault/ProtocolLib），可选集成跳过。");
        } else {
            getLogger().info("软依赖插件就绪 Soft-dependencies loaded: " + String.join(", ", found));
        }
    }

    /** 启动时检测服务端 MC 版本是否在支持范围 1.21.x - 1.26.3 内 */
    public boolean detectVersion() {
        int[] v = parseMcVersion(Bukkit.getBukkitVersion());
        if (v == null) {
            getLogger().warning("版本检测失败：无法解析服务端版本 " + Bukkit.getBukkitVersion());
            return false;
        }
        boolean ok = inSupportedRange(v);
        String disp = v[0] + "." + v[1] + (v[2] > 0 ? "." + v[2] : "");
        console("服务端版本检测 (Server version check): MC " + disp
                + " - " + (ok ? "兼容 Compatible" : "超出支持范围 Out of range")
                + " | 支持范围 Supported: 1.21.x - 1.26.3");
        if (!ok) {
            console("警告 WARNING: 当前 MC " + disp + " 不在支持范围 1.21.x-1.26.3 内，部分功能可能异常。");
        }
        return ok;
    }

    /** 带统一前缀的控制台输出（前缀固定，不动插件名以免破坏 PDC 数据） */
    private void console(String msg) {
        getServer().getConsoleSender().sendMessage("§7[AeathMC-pofengEnchan] §f" + msg);
    }

    /** 检测 GitHub 最新 release，有新版时提醒控制台与在线玩家 */
    public void checkUpdate(boolean notifyPlayers) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            String latest = fetchLatestTag();
            String cur = getDescription().getVersion();
            if (latest == null) {
                console("更新检测失败：无法访问 GitHub API（检查网络/加速器）。");
                return;
            }
            boolean hasNew = !latest.equalsIgnoreCase(cur);
            console("更新检查 Update check: 当前 Current " + cur
                    + " | 最新 Latest " + latest
                    + (hasNew ? " - 有更新 New version available!" : " - 已是最新 Up to date"));
            if (hasNew && notifyPlayers) {
                getServer().getScheduler().runTask(this, () ->
                        Bukkit.broadcastMessage("§6[AeathMC-pofengEnchan] §e检测到新版本 §f" + latest
                                + " §e（当前 " + cur + "），请到 GitHub 下载更新插件！"));
            }
        });
    }

    private String fetchLatestTag() {
        try {
            java.net.URL u = new java.net.URL(
                    "https://api.github.com/repos/Minecraftpofeng/AeathMC-pofengEnchants/releases/latest");
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "AeathMC-pofengEnchants");
            int code = c.getResponseCode();
            if (code != 200) {
                c.disconnect();
                return null;
            }
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            c.disconnect();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(sb.toString());
            return m.find() ? m.group(1) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static int[] parseMcVersion(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(s);
        if (!m.find()) return null;
        return new int[]{
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3))
        };
    }

    private static boolean inSupportedRange(int[] v) {
        if (compareVersion(v, 1, 21, 0) < 0) return false;
        return compareVersion(v, 1, 26, 3) <= 0;
    }

    private static int compareVersion(int[] a, int x, int y, int z) {
        int[] b = {x, y, z};
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        return 0;
    }

    /** 启动鸣谢弹幕：控制台 + 在线玩家各弹两行（Adventure 组件，16 进制颜色，中文后附英文） */
    private void sendSplash() {
        Component line1 = Component.text("鸣谢 / Thanks to: ", TextColor.fromHexString("#FFD700"))
                .append(Component.text("SKY_qwq  clowm贺  shadow_agent  H1111111  7gzztrgv  ╮(─▽─)╭：）： ⁧♡~喵呜⁧", TextColor.fromHexString("#00E5FF")));
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
