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
import java.util.List;
import java.util.Random;
import java.util.UUID;

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

        if (gems.isGem(off) && gems.canSocket(main)) {
            if (!gems.compatible(main, off)) {
                GemManager.GemDef d = gems.gemDef(off);
                String where = d != null && "weapon".equals(d.target) ? "武器" : "护甲";
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&c该宝石只能镶嵌在 " + where + " 上。"));
                return;
            }
            ItemStack updated = gems.insert(main, off);
            if (updated != null) {
                inv.setItemInMainHand(updated);
                off.setAmount(off.getAmount() - 1);
                GemManager.GemDef d = gems.gemDef(off);
                p.sendMessage(plugin.manager().prefix()
                        + plugin.manager().color("&a已镶嵌 " + (d == null ? "宝石" : d.name) + " §7Lv." + gems.gemLevel(off)));
                e.setCancelled(true);
                return;
            }
        }
        if (gems.hasGems(main) && !gems.isGem(off)) {
            ItemStack[] res = gems.extractLast(main);
            if (res != null) {
                inv.setItemInMainHand(res[1]);
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
            ItemStack updated = gems.insert(current, cursor);
            if (updated != null) {
                e.setCurrentItem(updated);
                cursor.setAmount(cursor.getAmount() - 1);
                e.setCursor(cursor);
                e.setCancelled(true);
                p.sendMessage(plugin.manager().prefix() + plugin.manager().color("&a已镶嵌宝石。"));
            }
        } else if ((cursor == null || cursor.getType().isAir()) && gems.hasGems(current)) {
            ItemStack[] res = gems.extractLast(current);
            if (res != null) {
                e.setCurrentItem(res[1]);
                e.setCursor(res[0]);
                e.setCancelled(true);
                p.sendMessage(plugin.manager().prefix() + plugin.manager().color("&7已取出宝石。"));
            }
        }
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
                default -> {
                }
            }
        }
        if (crit) dmg *= 2;
        e.setDamage(dmg);
    }

    // ---------- 受击减伤（钻石之心，护甲） ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        double reduce = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir() || !gems.hasGems(piece)) continue;
            for (GemManager.SlotEntry gem : gems.gemsOf(piece)) {
                if ("diamond".equals(gem.key)) reduce += 0.02 * gem.level;
            }
        }
        if (reduce > 0) {
            e.setDamage(e.getDamage() * (1 - Math.min(reduce, 0.6)));
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
        boolean regen = false, resist = false, absorb = false, haste = false, dragon = false;
        int regenLv = 0, resistLv = 0, absorbLv = 0, hasteLv = 0, dragonLv = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir() || !gems.hasGems(piece)) continue;
            for (GemManager.SlotEntry gem : gems.gemsOf(piece)) {
                switch (gem.key) {
                    case "emerald" -> { regen = true; regenLv = Math.max(regenLv, gem.level); }
                    case "amethyst" -> { resist = true; resistLv = Math.max(resistLv, gem.level); }
                    case "diamond" -> { absorb = true; absorbLv = Math.max(absorbLv, gem.level); }
                    case "topaz" -> { haste = true; hasteLv = Math.max(hasteLv, gem.level); }
                    case "dragon" -> { dragon = true; dragonLv = Math.max(dragonLv, gem.level); }
                    default -> {
                    }
                }
            }
        }
        if (regen) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, regenLv - 1, false, false, false));
        if (resist) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, resistLv - 1, false, false, false));
        if (haste) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, hasteLv - 1, false, false, false));
        if (absorb) p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 2.0 * absorbLv));
        if (dragon) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, dragonLv - 1, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, dragonLv - 1, false, false, false));
        }
    }
}
