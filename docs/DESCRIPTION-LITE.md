# Trevor+ Lite

**Trevor+ without ESP: a pelt-grinding companion for Hypixel SkyBlock's Trevor the Trapper.**

Everything that makes Trevor+ useful (quest timing, pelts per hour, dye odds, alerts, the Theodolite aid), without anything that shows you things through walls. The quest mob is boxed only while it is on your screen and in direct sight, and Theodolite blocks are only drawn where you can actually see them. There is no glow outline, no direction line, no off-screen pointer and no automatic accept. That code is not switched off: it is not in this jar at all.

Want the mob finder too? Get the full **Trevor+**. The two are updated together and share their settings and stats, but only one can be installed at a time.

Minecraft 26.2 · Fabric · requires Fabric API · client-side only

## Features

**Tracking**
- Quest timer with average, best and worst kill time, quests per hour and **pelts per hour**, plus a session clock that ignores long idle gaps.
- Dye odds with your Vincent multiplier, quests since your last dye, and your luck over time.
- Creature HUD: how many of each rarity you have gotten (session and total), creatures since the last Elusive, and the expected gap.
- Cooldown timer: "Next quest in 0:23", then "Trevor ready".
- Zone panel: the zone Trevor's mob is in, the zone you are in, and a green bar when they match.

**Mob finder**
- Boxes the quest mob with its name and distance, **only while it is on your screen and in sight**: the moment a wall is in the way, the box is gone.

**Theodolite aid**
- Reads Trevor's hints, estimates the Y level and highlights the blocks at that height, **only where you can see them**: walls hide the boxes.
- Optional dotted hint rings.

**Alerts**
- Flashing banners for Endangered and Elusive quests (you choose the minimum rarity), Pelt Dyes, "mob found" and "Trevor ready".
- Shuriken reminder when the quest mob is close and its name tag shows none, for the rarities you pick.
- Optional sound cues for each of those.

**Controls and comfort**
- A welcome screen on first launch that sets up the essentials in a few clicks.
- One smart warp key (default G): accepts Trevor's prompt, otherwise warps to the quest area, otherwise to the Trapper. One command per press, with an adjustable delay between presses.
- Click to accept: an on-screen button you click once to take Trevor's quest. Lite has no automatic accept: every quest is taken by you.
- Built-in menu (`/tp`, `/trevorplus`, or Mod Menu's Configure button), sorted into categories, with a description for every setting.
- Every on-screen element is draggable.
- Pick your own colour for each rarity: colour picker, R/G/B sliders or a hex code.
- Everything switches off a while after you leave the Farming Islands, and comes back when you return.

## Good to know

- **Client-side only.** It reads chat, the sidebar and what your client already has loaded. The only things it sends are the warp-key command (one per key press) and Trevor's own `[YES]` command when you accept a quest.
- **Update notice:** checks CurseForge once per start for a newer version (no personal data is sent). Can be switched off.
- Your data stays on your computer, in `config/trevorplus/`.
- Not affiliated with Hypixel or Mojang. Check Hypixel's rules for allowed mods before you use any of it.

Bug reports and suggestions: [GitHub issues](https://github.com/AirmailSoap3209/trevorplus/issues).
