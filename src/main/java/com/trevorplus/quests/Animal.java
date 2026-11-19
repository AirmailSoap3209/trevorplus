package com.trevorplus.quests;

/** Trapper animal types. visibleDistance is the render distance reference taken from SkyHanni's Trevor code. */
public enum Animal {
    COW("Cow", 68.0),
    HORSE("Horse", 90.0),
    SHEEP("Sheep", 68.0),
    PIG("Pig", 68.0),
    RABBIT("Rabbit", 43.0),
    CHICKEN("Chicken", 33.0);

    public final String displayName;
    public final double visibleDistance;

    Animal(String displayName, double visibleDistance) {
        this.displayName = displayName;
        this.visibleDistance = visibleDistance;
    }

    public static Animal parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        for (Animal a : values()) if (a.displayName.equalsIgnoreCase(t)) return a;
        return null;
    }
}
