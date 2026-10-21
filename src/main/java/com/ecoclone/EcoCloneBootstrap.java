package com.ecoclone;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.event.WritableRegistry;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;
import io.papermc.paper.registry.keys.ItemTypeKeys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器启动引导阶段（bootstrap）注册自定义附魔。
 * Paper 1.21.1 使用 freeze 事件 + WritableRegistry.register(key, builder) 注册新附魔，
 * 注册完成后在 onEnable 里通过 org.bukkit.Registry.ENCHANTMENT.get(key) 取回对象。
 */
public final class EcoCloneBootstrap implements PluginBootstrap {

    /** 注册失败的附魔 key + 异常，写文件便于云端面板查看 */
    private static final List<String> ERRORS = new ArrayList<>();

    public static List<String> registrationErrors() {
        return ERRORS;
    }

    @Override
    public void bootstrap(BootstrapContext context) {
        // Paper 1.21.1 创建新附魔用 ENCHANTMENT 的 freeze 事件（新版才叫 compose）
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.ENCHANTMENT.freeze().newHandler(event -> {
                    WritableRegistry<Enchantment, EnchantmentRegistryEntry.Builder> registry = event.registry();
                    ERRORS.clear();
                    for (EnchantDef def : Enchants.definitions()) {
                        try {
                            TypedKey<Enchantment> typed =
                                    TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("ecoclone", def.key));
                            registry.register(typed, b -> configure(b, def));
                            context.getLogger().info("已注册附魔: " + def.key);
                        } catch (Throwable t) {
                            ERRORS.add(def.key + " :: " + t);
                            context.getLogger().warn("注册附魔失败 [" + def.key + "]: " + t);
                        }
                    }
                    // 把失败原因写入 plugins/EcoClone/registration-errors.txt，云端面板直接打开就能看
                    try {
                        Path dir = context.getDataDirectory();
                        Files.createDirectories(dir);
                        Files.write(dir.resolve("registration-errors.txt"),
                                ERRORS.isEmpty() ? List.of("all 32 enchantments registered OK") : ERRORS);
                    } catch (Exception ignored) {
                    }
                    int ok = Enchants.definitions().size() - ERRORS.size();
                    context.getLogger().info("bootstrap 注册完成：成功 " + ok + " / 失败 " + ERRORS.size());
                }));
    }

    private void configure(EnchantmentRegistryEntry.Builder b, EnchantDef def) {
        RegistryKeySet<ItemType> supported = RegistrySet.keySet(RegistryKey.ITEM, def.targets.toArray(TypedKey[]::new));
        EnchantmentRegistryEntry.EnchantmentCost min =
                EnchantmentRegistryEntry.EnchantmentCost.of(def.minBase, def.minPerLevel);
        EnchantmentRegistryEntry.EnchantmentCost max =
                EnchantmentRegistryEntry.EnchantmentCost.of(def.maxBase, def.maxPerLevel);
        b.description(Component.text(def.name))
                .supportedItems(supported)
                .primaryItems(supported)
                .anvilCost(def.anvilCost)
                .maxLevel(def.maxLevel)
                .weight(def.weight)
                .minimumCost(min)
                .maximumCost(max)
                .activeSlots(slotsFor(def));
    }

    /** 根据目标物品推算一个合法的 activeSlots（避免用 ANY 被校验拒绝）。 */
    private EquipmentSlotGroup slotsFor(EnchantDef def) {
        List<TypedKey<ItemType>> t = def.targets;
        if (t.contains(ItemTypeKeys.LEATHER_HELMET) || t.contains(ItemTypeKeys.DIAMOND_HELMET)
                || t.contains(ItemTypeKeys.IRON_HELMET)) {
            return EquipmentSlotGroup.ARMOR;
        }
        if (t.contains(ItemTypeKeys.LEATHER_BOOTS) || t.contains(ItemTypeKeys.DIAMOND_BOOTS)) {
            return EquipmentSlotGroup.FEET;
        }
        return EquipmentSlotGroup.MAINHAND;
    }
}
