package ru.fridorin.translator.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TranslationCache {
    private static final Gson GSON = new GsonBuilder().create();
    private static File cacheFile;
    private static final int MAX_SIZE = 10000;

    // LRU cache: access-order LinkedHashMap with automatic eviction
    // All access must be synchronized on LRU_LOCK
    private static final LinkedHashMap<String, String> lruCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_SIZE;
        }
    };
    // Fast case-insensitive lookup index (no ordering needed)
    private static final ConcurrentHashMap<String, String> lowercaseCache = new ConcurrentHashMap<>();

    private static final Object LRU_LOCK = new Object();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SkyblockTranslator-CacheSaver");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pendingSaveTask = null;
    private static volatile boolean isDirty = false;

    private static final Object SCHEDULER_LOCK = new Object();

    public static void loadForLanguage(String lang) {
        synchronized (LRU_LOCK) {
            if (isDirty) {
                save();
            }
            lruCache.clear();
            lowercaseCache.clear();
            
            String filename = "cache_" + lang + ".json";
            cacheFile = FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/" + filename).toFile();
            
            if ("ru".equals(lang)) {
                File legacyFile = FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/cache.json").toFile();
                if (legacyFile.exists() && !cacheFile.exists()) {
                    legacyFile.renameTo(cacheFile);
                }
            }
            
            load();
        }
    }

    public static void init() {
        String lang = ru.fridorin.translator.config.TranslatorConfigManager.getConfig().targetLanguage;
        loadForLanguage(lang);
    }

    public static String get(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        // Try exact match first (O(1) from ConcurrentHashMap)
        String lower = trimmed.toLowerCase();
        String fromLower = lowercaseCache.get(lower);
        if (fromLower != null) {
            // Touch in LRU to keep it fresh
            synchronized (LRU_LOCK) {
                lruCache.get(trimmed);
            }
            return fromLower;
        }
        return null;
    }

    public static void put(String original, String translated) {
        if (original == null || translated == null) return;
        String trimmedOrig = original.trim();
        String trimmedTrans = translated.trim();
        if (trimmedOrig.isEmpty() || trimmedTrans.isEmpty() || trimmedOrig.equalsIgnoreCase(trimmedTrans)) return;
        
        String targetLang = ru.fridorin.translator.config.TranslatorConfigManager.getConfig().targetLanguage;
        if (!"en".equalsIgnoreCase(targetLang) && ru.fridorin.translator.service.SkyblockDictionary.isHybrid(trimmedTrans)) {
            return;
        }

        synchronized (LRU_LOCK) {
            lruCache.put(trimmedOrig, trimmedTrans);
        }
        lowercaseCache.put(trimmedOrig.toLowerCase(), trimmedTrans);
        isDirty = true;
        scheduleSave();
    }

    public static void load() {
        if (cacheFile == null || !cacheFile.exists()) {
            return;
        }
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(cacheFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                String targetLang = ru.fridorin.translator.config.TranslatorConfigManager.getConfig().targetLanguage;
                boolean checkHybrid = !"en".equalsIgnoreCase(targetLang);
                int count = 0;
                for (Map.Entry<String, String> entry : loaded.entrySet()) {
                    if (count >= MAX_SIZE) {
                        isDirty = true;
                        break;
                    }
                    String key = entry.getKey();
                    String val = entry.getValue();
                    if (key == null || val == null) continue;
                    if (checkHybrid && ru.fridorin.translator.service.SkyblockDictionary.isHybrid(val)) {
                        isDirty = true;
                        continue;
                    }
                    lruCache.put(key, val);
                    lowercaseCache.put(key.toLowerCase(), val);
                    count++;
                }
            }
        } catch (Exception e) {
        }
    }

    private static void save() {
        if (!isDirty || cacheFile == null) return;
        synchronized (LRU_LOCK) {
            if (!isDirty) return;
            Map<String, String> snapshot = new java.util.LinkedHashMap<>(lruCache);
            isDirty = false;
            try {
                File parent = cacheFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(cacheFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    GSON.toJson(snapshot, writer);
                }
            } catch (IOException e) {
            }
        }
    }

    public static void scheduleSave() {
        if (!isDirty || scheduler.isShutdown()) return;
        synchronized (SCHEDULER_LOCK) {
            if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
                pendingSaveTask.cancel(false);
            }
            pendingSaveTask = scheduler.schedule(TranslationCache::save, 5, TimeUnit.SECONDS);
        }
    }

    public static void shutdown() {
        synchronized (SCHEDULER_LOCK) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
            }
        }
        save();
        scheduler.shutdown();
    }

    public static void clear() {
        synchronized (LRU_LOCK) {
            lruCache.clear();
        }
        lowercaseCache.clear();
        isDirty = true;
        save();
    }
}

