package ru.fridorin.translator.service;

import ru.fridorin.translator.cache.TranslationCache;
import ru.fridorin.translator.config.TranslatorConfig;
import ru.fridorin.translator.config.TranslatorConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationService {
    // Bounded executor to prevent unbounded thread growth from HTTP requests
    private static final java.util.concurrent.ExecutorService HTTP_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "SkyblockTranslator-HTTP");
        t.setDaemon(true);
        return t;
    });

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(HTTP_EXECUTOR)
            .build();

    private static final java.util.concurrent.ExecutorService TRANSLATION_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "SkyblockTranslator-Worker");
        t.setDaemon(true);
        return t;
    });

    // Rate-limit: max 3 concurrent API requests to avoid flooding and IP blocks (429)
    private static final Semaphore API_SEMAPHORE = new Semaphore(3);
    private static final int MAX_ACTIVE_TRANSLATIONS = 500;
    private static final long ACTIVE_TRANSLATION_TTL_MS = 15000; // 15 seconds max per pending translation

    private static final ConcurrentHashMap<String, CompletableFuture<String>> ACTIVE_TRANSLATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ACTIVE_TRANSLATIONS_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> FAILED_TRANSLATIONS = new ConcurrentHashMap<>();

    // Callback for tooltip cache invalidation when async translations complete
    private static volatile Runnable tooltipCacheInvalidator = null;

    public static void setTooltipCacheInvalidator(Runnable invalidator) {
        tooltipCacheInvalidator = invalidator;
    }

    private static void notifyTooltipCacheInvalidation() {
        Runnable inv = tooltipCacheInvalidator;
        if (inv != null) {
            try { inv.run(); } catch (Exception e) { /* ignore */ }
        }
    }
    private static final ThreadLocal<Boolean> BYPASS_TRANSLATION = ThreadLocal.withInitial(() -> false);

    public static void runWithBypass(Runnable action) {
        BYPASS_TRANSLATION.set(true);
        try { action.run(); } finally { BYPASS_TRANSLATION.set(false); }
    }

    public static <T> T callWithBypass(java.util.function.Supplier<T> action) {
        BYPASS_TRANSLATION.set(true);
        try { return action.get(); } finally { BYPASS_TRANSLATION.set(false); }
    }

    public static boolean isBypassed() { return BYPASS_TRANSLATION.get(); }

    private static volatile long lastFailedCleanup = 0;
    private static final long FAILED_CLEANUP_INTERVAL = 30000;

    private static void markApiError(String text) {
        if (text != null) {
            FAILED_TRANSLATIONS.put(text.trim(), System.currentTimeMillis() + 10000); // 10s cooldown
        }
    }

    private static void cleanupFailedTranslations() {
        long now = System.currentTimeMillis();
        if (now - lastFailedCleanup < FAILED_CLEANUP_INTERVAL) return;
        lastFailedCleanup = now;
        FAILED_TRANSLATIONS.entrySet().removeIf(entry -> now >= entry.getValue());
        // Also clean up stale active translations that never completed
        ACTIVE_TRANSLATIONS_TIMESTAMPS.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > ACTIVE_TRANSLATION_TTL_MS) {
                CompletableFuture<String> stale = ACTIVE_TRANSLATIONS.remove(entry.getKey());
                if (stale != null && !stale.isDone()) {
                    stale.complete(""); // unblock any waiters
                }
                return true;
            }
            return false;
        });
    }

    private static boolean warnedAboutMissingKey = false;
    private static void warnMissingKey(String provider) {
        if (!warnedAboutMissingKey) {
            warnedAboutMissingKey = true;
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            client.execute(() -> {
                //? if <26.1 {
                if (client.gui != null) {
                    client.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                        "§c[SkyBlock Translator] API key for " + provider + " is missing! Falling back to Google Translator."
                    ));
                }
                //?} else {
                /*if (client.player != null) {
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[SkyBlock Translator] API key for " + provider + " is missing! Falling back to Google Translator."
                    ));
                }
                *///?}
            });
        }
    }

    public static boolean isDamageIndicator(String cleanText) {
        if (cleanText == null) return false;
        String lower = cleanText.toLowerCase().trim();
        if (lower.isEmpty()) return false;
        String stripped = lower.replaceAll("crit", "").replaceAll("[0-9.,\\s+\\-kmb%]", "").replaceAll("[✧❤⚡❈⚔☠]", "");
        return stripped.isEmpty();
    }

    public static boolean isTechnicalOrModLine(String text) {
        if (text == null) return true;
        String clean = text.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "")
                           .replaceAll("(?i)\\{F\\d+\\}|\\{P\\d+\\}|\\{C\\d+\\}|\\{\\d+\\}", "");
        String lower = clean.toLowerCase().trim();
        if (lower.isEmpty()) return true;

        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (config.ignoreUuidsAndIds && (lower.contains("uuid:") || lower.contains("profile id:") || lower.contains("coop id:") || lower.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"))) return true;
        if (config.ignoreUrlsAndIps && (lower.contains("hypixel.net") || lower.contains("http://") || lower.contains("https://"))) return true;
        if (config.ignoreModPrefixes && (lower.contains("neu:") || lower.contains("skytils:") || lower.contains("sba:"))) return true;
        
        if (lower.startsWith("/")) return true;

        return false;
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[+-]?\\d+(?:[.,]\\d+)*(?:st|nd|rd|th|ST|ND|RD|TH|[kKM%])?");
    public static final Pattern ENCHANTMENT_LINE_PATTERN = Pattern.compile("^(?:[A-Za-z0-9\\s'-]+\\s+[IVXLCDM]+)(?:,\\s+[A-Za-z0-9\\s'-]+\\s+[IVXLCDM]+)*$");
    public static final Pattern NAMESPACE_PATTERN = Pattern.compile("^(?i)(minecraft|skyblock)\\s*:\\s*.+$");
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§.", Pattern.CASE_INSENSITIVE);

    public static class FormattingTemplateResult {
        public final String templatedText;
        public final List<String> formattingCodes;
        public FormattingTemplateResult(String templatedText, List<String> formattingCodes) {
            this.templatedText = templatedText;
            this.formattingCodes = formattingCodes;
        }
    }

    public static FormattingTemplateResult extractFormatting(String text) {
        List<String> codes = new ArrayList<>();
        if (text == null) return new FormattingTemplateResult("", codes);
        Matcher matcher = FORMATTING_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            codes.add(matcher.group());
            matcher.appendReplacement(sb, "{F" + count + "}");
            count++;
        }
        matcher.appendTail(sb);
        return new FormattingTemplateResult(sb.toString(), codes);
    }

    public static String restoreFormatting(String text, List<String> codes) {
        if (text == null) return null;
        String result = text;
        for (int i = 0; i < codes.size(); i++) {
            String regex = "(?:\\{\\s*|\\b_?|[\\(\\[])[fFфФ]\\s*_*\\s*" + i + "\\s*_*\\s*(?:\\s*\\}|_?\\b|[\\)\\]])";
            result = Pattern.compile(regex).matcher(result).replaceAll(Matcher.quoteReplacement(codes.get(i)));
        }
        return result;
    }

    public static boolean isTranslationSame(String original, String translated) {
        if (original == null || translated == null) return true;
        String cleanOrig = original.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "").replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String cleanTrans = translated.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "").replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return cleanOrig.equals(cleanTrans);
    }

    public static class TemplateResult {
        public final String templatedText;
        public final List<String> values;
        public TemplateResult(String templatedText, List<String> values) {
            this.templatedText = templatedText;
            this.values = values;
        }
    }

    public static TemplateResult extractTemplates(String text) {
        List<String> values = new ArrayList<>();
        if (text == null) return new TemplateResult("", values);
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            values.add(matcher.group());
            matcher.appendReplacement(sb, "{" + count + "}");
            count++;
        }
        matcher.appendTail(sb);
        return new TemplateResult(sb.toString(), values);
    }

    public static String restoreTemplates(String templatedText, List<String> values) {
        if (templatedText == null) return null;
        String result = templatedText;
        for (int i = 0; i < values.size(); i++) {
            result = result.replace("{" + i + "}", values.get(i));
            String regex = "(?:\\{\\s*|[\\(\\[])" + i + "\\s*(?:\\s*\\}|[\\)\\]])";
            result = Pattern.compile(regex).matcher(result).replaceAll(Matcher.quoteReplacement(values.get(i)));
        }
        return result;
    }

    public static String normalizePlaceholders(String text) {
        if (text == null) return null;
        return text.replaceAll("[\\[\\{\\(]\\s*(\\d+)\\s*[\\}\\]\\)]", "{$1}");
    }

    public static String cleanPunctuationSpaces(String text) {
        if (text == null) return null;
        return text.replaceAll("\\s+(\\)|\\]|\\})", "$1").replaceAll("(\\(|\\[|\\{)\\s+", "$1").replaceAll("\\s+([.,!?:;])", "$1");
    }

    public static String getImmediateTranslation(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        
        // Normalize double spaces that may be introduced by componentToLegacy serialization
        String normalizedText = text.replaceAll("\\s+", " ").trim();

        FormattingTemplateResult fmtTemplate = extractFormatting(normalizedText);
        PlayerTemplateResult playerTemplate = templatePlayerNames(fmtTemplate.templatedText);
        CommandTemplateResult commandTemplate = templateCommands(playerTemplate.templatedText);

        String dictMatch = SkyblockDictionary.lookup(commandTemplate.templatedText);
        if (dictMatch != null) {
            String res = restoreCommands(dictMatch, commandTemplate.commands);
            res = restorePlayerNames(res, playerTemplate.playerNames);
            return restoreFormatting(res, fmtTemplate.formattingCodes);
        }

        TemplateResult template = extractTemplates(commandTemplate.templatedText);
        dictMatch = SkyblockDictionary.lookup(template.templatedText);
        if (dictMatch != null) {
            String res = restoreTemplates(dictMatch, template.values);
            res = restoreCommands(res, commandTemplate.commands);
            res = restorePlayerNames(res, playerTemplate.playerNames);
            return restoreFormatting(res, fmtTemplate.formattingCodes);
        }

        String cached = TranslationCache.get(template.templatedText);
        if (cached != null) {
            String res = restoreTemplates(cached, template.values);
            res = restoreCommands(res, commandTemplate.commands);
            res = restorePlayerNames(res, playerTemplate.playerNames);
            return restoreFormatting(res, fmtTemplate.formattingCodes);
        }

        return null;
    }

    public static CompletableFuture<String> translateAsync(String text) {
        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.enabled || text == null || text.trim().isEmpty()) return CompletableFuture.completedFuture("");
        if ("en".equalsIgnoreCase(config.targetLanguage)) return CompletableFuture.completedFuture(text);

        cleanupFailedTranslations();
        FormattingTemplateResult fmtResult = extractFormatting(text);

        PlayerTemplateResult playerTemplate = templatePlayerNames(fmtResult.templatedText);
        CommandTemplateResult commandTemplate = templateCommands(playerTemplate.templatedText);
        String textWithoutCommands = commandTemplate.templatedText;

        if ((!config.translateEnchantments && ENCHANTMENT_LINE_PATTERN.matcher(textWithoutCommands.trim()).matches()) || NAMESPACE_PATTERN.matcher(textWithoutCommands.trim()).matches()) return CompletableFuture.completedFuture(text);
        if (!containsTranslatableLetters(textWithoutCommands)) return CompletableFuture.completedFuture(text);

        final TemplateResult template;
        String exactDictMatch = SkyblockDictionary.lookup(textWithoutCommands);
        CompletableFuture<String> translationFuture;
        if (exactDictMatch != null) {
            translationFuture = CompletableFuture.completedFuture(exactDictMatch);
            template = new TemplateResult(textWithoutCommands, new ArrayList<>());
        } else {
            template = extractTemplates(textWithoutCommands);
            String dictMatch = SkyblockDictionary.lookup(template.templatedText);
            if (dictMatch != null) {
                translationFuture = CompletableFuture.completedFuture(dictMatch);
            } else {
                String cached = TranslationCache.get(template.templatedText);
                if (cached != null) {
                    translationFuture = CompletableFuture.completedFuture(cached);
                } else {
                    // Check isTechnicalOrModLine ONLY before API call — dictionary/cache lookups above must still work
                    if (isTechnicalOrModLine(fmtResult.templatedText)) return CompletableFuture.completedFuture(text);
                    String trimmedTemplate = template.templatedText.trim();
                    Long blockTime = FAILED_TRANSLATIONS.get(trimmedTemplate);
                    if (blockTime != null && System.currentTimeMillis() < blockTime) return CompletableFuture.completedFuture(text);
                    if (blockTime != null) FAILED_TRANSLATIONS.remove(trimmedTemplate);

                    // Reject if too many active translations (prevents memory leak)
                    if (ACTIVE_TRANSLATIONS.size() >= MAX_ACTIVE_TRANSLATIONS) {
                        return CompletableFuture.completedFuture(text);
                    }

                    // Use putIfAbsent to avoid deadlock in computeIfAbsent
                    CompletableFuture<String> newFuture = new CompletableFuture<>();
                    CompletableFuture<String> existing = ACTIVE_TRANSLATIONS.putIfAbsent(trimmedTemplate, newFuture);
                    if (existing != null) {
                        translationFuture = existing;
                    } else {
                        ACTIVE_TRANSLATIONS_TIMESTAMPS.put(trimmedTemplate, System.currentTimeMillis());
                        translationFuture = newFuture;
                        requestApiTranslation(trimmedTemplate, config, config.targetLanguage).orTimeout(8, TimeUnit.SECONDS).handle((res, err) -> {
                            ACTIVE_TRANSLATIONS.remove(trimmedTemplate);
                            ACTIVE_TRANSLATIONS_TIMESTAMPS.remove(trimmedTemplate);
                            if (err != null || res == null || res.trim().isEmpty() || res.startsWith("[")) {
                                if (err != null || (res != null && res.contains("Error"))) markApiError(trimmedTemplate);
                                newFuture.complete(res != null ? res : "");
                            } else {
                                String normalized = cleanPunctuationSpaces(normalizePlaceholders(res));
                                TranslationCache.put(trimmedTemplate, normalized);
                                newFuture.complete(normalized);
                                // Notify tooltip cache that new translations are available
                                notifyTooltipCacheInvalidation();
                            }
                            return null;
                        });
                    }
                }
            }
        }

        return translationFuture.thenApply(translatedTemplate -> {
            String res = restoreTemplates(translatedTemplate, template.values);
            res = restoreCommands(res, commandTemplate.commands);
            res = restorePlayerNames(res, playerTemplate.playerNames);
            return restoreFormatting(res, fmtResult.formattingCodes);
        });
    }

    private static CompletableFuture<String> requestApiTranslation(String text, TranslatorConfig config, String targetLang) {
        if ("DEEPL_FREE".equalsIgnoreCase(config.apiProvider)) {
            if (config.apiKey == null || config.apiKey.trim().isEmpty()) { warnMissingKey("DeepL"); return translateGoogleFreeAsync(text, targetLang); }
            return translateDeepLAsync(text, targetLang, config.apiKey).thenCompose(res -> res.startsWith("[") ? translateGoogleFreeAsync(text, targetLang) : CompletableFuture.completedFuture(res));
        } else if ("YANDEX".equalsIgnoreCase(config.apiProvider)) {
            if (config.apiKey == null || config.apiKey.trim().isEmpty()) { warnMissingKey("Yandex"); return translateGoogleFreeAsync(text, targetLang); }
            return translateYandexAsync(text, targetLang, config.apiKey).thenCompose(res -> res.startsWith("[") ? translateGoogleFreeAsync(text, targetLang) : CompletableFuture.completedFuture(res));
        }
        return translateGoogleFreeAsync(text, targetLang);
    }

    public static CompletableFuture<String> translateOutgoingAsync(String text) {
        TranslatorConfig config = TranslatorConfigManager.getConfig();
        if (!config.enabled || text == null || text.trim().isEmpty()) return CompletableFuture.completedFuture("");
        // For Hypixel, outgoing chat should always be translated to English
        return requestApiTranslation(text.trim(), config, "en").thenApply(TranslationService::cleanPunctuationSpaces);
    }

    private static CompletableFuture<String> translateGoogleFreeAsync(String text, String targetLang) {
        // Acquire semaphore permit to rate-limit API requests
        if (!API_SEMAPHORE.tryAcquire()) {
            return CompletableFuture.completedFuture("");
        }
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + targetLang + "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0").timeout(Duration.ofSeconds(5)).GET().build();
            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                API_SEMAPHORE.release();
                if (response.statusCode() == 200) {
                    JsonArray root = JsonParser.parseString(response.body()).getAsJsonArray();
                    if (root.size() > 0 && root.get(0).isJsonArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonElement s : root.get(0).getAsJsonArray()) if (s.isJsonArray()) sb.append(s.getAsJsonArray().get(0).getAsString());
                        return sb.toString();
                    }
                }
                return "";
            }).exceptionally(t -> { API_SEMAPHORE.release(); return ""; });
        } catch (Exception e) { API_SEMAPHORE.release(); return CompletableFuture.completedFuture(""); }
    }

    private static CompletableFuture<String> translateDeepLAsync(String text, String targetLang, String apiKey) {
        if (!API_SEMAPHORE.tryAcquire()) {
            return CompletableFuture.completedFuture("");
        }
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api-free.deepl.com/v2/translate")).header("Authorization", "DeepL-Auth-Key " + apiKey).header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString("text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&target_lang=" + targetLang.toUpperCase())).build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            API_SEMAPHORE.release();
            if (response.statusCode() == 200) return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("translations").get(0).getAsJsonObject().get("text").getAsString();
            return "[DeepL Error: " + response.statusCode() + "]";
        }).exceptionally(e -> { API_SEMAPHORE.release(); return "[DeepL Exception: " + e.getMessage() + "]"; });
    }

    private static CompletableFuture<String> translateYandexAsync(String text, String targetLang, String apiKey) {
        if (!API_SEMAPHORE.tryAcquire()) {
            return CompletableFuture.completedFuture("");
        }
        JsonObject body = new JsonObject(); body.addProperty("targetLanguageCode", targetLang); JsonArray texts = new JsonArray(); texts.add(text); body.add("texts", texts);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://translate.api.cloud.yandex.net/translate/v2/translate")).header("Authorization", "Api-Key " + apiKey).header("Content-Type", "application/json").timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            API_SEMAPHORE.release();
            if (response.statusCode() == 200) return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("translations").get(0).getAsJsonObject().get("text").getAsString();
            return "[Yandex Error: " + response.statusCode() + "]";
        }).exceptionally(e -> { API_SEMAPHORE.release(); return "[Yandex Exception: " + e.getMessage() + "]"; });
    }

    public static boolean containsNonEnglishLetters(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "");
        int nonEng = 0, eng = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isLetter(c)) { if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) nonEng++; else eng++; }
        }
        return nonEng > eng && nonEng >= 1;
    }

    public static boolean containsLetters(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "");
        for (int i = 0; i < clean.length(); i++) if (Character.isLetter(clean.charAt(i))) return true;
        return false;
    }

    public static class PlayerTemplateResult { public final String templatedText; public final List<String> playerNames; public PlayerTemplateResult(String t, List<String> p) { this.templatedText = t; this.playerNames = p; } }
    private static volatile List<String> cachedPlayerNames = new ArrayList<>();
    private static volatile java.util.Set<String> playerNamesSet = new java.util.HashSet<>();

    public static List<String> getOnlinePlayerNames() { return cachedPlayerNames; }
    public static void updateOnlinePlayerNames(net.minecraft.client.Minecraft client) {
        if (client == null) return;
        List<String> names = new ArrayList<>();
        try {
            if (client.player != null) names.add(client.player.getName().getString());
            if (client.getConnection() != null) for (net.minecraft.client.multiplayer.PlayerInfo i : client.getConnection().getOnlinePlayers()) {
                String n = i.getProfile().name();
                if (n != null && n.length() >= 3 && !names.contains(n)) names.add(n);
            }
        } catch (Exception e) {}
        cachedPlayerNames = names;
        playerNamesSet = new java.util.HashSet<>(names);
    }

    public static PlayerTemplateResult templatePlayerNames(String text) {
        if (text == null || text.isEmpty()) return new PlayerTemplateResult("", new ArrayList<>());
        List<String> matched = new ArrayList<>();
        String result = text;
        int idx = 0;
        // Optimized player name templating: only check words
        String[] words = text.split("(?<=[^a-zA-Z0-9_])|(?=[^a-zA-Z0-9_])");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            String cleanWord = word.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "");
            if (playerNamesSet.contains(cleanWord)) {
                matched.add(word);
                sb.append("{P").append(idx++).append("}");
            } else {
                sb.append(word);
            }
        }
        return new PlayerTemplateResult(sb.toString(), matched);
    }

    public static String restorePlayerNames(String text, List<String> playerNames) {
        if (text == null) return null;
        String result = text;
        for (int i = 0; i < playerNames.size(); i++) result = result.replace("{P" + i + "}", playerNames.get(i));
        return result;
    }

    private static final Pattern COMMAND_PATTERN = Pattern.compile("(?<![:/])/[a-zA-Z][a-zA-Z0-9_-]*");
    public static class CommandTemplateResult { public final String templatedText; public final List<String> commands; public CommandTemplateResult(String t, List<String> c) { this.templatedText = t; this.commands = c; } }
    public static CommandTemplateResult templateCommands(String text) {
        List<String> commands = new ArrayList<>();
        if (text == null || text.isEmpty()) return new CommandTemplateResult("", commands);
        Matcher matcher = COMMAND_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            commands.add(matcher.group());
            matcher.appendReplacement(sb, "{C" + count + "}");
            count++;
        }
        matcher.appendTail(sb);
        return new CommandTemplateResult(sb.toString(), commands);
    }

    public static String restoreCommands(String text, List<String> commands) {
        if (text == null) return null;
        String result = text;
        for (int i = 0; i < commands.size(); i++) result = result.replace("{C" + i + "}", commands.get(i));
        return result;
    }

    public static boolean containsTranslatableLetters(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("\\{P\\d+\\}|\\{F\\d+\\}|\\{C\\d+\\}|\\{\\d+\\}", "");
        return containsLetters(clean);
    }

    public static void shutdown() {
        try { TRANSLATION_EXECUTOR.shutdown(); } catch (Exception e) {}
        try { HTTP_EXECUTOR.shutdown(); } catch (Exception e) {}
        // Complete all pending futures to unblock any waiters
        ACTIVE_TRANSLATIONS.forEach((key, future) -> {
            if (!future.isDone()) future.complete("");
        });
        ACTIVE_TRANSLATIONS.clear();
        ACTIVE_TRANSLATIONS_TIMESTAMPS.clear();
    }
}
