package com.trevorplus.client;

import com.trevorplus.Trevorplus;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Key registration lives alone in this file on purpose: it is the one part whose 26.2 API names
 * I could not confirm from SkyHanni (it uses its own key system). If the compiler complains
 * here, send me the error; the fix is a one- or two-line change.
 */
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
