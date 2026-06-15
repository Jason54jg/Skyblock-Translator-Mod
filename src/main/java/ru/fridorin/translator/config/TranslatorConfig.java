package ru.fridorin.translator.config;

public class TranslatorConfig {
    public boolean enabled = true;
    public boolean translateChat = true;
    public boolean translateGuildChat = true;
    public boolean translatePartyChat = true;
    public boolean translateCoopChat = true;
    public boolean translateDirectMessages = true;
    public boolean translatePublicChat = true;
    public boolean translateSystemMessages = true;
    public boolean translateTooltips = true;
    public boolean translateInventoryTitles = true;
    public boolean translateScoreboard = false;
    public boolean translateNpcDialogues = true;
    public boolean translateNpcNames = true;
    public boolean translateMobNames = true;
    public boolean translateHolograms = true;
    public boolean translateEnchantments = true;
    public boolean translateEnchantmentDescriptions = true;
    public boolean translateAbilityNames = true;
    public boolean translateAbilityDescriptions = true;
    public boolean translateRarity = true;
    public boolean translateItemNames = true;
    public boolean translateOutgoingChat = true;
    public boolean showHoverOriginal = true;
    public boolean translateTabList = true;
    public boolean regexTranslation = true;
    public String chatDisplayMode = "SEPARATE_LINE";
    public String targetLanguage = "ru";
    public String apiProvider = "GOOGLE_FREE";
    public String apiKey = "";
    public int configVersion = 2; // Used to force migration of old dead configs

    public boolean ignoreAbilityCooldowns = true;
    public boolean ignoreDamageIndicators = true;
    public boolean ignoreModPrefixes = true;
    public boolean ignoreDungeonStats = true;
    public boolean ignoreSlayerAlerts = true;
    public boolean ignoreLobbyJoinLeft = true;
    public boolean ignoreClickActions = true;
    public boolean ignoreBazaarAndBin = true;
    public boolean ignoreUuidsAndIds = true;
    public boolean ignoreUrlsAndIps = true;
    public boolean ignoreDatesAndLobbies = true;
    public boolean disableOnShift = true;

    
    public static final String GITHUB_USERNAME = "fridorin";
    public static final String GITHUB_REPOSITORY = "Skyblock-Translator-Mod";
    public static final String GITHUB_BRANCH = "main";
    public static final String GITHUB_DICT_PATH = "dictionaries/dictionary_{lang}.json";

}

