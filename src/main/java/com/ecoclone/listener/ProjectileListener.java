package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弓弩/投掷物附魔效果：Boss Hunter / Optics / Force / Zeus / Tripleshot。
 */
public final class ProjectileListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    private static final Set<EntityType> BOSSES = Set.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN, EntityType.GIANT);

    /** projectileId -> [force, optics, bossHunter, zeus, explosive, blaze, shockwave, homing] */
    private final Map<UUID, int[]> stats = new ConcurrentHashMap<>();

    /** 正在追踪目标的箭 */
    private final Set<UUID> homingArrows = ConcurrentHashMap.newKeySet();

    public ProjectileListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        // Homing：追踪箭周期微调方向
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : homingArrows) {
                    org.bukkit.entity.Entity e = plugin.getServer().getEntity(id);
                    if (!(e instanceof Arrow a) || a.isDead() || a.isOnGround()) {
                        homingArrows.remove(id);
                        continue;
                    }
                    LivingEntity target = nearestEnemy(a);
                    if (target != null) {
                        Vector dir = target.getLocation().toVector().subtract(a.getLocation().toVector());
                        double len = dir.length();
                        if (len < 0.01) {
                            continue;
                        }
                        Vector vel = a.getVelocity();
                        Vector newVel = vel.add(dir.normalize().multiply(0.15)).normalize().multiply(vel.length());
                        if (newVel.length() < 0.1) {
                            continue;
                        }
                        a.setVelocity(newVel);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private LivingEntity nearestEnemy(Arrow a) {
        double best = 20 * 20;
        LivingEntity bestE = null;
        for (org.bukkit.entity.Entity e : a.getNearbyEntities(20, 20, 20)) {
            if (e instanceof LivingEntity le && !le.isDead() && le != a.getShooter()) {
                double d = a.getLocation().distanceSquared(le.getLocation());
                if (d < best) {
                    best = d;
                    bestE = le;
                }
            }
        }
        return bestE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null) {
            return;
        }
        Projectile proj = (Projectile) event.getProjectile();
        stats.put(proj.getUniqueId(), new int[]{
                Enchants.level(bow, "force"),
                Enchants.level(bow, "optics"),
                Enchants.level(bow, "boss_hunter"),
                Enchants.level(bow, "zeus"),
                Enchants.level(bow, "explosive_arrow"),
                Enchants.level(bow, "blaze"),
                Enchants.level(bow, "shockwave"),
                Enchants.level(bow, "homing")
        });

        // Homing：登记追踪箭
        if (Enchants.level(bow, "homing") > 0) {
            homingArrows.add(proj.getUniqueId());
        }

        // Tripleshot：再射出两支略偏的箭
        int triple = Enchants.level(bow, "tripleshot");
        if (triple > 0 && event.getProjectile() instanceof Arrow base) {
            for (double angle : new double[]{-0.08, 0.08}) {
                Vector vel = base.getVelocity().clone().rotateAroundY(angle);
                Arrow extra = player.launchProjectile(Arrow.class, vel);
                extra.setShooter(player);
                extra.setPickupStatus(base.getPickupStatus());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile proj)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        int[] s = stats.remove(proj.getUniqueId());
        if (s == null) {
            return;
        }
        homingArrows.remove(proj.getUniqueId());
        if (!(proj.getShooter() instanceof Player player)) {
            return;
        }
        int force = s[0], optics = s[1], bossHunter = s[2], zeus = s[3], explosive = s[4],
                blaze = s[5], shockwave = s[6], homing = s[7];

        double dmg = event.getDamage();
        boolean changed = false;

        if (force > 0) {
            dmg *= (1 + 0.25 * force);
            changed = true;
        }
        if (optics > 0) {
            double dist = proj.getLocation().distance(victim.getLocation());
            dmg *= (1 + 0.05 * optics * dist);
            changed = true;
        }
        if (bossHunter > 0 && BOSSES.contains(victim.getType())) {
            dmg *= (1 + 0.15 * bossHunter);
            changed = true;
        }
        if (changed) {
            event.setDamage(dmg);
        }

        if (zeus > 0 && Math.random() < 0.03 * zeus
                && !manager.isOnCooldown(player.getUniqueId(), "zeus")) {
            victim.getWorld().strikeLightning(victim.getLocation());
            manager.startCooldown(player.getUniqueId(), "zeus", manager.cooldownSeconds());
        }

        // Explosive Arrow：命中爆炸
        if (explosive > 0) {
            victim.getWorld().createExplosion(victim.getLocation(), 1.5f + 0.5f * explosive, false, false);
        }

        // Blaze：引燃目标
        if (blaze > 0) {
            victim.setFireTicks(40 * blaze);
        }

        // Shockwave：命中后击退附近敌人
        if (shockwave > 0) {
            double power = 0.5 * shockwave;
            for (org.bukkit.entity.Entity e : victim.getNearbyEntities(3, 3, 3)) {
                if (e instanceof LivingEntity && e != victim) {
                    Vector dir = e.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();
                    e.setVelocity(dir.multiply(power).setY(0.3 * shockwave));
                }
            }
        }
    }
}
