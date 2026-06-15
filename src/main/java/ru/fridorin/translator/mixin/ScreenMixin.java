package ru.fridorin.translator.mixin;

import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.service.TranslationService;
import ru.fridorin.translator.TranslatorModClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Shadow
    protected Component title;

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true)
    private void onGetTitle(CallbackInfoReturnable<Component> cir) {
        if (TranslatorModClient.isTranslationDisabled()) return;
        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.translateInventoryTitles) return;

        if (TranslationService.isBypassed()) {
            return;
        }

        Component originalTitle = this.title;
        if (originalTitle != null) {
            Component translated = TranslatorModClient.translateComponent(originalTitle);
            if (translated != null && translated != originalTitle) {
                cir.setReturnValue(translated);
            }
        }
    }
}
