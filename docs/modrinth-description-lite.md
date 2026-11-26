# Trevor+ Lite

**A fair-play companion for Hypixel SkyBlock's Trevor the Trapper.**

Trevor+ Lite takes the busywork out of pelt grinding. It times your quests, keeps the numbers that matter (pelts per hour above all) on a HUD you can put anywhere, reads Trevor's Theodolite hints for you, and tells you when something rare shows up. It shows you nothing you could not see yourself and never plays for you.

Minecraft 26.2 · Fabric · requires [Fabric API](https://modrinth.com/mod/fabric-api) · client-side only

## Fair play

- **Nothing through walls.** The quest mob is boxed only while it is on your screen and in direct line of sight. Theodolite blocks are only drawn where you can see them; walls hide them. There is no glow outline, no tracer, no direction arrow and no off-screen pointer.
- **No automation.** Trevor+ Lite never sends anything on its own. Taking a quest is always your action: press the warp key, click Trevor's `[YES]` in chat, or click the on-screen accept button. There is no automatic accept.
- **One command per key press.** The smart warp key sends a single command when you press it, with an adjustable delay between presses.
- **Reads only what your client already shows:** chat, the sidebar, and the blocks and entities already loaded around you.

## Features

**Quest tracking**
- Quest timer with average, best and worst kill time, quests per hour and **pelts per hour**, plus a session clock that ignores long idle gaps.
- Dye odds with your Vincent multiplier, quests since your last dye, and your luck over time.
- Creature HUD: how many of each rarity you have gotten (session and total), creatures since the last Elusive, and the expected gap.
- Cooldown timer: "Next quest in 0:23", then "Trevor ready".
- Zone panel: the zone Trevor's mob is in, the zone you are in, and a green bar when they match.

**Finding the mob**
- When the quest mob is on screen and in sight, a box marks it with its name and distance. As soon as a wall is in the way, the box is gone.

**Theodolite aid**
- Reads Trevor's distance and angle hints, estimates the Y level and highlights the visible blocks at that height.
- Optional dotted hint rings.

**Alerts**
- Banners for Endangered and Elusive quests (you choose the minimum rarity), Pelt Dyes, "mob found" and "Trevor ready".
- Shuriken reminder when the quest mob is close and its name tag shows none, for the rarities you pick.
- Optional sound cues for each of those.

**Comfort**
- A welcome screen on first launch that sets up the essentials in a few clicks.
- Smart warp key (default **G**): accepts Trevor's prompt, otherwise warps to the quest area, otherwise to the Trapper.
- Click to accept: when Trevor offers a quest, a button appears for five seconds; one click takes it.
- Settings menu sorted into categories, with a description for every option. Open it with `/tp`, `/trevorplus`, or Mod Menu's Configure button.
- Every panel, banner and button can be dragged to where you want it.
- Everything switches off a while after you leave the Farming Islands, and comes back when you return.
- Every chat line the mod prints can be switched off.

## Getting started

1. Install Fabric Loader and Fabric API for Minecraft 26.2, then put Trevor+ Lite in your `mods` folder.
2. Join a world. The welcome screen fills in your username (so your own Pelt Dye is recognised) and lets you set your Vincent multiplier.
3. Press **Arrange HUDs** to place the panels, then take a quest from Trevor.

`/trevorplus help` lists every command. `/tp` opens the menu; the vanilla teleport is still available as `/minecraft:tp`.

## Good to know

- **Update notice:** once per game start, Trevor+ Lite asks Modrinth whether a newer version exists and tells you in chat. No account or player data is sent. Switch it off under General > Check for updates.
- Your settings and stats stay on your computer, in `config/trevorplus/`.
- [Mod Menu](https://modrinth.com/mod/modmenu) is supported but not required.
- Not affiliated with Hypixel or Mojang. Check Hypixel's rules on allowed mods before you use any mod.

Source code, bug reports and suggestions: [GitHub](https://github.com/AirmailSoap3209/trevorplus) · [Issues](https://github.com/AirmailSoap3209/trevorplus/issues)
