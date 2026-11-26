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
| **Built-in editor** | Settings menu with a description for every option, and drag-and-drop placement for every on-screen element. Also reachable from Mod Menu. |
| **Behind-walls mode** | Mob box, glow and Theodolite blocks: always visible, faded behind walls, or only while in sight. |

## Trevor+ and Trevor+ Lite

Every release comes in two versions, built from the same code and published together:

| | **Trevor+** | **Trevor+ Lite** |
|---|---|---|
| Quest HUD, stats, dye odds, zone panel, cooldown | ✔ | ✔ |
| Alerts, sounds, Shuriken reminder, accept modes, warp key | ✔ | ✔ |
| Theodolite block highlight | ✔ (choose how it shows behind walls) | ✔ (visible blocks only, walls hide them) |
| Mob box | ✔ (choose how it shows behind walls) | ✔ (only while the mob is on screen and in sight) |
| Glow outline, direction line, off-screen pointer, behind-walls setting | ✔ | not included |
| Automatic quest accept | ✔ (off by default) | not included (warp key and click to accept only) |

In Lite the ESP code is compiled out, not just switched off. Both use the same `config/trevorplus/` folder, so you can switch between them and keep your stats, but only one can be installed at a time.

## Install

1. Install Fabric Loader (0.20.1 or newer) and [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.2.
2. Download Trevor+ from Modrinth, CurseForge or the GitHub releases, and put the jar in your `mods` folder.
3. Launch the game. Everything is saved in `config/trevorplus/`.

## First five minutes

1. Join a world or server. The **welcome screen** opens once: it fills in your username (so your own Pelt Dye is recognised) and lets you set your Vincent multiplier, behind-walls mode, sounds and how quests are accepted.
2. Press **Arrange HUDs** there (or **Move HUDs** in the menu) and drag the panels, the direction line and the alert banner where you like them.
3. Take a quest from Trevor. The HUD starts on its own.
4. Any time later: `/tp` (or `/trevorplus ui`) opens the full menu, `/trevorplus welcome` shows the welcome screen again.

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
| | `welcome` | Show the first-launch screen again |
| | `status` | Print your stats in chat |
| | `nav` | The warp key without the keybind |
| | `pause`, `resume`, `reset` | Session clock control, or start a fresh session |
| Panels | `hud`, `hud2`, `zone` | Show or hide the quest, creature and zone panels |
| | `hud pos <x> <y>` | Move a panel (`hud2 pos`, `zone pos` too) |
| Features | `highlight`, `blocks`, `rings` | Mob highlight, Theodolite blocks, dotted rings |
| | `blocks filter` | Only highlight blocks on the hint rings |
| | `esp always\|dim\|visible` | Mob box, glow and blocks behind walls: always / faded / only in sight |
| | `alerts [test]`, `alertmin <rarity>` | Rarity banners and their minimum rarity |
| | `sound [test]` | Sound cues on or off |
| | `autohide [seconds]` | Toggle auto-hide, or set how long you can be away first |
| | `shuriken on\|off\|learn\|clear` | Reminder and the Shuriken icon marker |
| | `shuriken tier <rarity>` | Switch the reminder on or off for one rarity (all on by default) |
| | `accept manual\|click\|auto` | How Trevor's `[YES]` is accepted: warp key or chat (default), an on-screen click, or automatically (`auto` is not in Lite) |
| | `warpdelay <seconds>` | Smallest gap between two warp-key presses, 0.5 to 10 s in 0.5 s steps (default 3) |
| Settings | `mult <1-3>` | Vincent multiplier |
| | `name <username>` | Your name, to recognise your dye |
| | `cooldown <seconds>` | Assumed Trevor cooldown (his own message overrides it) |
| | `fov <n>` | Fallback FOV for the on-screen mob box |
| | `warp <AREA> </warp cmd>`, `warps` | Change or list the warp for each area |
| Fix data | `data logdye` | Log a Pelt Dye you missed |
| | `data quests <n>` | Set quests since your last dye |
| | `data counts <t> <u> <d> <e> <el>` | Set your creature totals |
| | `data elusivegap <n>` | Set creatures since the last Elusive |

> `/tp` is also the vanilla teleport command. Trevor+ only claims the bare `/tp`; if you ever need the vanilla one, use `/minecraft:tp`.

## How it behaves

- **Reads, never snoops.** It only looks at chat, the sidebar, and entities and blocks your client already has loaded.
- **Sends almost nothing.** One command per press of the warp key. The only other thing it can send is Trevor's own `[YES]` command: when you accept with the key or the on-screen button, or by itself if you choose *Automatic*.
- **Click to accept.** For players who would rather not automate anything: when Trevor offers a quest, a button appears for 5 seconds and one left click (or a click on it with chat open) takes the quest.
- **One update check.** At start-up it asks CurseForge for Trevor+'s public file list (no account or player data is sent) and tells you once in chat if a newer version is out. Turn it off with "Check for updates" in the menu.
- **Stays out of the way.** After you have been outside the Farming Islands for a set time (default 1 minute, change it in the menu or with `/trevorplus autohide <seconds>`), every panel, banner, highlight and sound switches off, and the warp key and auto-accept stop doing anything. It all comes back when you return or start a quest. The settings menu and `/tp` still work anywhere.
- **Your chat, your call.** Each chat line Trevor+ prints (mob found, quest accepted, warp info, dye recorded) has its own toggle in the menu.
- **Server hops reset the quest.** Leaving or hopping servers drops the active quest without saving it, so your stats and history are not polluted. Past quests, totals and dye odds are untouched.
- **Quests are saved as they finish**, so a crash loses nothing.

## Files

Everything lives in `config/trevorplus/`: `settings.json`, `state.json`, `quests.jsonl`, `dyes.jsonl` and `sessions.jsonl`. Delete a file to reset that part.

## Troubleshooting

Report bugs on the [issue tracker](https://github.com/AirmailSoap3209/trevorplus/issues).

| Symptom | Try |
|---|---|
| Zone shows "unknown" | Turn on debug mode (below), run `/trevorplus debug sidebar` and paste the output into an issue. |
| A mob is not detected | Turn on debug mode, stand near it and run `/trevorplus debug nametag`, then attach `config/trevorplus/nametag-dump.txt`. |
| The Shuriken icon is not recognised | Look at a mob that has one and run `/trevorplus shuriken learn`. |
| No sounds | Check `/trevorplus sound test`, then look for `[sound]` in `logs/latest.log`. |
| Blocks only show as flat tops | Look for `[xray]` in `logs/latest.log`: the 3D renderer fell back to the cheap overlay. |

**Debug mode.** Diagnostic commands are hidden by default. To turn them on, close the game, add `"debugMode": true` to `config/trevorplus/settings.json`, and start it again. This adds `/trevorplus debug nametag | sidebar | sim <chat line>` and writes every quest event to `logs/latest.log`. Remove the line again when you are done.

## Building

```
./gradlew build          # in your project root, needs Java 25; both jars land in build/libs/
sh tools/run-tests.sh    # the pure logic tests, no Minecraft needed
python3 tools/make_icon.py
```

The code is split in two: `src/main` is the tested logic (no Minecraft classes), `src/client` is the thin Minecraft layer.
The Lite jar is the same client code compiled a second time with `BuildFlags.ESP = false` (generated by the build), which leaves every `if (BuildFlags.ESP)` block out of its bytecode.
See [docs/CHANGELOG.md](docs/CHANGELOG.md) for what changed in each milestone, and [docs/PUBLISHING.md](docs/PUBLISHING.md) for how releases go out to Modrinth and CurseForge.

## Credits

Made by **AirmailSoap3209**. Spawn weights, ring maths and several 26.2 rendering details were cross-checked against SkyHanni's open source code.
Not affiliated with Hypixel or Mojang. Check Hypixel's rules for what mods are allowed before you use any of it.
