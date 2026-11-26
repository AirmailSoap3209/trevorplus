package com.trevorplus.client;

import com.trevorplus.Trevorplus;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key registration: the smart warp key (G) and the menu key (unbound), under Controls > Trevor+. */
final class Keybinds {
    private Keybinds() {}

    private static KeyMapping.Category category;

    private static KeyMapping.Category category() {
        if (category == null) category = KeyMapping.Category.register(Trevorplus.id("main"));
        return category;
    }

    static KeyMapping registerNavKey() {
        KeyMapping key = new KeyMapping("key.trevorplus.nav", GLFW.GLFW_KEY_G, category());
        return KeyMappingHelper.registerKeyMapping(key);
    }

    /** Opens the settings screen. Unbound by default; set it in Controls > Trevor+. */
    static KeyMapping registerUiKey() {
        KeyMapping key = new KeyMapping("key.trevorplus.ui", GLFW.GLFW_KEY_UNKNOWN, category());
        return KeyMappingHelper.registerKeyMapping(key);
    }
}
