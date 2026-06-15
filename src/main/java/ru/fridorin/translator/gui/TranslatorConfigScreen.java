package ru.fridorin.translator.gui;

import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.cache.TranslationCache;
import ru.fridorin.translator.service.SkyblockDictionary;
import ru.fridorin.translator.TranslatorModClient;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.KeyMapping;

import java.util.List;

public class TranslatorConfigScreen {
    private static boolean showApiKey = false;

    private static final java.util.Map<String, String> LANGUAGE_MAP = new java.util.LinkedHashMap<>();
    static {
        LANGUAGE_MAP.put("ru", "Русский (Russian)");
        LANGUAGE_MAP.put("es", "Испанский (Spanish)");
        LANGUAGE_MAP.put("fr", "Французский (French)");
        LANGUAGE_MAP.put("de", "Немецкий (German)");
        LANGUAGE_MAP.put("zh", "Китайский (Chinese)");
        LANGUAGE_MAP.put("ja", "Японский (Japanese)");
        LANGUAGE_MAP.put("ko", "Корейский (Korean)");
        LANGUAGE_MAP.put("pt", "Португальский (Portuguese)");
        LANGUAGE_MAP.put("it", "Итальянский (Italian)");
        LANGUAGE_MAP.put("tr", "Турецкий (Turkish)");
        LANGUAGE_MAP.put("pl", "Польский (Polish)");
        LANGUAGE_MAP.put("uk", "Украинский (Ukrainian)");
        LANGUAGE_MAP.put("be", "Белорусский (Belarusian)");
        LANGUAGE_MAP.put("kk", "Казахский (Kazakh)");
        LANGUAGE_MAP.put("ar", "Арабский (Arabic)");
        LANGUAGE_MAP.put("hi", "Хинди (Hindi)");
        LANGUAGE_MAP.put("vi", "Вьетнамский (Vietnamese)");
        LANGUAGE_MAP.put("th", "Тайский (Thai)");
        LANGUAGE_MAP.put("nl", "Нидерландский (Dutch)");
        LANGUAGE_MAP.put("sv", "Шведский (Swedish)");
        LANGUAGE_MAP.put("no", "Норвежский (Norwegian)");
        LANGUAGE_MAP.put("da", "Датский (Danish)");
        LANGUAGE_MAP.put("fi", "Финский (Finnish)");
        LANGUAGE_MAP.put("cs", "Чешский (Czech)");
        LANGUAGE_MAP.put("el", "Греческий (Greek)");
        LANGUAGE_MAP.put("bg", "Болгарский (Bulgarian)");
        LANGUAGE_MAP.put("ro", "Румынский (Romanian)");
        LANGUAGE_MAP.put("hu", "Венгерский (Hungarian)");
        LANGUAGE_MAP.put("sk", "Словацкий (Slovak)");
        LANGUAGE_MAP.put("id", "Индонезийский (Indonesian)");
    }

    private static Screen activeParentScreen = null;

    private static Component translate(String key) {
        return Component.translatable("gui.skyblock_translator." + key);
    }

    private static Component translate(String key, Object... args) {
        return Component.translatable("gui.skyblock_translator." + key, args);
    }

    private static List<Option<?>> getLanguageOptions(TranslatorConfig config, Screen parent) {
        List<Option<?>> options = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> entry : LANGUAGE_MAP.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            boolean isCurrent = code.equals(config.targetLanguage);
            String displayName = (isCurrent ? "§a§l➤ " : "  ") + name;
            
            options.add(ButtonOption.createBuilder()
                .name(Component.literal(displayName))
                .description(OptionDescription.of(
                    isCurrent 
                        ? translate("lang_selected")
                        : translate("lang_select_btn", name)
                ))
                .action((opt, btn) -> {
                    config.targetLanguage = code;
                    TranslatorConfigManager.save();
                    TranslationCache.loadForLanguage(code);
                    SkyblockDictionary.loadForLanguage(code);
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        net.minecraft.client.Minecraft.getInstance().setScreen(createScreen(parent));
                    });
                })
                .build());
        }
        return options;
    }

    public static Screen createScreen(Screen parent) {
        activeParentScreen = parent;
        TranslatorConfig config = TranslatorConfigManager.getConfig();

        
        KeyMapping keySettingsMapping = TranslatorModClient.getKeyOpenSettings();
        KeyMapping keyToggleMapping = TranslatorModClient.getKeyToggleTranslator();

        String currentSettingsKey = keySettingsMapping != null ? keySettingsMapping.getTranslatedKeyMessage().getString() : "NONE";
        String currentToggleKey = keyToggleMapping != null ? keyToggleMapping.getTranslatedKeyMessage().getString() : "NONE";

        YetAnotherConfigLib configLib = YetAnotherConfigLib.createBuilder()
            .title(translate("title"))
            .save(TranslatorConfigManager::save)
            
            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.language"))
                .tooltip(translate("cat.language.tooltip"))
                .group(OptionGroup.createBuilder()
                    .name(translate("group.language_select"))
                    .options(getLanguageOptions(config, parent))
                    .build())
                .build())
            
            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.main"))
                .tooltip(translate("cat.main.tooltip"))
                
                .group(OptionGroup.createBuilder()
                    .name(translate("group.global"))
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.enabled"))
                        .description(OptionDescription.of(translate("opt.enabled.desc")))
                        .binding(true, () -> config.enabled, val -> config.enabled = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.disable_on_shift"))
                        .description(OptionDescription.of(translate("opt.disable_on_shift.desc")))
                        .binding(true, () -> config.disableOnShift, val -> config.disableOnShift = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.regex_translation"))
                        .description(OptionDescription.of(translate("opt.regex_translation.desc")))
                        .binding(true, () -> config.regexTranslation, val -> config.regexTranslation = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                
                .group(OptionGroup.createBuilder()
                    .name(translate("group.keybinds"))
                    .option(ButtonOption.createBuilder()
                        .name(translate("opt.key_settings"))
                        .description(OptionDescription.of(translate("opt.key_settings.desc")))
                        .action((opt, btn) -> {
                            net.minecraft.client.Minecraft.getInstance().setScreen(
                                new KeyBindCaptureScreen(createScreen(parent), keySettingsMapping)
                            );
                        })
                        .text(Component.literal(currentSettingsKey))
                        .build())
                    .option(ButtonOption.createBuilder()
                        .name(translate("opt.key_toggle"))
                        .description(OptionDescription.of(translate("opt.key_toggle.desc")))
                        .action((opt, btn) -> {
                            net.minecraft.client.Minecraft.getInstance().setScreen(
                                new KeyBindCaptureScreen(createScreen(parent), keyToggleMapping)
                            );
                        })
                        .text(Component.literal(currentToggleKey))
                        .build())
                    .build())
                
                .group(OptionGroup.createBuilder()
                    .name(translate("group.api"))
                    .option(Option.<String>createBuilder()
                        .name(translate("opt.provider"))
                        .description(OptionDescription.of(translate("opt.provider.desc")))
                        .binding("GOOGLE_FREE", () -> config.apiProvider, val -> config.apiProvider = val)
                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                            .values(List.of("GOOGLE_FREE", "DEEPL_FREE", "YANDEX"))
                            .formatValue(val -> {
                                switch(val) {
                                    case "GOOGLE_FREE": return translate("provider.google");
                                    case "DEEPL_FREE": return translate("provider.deepl");
                                    case "YANDEX": return translate("provider.yandex");
                                    default: return Component.literal(val);
                                }
                            }))
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.showkey"))
                        .description(OptionDescription.of(translate("opt.showkey.desc")))
                        .binding(false, () -> showApiKey, val -> showApiKey = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<String>createBuilder()
                        .name(translate("opt.key"))
                        .description(OptionDescription.of(translate("opt.key.desc")))
                        .binding("", 
                            () -> showApiKey ? (config.apiKey == null ? "" : config.apiKey) : (config.apiKey == null || config.apiKey.isEmpty() ? "" : "••••••••"),
                            val -> { if (showApiKey || (val != null && !val.equals("••••••••"))) { config.apiKey = val; } })
                        .controller(StringControllerBuilder::create)
                        .build())
                    .build())
                .build())
            
            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.chat"))
                .tooltip(translate("cat.chat.tooltip"))
                .group(OptionGroup.createBuilder()
                    .name(translate("group.chat"))
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_chat"))
                        .description(OptionDescription.of(translate("opt.translate_chat.desc")))
                        .binding(true, () -> config.translateChat, val -> config.translateChat = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_npc"))
                        .description(OptionDescription.of(translate("opt.translate_npc.desc")))
                        .binding(true, () -> config.translateNpcDialogues, val -> config.translateNpcDialogues = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<String>createBuilder()
                        .name(translate("opt.chat_display_mode"))
                        .description(OptionDescription.of(translate("opt.chat_display_mode.desc")))
                        .binding("SEPARATE_LINE", () -> config.chatDisplayMode, val -> config.chatDisplayMode = val)
                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                            .values(List.of("SEPARATE_LINE", "APPEND", "INLINE", "REPLACE"))
                            .formatValue(val -> {
                                switch(val) {
                                    case "SEPARATE_LINE": return translate("mode.separate_line");
                                    case "APPEND": return translate("mode.append");
                                    case "INLINE": return translate("mode.inline");
                                    case "REPLACE": return translate("mode.replace");
                                    default: return Component.literal(val);
                                }
                            }))
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.hover_original"))
                        .description(OptionDescription.of(translate("opt.hover_original.desc")))
                        .binding(true, () -> config.showHoverOriginal, val -> config.showHoverOriginal = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.outgoing"))
                        .description(OptionDescription.of(translate("opt.outgoing.desc")))
                        .binding(false, () -> config.translateOutgoingChat, val -> config.translateOutgoingChat = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                .group(OptionGroup.createBuilder()
                    .name(translate("group.channels"))
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.guild_chat"))
                        .description(OptionDescription.of(translate("opt.guild_chat.desc")))
                        .binding(true, () -> config.translateGuildChat, val -> config.translateGuildChat = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.party_chat"))
                        .description(OptionDescription.of(translate("opt.party_chat.desc")))
                        .binding(true, () -> config.translatePartyChat, val -> config.translatePartyChat = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.coop_chat"))
                        .description(OptionDescription.of(translate("opt.coop_chat.desc")))
                        .binding(true, () -> config.translateCoopChat, val -> config.translateCoopChat = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.dm_chat"))
                        .description(OptionDescription.of(translate("opt.dm_chat.desc")))
                        .binding(true, () -> config.translateDirectMessages, val -> config.translateDirectMessages = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.public_chat"))
                        .description(OptionDescription.of(translate("opt.public_chat.desc")))
                        .binding(true, () -> config.translatePublicChat, val -> config.translatePublicChat = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.system_chat"))
                        .description(OptionDescription.of(translate("opt.system_chat.desc")))
                        .binding(true, () -> config.translateSystemMessages, val -> config.translateSystemMessages = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                .build())
            
            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.world"))
                .tooltip(translate("cat.world.tooltip"))
                .group(OptionGroup.createBuilder()
                    .name(translate("group.entities"))
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.holograms"))
                        .description(OptionDescription.of(translate("opt.holograms.desc")))
                        .binding(true, () -> config.translateHolograms, val -> config.translateHolograms = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.mobs"))
                        .description(OptionDescription.of(translate("opt.mobs.desc")))
                        .binding(true, () -> config.translateMobNames, val -> config.translateMobNames = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.npc_names"))
                        .description(OptionDescription.of(translate("opt.npc_names.desc")))
                        .binding(true, () -> config.translateNpcNames, val -> config.translateNpcNames = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_tablist"))
                        .description(OptionDescription.of(translate("opt.translate_tablist.desc")))
                        .binding(true, () -> config.translateTabList, val -> config.translateTabList = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                .build())
            
            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.gui"))
                .tooltip(translate("cat.gui.tooltip"))
                .group(OptionGroup.createBuilder()
                    .name(translate("group.gui"))
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.tooltips"))
                        .description(OptionDescription.of(translate("opt.tooltips.desc")))
                        .binding(true, () -> config.translateTooltips, val -> config.translateTooltips = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.item_names"))
                        .description(OptionDescription.of(translate("opt.item_names.desc")))
                        .binding(false, () -> config.translateItemNames, val -> config.translateItemNames = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.enchantments"))
                        .description(OptionDescription.of(translate("opt.enchantments.desc")))
                        .binding(false, () -> config.translateEnchantments, val -> config.translateEnchantments = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_enchantment_descriptions"))
                        .description(OptionDescription.of(translate("opt.translate_enchantment_descriptions.desc")))
                        .binding(true, () -> config.translateEnchantmentDescriptions, val -> config.translateEnchantmentDescriptions = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_ability_names"))
                        .description(OptionDescription.of(translate("opt.translate_ability_names.desc")))
                        .binding(false, () -> config.translateAbilityNames, val -> config.translateAbilityNames = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_ability_descriptions"))
                        .description(OptionDescription.of(translate("opt.translate_ability_descriptions.desc")))
                        .binding(true, () -> config.translateAbilityDescriptions, val -> config.translateAbilityDescriptions = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.translate_rarity"))
                        .description(OptionDescription.of(translate("opt.translate_rarity.desc")))
                        .binding(false, () -> config.translateRarity, val -> config.translateRarity = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.titles"))
                        .description(OptionDescription.of(translate("opt.titles.desc")))
                        .binding(true, () -> config.translateInventoryTitles, val -> config.translateInventoryTitles = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.scoreboard"))
                        .description(OptionDescription.of(translate("opt.scoreboard.desc")))
                        .binding(false, () -> config.translateScoreboard, val -> config.translateScoreboard = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                .build())

            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.filters"))
                .tooltip(translate("cat.filters.tooltip"))
                .group(OptionGroup.createBuilder()
                    .name(translate("group.technical_filters"))
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_cooldowns"))
                        .description(OptionDescription.of(translate("opt.ignore_cooldowns.desc")))
                        .binding(true, () -> config.ignoreAbilityCooldowns, val -> config.ignoreAbilityCooldowns = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_damage"))
                        .description(OptionDescription.of(translate("opt.ignore_damage.desc")))
                        .binding(true, () -> config.ignoreDamageIndicators, val -> config.ignoreDamageIndicators = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_prefixes"))
                        .description(OptionDescription.of(translate("opt.ignore_prefixes.desc")))
                        .binding(true, () -> config.ignoreModPrefixes, val -> config.ignoreModPrefixes = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_dungeons"))
                        .description(OptionDescription.of(translate("opt.ignore_dungeons.desc")))
                        .binding(true, () -> config.ignoreDungeonStats, val -> config.ignoreDungeonStats = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_slayer"))
                        .description(OptionDescription.of(translate("opt.ignore_slayer.desc")))
                        .binding(true, () -> config.ignoreSlayerAlerts, val -> config.ignoreSlayerAlerts = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_lobby"))
                        .description(OptionDescription.of(translate("opt.ignore_lobby.desc")))
                        .binding(true, () -> config.ignoreLobbyJoinLeft, val -> config.ignoreLobbyJoinLeft = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_clicks"))
                        .description(OptionDescription.of(translate("opt.ignore_clicks.desc")))
                        .binding(true, () -> config.ignoreClickActions, val -> config.ignoreClickActions = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_bazaar"))
                        .description(OptionDescription.of(translate("opt.ignore_bazaar.desc")))
                        .binding(true, () -> config.ignoreBazaarAndBin, val -> config.ignoreBazaarAndBin = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_uuids"))
                        .description(OptionDescription.of(translate("opt.ignore_uuids.desc")))
                        .binding(true, () -> config.ignoreUuidsAndIds, val -> config.ignoreUuidsAndIds = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_urls"))
                        .description(OptionDescription.of(translate("opt.ignore_urls.desc")))
                        .binding(true, () -> config.ignoreUrlsAndIps, val -> config.ignoreUrlsAndIps = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .option(Option.<Boolean>createBuilder()
                        .name(translate("opt.ignore_dates"))
                        .description(OptionDescription.of(translate("opt.ignore_dates.desc")))
                        .binding(true, () -> config.ignoreDatesAndLobbies, val -> config.ignoreDatesAndLobbies = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                    .build())
                .build())
            
            
            .category(ConfigCategory.createBuilder()
                .name(translate("cat.cache"))
                .tooltip(translate("cat.cache.tooltip"))
                .group(OptionGroup.createBuilder()
                    .name(translate("group.actions"))
                    .option(ButtonOption.createBuilder()
                        .name(translate("opt.clear_cache"))
                        .description(OptionDescription.of(translate("opt.clear_cache.desc")))
                        .action((opt, btn) -> {
                            TranslationCache.clear();
                            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                            if (client.player != null) {
                                client.player.displayClientMessage(Component.literal("§a[SkyBlock Translator] Translation cache cleared successfully! / Кэш переводов успешно очищен!"), false);
                            }
                            try {
                                client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
                            } catch (Exception e) {}
                            try {
                                net.minecraft.client.gui.components.toasts.SystemToast.add(
                                    client.getToastManager(),
                                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("SkyBlock Translator"),
                                    Component.literal("Cache cleared! / Кэш очищен!")
                                );
                            } catch (Exception e) {}
                        })
                        .build())
                    .option(ButtonOption.createBuilder()
                        .name(translate("opt.reload"))
                        .description(OptionDescription.of(translate("opt.reload.desc")))
                        .action((opt, btn) -> {
                            TranslatorConfigManager.load();
                            SkyblockDictionary.loadUserDictionary();
                        })
                        .build())
                    .option(ButtonOption.createBuilder()
                        .name(translate("opt.report"))
                        .description(OptionDescription.of(translate("opt.report.desc")))
                        .action((opt, btn) -> {
                            String url = "https://github.com/fridorin/Skyblock-Translator-Mod/issues";
                            String os = System.getProperty("os.name").toLowerCase();
                            try {
                                ProcessBuilder pb;
                                if (os.contains("win")) {
                                    pb = new ProcessBuilder("cmd", "/c", "start", url);
                                } else if (os.contains("mac")) {
                                    pb = new ProcessBuilder("open", url);
                                } else {
                                    pb = new ProcessBuilder("xdg-open", url);
                                }
                                pb.start();
                            } catch (Exception e) {
                            }
                        })
                        .build())
                    .option(ButtonOption.createBuilder()
                        .name(translate("opt.suggest_translation"))
                        .description(OptionDescription.of(translate("opt.suggest_translation.desc")))
                        .action((opt, btn) -> {
                            String url = "https://github.com/fridorin/Skyblock-Translator-Mod/issues";
                            String os = System.getProperty("os.name").toLowerCase();
                            try {
                                ProcessBuilder pb;
                                if (os.contains("win")) {
                                    pb = new ProcessBuilder("cmd", "/c", "start", url);
                                } else if (os.contains("mac")) {
                                    pb = new ProcessBuilder("open", url);
                                } else {
                                    pb = new ProcessBuilder("xdg-open", url);
                                }
                                pb.start();
                            } catch (Exception e) {
                            }
                        })
                        .build())
                    .build())
                .build())
            .build();

        return configLib.generateScreen(parent);
    }
}
