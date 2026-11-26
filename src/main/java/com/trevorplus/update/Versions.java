package com.trevorplus.update;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Picks the newest usable release from a CurseForge file list and compares it with the running version. */
public final class Versions {
    private Versions() {}

    /** CurseForge file status "approved": files still in review or rejected are not offered. */
    public static final int STATUS_APPROVED = 4;
    /** CurseForge release type "release" (2 = beta, 3 = alpha). */
    public static final int RELEASE_TYPE_RELEASE = 1;

    /** One entry of the file list, reduced to what matters here. */
    public record RemoteFile(String displayName, String fileName, int status, int releaseType, List<String> gameVersions) {}

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** "Trevor+ 2.4.0" or "trevorplus-2.4.0-26.2.jar" -> "2.4.0"; null if there is none. */
    public static String extract(String text) {
        if (text == null) return null;
        Matcher m = VERSION.matcher(text);
        if (!m.find()) return null;
        return m.group(1) + "." + m.group(2) + "." + (m.group(3) == null ? "0" : m.group(3));
    }

    /** The mod version without build metadata: "2.3.0+26.2" -> "2.3.0". */
    public static String core(String version) {
        if (version == null) return "";
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    /** Numeric comparison of dotted versions; missing parts count as 0, non-numbers as 0. */
    public static int compare(String a, String b) {
        String[] x = core(a).split("[.-]"), y = core(b).split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Newest approved release that lists this Minecraft version; null if there is none. */
    public static String newest(List<RemoteFile> files, String minecraftVersion) {
        String best = null;
        for (RemoteFile f : files) {
            if (f.status() != STATUS_APPROVED || f.releaseType() != RELEASE_TYPE_RELEASE) continue;
            if (f.gameVersions() == null || !f.gameVersions().contains(minecraftVersion)) continue;
            String v = extract(f.displayName());
            if (v == null) v = extract(f.fileName());
            if (v != null && (best == null || compare(v, best) > 0)) best = v;
        }
        return best;
    }

    /** True when remote is strictly newer than the running version. */
    public static boolean isNewer(String remote, String running) {
        return remote != null && compare(remote, running) > 0;
    }
}
