package ru.fridorin.translator.mixin;

import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.service.TranslationService;
import ru.fridorin.translator.TranslatorModClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(PlayerInfo.class)
public class PlayerInfoMixin {

    private static class CacheEntry {
        final String originalText;
        final Component translatedComponent;
        final long timestamp;

        CacheEntry(String originalText, Component translatedComponent) {
            this.originalText = originalText;
            this.translatedComponent = translatedComponent;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000;
        }
    }

    // Keyed by player UUID string for stable caching across frames
    private static final ConcurrentHashMap<String, CacheEntry> TAB_LIST_CACHE = new ConcurrentHashMap<>();

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetTabListDisplayName(CallbackInfoReturnable<Component> cir) {
        if (TranslatorModClient.isTranslationDisabled()) return;
        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.translateTabList) return;

        Component original = cir.getReturnValue();
        if (original != null) {
            String plainText = original.getString();
            if (plainText != null && !plainText.trim().isEmpty()) {
                if (TranslationService.containsNonEnglishLetters(plainText) || !TranslationService.containsLetters(plainText)) return;

                // Get a stable cache key
                String cacheKey;
                try {
                    cacheKey = ((PlayerInfo) (Object) this).getProfile().id().toString();
                } catch (Exception e) {
                    cacheKey = plainText;
                }

                // Fast cache check
                CacheEntry cached = TAB_LIST_CACHE.get(cacheKey);
                if (cached != null && plainText.equals(cached.originalText) && !cached.isExpired()) {
                    if (cached.translatedComponent != original) {
                        cir.setReturnValue(cached.translatedComponent);
                    }
                    return;
                }

                String playerName = null;
                try {
                    playerName = ((PlayerInfo) (Object) this).getProfile().name();
                } catch (Exception e) {
                }

                Component translated = TranslatorModClient.translateComponentWithIgnore(original, playerName);
                if (translated != null && translated != original) {
                    TAB_LIST_CACHE.put(cacheKey, new CacheEntry(plainText, translated));
                    cir.setReturnValue(translated);
                } else {
                    TAB_LIST_CACHE.put(cacheKey, new CacheEntry(plainText, original));
                }
            }
        }
    }
}
