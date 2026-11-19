package com.trevorplus.client;

import com.trevorplus.Trevorplus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Short UI sounds (played at full volume regardless of where you stand). Built the same way SkyHanni
 * does it on 26.2: a SimpleSoundInstance played through the sound manager. Never throws.
 */
final class SoundCue {
    private static boolean broken;

    private SoundCue() {}

    private static final class Cue extends SimpleSoundInstance {
        Cue(Identifier id, float pitch, float volume) {
            super(id, SoundSource.UI, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        }
    }

    static void play(String sound, float pitch) {
        if (broken || !TrevorplusClient.settings().soundEnabled) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Cue cue = new Cue(Identifier.parse(sound), pitch, 1.0f);
            mc.execute(() -> {
                try {
                    mc.getSoundManager().play(cue);
                } catch (RuntimeException e) {
                    broken = true;
                    Trevorplus.LOGGER.warn("[sound] could not play {}: {}", sound, e.toString());
                }
            });
        } catch (Throwable t) {
            broken = true;
            Trevorplus.LOGGER.warn("[sound] sounds disabled: {}", t.toString());
        }
    }

    /** Rarity alert: higher rarity = higher, longer chime. */
    static void alert(int rarityOrdinal) {
        if (rarityOrdinal >= 4) {                    // Elusive
            play("entity.player.levelup", 1.0f);
        } else {
            play("block.note_block.pling", 1.2f + 0.15f * rarityOrdinal);
        }
    }
    static void dye() { play("entity.player.levelup", 1.3f); }
    static void found() { play("entity.experience_orb.pickup", 1.0f); }
    static void ready() { play("block.note_block.bell", 1.4f); }
    static void shuriken() { play("block.note_block.hat", 1.0f); }
}
