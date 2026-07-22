package ru.fridorin.translator.mixin;

import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.service.TranslationService;
import ru.fridorin.translator.TranslatorModClient;
//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else
/*import net.minecraft.client.gui.Hud;*/
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.ConcurrentHashMap;

//? if <26.2 {
@Mixin(Gui.class)
//?} else
/*@Mixin(Hud.class)*/
public class GuiMixin {
    private static class CacheEntry {
        final String originalText;
        final Component translatedComponent;
        final long timestamp;
        final boolean pending;

        CacheEntry(String originalText, Component translatedComponent, boolean pending) {
            this.originalText = originalText;
            this.translatedComponent = translatedComponent;
            this.timestamp = System.currentTimeMillis();
            this.pending = pending;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > (pending ? 2000 : 15000);
        }
    }

    // Keyed by plain text string — stable across frames unlike Component objects
    private static final ConcurrentHashMap<String, CacheEntry> SCOREBOARD_CACHE = new ConcurrentHashMap<>();

    @ModifyArg(
        method = "displayScoreboardSidebar",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
        ),
        index = 1
    )
    private Component modifyScoreboardText(Component text) {
        if (text == null) return null;
        String plainText = text.getString();
        if (plainText.trim().isEmpty()) return text;

        // Fast cache lookup by plain text — this is called every frame
        CacheEntry cached = SCOREBOARD_CACHE.get(plainText);
        if (cached != null && !cached.isExpired()) {
            return cached.pending ? text : cached.translatedComponent;
        }

        if (TranslatorModClient.isTranslationDisabled()) {
            SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, text, false));
            return text;
        }

        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.translateScoreboard) {
            SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, text, false));
            return text;
        }

        String legacyText = TranslatorModClient.componentToLegacy(text);
        String trimmed = legacyText.trim();
        String immediate = TranslationService.getImmediateTranslation(trimmed);
        if (immediate != null) {
            Component translated = TranslatorModClient.legacyToComponent(immediate);
            if (!TranslationService.isTranslationSame(legacyText, immediate)) {
                SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, translated, false));
                return translated;
            } else {
                SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, text, false));
                return text;
            }
        }

        // Mark as pending — don't re-fire translateAsync next frame
        SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, text, true));

        TranslationService.translateAsync(trimmed).thenAccept(translatedLegacy -> {
            if (translatedLegacy != null && !translatedLegacy.trim().isEmpty()
                    && !TranslationService.isTranslationSame(trimmed, translatedLegacy)) {
                Component translated = TranslatorModClient.legacyToComponent(translatedLegacy);
                SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, translated, false));
            } else {
                SCOREBOARD_CACHE.put(plainText, new CacheEntry(plainText, text, false));
            }
        });

        return text;
    }
}
