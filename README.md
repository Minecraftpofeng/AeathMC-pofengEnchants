# AeathMC-pofengEnchants

一款与 **EcoEnchants** 指令、配置格式、权限体系一致的自定义附魔插件，面向 **Paper 1.21.1**。
通过 Paper 官方 registry bootstrap 把自定义附魔注册为**真实附魔**，因此能被附魔台、铁砧、磨石
以及其它插件原生识别（与 EcoEnchants 的实现方式一致）。

当前版本 **v1.4.6.8**：
**215 个自定义附魔** · **123 种宝石**（1-100 级 / 10 档 / 每件最多 100 槽） · **9 件神器** · **物质团** · **内置更新检测**

> 开源协议：GPL-3.0（详见 [LICENSE](LICENSE)）。可自由使用、修改、分发，但任何衍生作品必须保持开源并同样采用 GPL-3.0，禁止闭源商用。

## 一、下载

- GitHub Releases（推荐，含 jar 与更新日志）：https://github.com/Minecraftpofeng/AeathMC-pofengEnchants/releases/latest
- GitHub 仓库：https://github.com/Minecraftpofeng/AeathMC-pofengEnchants
- SpigotMC 资源页：https://www.spigotmc.org/resources/aeathmc-pofengenchants-rpg.139838/
- SpigotMC 讨论帖：https://www.spigotmc.org/threads/aeathmc-pofengenchants-rpg.725451/

## 二、功能总览

| 模块 | 内容 |
| --- | --- |
| 自定义附魔 | 215 个，覆盖剑 / 斧 / 弓 / 弩 / 镐 / 铲 / 锄 / 护甲 / 靴 / 三叉戟等全部物品类别；含战斗、远程、挖掘、护甲、被动、诅咒六大类 |
| 附魔书与卷轴 | 随机附魔书、指定附魔书、卷轴发放，GUI 一览与洗附魔 |
| 宝石系统 | 123 种宝石，等级 1-100，每 10 级一档共 10 档：凡品 / 精良 / 稀有 / 史诗 / 传说 / 神话 / 不朽 / 神王 / 混沌 / 究极·超越；每件装备最多 100 个槽位，副手持装备右键可取出宝石 |
| 神器系列 | 9 件：寰宇支配之剑、创世神刃、彩虹猫之刃、以太五阶剑（1-5）、晨曦破晓剑、暗渊噬魂剑、大楚兴陈胜王之锄、七宗罪·暴怒、无尽防具四件套 |
| 物质团 | 对齐《无尽贪婪》Avaritia 的收纳物品：容量 4096，装满后升级为「极质物质团」容量 ∞，右键优先释放进背包 |
| 更新检测 | 启动自动检测、管理员进服自动检测、手动 `/aeath update`，均输出最新 GitHub Release 链接与 SpigotMC 链接 |
| 软依赖 | PlaceholderAPI / Vault / ProtocolLib，未安装时静默跳过，不影响主功能 |

## 三、环境要求

- **Paper 服务端**（不支持 Spigot——注册真实附魔依赖 Paper 的 registry API）
- **兼容版本：Paper 1.21.1 ～ 1.26.3**（含 1.21.x 全系列与 1.26.x）
  - 编译目标为 Java 21 字节码：向下跑 1.21.1 的 Java 21，向上跑新版本的 Java 25
  - `api-version: '1.21'` 加官方 registry-bootstrap API，向前兼容新版
  - 启动时会打印当前服务端版本与「兼容 / 超出支持范围」判定
- **Java**：1.21.1 服需 Java 21；1.26.x 服需 Java 25（jar 本身两种环境都兼容）
- Maven 3.8+（仅编译需要）

## 四、打包与安装

在项目根目录（含 `pom.xml`）执行：

```
mvn clean package
```

产物：`target/AeathMC-pofengEnchants.jar`

安装：

1. 把 `AeathMC-pofengEnchants.jar` 放入服务端 `plugins/` 目录。
2. 启动服务器。首次启动自动生成配置：
   `plugins/AeathMC-pofengEnchants/config.yml`、`rarity.yml`、`targets.yml`、`en.yml`、
   以及 `plugins/AeathMC-pofengEnchants/enchantments/*.yml`（每个附魔一个文件，格式与 EcoEnchants 一致）。
3. 控制台会显示已启用的附魔数量、服务端版本兼容判定与更新检测结果。

> 新增或修改附魔定义后需要**重启服务器**（registry 注册发生在服务器引导阶段）。

## 五、指令

主命令 `/aeath`，权限统一为 `ecoenchants.command.<子命令>`（`ecoenchants.command.*` 为全量）。

| 指令 | 说明 |
| --- | --- |
| `/aeath help` | 指令帮助 |
| `/aeath giverandombook [玩家]` | 给随机附魔书 |
| `/aeath randomenchant [玩家]` | 给手持物品随机附魔 |
| `/aeath givebook <附魔键> <等级> [玩家]` | 发放指定附魔书 |
| `/aeath scroll <附魔键> <等级> [玩家]` | 发放附魔卷轴 |
| `/aeath enchant <附魔> <等级> [玩家]` | 直接上附魔（可突破原版上限） |
| `/aeath set <附魔键> <等级> [玩家]` | 设置手持物品附魔（覆盖同名等级） |
| `/aeath gui` | 打开附魔 GUI |
| `/aeath wipe [玩家]` | 洗掉手持物品的自定义附魔 |
| `/aeath reload` | 重载配置（消息 / 稀有度 / 开关） |
| `/aeath artifact [玩家]` | 寰宇支配之剑·最终幻想 |
| `/aeath admin [玩家]` | 创世神刃·管理之怒 |
| `/aeath rainbow [玩家]` | 彩虹猫之刃 |
| `/aeath aether [玩家] <阶段1-5>` | 以太系列五阶剑 |
| `/aeath dawn [玩家]` | 晨曦破晓剑 |
| `/aeath abyss [玩家]` | 暗渊噬魂剑 |
| `/aeath hoe [玩家]` | 大楚兴陈胜王之锄 |
| `/aeath wrath [玩家]` | 七宗罪·暴怒「嗜血重生」 |
| `/aeath armor <helmet\|chest\|legs\|boots\|set> [玩家]` | 无尽防具与物质团 |
| `/aeath gem <宝石> [数量] [玩家]` | 获得宝石 |
| `/aeath gemlv <宝石> <等级1-100> [玩家]` | 指定等级宝石 |
| `/aeath socket [数量] [玩家]` | 为手持装备开宝石槽 |
| `/aeath gems` | 全部宝石一览 |
| `/aeath geminfo` | 查看手持装备宝石槽 |
| `/aeath version` | 插件版本 / 服务端版本 / 支持范围 / 兼容判定 |
| `/aeath update` | 立即检测更新，结果直接回给执行者 |
| `/enchantinfo <附魔>` | 查看附魔信息 |

附魔键名即权限后缀，例如 `Curse of Fragility` → `curse_of_fragility`。
控制某个附魔能否从附魔台获得：`ecoenchants.fromtable.<key>`（默认全部开放，`ecoenchants.fromtable.*`）。

## 六、更新检测

三条触发路径，结果都带**最新 GitHub Release 链接**与 **SpigotMC 链接**：

1. 服务器启动时自动检测一次（有新版本时同时广播给在线玩家）。
2. 管理员进入游戏时自动检测：`isOp()` 或拥有 `ecoenchants.command.update` 的玩家进服 2 秒后收到检测结果，普通玩家不触发。
3. 手动 `/aeath update`：直接把当前版本、最新版本、GitHub Release 链接、SpigotMC 链接回给执行者。

实现细节：读取 GitHub Releases API 的 `tag_name` 与 `html_url`，结果缓存 5 分钟，避免频繁请求；
网络不可达时只提示检测失败，不影响插件运行。

## 七、配置结构（与 EcoEnchants 一致）

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
- `obtaining` 控制附魔台 / 村民 / 战利品获取与稀有度。
- `general-config.targets` 对应 `targets.yml` 中的物品分组。
- `rarity.yml` 定义稀有度权重。

## 八、如何新增附魔

1. 在 `src/main/java/com/ecoclone/Enchants.java` 的 `definitions()` 中按已有行加一行定义
   （键名 / 显示名 / 描述 / 最大等级 / 权重 / 是否诅咒 / 是否宝藏 / 稀有度 / 目标物品 / 数值区间 / 铁砧成本）。
2. 在 `src/main/resources/enchantments/` 加一个同名 yml（复制任意现有文件改内容）。
   **缺这个 yml 会导致插件启动失败**，三个文件必须同名成套。
3. 在 `NewEnchantListener`（或对应的 CombatListener / MiningListener / ProjectileListener / ArmorListener / UtilityListener）
   写触发逻辑，用 `Enchants.level(物品, "键名")` 读等级、`chance("键名", 基础概率, 等级)` 判概率、
   `manager.duration("键名", 默认时长)` 取时长。
4. 重启服务器。

## 九、参考

- 本项目 GitHub：https://github.com/Minecraftpofeng/AeathMC-pofengEnchants
- EcoEnchants（开源）：https://www.spigotmc.org/resources/ecoenchants-217-custom-enchantments.59591/
- EcoEnchants 全附魔列表：https://xiaomomi.gitbook.io/ecoenchants/fu-mo/suo-you-fu-mo
- Paper 注册表 API 文档：https://docs.papermc.io/paper/dev/registries/
