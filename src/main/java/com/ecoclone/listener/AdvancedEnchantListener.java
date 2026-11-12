package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EffectEngine;
import com.ecoclone.EnchantGate;
import com.ecoclone.EnchantManager;
import com.ecoclone.EnchantType;
import com.ecoclone.Enchants;
import com.ecoclone.TriggerType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v1.4.8.0 高级附魔监听器：
 *   1) 逐级 effects 引擎的事件入口（attack / defend / kill / mine / shoot / death / fish / passive）
 *   2) spell 型附魔主动激活（右键；弓 / 弩 / 三叉戟 / 鱼竿为左键）与冷却
 *   3) artifact 型附魔粒子（鞘翅拖尾 / 手持环绕 / 击杀螺旋 / 挖掘粒子 / 投射物拖尾）
 */
public final class AdvancedEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;
    private final EffectEngine engine;

    public AdvancedEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.engine = new EffectEngine(manager);
        startTasks();
    }

    public EffectEngine engine() {
        return engine;
    }

    private void startTasks() {
        // 被动效果：每秒一次
        Bukkit.getScheduler().runTaskTimer(plugin, this::passiveTick, 20L, 20L);
        // 装饰粒子：每 5 tick 一次
        Bukkit.getScheduler().runTaskTimer(plugin, this::artifactTick, 5L, 5L);
    }

    // ---------- 事件入口 ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        EffectEngine.Ctx ctx = new EffectEngine.Ctx(event.getDamage());
        if (event.getDamager() instanceof Player attacker) {
            EnchantGate.enter(attacker, event.getEntity() instanceof Player victim ? victim : null);
            fireAll(attacker, attacker.getInventory().getItemInMainHand(), TriggerType.ATTACK, ctx);
            if (event.getEntity() instanceof LivingEntity living && isArtifactHeld(attacker)) {
                spiral(living.getLocation(), artifactParticles(attacker.getInventory().getItemInMainHand()));
            }
        }
        if (event.getEntity() instanceof Player victim) {
            LivingEntity damager = event.getDamager() instanceof LivingEntity le ? le : null;
            EnchantGate.enter(victim, damager instanceof Player pd ? pd : null);
            fireArmor(victim, TriggerType.DEFEND, ctx);
        }
        EventDamageSetter.set(event, ctx.damage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        EnchantGate.enter(killer);
        EffectEngine.Ctx ctx = new EffectEngine.Ctx();
        fireAll(killer, killer.getInventory().getItemInMainHand(), TriggerType.KILL, ctx);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player p = event.getPlayer();
        EnchantGate.enter(p);
        EffectEngine.Ctx ctx = new EffectEngine.Ctx();
        ctx.block = event.getBlock();
        ItemStack tool = p.getInventory().getItemInMainHand();
        fireAll(p, tool, TriggerType.MINE, ctx);
        if (isArtifactItem(tool)) {
            Block b = event.getBlock();
            spawn(artifactParticles(tool), b.getLocation().add(0.5, 0.5, 0.5), 12);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        EnchantGate.enter(p);
        EffectEngine.Ctx ctx = new EffectEngine.Ctx();
        ItemStack bow = event.getBow();
        fireAll(p, bow != null ? bow : p.getInventory().getItemInMainHand(), TriggerType.SHOOT, ctx);
        if (isArtifactItem(bow)) {
            spawn(artifactParticles(bow), event.getProjectile().getLocation(), 8);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        EnchantGate.enter(p);
        EffectEngine.Ctx ctx = new EffectEngine.Ctx();
        fireAll(p, p.getInventory().getItemInMainHand(), TriggerType.DEATH, ctx);
        fireArmor(p, TriggerType.DEATH, ctx);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        EnchantGate.enter(p);
        EffectEngine.Ctx ctx = new EffectEngine.Ctx();
        fireAll(p, p.getInventory().getItemInMainHand(), TriggerType.FISH, ctx);
    }

    // ---------- spell 主动技能 ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        Action action = event.getAction();
        boolean ranged = isRanged(item.getType());
        boolean use = ranged
                ? action == Action.LEFT_CLICK_AIR
                : action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!use) {
            return;
        }
        EnchantGate.enter(p);
        for (String key : spellKeys(item)) {
            int level = Enchants.level(item, key);
            if (level <= 0) {
                continue;
            }
            if (manager.isOnCooldown(p.getUniqueId(), key)) {
                if (manager.spellMessages(key)) {
                    String msg = manager.spellCooldownMessage()
                            .replace("%remaining%", String.format("%.1f",
                                    manager.remainingCooldown(p.getUniqueId(), key)));
                    p.sendActionBar(Component.text(manager.color(msg)));
                }
                continue;
            }
            if (!engine.fire(p, item, key, level, TriggerType.SPELL, null)) {
                continue;
            }
            double cooldown = manager.spellCooldown(key);
            if (cooldown > 0) {
                manager.startCooldown(p.getUniqueId(), key, cooldown);
            }
        }
    }

    // ---------- 被动 / 装饰 ----------

    private void passiveTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            EnchantGate.enter(p);
            EffectEngine.Ctx ctx = new EffectEngine.Ctx();
            fireAll(p, p.getInventory().getItemInMainHand(), TriggerType.PASSIVE, ctx);
            fireArmor(p, TriggerType.PASSIVE, ctx);
        }
    }

    private void artifactTick() {
        if (!manager.artifactsEnabled()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack chest = p.getInventory().getChestplate();
            if (chest != null && p.isGliding()) {
                for (String key : artifactKeys(chest)) {
                    if (Enchants.level(chest, key) <= 0) {
                        continue;
                    }
                    wingTrail(p, manager.artifactParticle(key));
                }
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (isArtifactItem(hand) && Math.random() < 0.35) {
                orbit(p, artifactParticles(hand));
            }
        }
    }

    // ---------- 工具方法 ----------

    private void fireAll(Player p, ItemStack item, TriggerType trigger, EffectEngine.Ctx ctx) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        TriggerType prev = TriggerType.set(trigger);
        try {
            for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                if (!entry.getKey().getKey().getNamespace().equals("ecoclone")) {
                    continue;
                }
                String key = entry.getKey().getKey().getKey();
                if (!engine.hasEffects(key)) {
                    continue;
                }
                int level = Enchants.level(item, key);
                if (level <= 0) {
                    continue;
                }
                engine.fire(p, item, key, level, trigger, ctx);
            }
        } finally {
            TriggerType.set(prev);
        }
    }

    private void fireArmor(Player p, TriggerType trigger, EffectEngine.Ctx ctx) {
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            fireAll(p, armor, trigger, ctx);
        }
    }

    private List<String> spellKeys(ItemStack item) {
        List<String> out = new ArrayList<>();
        for (Enchantment e : item.getEnchantments().keySet()) {
            if (!e.getKey().getNamespace().equals("ecoclone")) {
                continue;
            }
            String key = e.getKey().getKey();
            if (manager.type(key) == EnchantType.SPELL) {
                out.add(key);
            }
        }
        return out;
    }

    private List<String> artifactKeys(ItemStack item) {
        List<String> out = new ArrayList<>();
        for (Enchantment e : item.getEnchantments().keySet()) {
            if (!e.getKey().getNamespace().equals("ecoclone")) {
                continue;
            }
            String key = e.getKey().getKey();
            if (manager.type(key) == EnchantType.ARTIFACT) {
                out.add(key);
            }
        }
        return out;
    }

    private boolean isArtifactItem(ItemStack item) {
        return item != null && !artifactKeys(item).isEmpty();
    }

    private boolean isArtifactHeld(Player p) {
        return isArtifactItem(p.getInventory().getItemInMainHand());
    }

    private Particle artifactParticles(ItemStack item) {
        if (item == null) {
            return Particle.END_ROD;
        }
        String name = "end_rod";
        for (String key : artifactKeys(item)) {
            name = manager.artifactParticle(key);
            break;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase().replace(' ', '_'));
        } catch (Throwable ignored) {
            return Particle.END_ROD;
        }
    }

    private static boolean isRanged(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW
                || type == Material.TRIDENT || type == Material.FISHING_ROD;
    }

    /** 鞘翅双翼拖尾 */
    private void wingTrail(Player p, String particleName) {
        Particle particle = particle(particleName);
        if (particle == null) {
            return;
        }
        org.bukkit.util.Vector dir = p.getLocation().getDirection().normalize();
        org.bukkit.util.Vector side = dir.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) {
            side = new org.bukkit.util.Vector(1, 0, 0);
        }
        side.normalize().multiply(0.75);
        org.bukkit.Location base = p.getLocation().add(0, 1.0, 0).subtract(dir.clone().multiply(0.6));
        spawnAtPos(particle, base.clone().add(side));
        spawnAtPos(particle, base.clone().subtract(side));
    }

    /** 手持装饰附魔：绕玩家一圈粒子 */
    private void orbit(Player p, Particle particle) {
        double angle = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2;
        org.bukkit.Location loc = p.getLocation().add(Math.cos(angle) * 1.1, 1.0, Math.sin(angle) * 1.1);
        spawnAtPos(particle, loc);
    }

    /** 近战命中：围绕受害者螺旋 */
    private void spiral(org.bukkit.Location center, Particle particle) {
        for (int i = 0; i < 12; i++) {
            double t = i / 12.0;
            double angle = t * Math.PI * 2;
            double r = 0.45 + t * 0.45;
            double y = t * 1.6;
            spawnAtPos(particle, center.clone().add(Math.cos(angle) * r, 0.4 + y, Math.sin(angle) * r));
        }
    }

    private void spawn(Particle particle, org.bukkit.Location loc, int count) {
        if (particle == null || loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawnParticle(particle, loc, Math.max(1, count), 0.15, 0.15, 0.15, 0.01);
    }

    private void spawnAt(String name, org.bukkit.Location loc, int count) {
        spawn(particle(name), loc, count);
    }

    private void spawnAtPos(Particle particle, org.bukkit.Location loc) {
        if (particle == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static Particle particle(String name) {
        if (name == null) {
            return Particle.END_ROD;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase().replace(' ', '_'));
        } catch (Throwable ignored) {
            return Particle.END_ROD;
        }
    }

    /** 独立小工具：避免直接依赖事件实现细节，Paper 与 Spigot 都能设置伤害 */
    private static final class EventDamageSetter {
        static void set(EntityDamageByEntityEvent event, double damage) {
            if (damage > 0 && Math.abs(damage - event.getDamage()) > 0.0001) {
                event.setDamage(damage);
            }
        }
    }
}
