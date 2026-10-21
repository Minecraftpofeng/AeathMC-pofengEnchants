package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 近战附魔效果：Beheading / Criticals / First Strike / Bleed / Leeching / Thor /
 * Supercritical / Toxic / Wither Aspect / Razor。
 */
public final class CombatListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    /** 正在流血的实体 (uuid -> 剩余 tick) */
    private static final Map<UUID, Integer> BLEEDING = new ConcurrentHashMap<>();

    public CombatListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        // 流血持续掉血任务
        new BukkitRunnable() {
            @Override
            public void run() {
                BLEEDING.forEach((id, ticks) -> {
                    Entity e = plugin.getServer().getEntity(id);
                    if (e == null) {
                        BLEEDING.remove(id);
                    } else if (e.isDead()) {
                        BLEEDING.remove(id);
                    } else if (e instanceof LivingEntity le) {
                        le.damage(1.0);
                        BLEEDING.put(id, ticks - 20);
                        if (ticks - 20 <= 0) {
                            BLEEDING.remove(id);
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }

        double dmg = event.getDamage();
        boolean changed = false;

        // Criticals：跳跃暴击时额外伤害
        int critLvl = Enchants.level(held, "criticals");
        if (critLvl > 0 && attacker.isOnGround() == false && attacker.getVelocity().getY() < 0) {
            dmg *= (1 + 0.10 * critLvl);
            changed = true;
        }

        // First Strike：目标满血时增伤
        int fsLvl = Enchants.level(held, "first_strike");
        if (fsLvl > 0 && victim.getHealth() >= victim.getMaxHealth() - 0.001) {
            dmg *= (1 + 0.15 * fsLvl);
            changed = true;
        }

        // Razor：大幅增伤
        int razor = Enchants.level(held, "razor");
        if (razor > 0) {
            dmg *= (1 + 0.25 * razor);
            changed = true;
        }

        // Supercritical：极小概率暴击 3 倍
        int sup = Enchants.level(held, "supercritical");
        if (sup > 0 && Math.random() < 0.02 * sup) {
            dmg *= 3;
            changed = true;
        }

        if (changed) {
            event.setDamage(dmg);
        }

        // Leeching：吸血
        int leech = Enchants.level(held, "leeching");
        if (leech > 0 && victim instanceof LivingEntity) {
            double heal = event.getDamage() * 0.05 * leech;
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
        }

        // Bleed：概率流血
        int bleed = Enchants.level(held, "bleed");
        if (bleed > 0 && Math.random() < 0.1 * bleed) {
            BLEEDING.put(victim.getUniqueId(), 40 + bleed * 20); // 持续 2-4 秒 + 每级
        }

        // Thor：概率雷电
        int thor = Enchants.level(held, "thor");
        if (thor > 0 && Math.random() < 0.03 * thor
                && !manager.isOnCooldown(attacker.getUniqueId(), "thor")) {
            victim.getWorld().strikeLightning(victim.getLocation());
            manager.startCooldown(attacker.getUniqueId(), "thor", manager.cooldownSeconds());
        }

        // Toxic：概率中毒
        int toxic = Enchants.level(held, "toxic");
        if (toxic > 0 && Math.random() < 0.2 * toxic) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        }

        // Wither Aspect：概率凋零
        int wither = Enchants.level(held, "wither_aspect");
        if (wither > 0 && Math.random() < 0.15 * wither) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ItemStack held = killer.getInventory().getItemInMainHand();
        if (held == null) {
            return;
        }
        int beheading = Enchants.level(held, "beheading");
        if (beheading <= 0 || Math.random() >= 0.1 * (beheading + 1)) {
            return;
        }
        // 玩家：掉落带有其皮肤的玩家头颅
        if (event.getEntity() instanceof Player victim) {
            ItemStack h = new ItemStack(Material.PLAYER_HEAD);
            h.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(victim.getPlayerProfile()));
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), h);
            return;
        }
        // 生物：掉落对应材质的生物头颅（材质本身已决定贴图）
        Material head = headFor(event.getEntity().getType());
        if (head != null) {
            event.getEntity().getWorld().dropItemNaturally(
                    event.getEntity().getLocation(), new ItemStack(head));
        }
    }

    private static Material headFor(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case ZOMBIE, HUSK, DROWNED -> Material.ZOMBIE_HEAD;
            case SKELETON, STRAY -> Material.SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case PIGLIN, ZOMBIFIED_PIGLIN -> Material.PIGLIN_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            default -> null;
        };
    }
}
