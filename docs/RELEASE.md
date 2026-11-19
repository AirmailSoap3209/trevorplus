# Trevor+ 1.0 – a pelt-grinding companion for Hypixel SkyBlock

**By AirmailSoap3209** · Minecraft 26.2 · Fabric · Java 25 · client-side only

Trevor+ takes the busywork out of Trevor the Trapper. It times your quests, finds the mob, points you at the right blocks with the Theodolite, tells you when something rare shows up, and keeps the numbers that matter (pelts per hour above all) on a HUD you can put wherever you like.

## What's in it

**Tracking**
- Quest timer with average, best and worst kill time, quests per hour and **pelts per hour**, plus a session clock that ignores long idle gaps.
- Dye odds with your Vincent multiplier, quests since your last dye, and your luck over time.
- Creature HUD: how many of each rarity you have gotten (session and total), creatures since the last Elusive, and the expected gap.
- Cooldown timer: "Next quest in 0:23", then "Trevor ready".
- Quests are saved as they finish, so a crash loses nothing. A server hop drops the active quest without saving it, so your stats stay clean.

**Finding the mob**
- An on-screen box around the quest mob (through walls) and a direction line at the top with distance, height hint and Shuriken status. Works for horses, too.
- Zone panel, always on: the zone Trevor's mob is in, the zone you are in, and a green bar when they match.
- Theodolite aid: reads Trevor's hints, estimates the Y level and highlights the blocks at that height as real 3D boxes. Optional dotted hint rings.

**Alerts**
- Flashing banners for Endangered and Elusive quests (you choose the minimum rarity), Pelt Dyes, "mob found" and "Trevor ready".
- Shuriken reminder when the mob is close and its name tag shows none.
- Optional sound cues for each of those.

**Controls and comfort**
- One smart warp key (default G): accepts Trevor's prompt, otherwise warps to the quest area, otherwise to the Trapper. One command per press. Optional auto-accept.
- Built-in menu (`/tp` or `/trevorplus`) with a description for every setting.
- Every on-screen element is draggable: both HUDs, the zone panel, the direction line and the alert banner.
- Everything switches off a minute after you leave the Farming Islands, and returns when you do.
- Grouped, coloured `/trevorplus help`.

## Good to know

- Client-side only. It reads chat, the sidebar and what your client already has loaded. The only things it ever sends are the warp-key command and, if you turn it on, Trevor's own `[YES]` command.
- Not affiliated with Hypixel or Mojang. Check Hypixel's rules for mods before you use it.
- `/tp` opens the menu; the vanilla teleport is still available as `/minecraft:tp`.
- Setup: `/trevorplus name <your username>` so your own dye is recognised, then set your Vincent multiplier.

## Honest status

The logic (timing, stats, dye odds, parsing, cooldown, zone detection, auto-hide) is covered by 363 automated checks. The Minecraft-facing parts, especially the 3D block highlight, sounds and zone reading on the live sidebar, were built carefully against 26.2 but are young. If something misbehaves, `/trevorplus debug sidebar` and `/trevorplus debug nametag` give me exactly what I need to fix it, and the log lines starting with `[xray]` and `[sound]` say if a feature switched itself off.

## What to look forward to

- **Per-zone stats:** average kill time and rarity counts per zone, to show where grinding pays off.
- **Coins per hour:** pelt value tracking alongside pelts per hour.
- **CSV export** of your quest history.
- **Stuck-quest warning** when a quest runs unusually long (despawned mob, wrong zone).
- **"Quests since last dye"** readout on its own small panel.
- Anything you report: zone names I missed, mobs that are not detected, sounds you would swap.

Feedback and bug reports welcome. Happy grinding!
