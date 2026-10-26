package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
    /** 持续掉血 (uuid -> {剩余tick, 每秒掉血量})：腐蚀/撕裂/炽烧/腐烂 */
    private static final Map<UUID, int[]> DOTS = new ConcurrentHashMap<>();

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

        // DOT 持续掉血任务（腐蚀/撕裂/炽烧/腐烂）
        new BukkitRunnable() {
            @Override
            public void run() {
                DOTS.forEach((id, arr) -> {
                    Entity e = plugin.getServer().getEntity(id);
                    if (e == null) {
                        DOTS.remove(id);
                    } else if (e.isDead()) {
                        DOTS.remove(id);
                    } else if (e instanceof LivingEntity le) {
                        le.damage(arr[1]);
                        DOTS.put(id, new int[]{arr[0] - 20, arr[1]});
                        if (arr[0] - 20 <= 0) {
                            DOTS.remove(id);
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // Adrenaline：低血量时靴子附魔提供速度
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    int adv = Enchants.level(p.getInventory().getBoots(), "adrenaline");
                    if (adv > 0 && p.getHealth() < p.getMaxHealth() * 0.3) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, Math.min(1, adv - 1)));
                    }
                    // Regeneration：穿戴时持续回血
                    int regen = total(p, "regeneration");
                    if (regen > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, Math.min(1, regen - 1)));
                    }
                    // Night Vision：头盔夜视
                    if (Enchants.level(p.getInventory().getHelmet(), "night_vision") > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0));
                    }
                    // Diver：水下呼吸+夜视
                    int diver = Enchants.level(p.getInventory().getBoots(), "diver");
                    if (diver > 0 && p.isInWater()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0));
                    }
                    // Swift：疾跑速度
                    int swift = Enchants.level(p.getInventory().getBoots(), "swift");
                    if (swift > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, Math.min(1, swift - 1)));
                    }
                    // Speed Boots：疾速
                    int sb = Enchants.level(p.getInventory().getBoots(), "speed_boots");
                    if (sb > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, Math.min(1, sb - 1)));
                    }
                    // Vitality：活力（提升生命上限）
                    int vit = total(p, "vitality");
                    if (vit > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 120, Math.min(1, vit - 1)));
                    }
                    // Aura of Light：照亮周围
                    if (total(p, "aura_light") > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0));
                    }
                    // Night Eye：头盔夜视
                    if (Enchants.level(p.getInventory().getHelmet(), "night_eye") > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0));
                    }
                    // Water Breath：头盔水下呼吸
                    int wb = Enchants.level(p.getInventory().getHelmet(), "water_breath");
                    if (wb > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0));
                    }
                    // Aqua Step：水上行走（脚下生成冰霜，且提供水下呼吸）
                    int aqua = Enchants.level(p.getInventory().getBoots(), "aqua_step");
                    if (aqua > 0) {
                        org.bukkit.block.Block under = p.getLocation().subtract(0, 0.5, 0).getBlock();
                        if (under.getType() == Material.WATER || under.getType() == Material.LAVA) {
                            under.setType(Material.FROSTED_ICE);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0));
                        }
                    }
                    // Rocket Boots：冲刺向前推进
                    int rocket = Enchants.level(p.getInventory().getBoots(), "rocket_boots");
                    if (rocket > 0 && p.isSprinting()) {
                        org.bukkit.util.Vector v = p.getLocation().getDirection()
                                .multiply(0.08 * rocket).setY(0);
                        p.setVelocity(p.getVelocity().add(v));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** 护甲上某附魔的总等级 */
    private int total(Player p, String key) {
        int sum = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null) {
                sum += Enchants.level(piece, key);
            }
        }
        return sum;
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
        // 神器（寰宇支配之剑）：所有技能 100% 概率触发且无视冷却
        boolean artifact = manager.isArtifact(held);

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
        if (sup > 0 && (artifact || Math.random() < 0.02 * sup)) {
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
        if (bleed > 0 && (artifact || Math.random() < 0.1 * bleed)) {
            BLEEDING.put(victim.getUniqueId(), 40 + bleed * 20); // 持续 2-4 秒 + 每级
        }

        // Thor：概率雷电
        int thor = Enchants.level(held, "thor");
        if (thor > 0 && (artifact || Math.random() < 0.03 * thor)
                && (artifact || !manager.isOnCooldown(attacker.getUniqueId(), "thor"))) {
            victim.getWorld().strikeLightning(victim.getLocation());
            manager.startCooldown(attacker.getUniqueId(), "thor", manager.cooldownSeconds());
        }

        // Toxic：概率中毒
        int toxic = Enchants.level(held, "toxic");
        if (toxic > 0 && (artifact || Math.random() < 0.2 * toxic)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        }

        // Wither Aspect：概率凋零
        int wither = Enchants.level(held, "wither_aspect");
        if (wither > 0 && (artifact || Math.random() < 0.15 * wither)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
        }

        // Corrosion：腐蚀持续掉血（每秒 1+lv，无视护甲）
        int corrosion = Enchants.level(held, "corrosion");
        if (corrosion > 0 && (artifact || Math.random() < 0.25 * corrosion)) {
            DOTS.put(victim.getUniqueId(), new int[]{40 + 20 * corrosion, 1 + corrosion});
            victim.getWorld().spawnParticle(Particle.DRIPPING_LAVA,
                    victim.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.01);
        }

        // Laceration：撕裂持续大量掉血（每秒 2*lv）
        int laceration = Enchants.level(held, "laceration");
        if (laceration > 0 && (artifact || Math.random() < 0.25 * laceration)) {
            DOTS.put(victim.getUniqueId(), new int[]{60, 2 * laceration});
            victim.getWorld().spawnParticle(Particle.CRIT,
                    victim.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        }

        // Scorching（灵魂火焰）：灵魂火持续灼烧，每秒 1+lv，蓝焰视觉
        int scorching = Enchants.level(held, "scorching");
        if (scorching > 0 && (artifact || Math.random() < 0.3 * scorching)) {
            DOTS.put(victim.getUniqueId(), new int[]{80, 1 + scorching});
            victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    victim.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.01);
        }

        // Rotting：腐烂凋零持续掉血（每秒 1+lv）
        int rotting = Enchants.level(held, "rotting");
        if (rotting > 0 && (artifact || Math.random() < 0.2 * rotting)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            DOTS.put(victim.getUniqueId(), new int[]{60, 1 + rotting});
            victim.getWorld().spawnParticle(Particle.ASH,
                    victim.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.01);
        }

        // Frost：概率附加缓慢
        int frost = Enchants.level(held, "frost");
        if (frost > 0 && (artifact || Math.random() < 0.5 * frost)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 * frost, 0));
        }

        // Undead Bane：对亡灵生物增伤
        int ub = Enchants.level(held, "undead_bane");
        if (ub > 0 && isUndead(victim.getType())) {
            dmg *= (1 + 0.2 * ub);
            changed = true;
        }

        // Executioner：目标血量低时增伤
        int exec = Enchants.level(held, "executioner");
        if (exec > 0 && victim.getHealth() < victim.getMaxHealth() * 0.3) {
            dmg *= (1 + 0.2 * exec);
            changed = true;
        }

        // Armor Piercing：无视护甲（追加穿透伤害）
        int ap = Enchants.level(held, "armor_piercing");
        if (ap > 0) {
            dmg += dmg * 0.15 * ap;
            changed = true;
        }

        // Berserker：自身血量越低伤害越高
        int bers = Enchants.level(held, "berserker");
        if (bers > 0) {
            double missing = 1 - (attacker.getHealth() / attacker.getMaxHealth());
            dmg *= (1 + 0.3 * bers * missing);
            changed = true;
        }

        // Chain Lightning：概率对附近敌人落雷
        int chain = Enchants.level(held, "chain_lightning");
        if (chain > 0 && (artifact || Math.random() < 0.2 * chain)
                && (artifact || !manager.isOnCooldown(attacker.getUniqueId(), "chain_lightning"))) {
            victim.getWorld().strikeLightning(victim.getLocation());
            int extra = chain;
            for (Entity e : victim.getNearbyEntities(6, 6, 6)) {
                if (extra <= 0) {
                    break;
                }
                if (e instanceof LivingEntity && e != attacker && e != victim) {
                    e.getWorld().strikeLightning(e.getLocation());
                    extra--;
                }
            }
            manager.startCooldown(attacker.getUniqueId(), "chain_lightning", manager.cooldownSeconds());
        }

        // Blind：致盲
        int blind = Enchants.level(held, "blind");
        if (blind > 0 && (artifact || Math.random() < 0.3 * blind)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40 * blind, 0));
        }

        // Nausea：恶心
        int nausea = Enchants.level(held, "nausea");
        if (nausea > 0 && (artifact || Math.random() < 0.3 * nausea)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40 * nausea, 0));
        }

        // Execute：处决（低血概率秒杀非玩家）
        int exe = Enchants.level(held, "execute");
        if (exe > 0 && !(victim instanceof Player)
                && victim.getHealth() < victim.getMaxHealth() * 0.1 && (artifact || Math.random() < 0.3)) {
            victim.setHealth(0);
        }

        // Curse of Weakness：手持或穿戴时降低造成的伤害
        int weak = Enchants.level(held, "curse_of_weakness");
        for (ItemStack piece : attacker.getInventory().getArmorContents()) {
            if (piece != null) {
                weak += Enchants.level(piece, "curse_of_weakness");
            }
        }
        if (weak > 0) {
            dmg *= (1 - 0.1 * weak);
            changed = true;
        }

        // Insomnia：命中概率使目标疲劳（困倦）
        int ins = Enchants.level(held, "insomnia");
        if (ins > 0 && (artifact || Math.random() < 0.3 * ins)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40 * ins, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 * ins, 0));
        }

        // King's Descent（王的降临）：仅主人 ClowmPierce 持剑时生效，效果+50%，并触发狂热
        int king = Enchants.level(held, "king_descent");
        if (king > 0 && attacker.getName().equals(manager.artifactOwner())) {
            dmg *= 1.5;
            changed = true;
        }
        // 主人持剑攻击任意目标 → 狂热攻速
        if (attacker.getName().equals(manager.artifactOwner()) && manager.isArtifact(held)) {
            plugin.artifactListener().onAttack(attacker);
        }

        // Conductive（导电）：命中在目标正上方生成闪电
        int cond = Enchants.level(held, "conductive");
        if (cond > 0 && (artifact || Math.random() < 0.5)
                && (artifact || !manager.isOnCooldown(attacker.getUniqueId(), "conductive"))) {
            int bolts = cond >= 2 ? 3 : 1;
            for (int i = 0; i < bolts; i++) {
                victim.getWorld().strikeLightning(victim.getLocation().add(0, 2 + i, 0));
            }
            // 二级：三道闪电且伤害提升 20%
            if (cond >= 2) {
                dmg *= 1.2;
                changed = true;
            }
            manager.startCooldown(attacker.getUniqueId(), "conductive", manager.cooldownSeconds());
        }

        // Explosive（爆炸）：命中产生爆炸
        int expl = Enchants.level(held, "explosive");
        if (expl > 0 && (artifact || Math.random() < 0.4)
                && (artifact || !manager.isOnCooldown(attacker.getUniqueId(), "explosive"))) {
            victim.getWorld().createExplosion(victim.getLocation(), 1.0f + 0.5f * expl, false, false);
            manager.startCooldown(attacker.getUniqueId(), "explosive", manager.cooldownSeconds());
        }

        // Sneak Attack（偷袭）：背后攻击增伤
        int sneak = Enchants.level(held, "sneak_attack");
        if (sneak > 0 && isBehind(attacker, victim)) {
            dmg *= (1 + 0.25 * sneak);
            changed = true;
        }

        // Eclipse（日食）：概率使目标陷入黑暗（失明+缓慢）
        int eclipse = Enchants.level(held, "eclipse");
        if (eclipse > 0 && (artifact || Math.random() < 0.2 * eclipse)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40 * eclipse, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 * eclipse, 0));
        }

        // Frost Aura：概率冰冻（缓慢+弱化跳跃）
        int fa = Enchants.level(held, "frost_aura");
        if (fa > 0 && (artifact || Math.random() < 0.4 * fa)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 * fa, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60 * fa, -1));
        }

        // Cripple：致残（降低对方挖掘/攻速）
        int cri = Enchants.level(held, "cripple");
        if (cri > 0 && (artifact || Math.random() < 0.4 * cri)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60 * cri, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 * cri, 0));
        }

        // Vampiric：吸血强化
        int vamp = Enchants.level(held, "vampiric");
        if (vamp > 0) {
            double heal = event.getDamage() * 0.12 * vamp;
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
        }

        // Venomous：强毒
        int ven = Enchants.level(held, "venomous");
        if (ven > 0 && (artifact || Math.random() < 0.2 * ven)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
        }

        // Storm Call：概率召唤闪电
        int sc = Enchants.level(held, "storm_call");
        if (sc > 0 && (artifact || Math.random() < 0.15 * sc)
                && (artifact || !manager.isOnCooldown(attacker.getUniqueId(), "storm_call"))) {
            victim.getWorld().strikeLightning(victim.getLocation());
            manager.startCooldown(attacker.getUniqueId(), "storm_call", manager.cooldownSeconds());
        }

        // Ender Shift：命中概率瞬移到目标背后
        int es = Enchants.level(held, "ender_shift");
        if (es > 0 && (artifact || Math.random() < 0.25 * es)) {
            org.bukkit.Location behind = victim.getLocation()
                    .add(victim.getLocation().getDirection().multiply(-2.0));
            attacker.teleport(behind);
        }

        // Dual Strike：概率二段伤害（直接扣血，避免事件递归）
        int ds = Enchants.level(held, "dual_strike");
        if (ds > 0 && (artifact || Math.random() < 0.2 * ds) && victim instanceof LivingEntity) {
            double extra = event.getDamage() * 0.5 * ds;
            victim.setHealth(Math.max(0, victim.getHealth() - extra));
        }

        // Fear：恐惧（使对方虚弱）
        int fear = Enchants.level(held, "fear");
        if (fear > 0 && (artifact || Math.random() < 0.3 * fear)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60 * fear, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 * fear, 0));
        }

        // Cleave：横扫（对附近敌人造成范围伤害）
        int cle = Enchants.level(held, "cleave");
        if (cle > 0) {
            for (Entity e : victim.getNearbyEntities(3, 3, 3)) {
                if (e instanceof LivingEntity && e != attacker && e != victim) {
                    ((LivingEntity) e).setHealth(Math.max(0,
                            ((LivingEntity) e).getHealth() - event.getDamage() * 0.2 * cle));
                }
            }
        }

        // 抹杀（代码层面）：神器命中目标，无视血量/护甲/无敌直接归零
        if (artifact && victim.isValid() && !victim.isDead()) {
            victim.setHealth(0);
        }

        if (changed) {
            event.setDamage(dmg);
        }
    }

    /** 玩家受到伤害：护甲减伤 / 荆棘反伤 / 不死存活 / 格挡减伤 / 羽落 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        double dmg = event.getDamage();
        boolean changed = false;
        // Guardian：格挡减伤
        int guard = Enchants.level(victim.getInventory().getItemInMainHand(), "guardian");
        if (guard > 0 && victim.isBlocking()) {
            dmg *= (1 - 0.2 * guard);
            changed = true;
        }
        // Iron Body：钢铁之躯减伤
        int iron = total(victim, "iron_body");
        if (iron > 0) {
            dmg *= (1 - 0.06 * iron);
            changed = true;
        }
        // Resistance：全体减伤
        int resist = total(victim, "resistance");
        if (resist > 0) {
            dmg *= (1 - 0.08 * resist);
            changed = true;
        }
        // Fire Ward：火焰/岩浆减伤
        int fireWard = total(victim, "fire_ward");
        if (fireWard > 0 && (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            dmg *= (1 - 0.25 * fireWard);
            changed = true;
        }
        // Wither Ward：凋零伤害减伤
        int witherWard = total(victim, "wither_ward");
        if (witherWard > 0 && event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            dmg *= (1 - 0.25 * witherWard);
            changed = true;
        }
        // Explosion Ward：爆炸减伤
        int expWard = total(victim, "explosion_ward");
        if (expWard > 0 && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            dmg *= (1 - 0.2 * expWard);
            changed = true;
        }
        // Feather Fall：羽落（免疫/减免坠落伤害）
        int ff = Enchants.level(victim.getInventory().getBoots(), "feather_fall");
        if (ff > 0 && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            dmg *= (1 - 0.4 * ff);
            changed = true;
        }
        if (changed) {
            event.setDamage(dmg);
        }
        // Poison Ward：驱散中毒
        if (total(victim, "poison_ward") > 0) {
            victim.removePotionEffect(PotionEffectType.POISON);
        }
        // Undying：致死存活
        if (total(victim, "undying") > 0 && victim.getHealth() - dmg <= 0 && Math.random() < 0.4) {
            event.setDamage(0);
            victim.setHealth(1);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        }
        // Thorns Reflect：荆棘反伤（反弹给攻击者，直接扣血避免事件递归）
        int thorns = total(victim, "thorns_reflect");
        if (thorns > 0 && event instanceof EntityDamageByEntityEvent dbe
                && dbe.getDamager() instanceof LivingEntity source) {
            double reflect = dbe.getDamage() * 0.2 * thorns;
            if (reflect > 0) {
                source.setHealth(Math.max(0, source.getHealth() - reflect));
            }
        }
    }

    /** 攻击者是否位于目标背后 */
    private boolean isBehind(Player attacker, LivingEntity victim) {
        org.bukkit.util.Vector toVictim = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector()).normalize();
        org.bukkit.util.Vector facing = victim.getLocation().getDirection().normalize();
        return toVictim.dot(facing) > 0.3;
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
        // Frenzy：击杀后获得力量+速度
        int frenzy = Enchants.level(held, "frenzy");
        if (frenzy > 0) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60 * frenzy, 0));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 * frenzy, 0));
        }
        // Greedy：击杀额外经验
        int greedy = Enchants.level(held, "greedy");
        if (greedy > 0) {
            event.getEntity().getWorld().spawn(event.getEntity().getLocation(), ExperienceOrb.class,
                    orb -> orb.setExperience(5 * greedy));
        }
        // Bloodlust：击杀后治疗+速度
        int blood = Enchants.level(held, "bloodlust");
        if (blood > 0) {
            killer.setHealth(Math.min(killer.getMaxHealth(), killer.getHealth() + 2.0 * blood));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40 * blood, 0));
        }
        // Soul Siphon：击杀恢复饱食
        int siphon = Enchants.level(held, "soul_siphon");
        if (siphon > 0) {
            killer.setFoodLevel(Math.min(20, killer.getFoodLevel() + 2 * siphon));
            killer.setSaturation(Math.min(20, killer.getSaturation() + 1.0f * siphon));
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

    private static boolean isUndead(EntityType type) {
        return switch (type) {
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER, ZOMBIFIED_PIGLIN,
                 SKELETON, STRAY, WITHER_SKELETON, PHANTOM, WITHER,
                 ZOGLIN, GIANT, SKELETON_HORSE, ZOMBIE_HORSE -> true;
            default -> false;
        };
    }
}
