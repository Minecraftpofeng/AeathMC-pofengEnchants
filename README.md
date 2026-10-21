# AeathMC-pofengEnchants

一款与 **EcoEnchants** 指令、配置格式、权限体系一致的自定义附魔插件。
基于 **Paper 1.21.1**，通过 Paper 官方 registry bootstrap 把自定义附魔注册为**真实附魔**，
因此能被附魔台、铁砧、磨石以及其它插件原生识别（与 EcoEnchants 的实现方式一致）。

> **开源协议：GPL-3.0**（详见 [LICENSE](LICENSE)）。你可以自由使用、修改、分发，但任何衍生作品必须保持开源、同样采用 GPL-3.0 授权，禁止被拿去闭源商用。


> 说明：EcoEnchants 开源有 200+ 附魔、约两万行代码，本插件一次交付其**完整框架 + 32 个横跨所有物品类别的核心附魔**，
> 并保留与 EcoEnchants 完全一致的扩展方式——在 `enchantments/*.yml` 加配置即可继续扩展。

## 一、环境要求

- **JDK 21**（Paper 1.21.1 要求；低于 21 无法编译/运行）
- **Paper 1.21.1** 服务端（不适用于 Spigot——注册真实附魔依赖 Paper 的 registry API）
- Maven 3.8+

## 二、打包

在项目根目录（含 `pom.xml`）执行：

```
mvn clean package
```

产物：`target/AeathMC-pofengEnchants.jar`

## 三、安装

1. 将 `AeathMC-pofengEnchants.jar` 放入服务端 `plugins/` 目录。
2. 启动服务器。首次启动会自动生成配置：`plugins/AeathMC-pofengEnchants/config.yml`、`rarity.yml`、`targets.yml`、`en.yml`、
   `plugins/AeathMC-pofengEnchants/enchantments/*.yml`（每附魔一个配置文件，格式与 EcoEnchants 完全一致）。
3. 服务器日志会显示已启用的附魔数量。

> 新增/修改附魔定义后需要**重启服务器**（registry 注册发生在服务器引导阶段）。

## 四、指令

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/ecoenchants giverandombook <player>` | `ecoenchants.command.giverandombook` | 给玩家一本随机附魔书 |
| `/ecoenchants randomenchant [player]` | `ecoenchants.command.randomenchant` | 给手持物品添加随机附魔 |
| `/ecoenchants reload` | `ecoenchants.command.reload` | 重载配置（消息/稀有度/开关） |
| `/enchantinfo <enchant>` | `ecoenchants.command.enchantinfo` | 查看附魔信息 |

附魔键名即权限后缀，例如 `Curse of Fragility` → 键名 `curse_of_fragility`。
控制某个附魔能否从附魔台获得：`ecoenchants.fromtable.<key>`（默认全部开放，`ecoenchants.fromtable.*`）。

## 五、配置结构（与 EcoEnchants 一致）

每个附魔一个 yml（`enchantments/*.yml`）：

```yaml
name: "Beheading"
description: Chance of getting player or mob head
enabled: true

obtaining:
  table: true
  villager: true
  loot: true
  rarity: rare

general-config:
  targets:
  - swords
  - axes
  grindstoneable: true
  conflicts: []
  maximum-level: 3

config:
  chance: 0.1
```

- `enabled: false` 时该附魔效果不生效、不可抽取。
- `obtaining` 控制附魔台/村民/战利品获取与稀有度。
- `general-config.targets` 对应 `targets.yml` 中的物品分组。
- `rarity.yml` 定义稀有度权重。

## 六、已实现附魔（32 个）

- **剑/斧**：Beheading、Criticals、First Strike、Bleed、Leeching、Thor、Supercritical、Toxic、Wither Aspect、Razor(特)
- **挖掘**：Blast Mining、Vein、Infernal Touch、Telekinesis、Wisdom、Energizing(特)
- **弓弩**：Boss Hunter、Optics、Tripleshot、Force(特)、Zeus
- **护甲**：Evasion、Preservation(特)、Thrive
- **靴子**：Magnetic、Spring(特)、Freerunner
- **通用**：Soulbound、Indestructibility(特)、Repairing(特)
- **诅咒**：Curse of Fragility、Curse of Misfortune

## 七、如何新增附魔

1. 在 `src/main/java/com/ecoclone/Enchants.java` 的 `definitions()` 中按已有行加一行定义（键名/显示名/描述/最大等级/权重/目标物品/成本）。
2. 在 `src/main/resources/enchantments/` 加一个同名 yml（复制任意现有文件改内容）。
3. 在对应监听器（CombatListener / MiningListener / ProjectileListener / ArmorListener / UtilityListener）里写触发逻辑，
   用 `Enchants.level(物品, "键名")` 读取等级。
4. 重启服务器。

## 八、参考

- 原版 EcoEnchants（开源）：https://www.spigotmc.org/resources/ecoenchants-217-custom-enchantments.59591/
- Paper 注册表 API 文档：https://docs.papermc.io/paper/dev/registries/
