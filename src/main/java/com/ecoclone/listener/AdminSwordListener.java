package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 管理员之刃：创世神刃 —— 管理之怒秒杀 + Him 神力（飞行/时间） */
public class AdminSwordListener implements Listener {
    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    /** 记录由本插件开启飞行的玩家（避免误关他人飞行） */
    private final Set<UUID> pluginFlying = new HashSet<>();

    public AdminSwordListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickHimPower, 0L, 20L);
    }

    /** Him 神力：主手持剑且为 OP -> 自动开飞行 + 世界午夜；离手 -> 关闭飞行 + 世界白天 */
    private void tickHimPower() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (manager.isAdminSword(held) && p.isOp()) {
                pluginFlying.add(p.getUniqueId());
                p.setAllowFlight(true);
                if (!p.isFlying()) {
                    p.setFlying(true);
                }
                World w = p.getWorld();
                if (w.getTime() != 18000) {
                    w.setTime(18000); // 午夜
                }
            } else {
                // 只有之前由本插件开启飞行的玩家，离手后才关闭并恢复白天
                if (pluginFlying.remove(p.getUniqueId())) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    World w = p.getWorld();
                    if (w.getTime() != 1000) {
                        w.setTime(1000); // 白天
                    }
                }
            }
        }
    }

    /** 管理之怒：OP 持剑命中 -> 扣血动画 + 召唤闪电(不起火) + 秒杀 + 半径1000格玩家反胃/失明5秒 + 红色粗体标题 */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!attacker.isOp()) {
            return;
        }
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!manager.isAdminSword(held)) {
            return;
        }
        Entity victim = e.getEntity();
        if (!(victim instanceof LivingEntity living)) {
            return;
        }
        World world = victim.getWorld();
        org.bukkit.Location loc = victim.getLocation();
        // 扣血动画
        world.spawnParticle(Particle.HEART, loc.clone().add(0, 1.2, 0), 30, 0.4, 0.4, 0.4, 0.01);
        world.playSound(loc, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);
        // 召唤闪电（不起火，仅视觉效果）
        world.strikeLightningEffect(loc);
        // 秒杀
        living.setHealth(0);
        // 半径1000格内玩家：反胃 + 失明 5 秒 + 红色粗体放大标题
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) {
                continue;
            }
            if (p.getLocation().distanceSquared(loc) > 1000.0 * 1000.0) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1));
            p.sendTitle("§c§l让整个世界为之颤抖吧!", "", 10, 60, 20);
        }
        e.setDamage(0);
    }
}
