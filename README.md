# 🌍 SkyBlock Translator (Fabric Mod)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1.2%20%7C%2026.2-blue.svg?logo=minecraft&color=62B036)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&color=E2DBCE)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-Available-green.svg?logo=modrinth)](https://modrinth.com)

**SkyBlock Translator** is a high-performance, client-side translation mod for Minecraft 1.21.11, 26.1.2, and 26.2, designed specifically for **Hypixel SkyBlock**. The mod translates in-game chat, inventory item tooltips (lore), chest titles, holograms, player tab lists, and entities from English to your target language (default is Russian, with offline support for 30+ other target languages) in real-time.

Unlike generic translation mods, **SkyBlock Translator** is custom-built to parse the unique layout structures of Hypixel SkyBlock, optimizing performance, saving API limits, preserving interactive elements, and avoiding server bans.

---

### 🌐 Language / Язык / Langue
*   **English** (Current)
*   **[Русский (Russian)](README_ru.md)**
*   **[Français (French)](README_fr.md)**

---

## 📖 Table of Contents
1. [🌟 Key Features](#-key-features)
2. [⚙️ How the Translation Engine Works](#%EF%B8%8F-how-the-translation-engine-works)
3. [🛠️ In-Game Commands](#%EF%B8%8F-in-game-commands)
4. [🎮 Config Options & Explanations](#-config-options--explanations)
5. [🔑 How to Obtain API Keys](#-how-to-obtain-api-keys)
6. [✍️ How to Contribute Translations](#%EF%B8%8F-how-to-contribute-translations)
7. [❓ FAQ & Troubleshooting](#-faq--troubleshooting)
8. [🔒 Security & Server Rules Compliance](#-security--server-rules-compliance)

---

## 🌟 Key Features

*   ⚡ **Asynchronous Non-Blocking Pipeline**: All network requests are handled on a separate thread pool. Your game's main render loop will never freeze or stutter when waiting for an API response.
*   📴 **Zero-Delay Offline Translations**: Stats, item types, skill requirements, and enchantments are translated instantly offline via matching dictionary tables and regular expression patterns.
*   💬 **Intuitive Chat Display Modes**:
    *   `Separate Line (Recommended)`: Outputs a sub-line with the translation. Original chat links, player name clicks, and Profile ID clipboard components remain fully functional in the original line.
    *   `Append Below`: Appends the translation below the original message inside the same chat item.
    *   `Inline`: Inserts the translation in parentheses `(like this)` next to the original words.
    *   `Replace Original`: Replaces the original message entirely.
*   🔍 **Hover to Reveal Original**: Tooltips are appended to chat translations to show original English text when hovered.
*   🛡️ **Smart Technical Filters**: Excludes spam, mod-specific outputs (NEU, Skytils, SBA), IP addresses, dates, UUIDs, damage numbers, and copy prompts.
*   🛑 **Shift-Bypass (Panic Button)**: Simply hold **Shift** while browsing items or reading chat to temporarily suspend all translations and view raw English text.
*   🧹 **Interactive Cache Management**: Clear cache safely with a single click, instantly hearing a verification sound and seeing a system toast notification in the corner of your screen.

---

## ⚙️ How the Translation Engine Works

SkyBlock Translator processes incoming text and UI elements using a highly optimized multi-stage translation pipeline:

1. **🧹 Pre-Filtering & Safety Checks**:
   - The mod checks if the text contains technical information (e.g. damage indicators like `12,450 crit`, cooldown warnings, UUIDs, or formatting-only lines). If so, it passes the text through unchanged.
   - If the player is holding the **Shift** key, all translations are temporarily bypassed.

2. **⚙️ Flat Component Extraction**:
   - Instead of breaking a sentence into color-coded parts and translating them separately (which leads to broken grammar and hybrid translations), the mod converts the entire Minecraft `Component` into a single legacy string containing color codes (`§`).
   - The mod extracts dynamic values like numbers, percentages, and player names, replacing them with templates (e.g. `{0}`, `{P0}`). This protects usernames and numbers from being altered by translation engines.

3. **📴 Offline Dictionary & Regex Matching (Instant)**:
   - The engine checks for a match in your local `user_dictionary.json` or the global `dictionary_<lang>.json` downloaded from GitHub.
   - If no exact match exists, the text is run through a high-performance regex engine to instantly translate common stats (e.g. `Strength: +15` -> `Сила: +15`).
   - For rows containing colons (like `Interest: 2 hours`), the key and value are split. The key is translated, and the value is translated via the offline dictionary or regex engine before being stitched back together.

4. **🌐 Asynchronous Online API (Fallback)**:
   - If the text cannot be translated offline, a request is sent to the selected translation provider (Google Free, DeepL, or Yandex Cloud) on a background thread pool to prevent game stuttering.
   - If the selected provider is unavailable or its character limit is reached, the mod automatically falls back to Google Translate.

5. **🔍 Post-Processing & Validation**:
   - Once the translation is received, the mod reconstructs the original Minecraft formatting codes and restores the protected player names and numbers.
   - The mod validates the translation using `isTranslationSame`. If the translation is identical to the original English text (meaning the API failed to translate it), the translation is discarded to prevent duplicate text in chat.

---

## 🛠️ In-Game Commands

All commands can be triggered using `/translator` or aliases: `/переводчик`.

| Command | Usage Example | Detailed Behavior |
| :--- | :--- | :--- |
| `/translator` | `/translator` | Opens the graphical settings config screen (requires `YetAnotherConfigLib`). |
| `/translator add` | `/translator add Enchanted Leather = Зачарованная Кожа` | Saves a custom phrase mapping to your `user_dictionary.json` and loads it into active memory instantly. |
| `/translator lookup` | `/translator lookup Enchanted Leather` | Checks if a term is present in active dictionary tables and displays its source file (User vs GitHub). |
| `/translator toggle` | `/translator toggle tooltips` | Toggles a specific configuration parameter on/off instantly. |
| `/translator setlang` | `/translator setlang ru` | Switches target translation language and reloads corresponding caches. |
| `/translator setprovider` | `/translator setprovider DEEPL_FREE` | Swaps active API engine (choices: `GOOGLE_FREE`, `DEEPL_FREE`, `YANDEX`). |
| `/translator clear-cache` | `/translator clear-cache` | Empties local disk cache files. Plays a confirmation sound and shows a toast. |
| `/translator reload` | `/translator reload` | Re-reads configuration and user dictionary files from the config folder. |
| `/translator copy-hand` | `/translator copy-hand` | Copies a clean JSON translation template of all text lines in your main hand item directly to the clipboard. |
| `/translator update` | `/translator update` | Force updates and reloads the translation dictionaries from GitHub immediately without restarting. |
| `/translator test` | `/translator test` | Validates API credentials and checks connection latency to the selected translation engine. |

---

## 🎮 Config Options & Explanations

Each option in the YetAnotherConfigLib (YACL) GUI menu is structured with clear descriptive headers, emojis, and stability recommendations:

### 1. Main Category (⚙️ Main)
*   `✔ Enable Translator`: Global switch to enable or disable all mod operations. **[Recommended / Stable]**
*   `⇧ Disable on Shift`: Holding down Shift suspends item tooltip and chat translations, instantly rendering original text. **[Recommended / Stable]**
*   `⚡ Regex Translation (Offline)`: Enables pattern matching in local files for instantaneous offline stats translations. **[Recommended / High Performance]**
*   `🔑 Translation Provider`: Swaps translation APIs. **[Stable]**
*   `👁 Show API Key`: Toggles visibility of the characters in the API key field. **[Stable]**
*   `📝 API Key`: Input box to supply your API credentials. **[Stable]**

### 2. Chat Category (💬 Chat)
*   `💬 Translate Chat`: Master toggle for incoming chat translation. **[Recommended / Stable]**
*   `🗣 Translate NPC Dialogues`: Specifically translates lines prefixed with `[NPC]`. **[Recommended / Stable]**
*   `📺 Chat Display Mode`:
    *   `Separate Line`: Prints translation under original (safest, doesn't break hover/click events).
    *   `Append Below`: Inserts a sub-line inside the original message object.
    *   `Inline`: Inserts translation in parenthetical notation.
    *   `Replace Original`: Swaps text out completely.
*   `🔍 Hover to Show Original`: Appends tooltip actions to translations to show source lines. **[Recommended / Stable]**
*   `📤 Auto-translate Outgoing`: Translates outgoing message text before dispatching (translates Russian to English). Includes safety delays to prevent server spam kicks. **[Experimental / Use with care]**
*   `Chat Channel Filters`: Selective switches for Guild, Party, Co-op, DMs, Public, and System chats. **[Stable]**

### 3. World Category (🌍 Game World)
*   `🔮 Translate Holograms`: Translates hovering world entities (texts). **[Stable / Recommended]**
*   `🐉 Translate Mob Names`: Translates level/health tags of mobs in combat. **[Not Recommended / High API Spam / May cause micro-stutters during mob spawn]**
*   `🧑 Translate NPC Names`: Translates friendly NPC hover-tags. **[Not Recommended / API Spam]**
*   `📋 Translate Tab List`: Parses and translates scoreboard tables inside the Tab menu. **[Recommended / Stable]**

### 4. Interface Category (🖥️ Interface)
*   `📜 Item Descriptions (Lore)`: Translates item lore tooltips. **[Recommended / Stable]**
*   `🏷 Item Names`: Translates item title lines. **[Not Recommended / High API Spam / Makes it harder to search items on the Auction House/Bazaar]**
*   `✨ Translate Enchantments`: Translates the names of enchantments on items. **[Not Recommended / API Spam]**
*   `📖 Translate Enchantment Descriptions`: Translates what an enchantment does, leaving its name in English. **[Recommended / Stable]**
*   `⚡ Translate Ability Names`: Translates item ability headers. **[Not Recommended / API Spam]**
*   `📜 Translate Ability Descriptions`: Translates instructions of item abilities. **[Recommended / Stable]**
*   `💎 Translate Item Rarity`: Translates rarity classifications. **[Recommended / Stable]**
*   `📦 Menu/Inventory Titles`: Translates chest and interface titles. **[Recommended / Stable]**
*   `📊 Sidebar (Scoreboard)`: Translates scoreboard lines. **[Recommended / Stable]**

### 5. Filters Category (🛡️ Filters)
Individually ignore non-translatable server structures to save bandwidth and API limits:
*   *Skip Ability Cooldowns*, *Skip Damage Indicators*, *Skip Mod Prefixes*, *Skip Dungeon Stats*, *Skip Slayer Alerts*, *Skip Lobby Join/Leave*, *Skip Click Actions*, *Skip Bazaar & BIN Prices*, *Skip UUIDs & IDs*, *Skip URLs & IPs*, *Skip Dates & Lobbies*. **[All Filters are Recommended / Stable]**

---

## 🔑 How to Obtain API Keys

To use advanced translation providers (DeepL or Yandex), you need to acquire an API key. Both offer free usage tiers.

### 1. Google Free (No Setup Required)
*   **Cost**: Free (No limits, keyless).
*   **Setup**: Select `GOOGLE_FREE` as the provider. No API key is needed. It works right out of the box using web-based translation scraping.

### 2. DeepL API Free
*   **Cost**: Free up to 500,000 characters per month.
*   **Setup**:
    1. Visit [deepl.com](https://www.deepl.com/) and register a free account.
    2. Go to the **Developer Portal** or **Account Settings**.
    3. Subscribe to the **DeepL API Free** plan (requires a credit card for identity verification, but you will not be charged).
    4. Go to the **API Keys** section and copy the key (typically ends with `:fx`).
    5. In Minecraft, open `/translator` config, set the provider to `DEEPL_FREE`, and paste your key.

### 3. Yandex Cloud Translate
*   **Cost**: Free trial credit for new accounts, cheap pay-as-you-go pricing thereafter.
*   **Setup**:
    1. Sign in to the [Yandex Cloud Console](https://console.cloud.yandex.ru/).
    2. Create a Billing Account. New users usually get a free promotional grant.
    3. Create a folder in your Yandex Cloud directory.
    4. Create a **Service Account** and assign it the `ai.translate.user` role.
    5. Generate an **API key** for the Service Account in the console.
    6. Paste this API key into the mod's configuration screen, and select `YANDEX` as your provider.

---

## ✍️ How to Contribute Translations

The dictionary is a JSON key-value store. The mod automatically checks GitHub raw assets on startup.

### File Locations
- **Default Dictionary**: `src/main/resources/assets/skyblock_translator/dictionary/dictionary_ru.json` (Russian translation keys).
- **User Dictionary**: `.minecraft/config/skyblock_translator/user_dictionary.json` (Your custom overrides).

### Dictionary Formatting
1.  **Direct Phrases**:
    Keys must be lowercase:
    ```json
    "auction house": "Аукционный дом",
    "skyblock menu": "Меню SkyBlock"
    ```
2.  **Regular Expressions**:
    Regex keys must start with `r:` followed by a Java-compliant regex. Capture groups are referenced with `$1`, `$2`:
    ```json
    "r:lowest bin: ([\\d,kKmM]+) coins": "Худшая цена BIN: $1 монет"
    ```

### Suggesting Translation Fixes
If you spot incorrect translations or missing terms:
1.  Go to the mod config GUI (`/translator`), open the **Cache & Info** category, and click **Suggest Translation / Submit Words**.
2.  Your browser will open our GitHub issues tracker.
3.  Write down the English phrase, and your proposed target translation.

---

## ❓ FAQ & Troubleshooting

### Q: Why does my chat output show double spaces in translation lines?
**A**: Earlier versions had a bug where formatting codes (`§`) added padding spaces during extraction. This issue has been fully resolved. Make sure you are using the latest version of the mod.

### Q: Why are some chat messages showing up twice in English?
**A**: When the translation API fails or returns identical text (e.g. it cannot translate specific terms), the mod verifies it using the `isTranslationSame` validator and discards the duplicate translation. Make sure your translation provider keys are valid or choose `GOOGLE_FREE`.

### Q: Does keeping "Item Names" enabled break searches?
**A**: Yes. Skyblock menus query items using English terms. We recommend keeping **Item Names** disabled in settings. This keeps item names in English while translating their lore and abilities.

---

## 🧩 Building from Source (Multi-Version)

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build a single shared codebase against several Minecraft versions (1.21.11, 26.1.2, 26.2) with Fabric Loom. There is no per-version copy of the source — `src/main/` is shared, and version-specific code (when needed) lives inline behind Stonecutter `//? if` comments.

```bash
# Build every targeted version in one go
./gradlew ":1.21.11:build" ":26.1.2:build" ":26.2:build"

# Or build/run just one version
./gradlew ":26.1.2:build"
```

```bash
# Switch which version is active for editing/running in the IDE, then re-sync Gradle
./gradlew stonecutterSwitchTo26.1.2
```

Key files:
- `settings.gradle.kts` — declares which Minecraft versions are built (`stonecutter { create(rootProject) { versions(...) } }`).
- `stonecutter.properties.toml` — per-version dependency coordinates (Fabric API, ModMenu, YACL) and the shared mod id/version/group.
- `build.gradle.kts` (root) — the single Loom buildscript applied to every version subproject; `dev.kikugie.loom-back-compat` bridges the obfuscated-mappings API (<26.1) and the unobfuscated API (26.1+) so this one script works everywhere.
- `stonecutter.gradle.kts` — marks which version is currently active for editing/running.

To add a future Minecraft version: add it to `versions(...)` in `settings.gradle.kts`, add a matching `["x.y.z"]` section to `stonecutter.properties.toml` with that version's dependency coordinates, then resolve any compile errors Stonecutter reports for that version with a `//? if` block in the shared source.

---

## 🔒 Security & Server Rules Compliance

*   🎮 **100% Client-Side**: The mod runs entirely on your system. It does not send any packets back to Hypixel except standard game packets.
*   ⏱️ **Anti-Spam Delay**: Outgoing message translations are queued with a randomized delay (100ms - 300ms). This mimics organic typing speeds and prevents Hypixel's watchdog from flagging you for sending automated chat packets.
*   💾 **Thread Safety**: Local configurations and translation caches are loaded with explicit UTF-8 guards to prevent file corruption across different operating systems.
*   🛡️ **Thread Race Prevention**: All chat rendering updates are executed safely within the main Minecraft engine thread schedule via `Minecraft.getInstance().execute()`, preventing race conditions and rendering glitches.
