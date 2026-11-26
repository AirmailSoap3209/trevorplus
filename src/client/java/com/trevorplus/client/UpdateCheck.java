package com.trevorplus.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trevorplus.Trevorplus;
import com.trevorplus.update.Versions;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Looks for a newer Trevor+ release on CurseForge, once per game start, in the background.
 * One anonymous GET of the project's public file list (the same one the CurseForge website loads);
 * nothing about the player is sent. Only approved release files for this Minecraft version count.
 * Any failure (offline, blocked, format change) is silent. The result is announced once in chat
 * after joining a world, and is also what Mod Menu's update badge shows.
 */
final class UpdateCheck {
    private UpdateCheck() {}

    record Result(String latest, String running, boolean newer, String url) {}

    private static volatile CompletableFuture<Result> pending;
    private static boolean announced;

    private static String projectId() { return customString("curseforgeId"); }

    /** The project page, by slug when it is known, otherwise by numeric id (CurseForge redirects it). */
    static String pageUrl() {
        String slug = customString("curseforgeSlug");
        return slug.isBlank() ? "https://www.curseforge.com/projects/" + projectId()
                : "https://www.curseforge.com/minecraft/mc-mods/" + slug;
    }

    private static String customString(String key) {
        try {
            CustomValue v = FabricLoader.getInstance().getModContainer(BuildFlags.MOD_ID).orElseThrow()
                    .getMetadata().getCustomValue(Trevorplus.MOD_ID);
            CustomValue s = v == null ? null : v.getAsObject().get(key);
            return s == null ? "" : s.getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    /** Starts the request if the setting allows it and it has not run yet. Safe to call from any thread. */
    static synchronized CompletableFuture<Result> start() {
        if (pending != null) return pending;
        String id = projectId();
        if (!TrevorplusClient.settings().updateCheck || id.isBlank() || id.contains("$")) return null;
        String running = version(BuildFlags.MOD_ID), mc = version("minecraft");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.curseforge.com/api/v1/mods/" + id
                        + "/files?pageIndex=0&pageSize=25&sort=dateCreated&sortDescending=true"))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Trevor+/" + running + " (update check)")
                .header("Accept", "application/json")
                .GET().build();
        pending = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
                    String latest = Versions.newest(parse(resp.body()), mc);
                    Result r = new Result(latest, running, Versions.isNewer(latest, running), pageUrl());
                    Trevorplus.LOGGER.debug("[update] latest on CurseForge: {}, running {}", latest, running);
                    return r;
                })
                .exceptionally(t -> {
                    Trevorplus.LOGGER.debug("[update] check failed: {}", t.toString());
                    return null;
                });
        return pending;
    }

    private static List<Versions.RemoteFile> parse(String body) {
        List<Versions.RemoteFile> out = new ArrayList<>();
        JsonArray data = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("data");
        if (data == null) return out;
        for (JsonElement el : data) {
            JsonObject f = el.getAsJsonObject();
            List<String> games = new ArrayList<>();
            JsonArray gv = f.has("gameVersions") ? f.getAsJsonArray("gameVersions") : null;
            if (gv != null) for (JsonElement g : gv) games.add(g.getAsString());
            out.add(new Versions.RemoteFile(str(f, "displayName"), str(f, "fileName"),
                    f.has("status") ? f.get("status").getAsInt() : -1,
                    f.has("releaseType") ? f.get("releaseType").getAsInt() : -1, games));
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** For Mod Menu (called on its own thread): waits up to 10 s; null when unknown or switched off. */
    static Result awaitResult() {
        CompletableFuture<Result> f = start();
        if (f == null) return null;
        try {
            return f.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    /** Called every tick while in a world: prints the notice once, when the answer is in. */
    static void announceIfNeeded() {
        if (announced) return;
        CompletableFuture<Result> f = pending;
        if (f == null || !f.isDone()) return;
        announced = true;
        Result r = f.getNow(null);
        LocalPlayer p = Minecraft.getInstance().player;
        if (r == null || !r.newer() || p == null) return;
        Component link = Component.literal("§b§n[Download]").withStyle(style -> style
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(r.url())))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the " + BuildFlags.NAME + " CurseForge page"))));
        p.sendSystemMessage(Component.literal("§b[Trevor+] §fUpdate available: §a" + r.latest()
                + " §7(you have " + Versions.core(r.running()) + ")  ").append(link));
    }
}
