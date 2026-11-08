package com.ecoclone.listener;

import com.ecoclone.EcoClonePlugin;
import com.ecoclone.EnchantGate;
import com.ecoclone.EnchantManager;
import com.ecoclone.Enchants;
import com.ecoclone.TriggerType;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * v1.4.7.0 新增附魔：盾牌系 Spikes / Mirror / Shield Bash。
 * 只在玩家举盾格挡时生效，同样走 conditions 与 triggers（DEFEND 触发器）。
 */
public final class ShieldEnchantListener implements Listener {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public ShieldEnchantListener(EcoClonePlugin plugin, EnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldBlock(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !victim.isBlocking()) {
            return;
        }
        ItemStack shield = shieldOf(victim);
        if (shield == null) {
            return;
        }
        EnchantGate.enter(victim);
        TriggerType.set(TriggerType.DEFEND);

        int spikes = Enchants.level(shield, "spikes");
        int mirror = Enchants.level(shield, "mirror");
        int bash = Enchants.level(shield, "shield_bash");
        if (spikes <= 0 && mirror <= 0 && bash <= 0) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        double damage = event.getDamage();

        // Mirror：概率完全反射伤害
        if (mirror > 0 && Math.random() < Math.min(0.15 * mirror, 0.4)) {
            attacker.damage(damage, victim);
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        // Spikes：格挡时反伤
        if (spikes > 0 && Math.random() < Math.min(0.10 * spikes, 0.35)) {
            attacker.damage(1.0 * spikes, victim);
        }

        // Shield Bash：击退攻击者
        if (bash > 0 && Math.random() < Math.min(0.10 * bash, 0.30)) {
            Vector dir = attacker.getLocation().toVector()
                    .subtract(victim.getLocation().toVector());
            if (dir.lengthSquared() > 0.01) {
                attacker.setVelocity(dir.normalize().multiply(0.6 + 0.2 * bash).setY(0.35));
            }
        }
    }

    private static ItemStack shieldOf(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.SHIELD) {
            return main;
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        return off != null && off.getType() == Material.SHIELD ? off : null;
    }
}
