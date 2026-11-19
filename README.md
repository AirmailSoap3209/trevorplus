<div align="center">

<img src="docs/icon-128.png" alt="Trevor+ icon" width="112" height="112">

# Trevor+

**A pelt-grinding companion for Hypixel SkyBlock's Trevor the Trapper.**
Timer, finder, Theodolite aid, alerts and a HUD you can move anywhere.

`Minecraft 26.2` · `Fabric` · `Java 25` · `client-side only` · by **AirmailSoap3209**

</div>

---

## Highlights

| | |
|---|---|
| **Quest HUD** | Live quest timer, average / best / worst kill time, quests per hour, **pelts per hour**, session clock, and dye odds with your Vincent multiplier. |
| **Zone panel** | Always on. Shows the zone Trevor's mob is in, the zone you are in, and turns green when they match. |
| **Mob finder** | Boxes the quest mob on screen through walls, with a direction line, distance and height hint at the top. Finds horses, too. |
| **Theodolite aid** | Reads Trevor's hints, estimates the Y level and highlights every visible block at that height, as real 3D boxes. |
| **Creature HUD** | How many of each rarity you have gotten, creatures since the last Elusive, and the expected gap. |
| **Alerts and sounds** | Flashing banners for Endangered and Elusive quests, Pelt Dyes, "mob found" and "Trevor ready", each with its own sound. |
| **Shuriken reminder** | Tells you to apply a Shuriken when the mob is close and its name tag shows none. |
| **Cooldown timer** | "Next quest in 0:23", then "Trevor ready". |
| **Smart warp key** | One key: accept the quest prompt, warp to the mob's area, or warp to the Trapper. |
| **Built-in editor** | Settings menu with a description for every option, and drag-and-drop placement for every on-screen element. |

## Install

1. Install Fabric Loader (0.20.1 or newer) and Fabric API for Minecraft 26.2.
2. Put the Trevor+ jar in your `mods` folder.
3. Launch the game. Everything is saved in `config/trevorplus/`.

## First five minutes

1. Press `/tp` (or `/trevorplus ui`) to open the menu. Hover any row to see what it does.
2. Run `/trevorplus name <your username>` so your own Pelt Dye is recognised.
3. Set your Vincent multiplier in the menu, or with `/trevorplus mult 2`.
4. Press **Move HUDs** in the menu and drag the panels, the direction line and the alert banner where you like them.
5. Take a quest from Trevor. The HUD starts on its own.

## Controls

| Key | Action |
|---|---|
| **G** (rebindable under *Controls > Trevor+*) | Smart warp: accept Trevor's prompt, otherwise warp to the quest area, otherwise `/warp trapper`. Sends exactly one command per press. |
| Unbound by default | Open the Trevor+ menu. |
| `/tp` | Opens the menu. |

## Commands

`/trevorplus` and `/tp` both open the menu. `/trevorplus help` prints this list in game.

| Group | Command | What it does |
|---|---|---|
| Everyday | `ui` | Settings menu and HUD editor |
| | `status` | Print your stats in chat |
| | `nav` | The warp key without the keybind |
| | `pause`, `resume`, `reset` | Session clock control, or start a fresh session |
| Panels | `hud`, `hud2`, `zone` | Show or hide the quest, creature and zone panels |
| | `hud pos <x> <y>` | Move a panel (`hud2 pos`, `zone pos` too) |
| Features | `highlight`, `blocks`, `rings` | Mob highlight, Theodolite blocks, dotted rings |
| | `blocks filter` | Only highlight blocks on the hint rings |
| | `alerts [test]`, `alertmin <rarity>` | Rarity banners and their minimum rarity |
| | `sound [test]` | Sound cues on or off |
| | `shuriken on\|off\|learn\|clear` | Reminder and the Shuriken icon marker |
| | `autoaccept on\|off` | Accept Trevor's `[YES]` automatically (off by default) |
| Settings | `mult <1-3>` | Vincent multiplier |
| | `name <username>` | Your name, to recognise your dye |
| | `cooldown <seconds>` | Assumed Trevor cooldown (his own message overrides it) |
| | `fov <n>` | Fallback FOV for the on-screen mob box |
| | `warp <AREA> </warp cmd>`, `warps` | Change or list the warp for each area |
| Fix data | `data logdye` | Log a Pelt Dye you missed |
| | `data quests <n>` | Set quests since your last dye |
| | `data counts <t> <u> <d> <e> <el>` | Set your creature totals |
| | `data elusivegap <n>` | Set creatures since the last Elusive |
| Debug | `debug nametag`, `debug sidebar` | Write nametags / print the sidebar, for bug reports |
| | `debug sim <chat line>` | Feed a chat line through the parser |

> `/tp` is also the vanilla teleport command. Trevor+ only claims the bare `/tp`; if you ever need the vanilla one, use `/minecraft:tp`.

## How it behaves

- **Reads, never snoops.** It only looks at chat, the sidebar, and entities and blocks your client already has loaded.
- **Sends almost nothing.** One command per press of the warp key. The only other thing it can send is Trevor's own `[YES]` command, and only if you turn on auto-accept.
- **Server hops reset the quest.** Leaving or hopping servers drops the active quest without saving it, so your stats and history are not polluted. Past quests, totals and dye odds are untouched.
- **Quests are saved as they finish**, so a crash loses nothing.

## Files

Everything lives in `config/trevorplus/`: `settings.json`, `state.json`, `quests.jsonl`, `dyes.jsonl` and `sessions.jsonl`. Delete a file to reset that part.

## Troubleshooting

| Symptom | Try |
|---|---|
| Zone shows "unknown" | `/trevorplus debug sidebar` and send me the output. |
| A mob is not detected | Stand near it and run `/trevorplus debug nametag`. |
| The Shuriken icon is not recognised | Look at a mob that has one and run `/trevorplus shuriken learn`. |
| No sounds | Check `/trevorplus sound test`, then look for `[sound]` in `logs/latest.log`. |
| Blocks only show as flat tops | Look for `[xray]` in `logs/latest.log`: the 3D renderer fell back to the cheap overlay. |

## Building

```
./gradlew build          # in your project root, needs Java 25
sh tools/run-tests.sh    # the pure logic tests, no Minecraft needed
python3 tools/make_icon.py
```

The code is split in two: `src/main` is the tested logic (no Minecraft classes), `src/client` is the thin Minecraft layer.
See [docs/CHANGELOG.md](docs/CHANGELOG.md) for what changed in each milestone.

## Credits

Made by **AirmailSoap3209**. Spawn weights, ring maths and several 26.2 rendering details were cross-checked against SkyHanni's open source code.
Not affiliated with Hypixel or Mojang. Check Hypixel's rules for what mods are allowed before you use any of it.
