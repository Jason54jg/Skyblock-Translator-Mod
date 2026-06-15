package ru.fridorin.translator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SpamFilter {
    private static final List<Pattern> SPAM_PATTERNS = new ArrayList<>();

    // Pre-check strings for quick rejection before running expensive regex
    // Each entry corresponds to the pattern at the same index, null means no pre-check
    private static final List<String[]> QUICK_CHECKS = new ArrayList<>();

    static {
        // Pattern 0: .*HP.*Defense.*Mana.*
        SPAM_PATTERNS.add(Pattern.compile(".*HP.*Defense.*Mana.*", Pattern.CASE_INSENSITIVE));
        QUICK_CHECKS.add(new String[]{"HP", "Defense", "Mana"}); // all must be present

        // Pattern 1: .*\[[|=+\- ]+\].*
        SPAM_PATTERNS.add(Pattern.compile(".*\\[[|=+\\- ]+\\].*", Pattern.CASE_INSENSITIVE));
        QUICK_CHECKS.add(new String[]{"["}); // must contain a bracket

        // Pattern 2: +digits followed by currency/skill
        SPAM_PATTERNS.add(Pattern.compile("^\\+\\d+.*(Coins|Bits|XP|Gold|Silver|Motes|Gems|Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting).*", Pattern.CASE_INSENSITIVE));
        QUICK_CHECKS.add(new String[]{"+"}); // must start with +

        // Pattern 3: ✧ or ✦ damage indicators
        SPAM_PATTERNS.add(Pattern.compile("^[✧✦].*\\d+.*[✧✦]$"));
        QUICK_CHECKS.add(null); // no cheap pre-check (unicode chars)

        // Pattern 4: no ASCII letters at all
        SPAM_PATTERNS.add(Pattern.compile("^[^a-zA-Z]+$"));
        QUICK_CHECKS.add(null); // always check (it's actually a fast pattern)
    }

    public static boolean shouldIgnore(String text) {
        if (text == null) return true;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return true;

        for (int i = 0; i < SPAM_PATTERNS.size(); i++) {
            String[] checks = QUICK_CHECKS.get(i);
            if (checks != null) {
                boolean allPresent = true;
                for (String check : checks) {
                    if (!trimmed.contains(check)) {
                        allPresent = false;
                        break;
                    }
                }
                if (!allPresent) continue; // Skip expensive regex
            }

            if (SPAM_PATTERNS.get(i).matcher(trimmed).matches()) {
                return true;
            }
        }
        return false;
    }
}

