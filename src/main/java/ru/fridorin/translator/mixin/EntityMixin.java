package ru.fridorin.translator.mixin;

import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.service.TranslationService;
import ru.fridorin.translator.TranslatorModClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(Entity.class)
public class EntityMixin {
    private static class CacheEntry {
        final String originalText;
        final Component translatedComponent;
        final long timestamp;
        final boolean pending; // true = translateAsync() was fired, waiting for result

        CacheEntry(String originalText, Component translatedComponent, boolean pending) {
            this.originalText = originalText;
            this.translatedComponent = translatedComponent;
            this.timestamp = System.currentTimeMillis();
            this.pending = pending;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > (pending ? 5000 : 30000);
        }
    }

    // Key: entity id, value: cached translation. ConcurrentHashMap avoids lock contention on render thread.
    private static final ConcurrentHashMap<Integer, CacheEntry> TRANSLATED_NAMES_CACHE = new ConcurrentHashMap<>();

    // Periodic cleanup counter to avoid unbounded cache growth
    private static volatile long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL = 30000;

    private static void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL) return;
        lastCleanup = now;
        TRANSLATED_NAMES_CACHE.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Entity entity = (Entity) (Object) this;

        Component original = cir.getReturnValue();
        if (original == null) return;
        String plainText = original.getString();
        if (plainText == null || plainText.trim().isEmpty()) return;

        int entityId = entity.getId();

        // Fast cache check first — this is the hot path, must be cheap
        CacheEntry cached = TRANSLATED_NAMES_CACHE.get(entityId);
        if (cached != null && plainText.equals(cached.originalText) && !cached.isExpired()) {
            if (!cached.pending) {
                cir.setReturnValue(cached.translatedComponent);
            }
            // If pending, return original (don't re-fire translateAsync)
            return;
        }

        if (TranslatorModClient.isTranslationDisabled()) {
            TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            return;
        }

        if (entity instanceof net.minecraft.world.entity.player.Player) {
            TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            return;
        }

        if (TranslationService.isBypassed()) {
            return;
        }

        TranslatorConfig config = TranslatorConfigManager.getConfig();

        if (config.ignoreDamageIndicators && TranslationService.isDamageIndicator(plainText)) {
            TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            return;
        }

        boolean isMob = false;
        boolean isNpc = false;
        boolean isHologram = false;

        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            if (plainText.contains("❤") || plainText.contains("HP") || plainText.contains("[Lv") || plainText.contains("Lv ")) {
                isMob = true;
            } else if (plainText.contains("[NPC]") || ru.fridorin.translator.service.SkyblockDictionary.isNpcName(plainText)) {
                isNpc = true;
            } else {
                isHologram = true;
            }
        } else {
            if (entity instanceof net.minecraft.world.entity.monster.Monster || 
                entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || 
                entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
                isMob = true;
            } else if (entity instanceof net.minecraft.world.entity.npc.villager.Villager) {
                isNpc = true;
            } else {
                isMob = true;
            }
        }

        if (isMob && !config.translateMobNames) {
            TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            return;
        }
        if (isNpc && !config.translateNpcNames) {
            TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            return;
        }
        if (isHologram && !config.translateHolograms) {
            TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            return;
        }

        String legacyText = TranslatorModClient.componentToLegacy(original);
        String trimmed = legacyText.trim();
        String immediate = TranslationService.getImmediateTranslation(trimmed);
        
        if (immediate != null) {
            Component translated = TranslatorModClient.legacyToComponent(immediate);
            if (!TranslationService.isTranslationSame(legacyText, immediate)) {
                TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, translated, false));
                cir.setReturnValue(translated);
            } else {
                TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            }
            return;
        }

        // Mark as pending so next frame won't re-fire translateAsync
        TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, true));

        TranslationService.translateAsync(trimmed).thenAccept(translatedLegacy -> {
            if (translatedLegacy != null && !translatedLegacy.trim().isEmpty() 
                    && !TranslationService.isTranslationSame(trimmed, translatedLegacy)) {
                Component translated = TranslatorModClient.legacyToComponent(translatedLegacy);
                TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, translated, false));
            } else {
                TRANSLATED_NAMES_CACHE.put(entityId, new CacheEntry(plainText, original, false));
            }
        });

        // Periodic cleanup
        cleanupIfNeeded();
    }
}
