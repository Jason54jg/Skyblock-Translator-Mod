# 🌍 SkyBlock Translator (Fabric Mod)

**SkyBlock Translator** is a high-performance client-side translation mod for Minecraft 1.21.11, designed specifically for **Hypixel SkyBlock** players. It translates in-game chat, item tooltips (lore), chest titles, holograms, player tab lists, and entities from English to your target language (default is Russian, with support for over 30+ other target languages) in real-time.

---

## 🇷🇺 Описание на русском (Russian Summary)
**SkyBlock Translator** — это клиентский Fabric-мод для автоматического перевода с английского на русский (и другие языки) в реальном времени, оптимизированный под **Hypixel SkyBlock**.

Полное подробное описание возможностей, команд, настроек и инструкций по установке доступно на русском языке в **[GitHub README_ru.md](https://github.com/fridorin/Skyblock-Translator-Mod/blob/main/README_ru.md)**.

---

## 🌟 Key Features

### 1. Advanced Chat Translation
*   **Separate Line (Recommended)**: Displays translations as a clean sub-line below the original message, **preserving all original clickable elements** (like player names, coop invites, auction links, and Profile ID copy triggers). Original and translated lines are grouped as a single component, eliminating alignment mix-ups in fast multiplayer chat.
*   **Hover to Reveal Original**: Hover over any translated chat message to immediately see the original English message in a popup tooltip.
*   **Channel Filtering**: Toggle translations individually for Guild, Party, Co-op, DMs, Public lobby, and Server system messages.

### 2. Smart Tooltip & Lore Translation
*   **Flat Component Translation**: Translates entire sentences at once instead of individual fragments. This eliminates grammatically broken hybrid/partial translations (no more half-Russian, half-English tooltips).
*   **Keep Item Names in English**: Essential for trading, using the Auction House, or interacting with the Bazaar, while still translating all descriptive lore text.
*   **Enchantment & Ability Descriptions**: Translates what an enchantment or item ability does, while keeping its name in English to preserve gameplay muscle memory.
*   **Rarity Formatting**: Instantly translates rarity banners (e.g. `MYTHIC SWORD` ➔ `МИФИЧЕСКИЙ МЕЧ`) without stretching inventory margins.

### 3. Precise Entity & Hologram Separation
*   **Dynamic Lists**: Lists of known Skyblock NPCs and allowed English words are not hardcoded. You can customize, extend, or override them dynamically directly in your language dictionaries by modifying `"npc_names_list"` and `"allowed_english_words_list"` keys.
*   **NPC Name vs Hologram Classification**: Using standard lists and dynamic additions, the mod correctly identifies NPC nameplates (such as `Kat` or `Elizabeth`) from floating action holograms (like `Click` or `Right-click`). Disabling NPC translation will never hide or break general hologram translations.
*   **Offline Regex Engine**: Translates number-based item stats (e.g. `Strength: +15` ➔ `Сила: +15`) instantly **offline** using regular expressions. This completely bypasses network calls for stats, resulting in **zero frame drops** when browsing dense chests or Bazaar pages.

### 4. Smart Technical Filters
*   Bypasses translation for technical, non-translatable text to preserve API limits: ability cooldowns, combat damage indicators (`✧⚔❤`), other mod prefixes (NEU, Skytils, SBA), lobby join/leave info, dates, and server IPs.

### 5. Translation Providers
*   **Google Translate (Free & Out-of-the-Box)**: Free, keyless, and works immediately.
*   **DeepL API (Free/Pro)**: Connect your personal free DeepL API key for superior translation quality (up to 500,000 chars/month free).
*   **Yandex Translate**: Connect your Yandex developer API keys.

---

## 🛠️ In-Game Commands

All commands can be invoked using `/translator` or Russian alias `/переводчик`.

| Command | Description |
| :--- | :--- |
| `/translator` | Opens the graphical settings GUI configuration menu. |
| `/translator add <en> = <ru>` | Saves a custom translation override directly to `user_dictionary.json`. |
| `/translator lookup <phrase>` | Checks translation mapping source (User vs GitHub/Default). |
| `/translator toggle <category>`| Quickly toggles settings parameter on or off. |
| `/translator setlang <lang>` | Changes translation target language (e.g., `ru`, `de`, `es`). |
| `/translator setprovider <API>`| Swaps translation engines (`GOOGLE_FREE`, `DEEPL_FREE`, `YANDEX`). |
| `/translator clear-cache` | Clears the local disk translation caches with sound and toast notification. |
| `/translator reload` | Reloads all config and user dictionary files. |
| `/translator copy-hand` | Copies raw JSON translation template of the hand item. |
| `/translator update` | Pulls the latest dictionary from GitHub immediately. |
| `/translator test` | Validates API credentials and prints response latency. |

---

## 🔒 Safety & Rules Compliance

*   **Hypixel Safe**: 100% client-side. The mod does not perform suspicious requests or send automated chat packets.
*   **Typing Emulation**: If you enable **Auto-translate Outgoing Chat**, the mod queues outgoing messages with a randomized human-like delay (100ms - 300ms) to bypass Hypixel watchdog checks.
*   **Caching & Optimization**: Built-in HTTP connection pools and local JSON caches ensure the mod only queries API engines when a completely new phrase is found.
*   **Thread Safety**: UI actions and chat appends run in the main Minecraft game loop schedulers, preventing crashes and rendering glitches.
