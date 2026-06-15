package ru.fridorin.translator;

import ru.fridorin.translator.cache.TranslationCache;
import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import ru.fridorin.translator.service.TranslationService;
import ru.fridorin.translator.service.SkyblockDictionary;
import ru.fridorin.translator.service.SpamFilter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TranslatorModClient implements ClientModInitializer {
    private static final java.util.concurrent.ScheduledExecutorService watchdogScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SkyblockTranslator-Watchdog");
        t.setDaemon(true);
        return t;
    });

    // Fast, simple line-level cache to replace the complex tooltip cache
    private static final ConcurrentHashMap<String, Component> COMPONENT_CACHE = new ConcurrentHashMap<>();
    
    public static void clearComponentCache() {
        COMPONENT_CACHE.clear();
    }

    private static KeyMapping keyOpenSettings;
    private static KeyMapping keyToggleTranslator;
    public static volatile boolean bypassTranslation = false;
    private static int ticksElapsed = 0;
    private static volatile boolean initialized = false;

    public static KeyMapping getKeyOpenSettings() { return keyOpenSettings; }
    public static KeyMapping getKeyToggleTranslator() { return keyToggleTranslator; }

    @Override
    public void onInitializeClient() {
        // 1. FAST INIT: Load only config synchronously (it's small and essential)
        TranslatorConfigManager.init();

        // 2. ASYNC INIT: Move all heavy I/O and dictionary processing to a background thread
        CompletableFuture.runAsync(() -> {
            try {
                TranslationCache.init();
                SkyblockDictionary.loadForLanguage(TranslatorConfigManager.getConfig().targetLanguage);
                SkyblockDictionary.loadUserDictionary();
                initialized = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TranslationCache.shutdown();
            TranslationService.shutdown();
            try {
                watchdogScheduler.shutdown();
            } catch (Exception e) {
            }
        }));

        net.minecraft.client.KeyMapping.Category category = net.minecraft.client.KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("skyblock_translator", "keys")
        );

        keyOpenSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.skyblock_translator.settings",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            category
        ));

        keyToggleTranslator = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.skyblock_translator.toggle",
            InputConstants.Type.KEYSYM,
            -1,
            category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticksElapsed++;
            if (ticksElapsed % 100 == 0 || TranslationService.getOnlinePlayerNames().isEmpty()) {
                TranslationService.updateOnlinePlayerNames(client);
            }

            while (keyOpenSettings.consumeClick()) {
                client.setScreen(ru.fridorin.translator.gui.TranslatorConfigScreen.createScreen(null));
            }
            while (keyToggleTranslator.consumeClick()) {
                TranslatorConfig config = TranslatorConfigManager.getConfig();
                config.enabled = !config.enabled;
                TranslatorConfigManager.save();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("§a[SkyBlock Translator] " + (config.enabled ? "Enabled" : "Disabled")), true);
                }
            }
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!initialized || isTranslationDisabled() || !TranslatorConfigManager.getConfig().translateChat) return true;
            return handleIncomingChatEvent(message);
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            if (!initialized || isTranslationDisabled() || !TranslatorConfigManager.getConfig().translateNpcDialogues) return true;
            return handleIncomingChatEvent(message);
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (TranslationService.isBypassed() || isTranslationDisabled() || !TranslatorConfigManager.getConfig().translateOutgoingChat) return true;

            if (message.startsWith("/")) {
                return true;
            }

            if (!TranslationService.containsNonEnglishLetters(message)) return true;

            TranslationService.translateOutgoingAsync(message).thenAccept(translated -> {
                String toSend = (translated != null && !translated.trim().isEmpty()) ? translated : message;
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if (client.getConnection() != null) {
                        TranslationService.runWithBypass(() -> {
                            client.getConnection().sendChat(toSend);
                        });
                    }
                });
            });

            return false;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var command = ClientCommandManager.literal("translator")
                .then(ClientCommandManager.literal("toggle")
                    .then(ClientCommandManager.literal("chat")
                        .executes(context -> {
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.translateChat = !config.translateChat;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Chat translation: " + (config.translateChat ? "ON" : "OFF")));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("tooltips")
                        .executes(context -> {
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.translateTooltips = !config.translateTooltips;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Tooltips translation: " + (config.translateTooltips ? "ON" : "OFF")));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("titles")
                        .executes(context -> {
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.translateInventoryTitles = !config.translateInventoryTitles;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Inventory titles translation: " + (config.translateInventoryTitles ? "ON" : "OFF")));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("scoreboard")
                        .executes(context -> {
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.translateScoreboard = !config.translateScoreboard;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Scoreboard translation: " + (config.translateScoreboard ? "ON" : "OFF")));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("npc")
                        .executes(context -> {
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.translateNpcDialogues = !config.translateNpcDialogues;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] NPC dialogues translation: " + (config.translateNpcDialogues ? "ON" : "OFF")));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("outgoing")
                        .executes(context -> {
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.translateOutgoingChat = !config.translateOutgoingChat;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Outgoing chat translation: " + (config.translateOutgoingChat ? "ON" : "OFF")));
                            return 1;
                        }))
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Use: /translator toggle [chat|tooltips|titles|scoreboard|npc|outgoing]"));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("setlang")
                    .then(ClientCommandManager.argument("lang", StringArgumentType.string())
                        .executes(context -> {
                            String lang = StringArgumentType.getString(context, "lang");
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.targetLanguage = lang;
                            TranslatorConfigManager.save();
                            TranslationCache.loadForLanguage(lang);
                            SkyblockDictionary.loadForLanguage(lang);
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Target language set to: " + lang));
                            return 1;
                        }))
                )
                .then(ClientCommandManager.literal("setprovider")
                    .then(ClientCommandManager.argument("provider", StringArgumentType.string())
                        .executes(context -> {
                            String provider = StringArgumentType.getString(context, "provider").toUpperCase();
                            if (provider.equals("GOOGLE_FREE") || provider.equals("DEEPL_FREE") || provider.equals("YANDEX")) {
                                TranslatorConfig config = TranslatorConfigManager.getConfig();
                                config.apiProvider = provider;
                                TranslatorConfigManager.save();
                                context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] API Provider set to: " + provider));
                            } else {
                                context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Invalid provider. Valid options: GOOGLE_FREE, DEEPL_FREE, YANDEX"));
                            }
                            return 1;
                        }))
                )
                .then(ClientCommandManager.literal("setkey")
                    .then(ClientCommandManager.argument("key", StringArgumentType.string())
                        .executes(context -> {
                            String key = StringArgumentType.getString(context, "key");
                            TranslatorConfig config = TranslatorConfigManager.getConfig();
                            config.apiKey = key;
                            TranslatorConfigManager.save();
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] API Key updated."));
                            return 1;
                        }))
                )
                .then(ClientCommandManager.literal("clear-cache")
                    .executes(context -> {
                        TranslationCache.clear();
                        context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Translation cache cleared."));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("reload")
                    .executes(context -> {
                        TranslatorConfigManager.load();
                        SkyblockDictionary.loadUserDictionary();
                        context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Configuration and user dictionary reloaded."));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("pair", StringArgumentType.greedyString())
                        .executes(context -> {
                            String pair = StringArgumentType.getString(context, "pair");
                            int eqIdx = pair.indexOf('=');
                            if (eqIdx == -1) {
                                context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Format must be: /translator add <phrase> = <translation>"));
                                return 1;
                            }
                            String english = pair.substring(0, eqIdx).trim();
                            String translation = pair.substring(eqIdx + 1).trim();
                            if (english.isEmpty() || translation.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Phrase and translation cannot be empty!"));
                                return 1;
                            }
                            SkyblockDictionary.addUserTranslation(english, translation);
                            context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Added translation mapping: '" + english + "' -> '" + translation + "'"));
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("lookup")
                    .then(ClientCommandManager.argument("phrase", StringArgumentType.greedyString())
                        .executes(context -> {
                            String phrase = StringArgumentType.getString(context, "phrase");
                            String translation = SkyblockDictionary.getTranslationOrUserTranslation(phrase);
                            if (translation != null) {
                                context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] '" + phrase + "' = " + translation));
                            } else {
                                context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Phrase '" + phrase + "' not found in dictionaries."));
                            }
                            return 1;
                        })
                    )
                )
                .then(ClientCommandManager.literal("copy-hand")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        if (client.player == null) {
                            context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] You must be in-game to use this command!"));
                            return 1;
                        }
                        net.minecraft.world.item.ItemStack itemInHand = client.player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
                        if (itemInHand.isEmpty()) {
                            context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Hold an item in your main hand first!"));
                            return 1;
                        }
                        
                        bypassTranslation = true;
                        java.util.List<Component> rawLines;
                        try {
                            rawLines = itemInHand.getTooltipLines(
                                net.minecraft.world.item.Item.TooltipContext.of(client.level),
                                client.player,
                                net.minecraft.world.item.TooltipFlag.Default.NORMAL
                            );
                        } finally {
                            bypassTranslation = false;
                        }

                        if (rawLines == null || rawLines.isEmpty()) {
                            context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] No tooltip lines found on this item!"));
                            return 1;
                        }

                        java.util.Map<String, String> template = new java.util.LinkedHashMap<>();
                        for (Component line : rawLines) {
                            String plain = line.getString();
                            String clean = plain.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "").trim();
                            if (!clean.isEmpty()) {
                                template.put(clean.toLowerCase(), "");
                            }
                        }
                        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(template);
                        client.keyboardHandler.setClipboard(json);
                        context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Item translation JSON template copied to clipboard!"));
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("update")
                    .executes(context -> {
                        TranslatorConfig config = TranslatorConfigManager.getConfig();
                        context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Fetching latest dictionary from GitHub..."));
                        SkyblockDictionary.downloadDictionaryAsync(config.targetLanguage).thenAccept(size -> {
                            Minecraft client = Minecraft.getInstance();
                            client.execute(() -> {
                                if (size >= 0) {
                                    context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Dictionary updated! Loaded " + size + " entries from GitHub."));
                                } else {
                                    context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Failed to update dictionary from GitHub. Check your internet connection or repository settings."));
                                }
                            });
                        });
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("test")
                    .executes(context -> {
                        TranslatorConfig config = TranslatorConfigManager.getConfig();
                        context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Testing connection to " + config.apiProvider + "..."));
                        long start = System.currentTimeMillis();
                        TranslationService.translateAsync("Hello").thenAccept(translated -> {
                            long duration = System.currentTimeMillis() - start;
                            Minecraft client = Minecraft.getInstance();
                            client.execute(() -> {
                                if (translated == null || translated.trim().isEmpty() || translated.startsWith("[") || translated.equalsIgnoreCase("Hello")) {
                                    context.getSource().sendFeedback(Component.literal("§c[SkyBlock Translator] Test failed! Check your API Key or connection. Response: " + translated));
                                } else {
                                    context.getSource().sendFeedback(Component.literal("§a[SkyBlock Translator] Connection successful! (" + duration + "ms)"));
                                    context.getSource().sendFeedback(Component.literal("§7Test Translation: \"Hello\" ➔ \"" + translated + "\""));
                                }
                            });
                        });
                        return 1;
                    })
                )
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        client.setScreen(ru.fridorin.translator.gui.TranslatorConfigScreen.createScreen(null));
                    });
                    return 1;
                });

            dispatcher.register(command);

            dispatcher.register(ClientCommandManager.literal("переводчик")
                .redirect(dispatcher.getRoot().getChild("translator"))
            );
        });
    }

    public static String componentToLegacy(Component component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();

        Style style = component.getStyle();
        sb.append(styleToLegacyCodes(style));

        if (component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents) {
            sb.append(((net.minecraft.network.chat.contents.PlainTextContents) component.getContents()).text());
        } else if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents) {
            net.minecraft.network.chat.contents.TranslatableContents translatable = (net.minecraft.network.chat.contents.TranslatableContents) component.getContents();
            String key = translatable.getKey();
            String localized = net.minecraft.client.resources.language.I18n.get(key);
            Object[] args = translatable.getArgs();
            if (args != null && args.length > 0) {
                try {
                    Object[] formattedArgs = new Object[args.length];
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] instanceof Component) {
                            formattedArgs[i] = componentToLegacy((Component) args[i]);
                        } else {
                            formattedArgs[i] = args[i];
                        }
                    }
                    sb.append(String.format(localized, formattedArgs));
                } catch (Exception e) {
                    sb.append(localized);
                }
            } else {
                sb.append(localized);
            }
        }

        for (Component sibling : component.getSiblings()) {
            sb.append(componentToLegacy(sibling));
        }

        return sb.toString();
    }

    public static String styleToLegacyCodes(Style style) {
        if (style == null || style.isEmpty()) return "§r";
        StringBuilder sb = new StringBuilder("§r");
        if (style.getColor() != null) {
            String hex = style.getColor().serialize();
            if (hex.startsWith("#")) {
                sb.append("§x");
                for (char c : hex.substring(1).toCharArray()) {
                    sb.append("§").append(c);
                }
            } else {
                String colorStr = hex.toLowerCase();
                switch (colorStr) {
                    case "black": sb.append("§0"); break;
                    case "dark_blue": sb.append("§1"); break;
                    case "dark_green": sb.append("§2"); break;
                    case "dark_aqua": sb.append("§3"); break;
                    case "dark_red": sb.append("§4"); break;
                    case "dark_purple": sb.append("§5"); break;
                    case "gold": sb.append("§6"); break;
                    case "gray": sb.append("§7"); break;
                    case "dark_gray": sb.append("§8"); break;
                    case "blue": sb.append("§9"); break;
                    case "green": sb.append("§a"); break;
                    case "aqua": sb.append("§b"); break;
                    case "red": sb.append("§c"); break;
                    case "light_purple": sb.append("§d"); break;
                    case "yellow": sb.append("§e"); break;
                    case "white": sb.append("§f"); break;
                }
            }
        }
        if (style.isBold()) sb.append("§l");
        if (style.isItalic()) sb.append("§o");
        if (style.isUnderlined()) sb.append("§n");
        if (style.isStrikethrough()) sb.append("§m");
        if (style.isObfuscated()) sb.append("§k");
        return sb.toString();
    }

    public static TextColor getFirstColor(Component component) {
        if (component == null) return null;
        TextColor color = component.getStyle().getColor();
        if (color != null) return color;
        for (Component sibling : component.getSiblings()) {
            TextColor siblingColor = getFirstColor(sibling);
            if (siblingColor != null) return siblingColor;
        }
        return null;
    }

    public static Component legacyToComponent(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        MutableComponent root = Component.empty();
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);

                if (currentText.length() > 0) {
                    root.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                    currentText.setLength(0);
                }

                if ((code == 'x' || code == 'X') && i + 13 < text.length() && 
                    text.charAt(i + 2) == '§' && text.charAt(i + 4) == '§' && 
                    text.charAt(i + 6) == '§' && text.charAt(i + 8) == '§' && 
                    text.charAt(i + 10) == '§' && text.charAt(i + 12) == '§') {

                    StringBuilder hex = new StringBuilder("#");
                    hex.append(text.charAt(i + 3));
                    hex.append(text.charAt(i + 5));
                    hex.append(text.charAt(i + 7));
                    hex.append(text.charAt(i + 9));
                    hex.append(text.charAt(i + 11));
                    hex.append(text.charAt(i + 13));

                    try {
                        TextColor textColor = TextColor.parseColor(hex.toString()).getOrThrow();
                        currentStyle = Style.EMPTY.withColor(textColor);
                    } catch (Exception e) {
                    }
                    i += 14;
                    continue;
                }

                char lowerCode = Character.toLowerCase(code);
                switch (lowerCode) {
                    case '0': currentStyle = Style.EMPTY.withColor(ChatFormatting.BLACK); break;
                    case '1': currentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_BLUE); break;
                    case '2': currentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_GREEN); break;
                    case '3': currentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_AQUA); break;
                    case '4': currentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_RED); break;
                    case '5': currentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE); break;
                    case '6': currentStyle = Style.EMPTY.withColor(ChatFormatting.GOLD); break;
                    case '7': currentStyle = Style.EMPTY.withColor(ChatFormatting.GRAY); break;
                    case '8': currentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_GRAY); break;
                    case '9': currentStyle = Style.EMPTY.withColor(ChatFormatting.BLUE); break;
                    case 'a': currentStyle = Style.EMPTY.withColor(ChatFormatting.GREEN); break;
                    case 'b': currentStyle = Style.EMPTY.withColor(ChatFormatting.AQUA); break;
                    case 'c': currentStyle = Style.EMPTY.withColor(ChatFormatting.RED); break;
                    case 'd': currentStyle = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE); break;
                    case 'e': currentStyle = Style.EMPTY.withColor(ChatFormatting.YELLOW); break;
                    case 'f': currentStyle = Style.EMPTY.withColor(ChatFormatting.WHITE); break;
                    case 'k': currentStyle = currentStyle.withObfuscated(true); break;
                    case 'l': currentStyle = currentStyle.withBold(true); break;
                    case 'm': currentStyle = currentStyle.withStrikethrough(true); break;
                    case 'n': currentStyle = currentStyle.withUnderlined(true); break;
                    case 'o': currentStyle = currentStyle.withItalic(true); break;
                    case 'r': currentStyle = Style.EMPTY; break;
                }
                i += 2;
            } else {
                currentText.append(c);
                i++;
            }
        }

        if (currentText.length() > 0) {
            root.append(Component.literal(currentText.toString()).setStyle(currentStyle));
        }

        return root;
    }

    public static Component translateComponent(Component component) {
        return translateComponentWithIgnore(component, null);
    }

    public static Component translateComponentWithIgnore(Component component, String ignoreText) {
        if (component == null) return component;
        if (isTranslationDisabled()) {
            return component;
        }

        String legacyText = componentToLegacy(component);
        if (legacyText.isEmpty()) {
            return component;
        }

        int leadingSpaces = 0;
        while (leadingSpaces < legacyText.length() && legacyText.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        int trailingSpaces = 0;
        while (trailingSpaces < legacyText.length() && legacyText.charAt(legacyText.length() - 1 - trailingSpaces) == ' ') {
            trailingSpaces++;
        }
        String trimmed = legacyText.trim();

        if (trimmed.isEmpty() || TranslationService.containsNonEnglishLetters(trimmed) || !TranslationService.containsLetters(trimmed)) {
            return component;
        }

        // Check our fast line-level component cache
        Component cached = COMPONENT_CACHE.get(legacyText);
        if (cached != null) {
            return cached;
        }

        if (ignoreText != null && !ignoreText.trim().isEmpty()) {
            String cleanIgnore = ignoreText.trim();
            if (trimmed.equalsIgnoreCase(cleanIgnore) || trimmed.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "").trim().equalsIgnoreCase(cleanIgnore)) {
                return component;
            }
        }

        String immediate = TranslationService.getImmediateTranslation(trimmed);
        if (immediate != null) {
            if (TranslationService.isTranslationSame(trimmed, immediate)) {
                return component;
            }
            String restored = " ".repeat(leadingSpaces) + immediate + " ".repeat(trailingSpaces);
            Component translatedComponent = legacyToComponent(restored);
            COMPONENT_CACHE.put(legacyText, translatedComponent);
            
            // Limit cache size to prevent memory leaks
            if (COMPONENT_CACHE.size() > 5000) {
                COMPONENT_CACHE.clear();
            }
            return translatedComponent;
        }

        final int finalLeadingSpaces = leadingSpaces;
        final int finalTrailingSpaces = trailingSpaces;

        TranslationService.translateAsync(trimmed).thenAccept(translatedLegacy -> {
            if (translatedLegacy != null && !translatedLegacy.trim().isEmpty() && !translatedLegacy.equals(trimmed)) {
                String restored = " ".repeat(finalLeadingSpaces) + translatedLegacy + " ".repeat(finalTrailingSpaces);
                COMPONENT_CACHE.put(legacyText, legacyToComponent(restored));
            }
        });
        
        return component;
    }

    public static CompletableFuture<Component> translateComponentAsync(Component component) {
        if (component == null) {
            return CompletableFuture.completedFuture(component);
        }

        String legacyText = componentToLegacy(component);
        if (legacyText.isEmpty()) {
            return CompletableFuture.completedFuture(component);
        }

        int leadingSpaces = 0;
        while (leadingSpaces < legacyText.length() && legacyText.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        int trailingSpaces = 0;
        while (trailingSpaces < legacyText.length() && legacyText.charAt(legacyText.length() - 1 - trailingSpaces) == ' ') {
            trailingSpaces++;
        }
        String trimmed = legacyText.trim();

        if (trimmed.isEmpty() || TranslationService.containsNonEnglishLetters(trimmed) || !TranslationService.containsLetters(trimmed)) {
            return CompletableFuture.completedFuture(component);
        }

        String immediate = TranslationService.getImmediateTranslation(trimmed);
        if (immediate != null) {
            String restored = " ".repeat(leadingSpaces) + immediate + " ".repeat(trailingSpaces);
            return CompletableFuture.completedFuture(legacyToComponent(restored));
        }

        final int finalLeadingSpaces = leadingSpaces;
        final int finalTrailingSpaces = trailingSpaces;

        return TranslationService.translateAsync(trimmed).thenApply(translatedLegacy -> {
            if (translatedLegacy == null || translatedLegacy.trim().isEmpty() || translatedLegacy.equals(trimmed)) {
                return component;
            }
            String restored = " ".repeat(finalLeadingSpaces) + translatedLegacy + " ".repeat(finalTrailingSpaces);
            return legacyToComponent(restored);
        });
    }

    private boolean handleIncomingChatEvent(Component message) {
        if (!initialized) return true;
        String rawText = message.getString();
        if (rawText == null || rawText.trim().isEmpty()) return true;

        if (SpamFilter.shouldIgnore(rawText)) {
            return true;
        }
        if (TranslationService.containsNonEnglishLetters(rawText)) {
            return true;
        }

        TranslatorConfig config = TranslatorConfigManager.getConfig();

        int rawColonIndex = rawText.indexOf(": ");
        
        boolean shouldTranslate = true;
        if (rawColonIndex != -1) {
            String rawPrefix = rawText.substring(0, rawColonIndex).trim();
            String cleanPrefix = rawPrefix.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "");
            
            if (cleanPrefix.contains("Guild >") || cleanPrefix.contains("G >")) {
                shouldTranslate = config.translateGuildChat;
            } else if (cleanPrefix.contains("Party >") || cleanPrefix.contains("P >")) {
                shouldTranslate = config.translatePartyChat;
            } else if (cleanPrefix.contains("Co-op >") || cleanPrefix.contains("C >")) {
                shouldTranslate = config.translateCoopChat;
            } else if (cleanPrefix.contains("Officer >") || cleanPrefix.contains("O >")) {
                shouldTranslate = config.translateGuildChat;
            } else if (cleanPrefix.contains("From ") || cleanPrefix.contains("To ")) {
                shouldTranslate = config.translateDirectMessages;
            } else {
                shouldTranslate = config.translatePublicChat;
            }
        } else {
            if (rawText.trim().startsWith("[NPC]")) {
                shouldTranslate = config.translateNpcDialogues;
            } else {
                shouldTranslate = config.translateSystemMessages;
            }
        }

        if (!shouldTranslate) {
            return true;
        }

        translateComponentAsync(message).thenAccept(translated -> {
            if (translated == null) {
                return;
            }
            String originalRaw = message.getString();
            String translatedRaw = translated.getString();
            if (TranslationService.isTranslationSame(originalRaw, translatedRaw)) {
                return;
            }

            Component finalMessage;
            if ("REPLACE".equalsIgnoreCase(config.chatDisplayMode)) {
                finalMessage = translated;
            } else if ("INLINE".equalsIgnoreCase(config.chatDisplayMode)) {
                MutableComponent combined = message.copy();
                combined.append(Component.literal(" §7("));
                combined.append(translated);
                combined.append(Component.literal("§7)"));
                finalMessage = combined;
            } else if ("SEPARATE_LINE".equalsIgnoreCase(config.chatDisplayMode)) {
                TextColor firstColor = getFirstColor(message);
                MutableComponent prefix = Component.literal("\n↳ [" + config.targetLanguage.toUpperCase() + "] ");
                if (firstColor != null) {
                    prefix.setStyle(Style.EMPTY.withColor(firstColor));
                } else {
                    prefix.setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.GRAY)));
                }
                Component translationLine = Component.empty().append(prefix).append(translated);
                if (config.showHoverOriginal) {
                    net.minecraft.network.chat.HoverEvent hoverEvent = new net.minecraft.network.chat.HoverEvent.ShowText(
                        Component.literal("§7Original: §f").append(message.copy())
                    );
                    translationLine = translationLine.copy().setStyle(translationLine.getStyle().withHoverEvent(hoverEvent));
                }
                finalMessage = message.copy().append(translationLine);
            } else {
                TextColor firstColor = getFirstColor(message);
                MutableComponent prefix = Component.literal("↳ [" + config.targetLanguage.toUpperCase() + "] ");
                if (firstColor != null) {
                    prefix.setStyle(Style.EMPTY.withColor(firstColor));
                } else {
                    prefix.setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.GRAY)));
                }
                MutableComponent combined = message.copy();
                combined.append(Component.literal("\n ").append(prefix).append("§7§o"));
                combined.append(translated);
                finalMessage = combined;
            }

            if (config.showHoverOriginal && finalMessage != message && !"SEPARATE_LINE".equalsIgnoreCase(config.chatDisplayMode)) {
                net.minecraft.network.chat.HoverEvent hoverEvent = new net.minecraft.network.chat.HoverEvent.ShowText(
                    Component.literal("§7Original: §f").append(message.copy())
                );
                finalMessage = finalMessage.copy().setStyle(finalMessage.getStyle().withHoverEvent(hoverEvent));
            }

            final Component finalMsg = finalMessage;
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().gui != null && Minecraft.getInstance().gui.getChat() instanceof ru.fridorin.translator.ChatUpdateExtension) {
                    ((ru.fridorin.translator.ChatUpdateExtension) Minecraft.getInstance().gui.getChat()).skyblock_translator$updateMessage(message, finalMsg);
                }
            });
        });

        return true;
    }

    public static boolean isTranslationDisabled() {
        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.enabled) return true;
        if ("en".equalsIgnoreCase(config.targetLanguage)) return true;
        try {
            if (config.disableOnShift && net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }
}
