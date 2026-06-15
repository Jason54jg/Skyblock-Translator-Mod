package ru.fridorin.translator.mixin;

import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.TranslatorModClient;
import ru.fridorin.translator.service.TranslationService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void onGetTooltipLines(Item.TooltipContext context, @Nullable Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        if (TranslatorModClient.isTranslationDisabled() || TranslatorModClient.bypassTranslation) return;
        
        // Skip background indexing threads (like REI/JEI indexing) to avoid lags
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) return;

        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.translateTooltips) return;

        if (TranslationService.isBypassed()) return;

        List<Component> originalLines = cir.getReturnValue();
        if (originalLines == null || originalLines.isEmpty()) return;

        // Create a new list to avoid mutating caches from other mods (like REI or Skyblocker)
        List<Component> newLines = new java.util.ArrayList<>(originalLines.size());

        // Translate lines
        int startIndex = config.translateItemNames ? 0 : 1;
        
        for (int i = 0; i < originalLines.size(); i++) {
            Component line = originalLines.get(i);
            
            if (i < startIndex) {
                newLines.add(line);
                continue;
            }
            
            String plainText = line.getString();
            
            if (plainText.trim().isEmpty()) {
                newLines.add(line);
                continue;
            }
            
            String cleanText = plainText.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "").trim();
            
            // Apply lightweight toggle checks to respect config settings
            if (!config.translateEnchantments && TranslationService.ENCHANTMENT_LINE_PATTERN.matcher(cleanText).matches()) {
                newLines.add(line);
                continue;
            }
            if (!config.translateAbilityNames && (cleanText.toLowerCase().startsWith("ability:") || cleanText.toLowerCase().startsWith("item ability:"))) {
                newLines.add(line);
                continue;
            }
            
            // Skip technical mod identifiers or namespaces
            if (TranslationService.NAMESPACE_PATTERN.matcher(cleanText).matches()) {
                newLines.add(line);
                continue;
            }
            
            Component translated = TranslatorModClient.translateComponent(line);
            newLines.add(translated);
        }
        
        cir.setReturnValue(newLines);
    }
}
