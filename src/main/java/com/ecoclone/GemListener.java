package com.ecoclone;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 宝石效果与镶嵌监听器。
 *  - 右键：主手持装备 + 副手宝石 → 镶嵌；主手持带宝石装备（副手空）→ 取出最后一颗
 *  - 背包点击：光标宝石点击装备槽 → 镶嵌；光标空点击带宝石装备槽 → 取出最后一颗
 *  - 攻击：主手武器上的宝石（ruby/sapphire/topaz/onyx/dragon）加成
 *  - 穿戴：每 2 秒检查穿着装备上的护甲宝石（emerald/amethyst/topaz/dragon），施加药水效果
 */
public final class GemListener implements Listener {

    private final EcoClonePlugin plugin;
    private final GemManager gems;
    private final Random rnd = new Random();

    public GemListener(EcoClonePlugin plugin) {
        this.plugin = plugin;
        this.gems = plugin.gemManager();
    }

    // ---------- 镶嵌 / 取出 ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        PlayerInventory inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();

        ItemStack gem = null;
        ItemStack equip = null;
        boolean gemInMain = false;
        if (gems.isGem(main) && off != null && !off.getType().isAir()) {
            gem = main; equip = off; gemInMain = true;
        } else if (gems.isGem(off) && main != null && !main.getType().isAir()) {
            gem = off; equip = main;
        }
        if (gem != null && equip != null) {
            if (!gems.compatible(equip, gem)) {
                GemManager.GemDef d = gems.gemDef(gem);
                String where = d != null && "weapon".equals(d.target) ? "武器" : "护甲";
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c该宝石只能镶嵌在 " + where + " 上。"));
                return;
            }
            if (gems.slotsOf(equip) <= 0) {
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c该装备尚未开槽，先用 &e/aeath socket"));
                e.setCancelled(true);
                return;
            }
            if (!gems.canSocket(equip)) {
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c宝石槽已满，无法再镶嵌。"));
                e.setCancelled(true);
                return;
            }
            ItemStack updated = gems.insert(equip, gem);
            if (updated != null) {
                if (gemInMain) inv.setItemInOffHand(updated);
                else inv.setItemInMainHand(updated);
                gem.setAmount(gem.getAmount() - 1);
                GemManager.GemDef d = gems.gemDef(gem);
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&a已镶嵌 " + (d == null ? "宝石" : d.name) + " §7Lv." + gems.gemLevel(gem)));
                e.setCancelled(true);
                return;
            }
        }
        // 取出宝石：只能副手持带宝石装备、主手不持宝石时右键取出，避免穿上护甲误触取出
        if (off != null && !off.getType().isAir() && gems.hasGems(off) && !gems.isGem(main)) {
            ItemStack[] res = gems.extractLast(off);
            if (res != null) {
                inv.setItemInOffHand(res[1]);
                java.util.Map<Integer, ItemStack> left = inv.addItem(res[0]);
                if (!left.isEmpty()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left.get(0));
                }
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&7已取出 " + gemName(res[0]) + "，槽位空出 1 格。"));
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        org.bukkit.inventory.Inventory clicked = e.getClickedInventory();
        if (clicked == null || !(clicked instanceof PlayerInventory)) return;
        int raw = e.getRawSlot();
        // 玩家背包视角中 36-39 为盔甲槽
        boolean isArmorSlot = raw >= 36 && raw <= 39;
        if (!isArmorSlot) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        if (cursor != null && gems.isGem(cursor)) {
            // 光标宝石 → 装备
            if (!gems.compatible(current, cursor)) {
                GemManager.GemDef d = gems.gemDef(cursor);
                String where = d != null && "weapon".equals(d.target) ? "武器" : "护甲";
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c该宝石只能镶嵌在 " + where + " 上。"));
                return;
            }
            if (gems.slotsOf(current) <= 0) {
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c该装备尚未开槽，先用 &e/aeath socket"));
                e.setCancelled(true);
                return;
            }
            if (!gems.canSocket(current)) {
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c宝石槽已满，无法再镶嵌。"));
                e.setCancelled(true);
                return;
            }
            ItemStack updated = gems.insert(current, cursor);
            if (updated != null) {
                e.setCurrentItem(updated);
                cursor.setAmount(cursor.getAmount() - 1);
                e.setCursor(cursor);
                e.setCancelled(true);
                p.sendMessage(plugin.manager().prefix() + plugin.manager().color("&a已镶嵌宝石。"));
            }
        }
        // 取出宝石仅支持副手右键（见 /aeath geminfo），背包点击不再提供取出，避免穿上护甲误触
    }

    private String gemName(ItemStack gem) {
        GemManager.GemDef d = gems.gemDef(gem);
        return d == null ? "宝石" : d.name + " Lv." + gems.gemLevel(gem);
    }

    // ---------- 攻击加成 ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        ItemStack main = attacker.getInventory().getItemInMainHand();
        if (main == null || main.getType().isAir() || !gems.hasGems(main)) return;
        double dmg = e.getDamage();
        boolean crit = false;
        for (GemManager.SlotEntry gem : gems.gemsOf(main)) {
            int lv = gem.level;
            GemManager.GemDef d = gems.get(gem.key);
            if (d != null && d.fx != null) {
                // 新宝石：fx 效果模板
                switch (d.fx) {
                    case "DMG_FLAT" -> dmg += lv;
                    case "DMG_PCT" -> dmg *= (1 + 0.02 * lv);
                    case "FIRE" -> {
                        if (e.getEntity() instanceof LivingEntity le) {
                            le.setFireTicks(Math.max(le.getFireTicks(), lv * 20));
                        }
                    }
                    case "SLOWNESS" -> {
                        if (e.getEntity() instanceof LivingEntity le) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, lv - 1, false, false, false));
                        }
                    }
                    case "POISON" -> {
                        if (e.getEntity() instanceof LivingEntity le) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, lv - 1, false, false, false));
                        }
                    }
                    case "WITHER" -> {
                        if (e.getEntity() instanceof LivingEntity le) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, lv - 1, false, false, false));
                        }
                    }
                    case "LIGHTNING" -> {
                        if (rnd.nextDouble() < 0.10 * lv) {
                            e.getEntity().getWorld().strikeLightning(e.getEntity().getLocation());
                        }
                    }
                    case "LIFESTEAL" -> attacker.setHealth(Math.min(attacker.getMaxHealth(),
                            attacker.getHealth() + e.getDamage() * 0.05 * lv));
                    case "CRIT" -> {
                        if (rnd.nextDouble() < 0.05 * lv) crit = true;
                    }
                    case "TRUE_DMG" -> {
                        if (rnd.nextDouble() < 0.05 * lv && e.getEntity() instanceof LivingEntity le) {
                            le.damage(e.getDamage() * 0.5, attacker);
                        }
                    }
                    case "SPEED_ON_HIT" -> attacker.addPotionEffect(
                            new PotionEffect(PotionEffectType.SPEED, 20, lv - 1, false, false, false));
                    case "STRENGTH_ON_HIT" -> attacker.addPotionEffect(
                            new PotionEffect(PotionEffectType.STRENGTH, 20, lv - 1, false, false, false));
                    default -> {
                    }
                }
                continue;
            }
            switch (gem.key) {
                case "ruby" -> {
                    dmg *= (1 + 0.02 * lv);
                    if (rnd.nextDouble() < 0.30 && e.getEntity() instanceof LivingEntity le) {
                        le.setFireTicks(Math.max(le.getFireTicks(), lv * 20));
                    }
                }
                case "sapphire" -> {
                    dmg += lv;
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, lv - 1, false, false, false));
                }
                case "onyx" -> {
                    double heal = e.getDamage() * 0.05 * lv;
                    attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
                }
                case "topaz" -> {
                    if (rnd.nextDouble() < 0.05 * lv) crit = true;
                }
                case "dragon" -> dmg *= (1 + 0.03 * lv);
                case "tiger_eye" -> {
                    dmg += lv;
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20, lv - 1, false, false, false));
                }
                case "sunstone" -> {
                    if (rnd.nextDouble() < 0.30 && e.getEntity() instanceof LivingEntity le) {
                        le.setFireTicks(Math.max(le.getFireTicks(), lv * 20));
                    }
                }
                case "starstone" -> {
                    if (rnd.nextDouble() < 0.10 * lv) {
                        e.getEntity().getWorld().strikeLightning(e.getEntity().getLocation());
                    }
                }
                case "arcane" -> {
                    dmg *= (1 + 0.01 * lv);
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, lv - 1, false, false, false));
                }
                case "frost" -> {
                    dmg += lv;
                    if (e.getEntity() instanceof LivingEntity le) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, lv - 1, false, false, false));
                    }
                }
                case "venom" -> {
                    if (e.getEntity() instanceof LivingEntity le) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, lv - 1, false, false, false));
                    }
                }
                case "void" -> {
                    if (rnd.nextDouble() < 0.05 * lv && e.getEntity() instanceof LivingEntity le) {
                        le.damage(e.getDamage() * 0.5, attacker);
                    }
                }
                default -> {
                }
            }
        }
        if (crit) dmg *= 2;
        e.setDamage(dmg);
    }

    // ---------- 受击（钻石之心减伤 / 尖晶石反伤） ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        double reduce = 0, reflect = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir() || !gems.hasGems(piece)) continue;
            for (GemManager.SlotEntry gem : gems.gemsOf(piece)) {
                if ("diamond".equals(gem.key)) reduce += 0.02 * gem.level;
                else if ("spinel".equals(gem.key)) reflect += 0.05 * gem.level;
            }
        }
        double dmg = e.getDamage();
        if (reduce > 0) {
            dmg *= (1 - Math.min(reduce, 0.6));
            e.setDamage(dmg);
        }
        if (reflect > 0 && e instanceof EntityDamageByEntityEvent be
                && be.getDamager() instanceof LivingEntity dl) {
            double r = e.getFinalDamage() * Math.min(reflect, 0.6);
            if (r > 0) dl.damage(r);
        }
    }

    // ---------- 穿戴药水效果 ----------

    public void startTick() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    applyArmorGems(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private void applyArmorGems(Player p) {
        Map<PotionEffectType, Integer> fx = new HashMap<>();
        double absorb = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir() || !gems.hasGems(piece)) continue;
            for (GemManager.SlotEntry gem : gems.gemsOf(piece)) {
                int lv = gem.level;
                GemManager.GemDef d = gems.get(gem.key);
                if (d != null && d.fx != null) {
                    // 新宝石：fx 效果模板
                    switch (d.fx) {
                        case "REGEN" -> fx.merge(PotionEffectType.REGENERATION, lv, Math::max);
                        case "RESISTANCE" -> fx.merge(PotionEffectType.RESISTANCE, lv, Math::max);
                        case "SPEED" -> fx.merge(PotionEffectType.SPEED, lv, Math::max);
                        case "HASTE" -> fx.merge(PotionEffectType.HASTE, lv, Math::max);
                        case "NIGHT_VISION" -> fx.merge(PotionEffectType.NIGHT_VISION, lv, Math::max);
                        case "FIRE_RESIST" -> fx.merge(PotionEffectType.FIRE_RESISTANCE, lv, Math::max);
                        case "ABSORB" -> absorb = Math.max(absorb, lv);
                        case "INVIS" -> fx.merge(PotionEffectType.INVISIBILITY, lv, Math::max);
                        case "WATER_BREATH" -> fx.merge(PotionEffectType.WATER_BREATHING, lv, Math::max);
                        case "JUMP" -> fx.merge(PotionEffectType.JUMP_BOOST, lv, Math::max);
                        case "STRENGTH" -> fx.merge(PotionEffectType.STRENGTH, lv, Math::max);
                        default -> {
                        }
                    }
                    continue;
                }
                switch (gem.key) {
                    case "emerald" -> fx.merge(PotionEffectType.REGENERATION, lv, Math::max);
                    case "amethyst" -> fx.merge(PotionEffectType.RESISTANCE, lv, Math::max);
                    case "diamond" -> absorb = Math.max(absorb, lv);
                    case "topaz" -> fx.merge(PotionEffectType.HASTE, lv, Math::max);
                    case "dragon" -> {
                        fx.merge(PotionEffectType.STRENGTH, lv, Math::max);
                        fx.merge(PotionEffectType.RESISTANCE, lv, Math::max);
                    }
                    case "moonstone" -> {
                        fx.merge(PotionEffectType.NIGHT_VISION, lv, Math::max);
                        fx.merge(PotionEffectType.SPEED, lv, Math::max);
                    }
                    case "aquamarine" -> {
                        fx.merge(PotionEffectType.WATER_BREATHING, lv, Math::max);
                        fx.merge(PotionEffectType.SPEED, lv, Math::max);
                    }
                    case "sunstone" -> {
                        fx.merge(PotionEffectType.FIRE_RESISTANCE, lv, Math::max);
                        fx.merge(PotionEffectType.HASTE, lv, Math::max);
                    }
                    case "malachite" -> {
                        fx.merge(PotionEffectType.HASTE, lv, Math::max);
                        fx.merge(PotionEffectType.SPEED, lv, Math::max);
                    }
                    case "shadowstone" -> {
                        fx.merge(PotionEffectType.NIGHT_VISION, lv, Math::max);
                        fx.merge(PotionEffectType.INVISIBILITY, lv, Math::max);
                    }
                    case "starstone" -> {
                        fx.merge(PotionEffectType.RESISTANCE, lv, Math::max);
                        absorb = Math.max(absorb, lv);
                    }
                    case "vitality" -> {
                        fx.merge(PotionEffectType.REGENERATION, lv, Math::max);
                        absorb = Math.max(absorb, lv);
                    }
                    case "arcane" -> {
                        fx.merge(PotionEffectType.STRENGTH, lv, Math::max);
                        fx.merge(PotionEffectType.SPEED, lv, Math::max);
                    }
                    case "guardian" -> {
                        fx.merge(PotionEffectType.RESISTANCE, lv, Math::max);
                        absorb = Math.max(absorb, lv);
                    }
                    case "phoenix" -> {
                        fx.merge(PotionEffectType.FIRE_RESISTANCE, lv, Math::max);
                        fx.merge(PotionEffectType.REGENERATION, lv, Math::max);
                    }
                    case "void" -> {
                        fx.merge(PotionEffectType.RESISTANCE, lv, Math::max);
                        fx.merge(PotionEffectType.NIGHT_VISION, lv, Math::max);
                    }
                    default -> {
                    }
                }
            }
        }
        fx.forEach((type, lv) ->
                p.addPotionEffect(new PotionEffect(type, 80, lv - 1, false, false, false)));
        if (absorb > 0) {
            p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 2.0 * absorb));
        }
    }
}
