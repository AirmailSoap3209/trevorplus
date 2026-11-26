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

    /** Semantic Versioning 2.0.0, with an optional pre-release and build part. */
    public static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
            + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
            + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    public static boolean isSemver(String v) { return v != null && SEMVER.matcher(v).matches(); }

    /**
     * Semantic-versioning precedence (semver.org, section 11): MAJOR.MINOR.PATCH compared as numbers
     * (missing parts count as 0), a pre-release sorts before the release it leads to, pre-release
     * identifiers compare one by one (numbers numerically and below text), and build metadata is ignored.
     */
    public static int compare(String a, String b) {
        String x = core(a), y = core(b);
        int dx = x.indexOf('-'), dy = y.indexOf('-');
        String[] nx = (dx < 0 ? x : x.substring(0, dx)).split("\\."), ny = (dy < 0 ? y : y.substring(0, dy)).split("\\.");
        for (int i = 0; i < 3; i++) {
            long p = i < nx.length ? num(nx[i]) : 0, q = i < ny.length ? num(ny[i]) : 0;
            if (p != q) return Long.compare(p, q);
        }
        if (dx < 0 || dy < 0) return dx < 0 ? (dy < 0 ? 0 : 1) : -1;     // a release outranks its pre-releases
        String[] px = x.substring(dx + 1).split("\\."), py = y.substring(dy + 1).split("\\.");
        for (int i = 0; i < Math.min(px.length, py.length); i++) {
            boolean ax = px[i].matches("\\d+"), ay = py[i].matches("\\d+");
            int c = ax && ay ? Long.compare(num(px[i]), num(py[i]))
                    : ax != ay ? (ax ? -1 : 1)
                    : px[i].compareTo(py[i]);
            if (c != 0) return c;
        }
        return Integer.compare(px.length, py.length);
    }

    private static long num(String s) {
        try {
            return Long.parseLong(s);
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
