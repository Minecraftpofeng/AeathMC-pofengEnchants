package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * v1.4.6.8：管理员（OP 或拥有 ecoenchants.command.update 权限）进入游戏时，
 * 自动执行一次更新检测，并把最新 GitHub 版本链接与 SpigotMC 链接发给该管理员。
 * 普通玩家不触发，避免刷屏与无谓的网络请求。
 */
public final class UpdateNotifyListener implements Listener {

    private final EcoClonePlugin plugin;

    public UpdateNotifyListener(EcoClonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!p.isOp() && !p.hasPermission("ecoenchants.command.update")) {
            return;
        }
        // 延后 2 秒，等玩家完全进入游戏再发送，避免被登录消息刷掉
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.checkUpdate(p), 40L);
    }
}
