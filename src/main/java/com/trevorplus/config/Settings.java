package com.trevorplus.config;

import com.trevorplus.quests.Area;
import com.trevorplus.storage.FlatJson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** User settings. Plain fields so a GUI can bind to them later; saved as flat JSON. */
public final class Settings {
    /** Active Vincent multiplier: 1 (off), 2 or 3. Applied when a quest is completed. */
    public int multiplier = 1;
    /** Own username, used to recognise your own dye drop in the server-wide broadcast. */
    public String playerName = "";
    /** Whether "mob died randomly" quests count in kill-time averages. */
    public boolean timeSelfDeaths = true;
    /** Whether "mob died randomly" quests advance dye odds. Unverified mechanic, so off by default. */
    public boolean selfDeathCountsForDye = false;
    /** 6 normally, 7 with Enderman Slayer IX. */
    public int elusiveWeight = 6;
    public int idleTimeoutSeconds = 300;
    public long navMinIntervalMs = 3000;
    public boolean navAcceptPrompt = true;
    public boolean navWarpArea = true;
    public boolean navWarpTrapper = true;
    public boolean hudVisible = true;
    public int hudX = 6;
    public int hudY = 6;
    /** Second HUD: creature counts per rarity and the Elusive gap. */
    public boolean hud2Visible = true;
    public int hud2X = 6;
    public int hud2Y = 130;
    /** Hide every overlay (and switch off the warp key and auto-accept) after a while outside the Farming Islands. */
    public boolean autoHide = true;
    public int autoHideSeconds = 60;
    /** Chat messages Trevor+ prints for you, each switchable. */
    public boolean msgFound = true;
    public boolean msgAccepted = true;
    public boolean msgWarp = true;
    public boolean msgDye = true;
    /** Play short sounds for alerts, the mob being found and Trevor being ready. */
    public boolean soundEnabled = true;
    /** Ping (sound plus banner) when the quest mob first loads in range. */
    public boolean mobFoundPing = true;
    /** Ping when Trevor's cooldown is over and a new quest can be taken. */
    public boolean readyPing = true;
    /** Seconds Trevor makes you wait after a quest; his own "come back in Ns" message overrides it. */
    public int cooldownSeconds = 30;
    /** Zone indicator: where the mob is and where you are. Always on by default. */
    public boolean zoneVisible = true;
    public int zoneX = 6;
    public int zoneY = 200;
    /** Top-centre direction line: horizontal centre and top edge; -1 = default placement. */
    public int finderX = -1;
    public int finderY = -1;
    /** Rarity alert banner: horizontal centre and top edge; -1 = default placement. */
    public int alertX = -1;
    public int alertY = -1;
    /** Mark the quest mob when it is found (glow outline plus a direction line). */
    public boolean highlightEnabled = true;
    /** Used for the on-screen mob box when the game's own FOV setting cannot be read. */
    public int fovDegrees = 70;
    /** How Trevor's [YES] prompt is accepted (AcceptMode name). Manual by default, see the README. */
    public String acceptMode = AcceptMode.MANUAL.name();
    /** Click-to-accept button: horizontal centre and top edge; -1 = default placement. */
    public int acceptX = -1;
    public int acceptY = -1;
    /** Flash a banner when a quest of at least alertMin rarity starts (or a dye drops). */
    public boolean alertsEnabled = true;
    /** Draw Theodolite hint rings on screen. */
    public boolean ringsEnabled = false;
    /** Highlight the standing blocks at the hinted height (the main Theodolite aid). */
    public boolean blocksEnabled = true;
    /** Only highlight blocks that sit on the hint rings (within a few blocks). Off = every block at that level. */
    public boolean blocksRingFilter = false;
    public String alertMin = "ENDANGERED";
    /** Remind to apply a Shuriken while the quest mob is near and its name tag shows no marker. */
    public boolean shurikenReminder = true;
    /** Rarities the Shuriken reminder fires for, as comma-separated Rarity names. Default: all of them. */
    public String shurikenRarities = ALL_RARITIES;
    /** The icon text learned with /trevorplus shuriken learn; empty = detect any blue symbol. */
    public String shurikenMarker = "";
    /** Mob box, glow outline and Theodolite blocks behind walls: see EspMode. Stored as the enum name. */
    public String espMode = EspMode.DIM.name();
    /** Look for a newer release on CurseForge at start-up (one anonymous request). */
    public boolean updateCheck = true;
    /** True once the first-launch welcome screen has been shown (or skipped). */
    public boolean welcomeShown = false;
    /**
     * Not in the menu: enables the /trevorplus debug commands and verbose engine logging. Set
     * "debugMode": true in settings.json by hand when collecting data for a bug report.
     */
    public boolean debugMode = false;
    /** Area enum name -> custom command, e.g. OASIS -> "/warp desert". */
    public final Map<String, String> warpOverrides = new HashMap<>();

    /** Minimum rarity for alerts; falls back to Endangered if the saved text is not a rarity. */
    public com.trevorplus.quests.Rarity alertMinRarity() {
        com.trevorplus.quests.Rarity r = com.trevorplus.quests.Rarity.parse(alertMin);
        return r == null ? com.trevorplus.quests.Rarity.ENDANGERED : r;
    }

    private static final String ALL_RARITIES = java.util.Arrays.stream(com.trevorplus.quests.Rarity.values())
            .map(Enum::name).collect(java.util.stream.Collectors.joining(","));

    /** Accept mode; falls back to Manual if the saved text is not a mode. */
    public AcceptMode accept() {
        AcceptMode m = AcceptMode.parse(acceptMode);
        return m == null ? AcceptMode.MANUAL : m;
    }

    /** Whether the Shuriken reminder fires for a mob of this rarity (unknown rarity: yes). */
    public boolean shurikenFor(com.trevorplus.quests.Rarity r) {
        if (r == null) return true;
        for (String part : shurikenRarities.split(",")) {
            if (part.trim().equalsIgnoreCase(r.name())) return true;
        }
        return false;
    }

    /** Switches the reminder on or off for one rarity, keeping the list in rarity order. */
    public void setShurikenFor(com.trevorplus.quests.Rarity r, boolean on) {
        StringBuilder out = new StringBuilder();
        for (com.trevorplus.quests.Rarity x : com.trevorplus.quests.Rarity.values()) {
            if (x == r ? on : shurikenFor(x)) out.append(out.length() == 0 ? "" : ",").append(x.name());
        }
        shurikenRarities = out.toString();
    }

    /** Behind-walls mode; falls back to Dim if the saved text is not a mode. */
    public EspMode esp() {
        EspMode m = EspMode.parse(espMode);
        return m == null ? EspMode.DIM : m;
    }

    public void setMultiplier(int m) { multiplier = Math.max(1, Math.min(3, m)); }

    public String warpCommandFor(Area area) {
        String o = warpOverrides.get(area.name());
        return (o != null && !o.isBlank()) ? o : area.defaultCommand();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("multiplier", multiplier);
        m.put("playerName", playerName);
        m.put("timeSelfDeaths", timeSelfDeaths);
        m.put("selfDeathCountsForDye", selfDeathCountsForDye);
        m.put("elusiveWeight", elusiveWeight);
        m.put("idleTimeoutSeconds", idleTimeoutSeconds);
        m.put("navMinIntervalMs", navMinIntervalMs);
        m.put("navAcceptPrompt", navAcceptPrompt);
        m.put("navWarpArea", navWarpArea);
        m.put("navWarpTrapper", navWarpTrapper);
        m.put("hudVisible", hudVisible);
        m.put("hudX", hudX);
        m.put("hudY", hudY);
        m.put("hud2Visible", hud2Visible);
        m.put("hud2X", hud2X);
        m.put("hud2Y", hud2Y);
        m.put("autoHide", autoHide);
        m.put("autoHideSeconds", autoHideSeconds);
        m.put("msgFound", msgFound);
        m.put("msgAccepted", msgAccepted);
        m.put("msgWarp", msgWarp);
        m.put("msgDye", msgDye);
        m.put("soundEnabled", soundEnabled);
        m.put("mobFoundPing", mobFoundPing);
        m.put("readyPing", readyPing);
        m.put("cooldownSeconds", cooldownSeconds);
        m.put("zoneVisible", zoneVisible);
        m.put("zoneX", zoneX);
        m.put("zoneY", zoneY);
        m.put("finderX", finderX);
        m.put("finderY", finderY);
        m.put("alertX", alertX);
        m.put("alertY", alertY);
        m.put("highlightEnabled", highlightEnabled);
        m.put("fovDegrees", fovDegrees);
        m.put("acceptMode", acceptMode);
        m.put("acceptX", acceptX);
        m.put("acceptY", acceptY);
        m.put("alertsEnabled", alertsEnabled);
        m.put("ringsEnabled", ringsEnabled);
        m.put("blocksEnabled", blocksEnabled);
        m.put("blocksRingFilter", blocksRingFilter);
        m.put("alertMin", alertMin);
        m.put("shurikenReminder", shurikenReminder);
        m.put("shurikenMarker", shurikenMarker);
        m.put("shurikenRarities", shurikenRarities);
        m.put("espMode", espMode);
        m.put("updateCheck", updateCheck);
        m.put("welcomeShown", welcomeShown);
        if (debugMode) m.put("debugMode", true);      // stays out of the file unless someone turned it on
        for (Map.Entry<String, String> e : warpOverrides.entrySet()) m.put("warp." + e.getKey(), e.getValue());
        return m;
    }

    public static Settings fromMap(Map<String, Object> m) {
        Settings s = new Settings();
        s.setMultiplier((int) FlatJson.lng(m, "multiplier", 1));
        s.playerName = FlatJson.str(m, "playerName", "");
        s.timeSelfDeaths = FlatJson.bool(m, "timeSelfDeaths", true);
        s.selfDeathCountsForDye = FlatJson.bool(m, "selfDeathCountsForDye", false);
        s.elusiveWeight = (int) FlatJson.lng(m, "elusiveWeight", 6);
        s.idleTimeoutSeconds = (int) FlatJson.lng(m, "idleTimeoutSeconds", 300);
        s.navMinIntervalMs = FlatJson.lng(m, "navMinIntervalMs", 3000);
        s.navAcceptPrompt = FlatJson.bool(m, "navAcceptPrompt", true);
        s.navWarpArea = FlatJson.bool(m, "navWarpArea", true);
        s.navWarpTrapper = FlatJson.bool(m, "navWarpTrapper", true);
        s.hudVisible = FlatJson.bool(m, "hudVisible", true);
        s.hudX = (int) FlatJson.lng(m, "hudX", 6);
        s.hudY = (int) FlatJson.lng(m, "hudY", 6);
        s.hud2Visible = FlatJson.bool(m, "hud2Visible", true);
        s.hud2X = (int) FlatJson.lng(m, "hud2X", 6);
        s.hud2Y = (int) FlatJson.lng(m, "hud2Y", 130);
        s.autoHide = FlatJson.bool(m, "autoHide", true);
        s.autoHideSeconds = (int) Math.max(5, Math.min(3600, FlatJson.lng(m, "autoHideSeconds", 60)));
        s.msgFound = FlatJson.bool(m, "msgFound", true);
        s.msgAccepted = FlatJson.bool(m, "msgAccepted", true);
        s.msgWarp = FlatJson.bool(m, "msgWarp", true);
        s.msgDye = FlatJson.bool(m, "msgDye", true);
        s.soundEnabled = FlatJson.bool(m, "soundEnabled", true);
        s.mobFoundPing = FlatJson.bool(m, "mobFoundPing", true);
        s.readyPing = FlatJson.bool(m, "readyPing", true);
        s.cooldownSeconds = (int) Math.max(0, Math.min(600, FlatJson.lng(m, "cooldownSeconds", 30)));
        s.zoneVisible = FlatJson.bool(m, "zoneVisible", true);
        s.zoneX = (int) FlatJson.lng(m, "zoneX", 6);
        s.zoneY = (int) FlatJson.lng(m, "zoneY", 200);
        s.finderX = (int) FlatJson.lng(m, "finderX", -1);
        s.finderY = (int) FlatJson.lng(m, "finderY", -1);
        s.alertX = (int) FlatJson.lng(m, "alertX", -1);
        s.alertY = (int) FlatJson.lng(m, "alertY", -1);
        s.highlightEnabled = FlatJson.bool(m, "highlightEnabled", true);
        s.fovDegrees = (int) Math.max(30, Math.min(130, FlatJson.lng(m, "fovDegrees", 70)));
        // older files only have the on/off autoAccept flag
        s.acceptMode = FlatJson.str(m, "acceptMode",
                (FlatJson.bool(m, "autoAccept", false) ? AcceptMode.AUTO : AcceptMode.MANUAL).name());
        s.acceptX = (int) FlatJson.lng(m, "acceptX", -1);
        s.acceptY = (int) FlatJson.lng(m, "acceptY", -1);
        s.alertsEnabled = FlatJson.bool(m, "alertsEnabled", true);
        s.ringsEnabled = FlatJson.bool(m, "ringsEnabled", false);
        s.blocksEnabled = FlatJson.bool(m, "blocksEnabled", true);
        s.blocksRingFilter = FlatJson.bool(m, "blocksRingFilter", false);
        s.alertMin = FlatJson.str(m, "alertMin", "ENDANGERED");
        s.shurikenReminder = FlatJson.bool(m, "shurikenReminder", true);
        s.shurikenMarker = FlatJson.str(m, "shurikenMarker", "");
        s.shurikenRarities = FlatJson.str(m, "shurikenRarities", ALL_RARITIES);
        // upgraders keep the old always-through-walls look; new installs start on Dim
        s.espMode = FlatJson.str(m, "espMode", (m.isEmpty() ? EspMode.DIM : EspMode.ALWAYS).name());
        s.updateCheck = FlatJson.bool(m, "updateCheck", true);
        // an existing settings file without the key belongs to someone upgrading: do not greet them as new
        s.welcomeShown = FlatJson.bool(m, "welcomeShown", !m.isEmpty());
        s.debugMode = FlatJson.bool(m, "debugMode", false);
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getKey().startsWith("warp.") && e.getValue() instanceof String v) {
                s.warpOverrides.put(e.getKey().substring(5), v);
            }
        }
        return s;
    }
}
