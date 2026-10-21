package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 周期任务 + 工具类附魔：
 * Magnetic（磁铁）、Thrive（生命上限）、Spring（跳跃）、
 * Repairing（自动修复）、Indestructibility / Curse of Fragility（耐久）。
 */
public final class UtilityListener implements Listener {

    private static final NamespacedKey THRIVE_KEY = NamespacedKey.fromString("ecoclone:thrive");

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private final Map<UUID, Integer> appliedThrive = new ConcurrentHashMap<>();

    public UtilityListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    magnetic(player);
                    thrive(player);
                    spring(player);
                    repairing(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ---------- Magnetic：吸附掉落物与经验 ----------
    private void magnetic(Player player) {
        int level = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null) {
                level += Enchants.level(piece, "magnetic");
            }
        }
        if (level == 0) {
            return;
        }
        double radius = 3.0 + level;
        Vector center = player.getLocation().toVector();
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Item || e instanceof ExperienceOrb) {
                Vector dir = center.subtract(e.getLocation().toVector());
                e.setVelocity(e.getVelocity().add(dir.normalize().multiply(0.15)));
            }
        }
    }

    // ---------- Thrive：增加最大生命 ----------
    private void thrive(Player player) {
        int level = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null) {
                level += Enchants.level(piece, "thrive");
            }
        }
        Integer old = appliedThrive.get(player.getUniqueId());
        if (old != null && old.intValue() == level) {
            return;
        }
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) {
            return;
        }
        attr.getModifiers().stream()
                .filter(m -> m.getKey().equals(THRIVE_KEY))
                .forEach(attr::removeModifier);
        if (level > 0) {
            attr.addModifier(new AttributeModifier(THRIVE_KEY, 2.0 * level,
                    AttributeModifier.Operation.ADD_NUMBER));
            double max = player.getMaxHealth();
            if (player.getHealth() < max) {
                player.setHealth(max);
            }
        }
        appliedThrive.put(player.getUniqueId(), level);
    }

    // ---------- Spring：跳跃提升 ----------
    private void spring(Player player) {
        int level = 0;
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null) {
            level = Enchants.level(boots, "spring");
        }
        if (level > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, level - 1));
        }
    }

    // ---------- Repairing：不持有时自动修复 ----------
    private void repairing(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (item.equals(held)) {
                continue;
            }
            int level = Enchants.level(item, "repairing");
            if (level == 0) {
                continue;
            }
            short max = item.getType().getMaxDurability();
            short dmg = item.getDurability();
            if (dmg > 0) {
                item.setDurability((short) Math.max(0, dmg - level));
            }
        }
    }

    // ---------- 耐久：Indestructibility / Curse of Fragility ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        int indestructible = Enchants.level(item, "indestructibility");
        if (indestructible > 0) {
            event.setDamage(Math.max(0, (int) Math.ceil(event.getDamage() / (1.0 + indestructible))));
            return;
        }
        int fragile = Enchants.level(item, "curse_of_fragility");
        if (fragile > 0) {
            event.setDamage(event.getDamage() * (1 + fragile));
        }
    }

    // ---------- 静态清理 / 重算 ----------
    public static void clearAll(Server server) {
        for (Player player : server.getOnlinePlayers()) {
            AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                attr.getModifiers().stream()
                        .filter(m -> m.getKey().equals(THRIVE_KEY))
                        .forEach(attr::removeModifier);
            }
        }
    }

    public static void reapply(Server server, EcoClonePlugin plugin) {
        clearAll(server);
    }
}
