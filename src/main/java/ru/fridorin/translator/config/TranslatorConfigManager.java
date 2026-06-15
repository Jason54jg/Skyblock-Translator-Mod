package ru.fridorin.translator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.IOException;

public class TranslatorConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static TranslatorConfig config = new TranslatorConfig();

    public static void init() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("skyblock_translator/config.json").toFile();
        load();
    }

    public static TranslatorConfig getConfig() {
        return config;
    }

    public static void load() {
        if (configFile == null) {
            return;
        }
        if (!configFile.exists()) {
            save();
            return;
        }
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            TranslatorConfig loaded = GSON.fromJson(reader, TranslatorConfig.class);
            if (loaded != null) {
                config = loaded;
                
                // Force migration of old configs where defaults were false
                if (config.configVersion < 2) {
                    config.translateEnchantments = true;
                    config.translateEnchantmentDescriptions = true;
                    config.translateAbilityNames = true;
                    config.translateAbilityDescriptions = true;
                    config.translateRarity = true;
                    config.translateItemNames = true;
                    config.translateOutgoingChat = true;
                    config.translateTooltips = true;
                    config.configVersion = 2;
                    save();
                }
            }
        } catch (Exception e) {
        }
    }

    public static void save() {
        if (configFile == null) {
            return;
        }
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            ru.fridorin.translator.TranslatorModClient.clearComponentCache();
        } catch (IOException e) {
        }
    }
}
