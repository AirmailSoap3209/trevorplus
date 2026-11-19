package com.trevorplus.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * "Is this block something you can stand on / that blocks sight?" asked by method name so the
 * build does not depend on which of the vanilla names 26.2 kept. Tries blocksMotion, then isSolid,
 * then falls back to "not air".
 */
final class BlockProbe {
    private static MethodHandle solid;
    private static MethodHandle air;
    private static boolean tried;

    private BlockProbe() {}

    private static MethodHandle find(Object state, String... names) {
        for (String name : names) {
            try {
                Method m = state.getClass().getMethod(name);
                if (m.getReturnType() == boolean.class) return MethodHandles.publicLookup().unreflect(m);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    static boolean solid(Object state) {
        if (!tried) {
            tried = true;
            solid = find(state, "blocksMotion", "isSolid");
            air = find(state, "isAir");
        }
        try {
            if (solid != null) return (boolean) solid.invoke(state);
            if (air != null) return !(boolean) air.invoke(state);
        } catch (Throwable t) {
            solid = null;
            air = null;
        }
        return false;
    }
}
