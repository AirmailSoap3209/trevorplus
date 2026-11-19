package com.trevorplus.client;

import com.trevorplus.entities.ShurikenDetector;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** Flattens a styled name tag into (text, colour) pieces for the Shuriken detector. */
final class NameSegments {
    private NameSegments() {}

    static List<ShurikenDetector.Segment> of(Component name) {
        List<ShurikenDetector.Segment> out = new ArrayList<>();
        collect(name, -1, out);
        return out;
    }

    private static void collect(Component c, int inherited, List<ShurikenDetector.Segment> out) {
        TextColor tc = c.getStyle().getColor();
        int color = tc != null ? tc.getValue() : inherited;
        StringBuilder siblings = new StringBuilder();
        for (Component s : c.getSiblings()) siblings.append(s.getString());
        String full = c.getString();
        String own = full.endsWith(siblings.toString()) ? full.substring(0, full.length() - siblings.length()) : full;
        if (!own.isEmpty()) out.add(new ShurikenDetector.Segment(own, color));
        for (Component s : c.getSiblings()) collect(s, color, out);
    }
}
