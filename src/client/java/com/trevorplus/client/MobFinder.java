package com.trevorplus.client;

import com.trevorplus.Trevorplus;
import com.trevorplus.entities.NametagInfo;
import com.trevorplus.entities.NametagParser;
import com.trevorplus.entities.ShurikenDetector;
import com.trevorplus.quests.QuestTracker;
import com.trevorplus.quests.Rarity;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Finds the quest mob among the entities the client already has loaded (nothing is requested
 * from the server) and marks it: a glow outline on the animal plus a direction line on screen.
 * Two ways to recognise it, both from data the client already shows:
 *  1. the name tag above it ("Elusive Cow 10,000/10,000"), same parser as the rest of the mod,
 *  2. an unnamed living entity whose max health matches the rarity.
 */
final class MobFinder {
    private static final NametagParser NAMETAGS = new NametagParser();

    /** The thing to point at. entity may be null when only the name tag was found. */
    /** shuriken: TRUE marker on the name tag, FALSE none, null unknown (no name tag seen). */
    record Target(Rarity rarity, String label, double x, double y, double z, Entity entity, Boolean shuriken) {}

    private static Target current;
    private static final Map<Integer, Entity> glowing = new HashMap<>();
    /** The vanilla outline always shows through walls, so it is only lit while the ESP mode allows it. */
    private static volatile boolean glowAllowed = true;

    private MobFinder() {}

    static Target current() { return current; }

    /** Run about every half second while a quest is active. Returns the target for chat messages. */
    static Target scan(Minecraft mc, QuestTracker.ActiveQuest q, boolean glow) {
        if (mc.level == null || mc.player == null || q == null) {
            clear();
            return null;
        }
        double radius = q.animal() != null ? Math.max(48.0, q.animal().visibleDistance) : 96.0;
        Target bestStand = null, bestBody = null;
        double bestStandD = Double.MAX_VALUE, bestBodyD = Double.MAX_VALUE;

        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius))) {
            if (e.isRemoved()) continue;
            double d = e.distanceToSqr(mc.player);
            Component name = e.getCustomName();
            if (name != null) {
                NametagInfo info = NAMETAGS.parse(name.getString(), false).filter(NametagInfo::plausible).orElse(null);
                if (info == null || (q.rarity() != null && info.rarity() != q.rarity())) continue;
                if (d < bestStandD) {
                    bestStandD = d;
                    Entity body = bodyUnder(mc, e, info.rarity());
                    String label = info.rarity().label() + (info.animal() != null ? " " + info.animal().displayName : "");
                    Boolean marked = ShurikenDetector.hasMarker(NameSegments.of(name), TrevorplusClient.settings().shurikenMarker);
                    bestStand = body != null
                            ? new Target(info.rarity(), label, body.getX(), body.getY(), body.getZ(), body, marked)
                            : new Target(info.rarity(), label, e.getX(), e.getY() - 1.5, e.getZ(), null, marked);
                }
            } else if (q.rarity() != null && e instanceof LivingEntity && !(e instanceof Player)
                    && !e.isInvisible() && healthMatches(maxHealth(e), q.rarity())) {
                if (d < bestBodyD) {
                    bestBodyD = d;
                    String label = q.rarity().label() + (q.animal() != null ? " " + q.animal().displayName : "");
                    bestBody = new Target(q.rarity(), label, e.getX(), e.getY(), e.getZ(), e, null);
                }
            }
        }
        Target t = bestStand != null ? bestStand : bestBody;
        current = t;
        applyGlow(glow && t != null ? t.entity() : null);
        return t;
    }

    /** Called every client tick: a server entity-data update can switch the outline off again. */
    static void keepGlow() {
        Target t = current;
        if (t != null && t.entity() != null && !t.entity().isRemoved()) setGlow(t.entity(), glowAllowed);
    }

    /** False while the mob is behind a wall in the Dim and Only-when-visible modes. */
    static void setGlowAllowed(boolean allowed) { glowAllowed = allowed; }

    static void clear() {
        current = null;
        applyGlow(null);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static Entity bodyUnder(Minecraft mc, Entity stand, Rarity rarity) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(stand, stand.getBoundingBox().inflate(1.5, 3.0, 1.5))) {
            if (e.isRemoved() || e.getCustomName() != null || !(e instanceof LivingEntity) || e instanceof Player) continue;
            double mh = maxHealth(e);
            if (!Double.isNaN(mh) && !healthMatches(mh, rarity) && mh < 50) continue;   // skip small stuff
            double d = e.distanceToSqr(stand);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    static boolean healthMatches(double max, Rarity r) {
        if (Double.isNaN(max)) return false;
        return Math.abs(max - r.maxHealth) < 0.5 || Math.abs(max - 2.0 * r.maxHealth) < 0.5;
    }

    private static void applyGlow(Entity target) {
        glowing.entrySet().removeIf(en -> {
            boolean keep = target != null && en.getKey() == target.getId();
            if (!keep) setGlow(en.getValue(), false);
            return !keep;
        });
        if (target != null && !glowing.containsKey(target.getId())) {
            glowing.put(target.getId(), target);
            setGlow(target, glowAllowed);
        }
    }

    // Reflection keeps the build safe: these two vanilla methods are looked up by name when first
    // needed, and the feature quietly degrades (direction line only) if one is missing.
    private static Method glowMethod, healthMethod;
    private static boolean glowTried, healthTried;

    private static Method find(Class<?> from, String name, Class<?>... params) {
        for (Class<?> c = from; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    static void setGlow(Entity e, boolean on) {
        if (BuildFlags.ESP) {          // compiled out of Trevor+ Lite
            if (!glowTried) {
                glowTried = true;
                glowMethod = find(Entity.class, "setGlowingTag", boolean.class);
                if (glowMethod == null) Trevorplus.LOGGER.warn("[highlight] setGlowingTag not found, outline disabled");
            }
            if (glowMethod == null) return;
            try {
                glowMethod.invoke(e, on);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                Trevorplus.LOGGER.warn("[highlight] could not set glow: {}", ex.toString());
                glowMethod = null;
            }
        }
    }

    static double maxHealth(Entity e) {
        if (!healthTried) {
            healthTried = true;
            healthMethod = find(LivingEntity.class, "getMaxHealth");
            if (healthMethod == null) Trevorplus.LOGGER.warn("[highlight] getMaxHealth not found, using name tags only");
        }
        if (healthMethod == null) return Double.NaN;
        try {
            return ((Number) healthMethod.invoke(e)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            healthMethod = null;
            return Double.NaN;
        }
    }
}
