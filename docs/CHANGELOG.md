# Trevor+ changelog (per milestone, newest at the bottom)

Client-side Fabric mod for Hypixel SkyBlock Trevor/pelt grinding.

## What works (logic is unit-tested: 204 checks; the Minecraft glue is NOT yet run in game)
- Quest timer: starts at Trevor's "You can find your X animal near Y" line, stops at the reward line.
  Time between quests is never counted. Outcomes: killed, mob died randomly, expired, lost, interrupted.
- Pelts, pelts/hour, pelts/min, pelts/quest, quests/hour, average/fastest/slowest time, per-rarity and
  per-animal averages. Pelt counts are read from the real reward lines.
- Session clock: idle gaps capped at 5 minutes, pause/resume/reset, history saved to sessions.jsonl.
- Dye luck (the dye has no pity): per-rarity odds x Vincent multiplier, chance of having seen a dye by now,
  quests since last dye, lifetime dyes vs expected. Only YOUR dye line counts (set `/trevorplus name`).
- Smart warp key (default G, rebindable in Controls): quest active -> warps to the animal's area,
  otherwise /warp trapper. One command per key press.
- HUD (translucent black, default font): quest, nav target, timing, quests, pelts, session, dye.
- Everything is saved as it happens (config/trevorplus/): quests.jsonl, dyes.jsonl, sessions.jsonl, state.json.

## Commands (/trevorplus ...)
status, nav, pause, resume, reset, dye, warps, help, hud, hud pos <x> <y>, mult <1-3>, name <username>,
setquests <n>, warp <AREA> </warp command>, sim <chat line>, dumpnametag

## First things to do in game
1. `/trevorplus name <your username>` (needed to recognise your own dye).
2. `/trevorplus mult 2` or `3` when Vincent's multiplier is active (`1` when off).
3. Do a quest and watch the HUD. If anything looks wrong, send me `logs/latest.log` lines containing
   `[engine]` or `Trevor+`.
4. Stand near a Trapper animal and run `/trevorplus dumpnametag`, then once more with a Shuriken on it.
   Send me config/trevorplus/nametag-dump.txt so I can build the Shuriken detector.

## Not built yet
Accepting the [YES] prompt with the key, entity highlighting/mob list, rarity alerts and Shuriken reminder,
Theodolite rings in the world, HUD editor and config screen, session comparison view.

## Unverified assumptions
1. Multiplier applies per completed kill and is read when the quest completes.
2. "Mob died randomly" quests do not advance dye odds (`selfDeathCountsForDye` setting).
3. Your own dye broadcast looks like other players' (`/trevorplus dye` logs one manually).
4. The scoreboard line "Tracker Mob Location:" is followed by the area on the next line.
5. 26.2 API calls: confirmed from SkyHanni's 26.2 jar except Keybinds.java (key registration), which is a
   best guess and may need a one-line fix.
6. Animal render distances in Animal.java come from SkyHanni and are unchecked.

## Added in milestone 3

- **Mob highlight** (`/trevorplus highlight` toggles). While a quest is active the mod looks through the entities your client already has loaded. It recognises the quest mob by its name tag (same parser as before) or by max health matching the rarity. The animal gets a glow outline (visible through blocks) and a line at the top of the screen shows rarity, distance, direction and height.
- **Auto-accept** (`/trevorplus autoaccept on|off`, default **off**). When Trevor's `[YES]` button appears (and Trevor spoke within the last 5 seconds, and no quest is running) the mod sends that button's command about 0.25 s later. It only ever answers Trevor's own prompt, once per prompt. The nav key does the same thing on demand if you leave auto-accept off. This is an automated action: check Hypixel's current rules for your account before enabling it.
- **Creature HUD** (`/trevorplus hud2`, `hud2 pos x y`). Per rarity: this session / all time, plus creatures since the last Elusive, last / average / longest gap, and the expected 1-in-101. Counted when a quest is accepted. Sync with another mod using `/trevorplus setcounts t u ud e el` and `/trevorplus elusivegap n`.
- `/trevorplus dumpnametag` now also records class, max health and invisibility, so the finder can be tuned.

## Milestone 3.1

- The mob highlight is now an on-screen box drawn from plain camera math (`navigation/Projection`), visible through walls, with a pointer at the screen edge when the mob is off screen or behind you. It reads the game's FOV setting by name; if that fails, `/trevorplus fov <n>` (or the settings screen) is used.
- The vanilla glow outline is still attempted as a bonus, but does not need to work.
- HUD 1 redesigned: dark panel, rarity-coloured accent bar, grey labels, bright values.
- `/trevorplus ui` opens a settings screen (also bindable under Controls > Trevor+). Drag either HUD box in it to move it.

## Milestone 4

- **Rarity alerts**: a flashing banner (screen only, never chat) when a quest of at least the chosen rarity starts, and when your own dye drops. `/trevorplus alerts` toggles, `/trevorplus alertmin <rarity>` sets the minimum (default Endangered), `/trevorplus alerts test` previews it.
- **Shuriken reminder**: while the quest mob is within 40 blocks and its name tag shows no Shuriken marker for 1.5 s, an "APPLY SHURIKEN" banner flashes, repeating every 12 s until the marker appears. The marker is learned from your own name tag: stand next to a mob that has your Shuriken on it and run `/trevorplus shuriken learn`. Without learning, any blue symbol on the tag counts. `/trevorplus shuriken on|off|clear`.
- **HUD editor**: `/trevorplus ui` > Move HUDs. Drag the boxes, press Done. Reset HUD positions is in the same list.

## Milestone 5: Theodolite rings

- Each Theodolite hint draws a dotted ring on screen (through walls) at the animal's estimated height, centred where you used the item. The newest ring is bright, older ones fainter (last four kept). Where the two newest rings cross, a gold "Likely" box shows the distance.
- The quest HUD gets a THEODOLITE section while hints are active: Y band, rings used, how far you are from the latest ring, and the nearest likely spots with coordinates.
- `/trevorplus rings` toggles it (also in the settings screen).
- Shuriken detection also accepts a non-heart symbol after the health text (e.g. the star after the heart).

## Milestone 5.1

- Theodolite block highlight replaces the dotted circles as the default aid: every standing surface within 40 blocks whose top is within 1.5 blocks of the hinted height, and that you can see (sight-line test), is painted on screen. Nearest 800 are shown. `/trevorplus blocks` toggles; `/trevorplus blocks filter` limits it to blocks on the hint rings. Dotted rings are still available (`/trevorplus rings`, off by default).
- Shuriken: the marker is the symbol after the health heart (custom-font glyph, aqua). Detection is anchored on that heart, so split name-tag pieces work.

## Milestone 5.2 – Real 3D block highlight

The Theodolite block highlight is now drawn as real boxes in the world (all six faces, through walls),
submitted in a single batch per frame from the level-render event. This replaces the screen-space
top-face painting that cost most of the FPS.

- Scan: once per second (was twice), radius 32, at most 400 blocks, and only when a Y band is known.
- If the 3D pipeline cannot be created or throws, it logs `[xray] ...` and falls back to a capped
  top-face overlay (max 120 blocks) so it can never tank FPS again.
- Toggle with `/trevorplus blocks on|off`.

## Milestone 6 – Zone panel, setting descriptions, everything movable

- **Zone panel** (always on by default, `/trevorplus zone`): shows the zone of Trevor's mob (the quest
  area), the zone you are standing in (read from the sidebar location line), and whether they match.
  Its accent bar turns green when you are in the mob's zone and amber when not.
- **Settings descriptions:** hover any row in `/trevorplus ui` to read what it does.
- **Everything movable:** in "Move HUDs" you can now drag the quest HUD, creature HUD, zone panel,
  top direction line and the alert banner (they preview with sample text). "Reset positions" restores all.
  The on-screen mob box and Theodolite blocks stay attached to the world on purpose.

## Milestone 7 – Zone fix, cooldown timer, sounds, mob-found ping

- **Zone detection:** the sidebar location symbol was not the one I expected, so "You" showed unknown. The reader now
  accepts the known pins and, failing that, any line that starts with a symbol followed by a name (time, date and
  "Purse:" style lines are skipped). `/trevorplus dumpsidebar` prints each sidebar line with its first code points
  and the zone it read, for diagnosis.
- **Cooldown timer:** after a quest ends the quest HUD shows "Next quest in m:ss", then "Trevor ready". The assumed
  length is 30 s (`/trevorplus cooldown <s>`); Trevor's own "Try coming back in Ns!" message overrides it.
- **Ready ping:** banner plus sound when the cooldown ends (only if you have not started a new quest).
- **Sound cues** (`/trevorplus sound`, `sound test`): rarity alerts (higher rarity = higher chime, Elusive = level-up),
  Pelt Dye, mob found, Trevor ready, Shuriken reminder. Played as UI sounds, so distance never matters.
- **Mob-found ping:** banner "MOB FOUND - 23m" and a sound when the quest mob first loads in range.
- All three are toggles in the settings menu.

## Milestone 8 – Polish

- New README, mod icon (`tools/make_icon.py`) and mod description; author AirmailSoap3209.
- Server hop or disconnect now drops the active quest without saving it (no history entry, no stats, no dye odds).
  Cooldown and prompt state are reset too.
- `/tp` opens the menu; `/trevorplus` with no arguments opens the menu as well.
- Command cleanup: grouped, coloured `/trevorplus help`; fix-up commands moved under `data` (`logdye`, `quests`,
  `counts`, `elusivegap`) and diagnostics under `debug` (`nametag`, `sidebar`, `sim`).

## Milestone 9 – Auto-hide

- All overlays (panels, direction line, alert banner, mob box, Theodolite blocks and rings) and sounds switch off after
  one minute outside the Farming Islands, and return when you come back or a quest is active. Based on the sidebar zone;
  if the sidebar never names a zone the overlays stay on. Toggle: settings menu or `/trevorplus autohide`.

## Milestone 10

- Auto-hide delay is configurable (menu row or `/trevorplus autohide <seconds>`, default 60 s).
- While hidden, the warp key and auto-accept are disabled too. The menu key and `/tp` still open the menu.
- Individual toggles for the chat messages the mod prints: mob found, quest accepted, warp info, dye recorded.
- Settings menu is paged when it does not fit the screen.

## 2.3.0 – First public release

- First-launch welcome screen: detects your username, sets the Vincent multiplier, sounds and auto-accept, shows the
  warp key, and links to the HUD editor and full settings. Shown once (not for existing installs); reopen with
  `/trevorplus welcome` or the new "Welcome screen" row in the menu.
- `/trevorplus debug ...` is only registered in debug mode (`"debugMode": true` in settings.json, or a dev environment),
  and is hidden from `/trevorplus help` otherwise. Routine engine log lines are logged at debug level unless debug mode is on.
- Removed the template example mixins. Release jars are named `trevorplus-<version>+<mc>.jar`.
- Modrinth and CurseForge publishing via `./gradlew publishMods` and a release workflow (docs/PUBLISHING.md).
- `tools/run-tests.sh` works on Windows (Git Bash) too, and CI runs it.

## 2.4.0 – Behind-walls mode, Mod Menu, update notice

- ESP mode (`espMode`): Always / Dim behind walls / Only when visible, for the mob box and edge pointer, the glow
  outline and the 3D Theodolite blocks. Line of sight to the mob is a block walk every other tick (same test as the
  block scan); blocks use a second, depth-tested pipeline. The direction line stays regardless. New installs default
  to Dim, existing settings files to Always. Menu row, welcome-screen row, `/trevorplus esp`.
- Mod Menu (optional, compileOnly): Configure opens the settings screen, which now returns to its parent on close.
- Update check against CurseForge's public file list (approved release files for the running MC version only),
  announced once in chat with a link; also Mod Menu's update checker. `updateCheck` setting, on by default.
  Project ID and slug come from gradle.properties via fabric.mod.json.
- Settings menu rebuilt: category sidebar (General, HUD, Mob finder, Theodolite, Alerts, Chat), section headers,
  pill toggles, dependent rows indented and disabled while their parent is off, wheel scrolling instead of pages,
  last category remembered.
- Accept mode (`acceptMode`: MANUAL / CLICK / AUTO) replaces the `autoAccept` flag (old `true` becomes AUTO).
  CLICK shows a movable button with a countdown while the 5 s prompt window is open; a left click in game (swallowed
  before vanilla's attack handling) or a click on the button with chat open accepts. `/trevorplus accept`, with
  `autoaccept on|off` kept as an alias.
- Shuriken reminder per rarity (`shurikenRarities`, all by default): menu rows and `/trevorplus shuriken tier <rarity>`.

## 2.5.0 – Trevor+ Lite

- Second variant, Trevor+ Lite (mod id `trevorplus-lite`), from the same sources. The build generates
  `BuildFlags` (ESP, MOD_ID, NAME) per variant; `compileLiteClientJava` + `processLiteResources` + `liteJar` build the
  lite jar, and `build` produces both. ESP code is guarded by `if (BuildFlags.ESP)` so javac drops it from the lite
  bytecode: mob box, edge pointer and direction line (`renderFinder`, `drawFinderBar`), glow (`MobFinder.setGlow`),
  the see-through block pipeline and the screen-overlay fallback. Lite always draws Theodolite boxes depth-tested.
  Mob-found ping and chat line stay in Lite without the distance.
- `fabric.mod.json` is a template now (id, name, description, `breaks` on the other variant, CurseForge id/slug).
- Publishing: `modrinthLite` / `curseforgeLite` targets with their own jar and display name; the release workflow's
  setup check covers them.
