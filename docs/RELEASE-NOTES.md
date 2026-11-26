- **Trevor+ Lite**: a new, separate download with nothing shown through walls: the mob box only appears while the mob is on screen and in sight, Theodolite blocks only where you can see them, and there is no glow outline, direction line, off-screen pointer or automatic quest accept. Built from the same code and released together with Trevor+. Settings and stats are shared, so you can switch.
- **Behind-walls mode** for the mob box, glow outline and Theodolite blocks: *Always*, *Dim behind walls* (faded when something is in the way, full strength in sight) or *Only when visible*. In the menu, on the welcome screen, or `/trevorplus esp always|dim|visible`. New installs start on Dim; existing settings keep Always.
- **Mod Menu support** (optional): the Configure button opens the Trevor+ menu.
- **Update notice**: Trevor+ checks CurseForge once per game start and tells you in chat when a newer version is out, with a download link. Also shows as Mod Menu's update badge. Switch it off under "Check for updates" in the menu.
- **Click to accept**: a new way to take Trevor's quests without automation. When he offers one, a button appears for 5 seconds; one left click (or clicking it with chat open) accepts. Choose it under General > Accept quests or `/trevorplus accept click`. "Auto-accept" is now the *Automatic* option there.
- **Shuriken reminder per rarity**: pick which rarities the reminder fires for (Mob finder > Shuriken reminder, or `/trevorplus shuriken tier <rarity>`).
- **New settings menu**: sorted into categories (General, HUD, Mob finder, Theodolite, Alerts, Chat), with grouped sections, settings that depend on another one greyed out while it is off, and mouse-wheel scrolling.
- **Warp key delay**: choose the smallest gap between two presses of the warp key, from 0.5 s to 10 s in 0.5 s steps (default 3 s). General > Warp key delay, or `/trevorplus warpdelay <seconds>`.

Requires Fabric API. Minecraft 26.2, Java 25, client-side only.
