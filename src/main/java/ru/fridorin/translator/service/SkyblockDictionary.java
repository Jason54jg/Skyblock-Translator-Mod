package ru.fridorin.translator.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ru.fridorin.translator.service.TranslationService.HTTP_CLIENT;

public class SkyblockDictionary {
    private static final Map<String, String> DICTIONARY = new ConcurrentHashMap<>();
    private static final Map<String, String> USER_DICTIONARY = new ConcurrentHashMap<>();
    private static final Map<String, String> ITEM_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, String> ITEM_TYPES_GENDER = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String activeLanguage = "ru";

    public static final java.util.Set<String> DEFAULT_NPC_NAMES = java.util.Set.of(
        "jerry", "kat", "elizabeth", "maddox", "ophelia", "malik", "mort", "anita", "jacob", "taylor", 
        "george", "tia", "rusty", "vanessa", "sirius", "shaman", "zog", "odger", "horace", "jax", 
        "toby", "elise", "diana", "marina", "paul", "cole", "foxy", "aatrox", "barry", "diaz", 
        "scorpius", "derpy", "walter", "rhys", "guber", "seymour", "lucius", "marco", "dusk", 
        "lynn", "lucia", "grog", "bulvar", "gwendolyn", "jotra", "bubu", "katrina", "vargul", 
        "tarn", "nema", "nika", "ruber", "kruger", "bort", "kate", "alphonso", "duchess", 
        "king", "queen", "emperor", "mayor", "minister", "plumber joe", "blacksmith", 
        "lapis miner", "iron forger", "gold forger", "guild master", "adventurer", "bartender", 
        "pat", "sven", "dust", "lumberjack", "alchemist", "wind glem", "josh", "amy", "luna", 
        "plumber", "builder", "wool weaver", "fish merchant", "artist", "banker", "bazaar", 
        "master tactician", "claudio"
    );

    public static final java.util.Set<String> DEFAULT_ALLOWED_ENGLISH_WORDS = java.util.Set.of(
        "xp", "hp", "hotm", "npc", "vip", "mvp", "api", "bin", "lvl", "uuid", "id", "coop", "neu", "sba", "v",
        "i", "ii", "iii", "iv", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv", "xvi", "xvii", "xviii", "xix", "xx",
        "skyblock", "hypixel", "dungeon", "dungeons"
    );

    public static final java.util.Set<String> DYNAMIC_NPC_NAMES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final java.util.Set<String> DYNAMIC_ALLOWED_ENGLISH_WORDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isNpcName(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "");
        clean = clean.toLowerCase().replaceAll("[^a-z\\s]", "").trim();
        if (clean.isEmpty()) return false;
        if (DEFAULT_NPC_NAMES.contains(clean) || DYNAMIC_NPC_NAMES.contains(clean)) return true;
        
        String[] words = clean.split("\\s+");
        if (words.length <= 2) {
            for (String word : words) {
                if (DEFAULT_NPC_NAMES.contains(word) || DYNAMIC_NPC_NAMES.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void updateDynamicLists() {
        DYNAMIC_NPC_NAMES.clear();
        DYNAMIC_ALLOWED_ENGLISH_WORDS.clear();

        
        String globalNpcs = DICTIONARY.get("npc_names_list");
        if (globalNpcs != null) {
            for (String name : globalNpcs.split(",")) {
                DYNAMIC_NPC_NAMES.add(name.trim().toLowerCase());
            }
        }
        String userNpcs = USER_DICTIONARY.get("npc_names_list");
        if (userNpcs != null) {
            for (String name : userNpcs.split(",")) {
                DYNAMIC_NPC_NAMES.add(name.trim().toLowerCase());
            }
        }

        
        String globalWords = DICTIONARY.get("allowed_english_words_list");
        if (globalWords != null) {
            for (String word : globalWords.split(",")) {
                DYNAMIC_ALLOWED_ENGLISH_WORDS.add(word.trim().toLowerCase());
            }
        }
        String userWords = USER_DICTIONARY.get("allowed_english_words_list");
        if (userWords != null) {
            for (String word : userWords.split(",")) {
                DYNAMIC_ALLOWED_ENGLISH_WORDS.add(word.trim().toLowerCase());
            }
        }
    }

    public static String getVal(String key) {
        if (key == null) return null;
        return DICTIONARY.get(key.toLowerCase());
    }

    private static String getRarityTranslation(String rarity, String gender) {
        boolean isVery = rarity.startsWith("VERY ");
        String baseRarity = isVery ? rarity.substring(5) : rarity;
        
        String genderKey = "rarity." + baseRarity.toLowerCase() + "." + (gender != null ? gender.toLowerCase() : "m");
        String translation = DICTIONARY.get(genderKey);
        
        if (translation == null) {
            translation = DICTIONARY.get("rarity." + baseRarity.toLowerCase());
        }
        if (translation == null) {
            translation = DICTIONARY.get(baseRarity.toLowerCase());
        }
        if (translation == null) {
            translation = baseRarity;
        }

        String prefix = "";
        if (isVery) {
            String veryTrans = DICTIONARY.get("very");
            if (veryTrans == null) {
                veryTrans = "VERY";
            }
            prefix = veryTrans.toUpperCase() + " ";
        }

        return prefix + translation.toUpperCase();
    }

    public static String cleanPlaceholders(String text) {
        if (text == null) return "";
        return text
            .replaceAll("§x(?:§[0-9a-fA-F]){6}|§.", "")
            .replaceAll("(?i)\\{F\\d+\\}", "")
            .replaceAll("(?i)\\{P\\d+\\}", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String restoreLookupFormat(String original, String cleanText, String translated) {
        if (original == null || cleanText == null || cleanText.isEmpty()) return original;
        String lowerOrig = original.toLowerCase();
        String lowerClean = cleanText.toLowerCase();
        int idx = lowerOrig.indexOf(lowerClean);
        if (idx != -1) {
            return original.substring(0, idx) + translated + original.substring(idx + cleanText.length());
        }
        return original.replace(cleanText, translated);
    }

    private static String getWordBoundaryRegex(String key) {
        StringBuilder sb = new StringBuilder();
        if (key.length() > 0 && Character.isLetterOrDigit(key.charAt(0))) {
            sb.append("\\b");
        }
        sb.append(java.util.regex.Pattern.quote(key));
        if (key.length() > 0 && Character.isLetterOrDigit(key.charAt(key.length() - 1))) {
            sb.append("\\b");
        }
        return sb.toString();
    }

    public static boolean isHybrid(String text) {
        if (text == null) return false;
        String clean = text
            .replaceAll("(?i)\\{P\\d+\\}", "")
            .replaceAll("(?i)\\{F\\d+\\}", "")
            .replaceAll("(?i)\\{C\\d+\\}", "")
            .replaceAll("(?i)\\{\\d+\\}", "");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-zA-Z]+");
        java.util.regex.Matcher matcher = pattern.matcher(clean);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            if (!DEFAULT_ALLOWED_ENGLISH_WORDS.contains(word) && !DYNAMIC_ALLOWED_ENGLISH_WORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }


    // Pre-compiled phrase patterns cache for translateByPhrases() — avoids re-sorting
    // the entire dictionary and re-compiling regex patterns on every call
    private static class PhraseEntry {
        final String key;
        final String translation;
        final java.util.regex.Pattern pattern;

        PhraseEntry(String key, String translation, java.util.regex.Pattern pattern) {
            this.key = key;
            this.translation = translation;
            this.pattern = pattern;
        }
    }
    private static volatile java.util.List<PhraseEntry> cachedPhraseEntries = null;

    /**
     * Rebuild the phrase translation cache. Must be called whenever the dictionary changes.
     */
    static void rebuildPhraseCache() {
        java.util.List<String> sortedKeys = new java.util.ArrayList<>(DICTIONARY.keySet());
        sortedKeys.sort((a, b) -> Integer.compare(b.length(), a.length()));

        java.util.List<PhraseEntry> entries = new java.util.ArrayList<>();
        for (String key : sortedKeys) {
            if (key.trim().isEmpty() || key.startsWith("gui.") || key.startsWith("item_type.") || key.startsWith("item_gender.") || key.contains(".") || key.length() < 3) {
                continue;
            }
            String translation = DICTIONARY.get(key);
            if (translation == null || translation.trim().isEmpty() || translation.equalsIgnoreCase(key)) {
                continue;
            }
            String regex = getWordBoundaryRegex(key);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
            entries.add(new PhraseEntry(key, translation, pattern));
        }
        cachedPhraseEntries = entries;
    }

    public static String translateByPhrases(String text) {
        if (text == null || text.trim().isEmpty()) return text;
        
        java.util.List<PhraseEntry> entries = cachedPhraseEntries;
        if (entries == null) {
            rebuildPhraseCache();
            entries = cachedPhraseEntries;
        }
        if (entries == null || entries.isEmpty()) return null;

        String result = text;
        boolean changed = false;
        for (PhraseEntry entry : entries) {
            // Quick contains() check before running regex (much cheaper)
            if (!result.toLowerCase().contains(entry.key)) {
                continue;
            }
            java.util.regex.Matcher matcher = entry.pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(java.util.regex.Matcher.quoteReplacement(entry.translation));
                changed = true;
            }
        }
        if (changed) {
            if (isHybrid(result)) {
                return null;
            }
            return result;
        }
        return null;
    }

    // Pre-compiled regex patterns cache for lookupRegex() — avoids compiling on every call
    private static class RegexEntry {
        final java.util.regex.Pattern pattern;
        final String replacement;

        RegexEntry(java.util.regex.Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
    private static volatile java.util.List<RegexEntry> cachedUserRegex = java.util.Collections.emptyList();
    private static volatile java.util.List<RegexEntry> cachedDictRegex = java.util.Collections.emptyList();

    /**
     * Rebuild the regex pattern caches. Must be called whenever dictionaries change.
     */
    static void rebuildRegexCache() {
        cachedUserRegex = buildRegexEntries(USER_DICTIONARY);
        cachedDictRegex = buildRegexEntries(DICTIONARY);
    }

    private static java.util.List<RegexEntry> buildRegexEntries(Map<String, String> dict) {
        java.util.List<RegexEntry> entries = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : dict.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("r:") && key.length() > 2) {
                try {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                        key.substring(2), java.util.regex.Pattern.CASE_INSENSITIVE);
                    entries.add(new RegexEntry(pattern, entry.getValue()));
                } catch (Exception e) {
                    // Invalid regex pattern — skip it
                }
            }
        }
        return entries;
    }

    public static String lookupRegex(String text) {
        if (text == null || text.isEmpty()) return null;
        for (RegexEntry entry : cachedUserRegex) {
            java.util.regex.Matcher matcher = entry.pattern.matcher(text);
            if (matcher.find()) {
                return matcher.replaceAll(entry.replacement);
            }
        }
        for (RegexEntry entry : cachedDictRegex) {
            java.util.regex.Matcher matcher = entry.pattern.matcher(text);
            if (matcher.find()) {
                return matcher.replaceAll(entry.replacement);
            }
        }
        return null;
    }

    public static String lookup(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;

        String cleanText = cleanPlaceholders(trimmed);
        if (cleanText.isEmpty()) return null;

        String userMatch = USER_DICTIONARY.get(cleanText.toLowerCase());
        if (userMatch != null) {
            return restoreLookupFormat(trimmed, cleanText, userMatch);
        }

        String match = DICTIONARY.get(cleanText.toLowerCase());
        if (match != null) {
            return restoreLookupFormat(trimmed, cleanText, match);
        }

        ru.fridorin.translator.config.TranslatorConfig config = ru.fridorin.translator.config.TranslatorConfigManager.getConfig();
        if (config != null && config.regexTranslation) {
            String regexMatch = lookupRegex(cleanText);
            if (regexMatch != null) {
                return restoreLookupFormat(trimmed, cleanText, regexMatch);
            }
        }

        String formattedRarity = tryTranslateRarityAndType(trimmed, cleanText);
        if (formattedRarity != null) {
            return formattedRarity;
        }

        String phraseTranslated = translateByPhrases(cleanText);
        if (phraseTranslated != null) {
            return restoreLookupFormat(trimmed, cleanText, phraseTranslated);
        }

        return null;
    }

    private static String tryTranslateRarityAndType(String original, String cleanText) {
        String upperText = cleanText.toUpperCase().trim();
        String baseLine = upperText.replaceAll("^[^A-Z]+", "").replaceAll("[^A-Z]+$", "").trim();

        String foundRarity = null;
        if (baseLine.startsWith("VERY SPECIAL ")) foundRarity = "VERY SPECIAL";
        else if (baseLine.startsWith("SPECIAL ")) foundRarity = "SPECIAL";
        else if (baseLine.startsWith("COMMON ")) foundRarity = "COMMON";
        else if (baseLine.startsWith("UNCOMMON ")) foundRarity = "UNCOMMON";
        else if (baseLine.startsWith("RARE ")) foundRarity = "RARE";
        else if (baseLine.startsWith("EPIC ")) foundRarity = "EPIC";
        else if (baseLine.startsWith("LEGENDARY ")) foundRarity = "LEGENDARY";
        else if (baseLine.startsWith("MYTHIC ")) foundRarity = "MYTHIC";
        else if (baseLine.startsWith("DIVINE ")) foundRarity = "DIVINE";

        if (foundRarity != null) {
            String remainder = baseLine.substring(foundRarity.length()).trim();
            boolean isDungeon = remainder.startsWith("DUNGEON ");
            if (isDungeon) {
                remainder = remainder.substring(8).trim();
            }
            String mappedType = ITEM_TYPES.get(remainder);
            String gender = ITEM_TYPES_GENDER.get(remainder);
            if (mappedType != null && gender != null) {
                String dungeonFormat = DICTIONARY.get("dungeon_format");
                if (dungeonFormat == null) {
                    dungeonFormat = "DUNGEON {type}";
                }
                String translatedType = isDungeon ? dungeonFormat.replace("{type}", mappedType) : mappedType;

                String format = DICTIONARY.get("rarity_format");
                if (format == null) {
                    format = "{rarity} {type}";
                }
                String translatedRarity = getRarityTranslation(foundRarity, gender);
                String translatedRarityText = format
                    .replace("{rarity}", translatedRarity)
                    .replace("{type}", translatedType);
                
                int idx = cleanText.toUpperCase().indexOf(baseLine);
                String cleanRarityLine = cleanText.substring(0, idx) + translatedRarityText + cleanText.substring(idx + baseLine.length());
                return restoreLookupFormat(original, cleanText, cleanRarityLine);
            }
        }

        int colonIndex = cleanText.indexOf(":");
        if (colonIndex != -1) {
            String key = cleanText.substring(0, colonIndex).trim();
            String value = cleanText.substring(colonIndex + 1).trim();
            String translatedKey = DICTIONARY.get(key.toLowerCase());
            if (translatedKey != null) {
                int origColonIdx = original.indexOf(":");
                if (origColonIdx != -1) {
                    String keyPart = original.substring(0, origColonIdx).trim();
                    String valuePart = original.substring(origColonIdx + 1).trim();
                    
                    String restoredKey = restoreLookupFormat(keyPart, key, translatedKey);
                    String restoredValue = translateValuePart(valuePart);
                    return restoredKey + ": " + restoredValue;
                }
            }
        }

        if (cleanText.toLowerCase().startsWith("requires ") || cleanText.toLowerCase().startsWith("requires:")) {
            String cleanRequirement = cleanText;
            if (cleanRequirement.toLowerCase().startsWith("requires:")) {
                cleanRequirement = cleanRequirement.substring(9).trim();
            } else {
                cleanRequirement = cleanRequirement.substring(9).trim();
            }

            int levelIndex = cleanRequirement.toLowerCase().indexOf(" level ");
            if (levelIndex != -1) {
                String skillName = cleanRequirement.substring(0, levelIndex).trim();
                String levelVal = cleanRequirement.substring(levelIndex + 7).trim();
                String translatedSkill = DICTIONARY.get(skillName.toLowerCase());
                if (translatedSkill == null) translatedSkill = skillName;

                String skillFormat = DICTIONARY.get("requirement_skill_format");
                if (skillFormat == null) {
                    skillFormat = "Requires {skill} Level {level}";
                }
                String translatedReq = skillFormat
                    .replace("{skill}", translatedSkill)
                    .replace("{level}", levelVal);
                return restoreLookupFormat(original, cleanText, translatedReq);
            } else {
                int lastSpace = cleanRequirement.lastIndexOf(' ');
                if (lastSpace != -1) {
                    String slayerName = cleanRequirement.substring(0, lastSpace).trim();
                    String slayerLevel = cleanRequirement.substring(lastSpace + 1).trim();
                    String translatedSlayer = DICTIONARY.get(slayerName.toLowerCase());
                    if (translatedSlayer == null) translatedSlayer = slayerName;

                    String slayerFormat = DICTIONARY.get("requirement_slayer_format");
                    if (slayerFormat == null) {
                        slayerFormat = "Requires {slayer} Level {level}";
                    }
                    String translatedReq = slayerFormat
                        .replace("{slayer}", translatedSlayer)
                        .replace("{level}", slayerLevel);
                    return restoreLookupFormat(original, cleanText, translatedReq);
                }
            }
        }

        return null;
    }

    private static String translateValuePart(String valueStr) {
        if (valueStr == null || valueStr.trim().isEmpty()) return valueStr;
        String trimmed = valueStr.trim();
        String cleanVal = cleanPlaceholders(trimmed);
        if (cleanVal.isEmpty()) return valueStr;
        
        String lowerClean = cleanVal.toLowerCase();
        
        
        String userMatch = USER_DICTIONARY.get(lowerClean);
        if (userMatch != null) {
            return restoreLookupFormat(trimmed, cleanVal, userMatch);
        }
        
        
        String match = DICTIONARY.get(lowerClean);
        if (match != null) {
            return restoreLookupFormat(trimmed, cleanVal, match);
        }
        
        
        ru.fridorin.translator.config.TranslatorConfig config = ru.fridorin.translator.config.TranslatorConfigManager.getConfig();
        if (config != null && config.regexTranslation) {
            String regexMatch = lookupRegex(cleanVal);
            if (regexMatch != null) {
                return restoreLookupFormat(trimmed, cleanVal, regexMatch);
            }
        }
        
        
        String phraseTranslated = translateByPhrases(cleanVal);
        if (phraseTranslated != null) {
            return restoreLookupFormat(trimmed, cleanVal, phraseTranslated);
        }
        
        
        String coinsTrans = DICTIONARY.get("coins");
        if (coinsTrans == null) coinsTrans = "Coins";
        String eachTrans = DICTIONARY.get("each");
        if (eachTrans == null) eachTrans = "each";
        String notDonatedTrans = DICTIONARY.get("not donated");
        if (notDonatedTrans == null) notDonatedTrans = "Not Donated";
        String donatedTrans = DICTIONARY.get("donated");
        if (donatedTrans == null) donatedTrans = "Donated";
        
        return trimmed
            .replace("Coins", coinsTrans)
            .replace("each", eachTrans)
            .replace("Not Donated", notDonatedTrans)
            .replace("Donated", donatedTrans);
    }

    public static void loadForLanguage(String lang) {
        activeLanguage = lang;
        DICTIONARY.clear();
        ITEM_TYPES.clear();
        ITEM_TYPES_GENDER.clear();

        // 1. Load from resources (Synchronous, local, fast)
        loadDictionaryFromResources(lang);

        // 2. Load from local config (Synchronous, local, fast)
        File dictFile = FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/dictionary_" + lang + ".json").toFile();
        if (dictFile.exists()) {
            loadDictionaryFile(dictFile);
        } else {
            saveDictionaryFile(dictFile);
        }

        // 3. Process loaded data
        for (Map.Entry<String, String> entry : DICTIONARY.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith("item_type.")) {
                String typeName = key.substring(10).trim().toUpperCase();
                ITEM_TYPES.put(typeName, value);
            } else if (key.startsWith("item_gender.")) {
                String typeName = key.substring(12).trim().toUpperCase();
                ITEM_TYPES_GENDER.put(typeName, value.toUpperCase());
            }
        }

        updateDynamicLists();
        rebuildPhraseCache();
        rebuildRegexCache();

        // 4. Download updates asynchronously (Fully non-blocking)
        java.util.concurrent.CompletableFuture.runAsync(() -> downloadDictionaryAsync(lang));
    }

    private static void loadDictionaryFromResources(String lang) {
        String resourcePath = "/assets/skyblock_translator/dictionary/dictionary_" + lang + ".json";
        java.io.InputStream stream = SkyblockDictionary.class.getResourceAsStream(resourcePath);
        if (stream != null) {
            try (java.io.InputStreamReader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    for (Map.Entry<String, String> entry : loaded.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            DICTIONARY.put(entry.getKey().toLowerCase().trim(), entry.getValue().trim());
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    private static void loadDictionaryFile(File file) {
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                for (Map.Entry<String, String> entry : loaded.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key != null && value != null) {
                        DICTIONARY.put(key.toLowerCase().trim(), value.trim());
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private static void saveDictionaryFile(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Map<String, String> toSave = new java.util.TreeMap<>();
            for (Map.Entry<String, String> entry : DICTIONARY.entrySet()) {
                toSave.put(entry.getKey(), entry.getValue());
            }
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(toSave, writer);
            }
        } catch (Exception e) {
        }
    }

    public static java.util.concurrent.CompletableFuture<Integer> downloadDictionaryAsync(String lang) {
        String username = ru.fridorin.translator.config.TranslatorConfig.GITHUB_USERNAME;
        String repository = ru.fridorin.translator.config.TranslatorConfig.GITHUB_REPOSITORY;
        String branch = ru.fridorin.translator.config.TranslatorConfig.GITHUB_BRANCH;
        String dictPath = ru.fridorin.translator.config.TranslatorConfig.GITHUB_DICT_PATH;

        String path = dictPath.replace("{lang}", lang);
        String url = "https://raw.githubusercontent.com/" + username + "/" + repository + "/" + branch + "/" + path;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> {
                if (response.statusCode() != 200 && "ru".equals(lang)) {
                    String fallbackUrl = "https://raw.githubusercontent.com/" + username + "/" + repository + "/" + branch + "/dictionary.json";
                    HttpRequest fallbackRequest = HttpRequest.newBuilder()
                            .uri(URI.create(fallbackUrl))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    return HTTP_CLIENT.sendAsync(fallbackRequest, HttpResponse.BodyHandlers.ofString());
                }
                return java.util.concurrent.CompletableFuture.completedFuture(response);
            })
            .thenApply(response -> {
                try {
                    if (response.statusCode() == 200) {
                        Type type = new TypeToken<Map<String, String>>(){}.getType();
                        Map<String, String> downloaded = GSON.fromJson(response.body(), type);
                        if (downloaded != null && !downloaded.isEmpty()) {
                            for (Map.Entry<String, String> entry : downloaded.entrySet()) {
                                String key = entry.getKey();
                                String value = entry.getValue();
                                if (key != null && value != null) {
                                    DICTIONARY.put(key.toLowerCase().trim(), value.trim());
                                }
                            }
                            File dictFile = FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/dictionary_" + lang + ".json").toFile();
                            saveDictionaryFile(dictFile);
                            updateDynamicLists();
                            rebuildPhraseCache();
                            rebuildRegexCache();

                            net.minecraft.client.Minecraft clientInstance = net.minecraft.client.Minecraft.getInstance();
                            if (clientInstance != null) {
                                clientInstance.execute(() -> {
                                    if (clientInstance.player != null) {
                                        clientInstance.player.displayClientMessage(
                                            net.minecraft.network.chat.Component.literal("§a[SkyBlock Translator] Dictionary updated from GitHub! Loaded " + downloaded.size() + " entries."),
                                            false
                                        );
                                    }
                                });
                            }
                            return downloaded.size();
                        }
                    }
                } catch (Exception e) {
                }
                return -1;
            });
    }

    public static void loadUserDictionary() {
        try {
            java.io.File configFile = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/user_dictionary.json").toFile();
            if (!configFile.exists()) {
                java.io.File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    writer.write("{\n" +
                            "  \"info\": \"Add your own translations here in the format: \\\"english text\\\": \\\"translated text\\\"\",\n" +
                            "  \"hello\": \"hello\",\n" +
                            "  \"lf coop\": \"looking for coop\"\n" +
                            "}");
                }
            }
            if (configFile.exists()) {
                try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        USER_DICTIONARY.clear();
                        for (Map.Entry<String, String> entry : loaded.entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null && !entry.getKey().equals("info")) {
                                USER_DICTIONARY.put(entry.getKey().toLowerCase().trim(), entry.getValue().trim());
                            }
                        }
                    }
                }
            }
            updateDynamicLists();
            rebuildRegexCache();
        } catch (Exception e) {
        }
    }

    public static void addUserTranslation(String english, String translation) {
        if (english == null || translation == null) return;
        String trimmedEng = english.trim().toLowerCase();
        String trimmedTrans = translation.trim();
        if (trimmedEng.isEmpty() || trimmedTrans.isEmpty()) return;

        USER_DICTIONARY.put(trimmedEng, trimmedTrans);

        try {
            java.io.File configFile = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/user_dictionary.json").toFile();
            Map<String, String> currentDict = new java.util.LinkedHashMap<>();
            currentDict.put("info", "Add your own translations here in the format: \"english text\": \"translated text\"");
            
            if (configFile.exists()) {
                try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        for (Map.Entry<String, String> entry : loaded.entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null && !entry.getKey().equalsIgnoreCase("info")) {
                                currentDict.put(entry.getKey().toLowerCase().trim(), entry.getValue().trim());
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
            
            currentDict.put(trimmedEng, trimmedTrans);

            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(currentDict, writer);
            }
            updateDynamicLists();
        } catch (Exception e) {
        }
    }

    public static String getTranslationOrUserTranslation(String key) {
        if (key == null) return null;
        String lower = key.trim().toLowerCase();
        String userMatch = USER_DICTIONARY.get(lower);
        if (userMatch != null) return userMatch + " §e(User Dictionary)§r";
        String dictMatch = DICTIONARY.get(lower);
        if (dictMatch != null) return dictMatch + " §7(GitHub/Default)§r";
        return null;
    }
}
