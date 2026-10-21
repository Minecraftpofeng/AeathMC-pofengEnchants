# EcoClone 附魔配置生成脚本（一次性）
# 为 src/main/resources/enchantments/ 生成 32 个附魔配置文件，格式与 EcoEnchants 一致
$ErrorActionPreference = "Stop"
$outDir = "C:\Users\admin\Doubao\chats\2026-10-19\new-chat-1\EcoClone\src\main\resources\enchantments"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# key, name, description, maxLevel, rarity, targets(string[]), configBlock(可空)
$rows = @(
  @{key="beheading";          name="Beheading";           desc="Chance of getting player or mob head";                     max=3; rarity="rare";     tg=@("swords","axes");  cfg=@("chance: 0.1")},
  @{key="criticals";          name="Criticals";           desc="Increases critical damage";                                  max=3; rarity="uncommon"; tg=@("swords","axes");  cfg=@("multiplier: 0.10")},
  @{key="first_strike";       name="First Strike";        desc="Increases damage dealt when entity is on max health";        max=5; rarity="rare";     tg=@("swords");         cfg=@("multiplier: 0.15")},
  @{key="bleed";              name="Bleed";               desc="Causes your opponent to bleed, damaging them repeatedly";    max=7; rarity="rare";     tg=@("swords");         cfg=@("chance: 0.10","duration: 40")},
  @{key="leeching";           name="Leeching";            desc="Heals a portion of damage dealt";                            max=6; rarity="epic";     tg=@("swords","axes");  cfg=@("multiplier: 0.05")},
  @{key="thor";               name="Thor";                desc="Chance of striking lightning on your opponent";              max=6; rarity="epic";     tg=@("swords");         cfg=@("chance: 0.03")},
  @{key="supercritical";      name="Supercritical";       desc="Small chance to deal extreme damage";                        max=3; rarity="mythic";   tg=@("swords");         cfg=@("chance: 0.02","damage-multiplier: 3")},
  @{key="toxic";              name="Toxic";               desc="Chance to apply poison to your opponent";                    max=2; rarity="common";   tg=@("swords");         cfg=@("chance: 0.20","duration: 60")},
  @{key="wither_aspect";      name="Wither Aspect";       desc="Chance to apply wither to your opponent";                    max=2; rarity="uncommon"; tg=@("swords");         cfg=@("chance: 0.15","duration: 60")},
  @{key="razor";              name="Razor";               desc="Massively increases damage dealt";                            max=8; rarity="mythic";   tg=@("swords");         cfg=@("multiplier: 0.25")},

  @{key="blast_mining";       name="Blast Mining";        desc="Mines blocks in a 3x3 area";                                 max=1; rarity="rare";     tg=@("pickaxes");       cfg=@()},
  @{key="vein";               name="Vein";                desc="Mine blocks in a vein";                                      max=5; rarity="epic";     tg=@("pickaxes");       cfg=@("radius: 3")},
  @{key="infernal_touch";     name="Infernal Touch";      desc="Automatically smelt mined blocks";                           max=1; rarity="rare";     tg=@("tools");          cfg=@()},
  @{key="telekinesis";        name="Telekinesis";         desc="Drops and xp go directly into your inventory";               max=1; rarity="epic";     tg=@("tools");          cfg=@()},
  @{key="wisdom";             name="Wisdom";              desc="Increases dropped experience";                                max=3; rarity="uncommon"; tg=@("tools");          cfg=@("multiplier: 2")},
  @{key="energizing";         name="Energizing";          desc="Receive a short burst of haste after breaking a block";      max=3; rarity="mythic";   tg=@("tools");          cfg=@("duration: 40")},

  @{key="boss_hunter";        name="Boss Hunter";         desc="Increases damage against bosses";                             max=8; rarity="epic";     tg=@("bows","crossbows"); cfg=@("multiplier: 0.15")},
  @{key="optics";             name="Optics";              desc="Increases damage when further from target";                   max=5; rarity="rare";     tg=@("bows","crossbows"); cfg=@("multiplier: 0.05")},
  @{key="tripleshot";         name="Tripleshot";          desc="Shoots 3 arrows";                                             max=1; rarity="mythic";   tg=@("bows");           cfg=@()},
  @{key="force";              name="Force";               desc="Massively increases arrow damage";                            max=8; rarity="mythic";   tg=@("bows");           cfg=@("multiplier: 0.25")},
  @{key="zeus";               name="Zeus";                desc="Chance to strike lightning on your opponent";                 max=6; rarity="epic";     tg=@("bows","crossbows"); cfg=@("chance: 0.03")},

  @{key="evasion";            name="Evasion";             desc="Chance to ignore incoming damage";                            max=3; rarity="epic";     tg=@("armor");          cfg=@("chance: 0.08")},
  @{key="preservation";       name="Preservation";        desc="Massively reduces incoming damage";                           max=4; rarity="mythic";   tg=@("armor");          cfg=@("multiplier: 0.15")},
  @{key="thrive";             name="Thrive";              desc="Increases maximum health";                                    max=2; rarity="rare";     tg=@("armor");          cfg=@("health-per-level: 2")},

  @{key="magnetic";           name="Magnetic";            desc="Items and XP go towards you";                                 max=4; rarity="uncommon"; tg=@("boots");          cfg=@("radius: 3")},
  @{key="spring";             name="Spring";              desc="Increases jump height and distance";                          max=3; rarity="mythic";   tg=@("boots");          cfg=@()},
  @{key="freerunner";         name="Freerunner";          desc="Chance to ignore fall damage";                                max=6; rarity="rare";     tg=@("boots");          cfg=@("chance: 0.20")},

  @{key="soulbound";          name="Soulbound";           desc="Keep this item on death";                                     max=1; rarity="mythic";   tg=@("everything");     cfg=@()},
  @{key="indestructibility";  name="Indestructibility";   desc="Massively increases item durability";                         max=3; rarity="mythic";   tg=@("everything");     cfg=@()},
  @{key="repairing";          name="Repairing";           desc="Automatically repairs items when not held";                   max=3; rarity="mythic";   tg=@("everything");     cfg=@()},

  @{key="curse_of_fragility"; name="Curse of Fragility";  desc="Decreases item durability";                                   max=1; rarity="common";   tg=@("everything");     cfg=@("curse: true")},
  @{key="curse_of_misfortune";name="Curse of Misfortune"; desc="Chance of blocks to not drop items";                          max=1; rarity="common";   tg=@("tools");          cfg=@("curse: true","chance: 0.25")}
)

foreach ($r in $rows) {
  $tg = ($r.tg | ForEach-Object { "  - $_" }) -join "`n"
  $cfgBlock = ""
  if ($r.cfg.Count -gt 0) {
    $cfgLines = ($r.cfg | ForEach-Object { "  $_" }) -join "`n"
    $cfgBlock = "`nconfig:`n$cfgLines"
  }
  $content = @"
#
# $($r.name) EcoEnchant
#
name: "$($r.name)"
description: $($r.desc)
enabled: true

obtaining:
  table: true
  villager: true
  loot: true
  rarity: $($r.rarity)

general-config:
  targets:
$tg
  grindstoneable: true
  conflicts: []
  maximum-level: $($r.max)
$cfgBlock
"@
  $out = Join-Path $outDir ($r.key + ".yml")
  [System.IO.File]::WriteAllText($out, $content, (New-Object System.Text.UTF8Encoding $false))
}
"Generated " + $rows.Count + " enchantment files."
