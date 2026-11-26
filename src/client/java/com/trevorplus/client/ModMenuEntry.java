package com.trevorplus.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.UpdateChannel;
import com.terraformersmc.modmenu.api.UpdateChecker;
import com.terraformersmc.modmenu.api.UpdateInfo;

/**
 * Mod Menu integration (optional: only loaded when Mod Menu is installed). The "Configure" button
 * opens the Trevor+ settings, and the update badge comes from the CurseForge check instead of
 * Mod Menu's own Modrinth lookup.
 */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new TrevorUiScreen(false, parent);
    }

    @Override
    public UpdateChecker getUpdateChecker() {
        return () -> {
            UpdateCheck.Result r = UpdateCheck.awaitResult();
            boolean newer = r != null && r.newer();
            String link = r != null ? r.url() : UpdateCheck.pageUrl();
            return new UpdateInfo() {
                @Override public boolean isUpdateAvailable() { return newer; }
                @Override public String getDownloadLink() { return link; }
                @Override public UpdateChannel getUpdateChannel() { return UpdateChannel.RELEASE; }
            };
        };
    }
}
