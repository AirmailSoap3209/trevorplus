# Trevor+

**A pelt-grinding companion for Hypixel SkyBlock's Trevor the Trapper.**

Trevor+ takes the busywork out of Trevor the Trapper. It times your quests, finds the mob, points you at the right blocks with the Theodolite, tells you when something rare shows up, and keeps the numbers that matter (pelts per hour above all) on a HUD you can put wherever you like.

Minecraft 26.2 · Fabric · requires Fabric API · client-side only

*Prefer no ESP at all? **Trevor+ Lite** boxes the mob only while it is in sight, has no glow outline or direction line, and is updated together with this one.*

## Features

**Tracking**
- Quest timer with average, best and worst kill time, quests per hour and **pelts per hour**, plus a session clock that ignores long idle gaps.
- Dye odds with your Vincent multiplier, quests since your last dye, and your luck over time.
- Creature HUD: how many of each rarity you have gotten (session and total), creatures since the last Elusive, and the expected gap.
- Cooldown timer: "Next quest in 0:23", then "Trevor ready".
- Quests are saved as they finish, so a crash loses nothing. A server hop drops the active quest without saving it, so your stats stay clean.

**Finding the mob**
- An on-screen box around the quest mob (through walls) and a direction line at the top with distance, height hint and Shuriken status. Works for horses, too.
- Zone panel: the zone Trevor's mob is in, the zone you are in, and a green bar when they match.
- Theodolite aid: reads Trevor's hints, estimates the Y level and highlights the blocks at that height. Optional dotted hint rings.

**Alerts**
- Flashing banners for Endangered and Elusive quests (you choose the minimum rarity), Pelt Dyes, "mob found" and "Trevor ready".
- Shuriken reminder when the mob is close and its name tag shows none, for the rarities you pick.
- Optional sound cues for each of those.

**Controls and comfort**
- A welcome screen on first launch that sets up the essentials in a few clicks.
- One smart warp key (default G): accepts Trevor's prompt, otherwise warps to the quest area, otherwise to the Trapper. One command per press.
- Built-in menu (`/tp` or `/trevorplus`, or Mod Menu's Configure button), sorted into categories, with a description for every setting.
- Behind-walls mode for the mob box, glow and Theodolite blocks: always, faded behind walls, or only while in sight.
- Every on-screen element is draggable: both HUDs, the zone panel, the direction line and the alert banner.
- Everything switches off a minute (configurable) after you leave the Farming Islands, and comes back when you return.
- Every chat line the mod prints can be switched off.

## Getting started

1. Install Fabric Loader and Fabric API for Minecraft 26.2, then drop Trevor+ into your `mods` folder.
2. Join a world. The welcome screen fills in your username and lets you set your Vincent multiplier.
3. Press **Arrange HUDs** to place the panels, then take a quest from Trevor.

`/trevorplus help` lists every command. `/tp` opens the menu; the vanilla teleport is still available as `/minecraft:tp`.

## Good to know

- **Client-side only.** It reads chat, the sidebar and what your client already has loaded. The only things it ever sends are the warp-key command (one per key press) and, if you turn it on, Trevor's own `[YES]` command.
- **Update notice:** checks CurseForge once per start for a newer version (no personal data is sent). Can be switched off.
- **You choose how quests are accepted:** the warp key (default), a one-click on-screen button, or automatically. Check the server's rules on automation before choosing Automatic.
- Your data stays on your computer, in `config/trevorplus/`.
- Not affiliated with Hypixel or Mojang. Check Hypixel's rules for allowed mods before you use any of it.

## Coming up

- Per-zone stats: average kill time and rarity counts per zone.
- Coins per hour alongside pelts per hour.
- CSV export of your quest history.
- A warning when a quest runs unusually long.

Bug reports and suggestions: [GitHub issues](https://github.com/AirmailSoap3209/trevorplus/issues).
