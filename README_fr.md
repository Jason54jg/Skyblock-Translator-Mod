# 🌍 SkyBlock Translator (Mod Fabric)

[![Version Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1.2%20%7C%2026.2-blue.svg?logo=minecraft&color=62B036)](https://www.minecraft.net/)
[![Chargeur](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&color=E2DBCE)](https://fabricmc.net/)
[![Licence](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-Disponible-green.svg?logo=modrinth)](https://modrinth.com)

**SkyBlock Translator** est un mod de traduction client haute performance pour Minecraft 1.21.11, 26.1.2 et 26.2, conçu spécifiquement pour **Hypixel SkyBlock**. Le mod traduit en temps réel le chat, les descriptions d'objets (lore), les titres de coffres, les hologrammes, la liste des joueurs (Tab) et les entités, de l'anglais vers votre langue cible (le russe par défaut, avec un support hors ligne pour plus de 30 autres langues).

Contrairement aux mods de traduction génériques, **SkyBlock Translator** est spécialement conçu pour analyser les structures particulières de Hypixel SkyBlock, optimisant les performances, économisant les limites d'API, préservant les éléments interactifs et évitant les bannissements de serveur.

---

### 🌐 Langue / Language / Язык
*   **[English](README.md)**
*   **[Русский (Russe)](README_ru.md)**
*   **Français** (Actuel)

---

## 📖 Table des matières
1. [🌟 Fonctionnalités clés](#-fonctionnalités-clés)
2. [⚙️ Fonctionnement du moteur de traduction](#%EF%B8%8F-fonctionnement-du-moteur-de-traduction)
3. [🛠️ Commandes en jeu](#%EF%B8%8F-commandes-en-jeu)
4. [🎮 Options de configuration & explications](#-options-de-configuration--explications)
5. [🔑 Comment obtenir des clés API](#-comment-obtenir-des-clés-api)
6. [✍️ Comment contribuer aux traductions](#%EF%B8%8F-comment-contribuer-aux-traductions)
7. [❓ FAQ & Dépannage](#-faq--dépannage)
8. [🔒 Sécurité & Conformité aux règles du serveur](#-sécurité--conformité-aux-règles-du-serveur)
9. [🧩 Compiler depuis les sources (Multi-version)](#-compiler-depuis-les-sources-multi-version)

---

## 🌟 Fonctionnalités clés

*   ⚡ **Pipeline asynchrone non bloquant** : Toutes les requêtes réseau sont traitées sur un pool de threads séparé. La boucle de rendu principale du jeu ne gèlera jamais en attendant une réponse de l'API.
*   📴 **Traductions hors ligne instantanées** : Les statistiques, types d'objets, prérequis de compétences et enchantements sont traduits instantanément hors ligne via des tables de dictionnaire et des motifs d'expressions régulières.
*   💬 **Modes d'affichage du chat intuitifs** :
    *   `Ligne séparée (Recommandé)` : Affiche une sous-ligne avec la traduction. Les liens de chat originaux, les clics sur les noms de joueurs et les composants de copie d'ID de profil restent pleinement fonctionnels sur la ligne originale.
    *   `Ajouter en dessous` : Ajoute la traduction sous le message original, à l'intérieur du même élément de chat.
    *   `En ligne` : Insère la traduction entre parenthèses `(comme ceci)` à côté des mots originaux.
    *   `Remplacer l'original` : Remplace entièrement le message original.
*   🔍 **Survoler pour révéler l'original** : Des infobulles sont ajoutées aux traductions du chat pour afficher le texte anglais original au survol.
*   🛡️ **Filtres techniques intelligents** : Exclut le spam, les sorties spécifiques à d'autres mods (NEU, Skytils, SBA), les adresses IP, les dates, les UUID, les chiffres de dégâts et les invites de clic.
*   🛑 **Contournement via Shift (bouton panique)** : Maintenez simplement **Shift** en consultant des objets ou en lisant le chat pour suspendre temporairement toutes les traductions et voir le texte anglais brut.
*   🧹 **Gestion interactive du cache** : Videz le cache en toute sécurité d'un simple clic, avec un son de confirmation instantané et une notification système (toast) dans le coin de l'écran.

---

## ⚙️ Fonctionnement du moteur de traduction

SkyBlock Translator traite le texte entrant et les éléments d'interface via un pipeline de traduction multi-étapes hautement optimisé :

1. **🧹 Pré-filtrage & vérifications de sécurité** :
   - Le mod vérifie si le texte contient des informations techniques (par ex. indicateurs de dégâts comme `12,450 crit`, avertissements de temps de recharge, UUID, ou lignes de formatage seul). Si c'est le cas, le texte passe inchangé.
   - Si le joueur maintient la touche **Shift**, toutes les traductions sont temporairement contournées.

2. **⚙️ Extraction du composant à plat** :
   - Plutôt que de découper une phrase en morceaux colorés et de les traduire séparément (ce qui casse la grammaire et produit des traductions hybrides), le mod convertit le `Component` Minecraft entier en une seule chaîne de type « legacy » contenant les codes couleur (`§`).
   - Le mod extrait les valeurs dynamiques comme les nombres, pourcentages et noms de joueurs, en les remplaçant par des modèles (ex. `{0}`, `{P0}`). Cela protège les pseudos et les nombres d'être altérés par les moteurs de traduction.

3. **📴 Correspondance dictionnaire & regex hors ligne (Instantané)** :
   - Le moteur vérifie une correspondance dans votre `user_dictionary.json` local ou le `dictionary_<lang>.json` global téléchargé depuis GitHub.
   - Si aucune correspondance exacte n'existe, le texte passe par un moteur regex haute performance pour traduire instantanément les statistiques courantes (ex. `Strength: +15` -> `Force : +15`).
   - Pour les lignes contenant des deux-points (comme `Interest: 2 hours`), la clé et la valeur sont séparées. La clé est traduite, et la valeur est traduite via le dictionnaire hors ligne ou le moteur regex avant d'être recollées.

4. **🌐 API en ligne asynchrone (Recours)** :
   - Si le texte ne peut pas être traduit hors ligne, une requête est envoyée au fournisseur de traduction sélectionné (Google Free, DeepL, ou Yandex Cloud) sur un pool de threads en arrière-plan pour éviter les saccades du jeu.
   - Si le fournisseur sélectionné est indisponible ou que sa limite de caractères est atteinte, le mod bascule automatiquement sur Google Translate.

5. **🔍 Post-traitement & validation** :
   - Une fois la traduction reçue, le mod reconstruit les codes de formatage Minecraft d'origine et restaure les noms de joueurs et nombres protégés.
   - Le mod valide la traduction via `isTranslationSame`. Si la traduction est identique au texte anglais original (signifiant que l'API n'a pas réussi à le traduire), la traduction est écartée pour éviter les doublons dans le chat.

---

## 🛠️ Commandes en jeu

Toutes les commandes peuvent être déclenchées via `/translator` ou son alias `/переводчик`.

| Commande | Exemple d'utilisation | Comportement détaillé |
| :--- | :--- | :--- |
| `/translator` | `/translator` | Ouvre l'écran graphique de configuration (nécessite `YetAnotherConfigLib`). |
| `/translator add` | `/translator add Enchanted Leather = Cuir Enchanté` | Enregistre une correspondance de phrase personnalisée dans votre `user_dictionary.json` et la charge instantanément en mémoire active. |
| `/translator lookup` | `/translator lookup Enchanted Leather` | Vérifie si un terme est présent dans les tables de dictionnaire actives et affiche son fichier source (Utilisateur vs GitHub). |
| `/translator toggle` | `/translator toggle tooltips` | Active/désactive instantanément un paramètre de configuration spécifique. |
| `/translator setlang` | `/translator setlang fr` | Change la langue cible de traduction et recharge les caches correspondants. |
| `/translator setprovider` | `/translator setprovider DEEPL_FREE` | Change le moteur API actif (choix : `GOOGLE_FREE`, `DEEPL_FREE`, `YANDEX`). |
| `/translator clear-cache` | `/translator clear-cache` | Vide les fichiers de cache locaux sur le disque. Joue un son de confirmation et affiche un toast. |
| `/translator reload` | `/translator reload` | Relit les fichiers de configuration et de dictionnaire utilisateur depuis le dossier de config. |
| `/translator copy-hand` | `/translator copy-hand` | Copie un modèle JSON propre de traduction de toutes les lignes de texte de l'objet en main directement dans le presse-papiers. |
| `/translator update` | `/translator update` | Force la mise à jour et le rechargement des dictionnaires de traduction depuis GitHub immédiatement, sans redémarrage. |
| `/translator test` | `/translator test` | Valide les identifiants API et vérifie la latence de connexion au moteur de traduction sélectionné. |

---

## 🎮 Options de configuration & explications

Chaque option du menu graphique YetAnotherConfigLib (YACL) est structurée avec des en-têtes descriptifs clairs, des émojis et des recommandations de stabilité :

### 1. Catégorie Principal (⚙️ Principal)
*   `✔ Activer le traducteur` : Interrupteur global pour activer ou désactiver toutes les opérations du mod. **[Recommandé / Stable]**
*   `⇧ Désactiver avec Shift` : Maintenir Shift suspend les traductions d'objets et de chat, affichant instantanément le texte original. **[Recommandé / Stable]**
*   `⚡ Traduction Regex (Hors ligne)` : Active la correspondance de motifs dans les fichiers locaux pour des traductions de statistiques instantanées hors ligne. **[Recommandé / Haute performance]**
*   `🔑 Fournisseur de traduction` : Change les API de traduction. **[Stable]**
*   `👁 Afficher la clé API` : Bascule la visibilité des caractères dans le champ de la clé API. **[Stable]**
*   `📝 Clé API` : Champ de saisie pour vos identifiants API. **[Stable]**

### 2. Catégorie Chat (💬 Chat)
*   `💬 Traduire le chat` : Interrupteur principal pour la traduction du chat entrant. **[Recommandé / Stable]**
*   `🗣 Traduire les dialogues des PNJ` : Traduit spécifiquement les lignes préfixées par `[NPC]`. **[Recommandé / Stable]**
*   `📺 Mode d'affichage du chat` :
    *   `Ligne séparée` : Affiche la traduction sous l'original (le plus sûr, ne casse pas les événements de survol/clic).
    *   `Ajouter en dessous` : Insère une sous-ligne à l'intérieur de l'objet du message original.
    *   `En ligne` : Insère la traduction entre parenthèses.
    *   `Remplacer l'original` : Remplace complètement le texte.
*   `🔍 Survoler pour voir l'original` : Ajoute des infobulles aux traductions pour afficher les lignes source. **[Recommandé / Stable]**
*   `📤 Traduire les messages envoyés` : Traduit le texte des messages sortants avant l'envoi (traduit du français vers l'anglais). Inclut des délais de sécurité pour éviter les exclusions anti-spam du serveur. **[Expérimental / À utiliser avec précaution]**
*   `Filtres de canaux de chat` : Interrupteurs sélectifs pour la Guilde, le Groupe, le Coop, les MP, le Public et le Système. **[Stable]**

### 3. Catégorie Monde (🌍 Monde du jeu)
*   `🔮 Traduire les hologrammes` : Traduit les entités textuelles flottantes du monde. **[Stable / Recommandé]**
*   `🐉 Traduire les noms des monstres` : Traduit les étiquettes de niveau/vie des monstres en combat. **[Non recommandé / Spam API élevé / Peut provoquer de micro-saccades lors de l'apparition des monstres]**
*   `🧑 Traduire les noms des PNJ` : Traduit les étiquettes de survol des PNJ amicaux. **[Non recommandé / Spam API]**
*   `📋 Traduire la liste des joueurs (Tab)` : Analyse et traduit les tableaux du menu Tab. **[Recommandé / Stable]**

### 4. Catégorie Interface (🖥️ Interface)
*   `📜 Description des objets (Lore)` : Traduit les infobulles de description des objets. **[Recommandé / Stable]**
*   `🏷 Noms des objets` : Traduit les lignes de titre des objets. **[Non recommandé / Spam API élevé / Complique la recherche d'objets à l'hôtel des ventes/bazar]**
*   `✨ Traduire les enchantements` : Traduit les noms des enchantements sur les objets. **[Non recommandé / Spam API]**
*   `📖 Description des enchantements` : Traduit l'effet d'un enchantement, en laissant son nom en anglais. **[Recommandé / Stable]**
*   `⚡ Noms des capacités` : Traduit les titres des capacités d'objets. **[Non recommandé / Spam API]**
*   `📜 Description des capacités` : Traduit les instructions des capacités d'objets. **[Recommandé / Stable]**
*   `💎 Rareté des objets` : Traduit les classifications de rareté. **[Recommandé / Stable]**
*   `📦 Titres des menus/inventaires` : Traduit les titres des coffres et interfaces. **[Recommandé / Stable]**
*   `📊 Barre latérale (Scoreboard)` : Traduit les lignes de la barre latérale. **[Recommandé / Stable]**

### 5. Catégorie Filtres (🛡️ Filtres)
Ignore individuellement les structures serveur non traduisibles pour économiser la bande passante et les limites d'API :
*   *Ignorer les temps de recharge*, *Ignorer les indicateurs de dégâts*, *Ignorer les préfixes de mods*, *Ignorer les stats de donjon*, *Ignorer les alertes Slayer*, *Ignorer arrivées/départs du lobby*, *Ignorer les actions de clic*, *Ignorer les prix Bazaar & BIN*, *Ignorer les UUID & ID*, *Ignorer les liens & IP*, *Ignorer les dates & lobbies*. **[Tous les filtres sont Recommandés / Stables]**

---

## 🔑 Comment obtenir des clés API

Pour utiliser les fournisseurs de traduction avancés (DeepL ou Yandex), vous devez acquérir une clé API. Les deux offrent des paliers d'utilisation gratuits.

### 1. Google Free (Aucune configuration requise)
*   **Coût** : Gratuit (sans limites, sans clé).
*   **Configuration** : Sélectionnez `GOOGLE_FREE` comme fournisseur. Aucune clé API n'est nécessaire. Fonctionne immédiatement via l'utilisation de la traduction web de Google.

### 2. DeepL API Free
*   **Coût** : Gratuit jusqu'à 500 000 caractères par mois.
*   **Configuration** :
    1. Rendez-vous sur [deepl.com](https://www.deepl.com/) et créez un compte gratuit.
    2. Allez dans le **Portail Développeur** ou les **Paramètres du compte**.
    3. Souscrivez au plan **DeepL API Free** (une carte bancaire est requise pour la vérification d'identité, mais vous ne serez pas débité).
    4. Allez dans la section **API Keys** et copiez la clé (se terminant généralement par `:fx`).
    5. Dans Minecraft, ouvrez la configuration `/translator`, réglez le fournisseur sur `DEEPL_FREE`, et collez votre clé.

### 3. Yandex Cloud Translate
*   **Coût** : Crédit d'essai gratuit pour les nouveaux comptes, tarification à l'usage bon marché ensuite.
*   **Configuration** :
    1. Connectez-vous à la [Console Yandex Cloud](https://console.cloud.yandex.ru/).
    2. Créez un compte de facturation. Les nouveaux utilisateurs reçoivent généralement une subvention promotionnelle gratuite.
    3. Créez un dossier dans votre répertoire Yandex Cloud.
    4. Créez un **Compte de service** et attribuez-lui le rôle `ai.translate.user`.
    5. Générez une **clé API** pour le compte de service dans la console.
    6. Collez cette clé API dans l'écran de configuration du mod, et sélectionnez `YANDEX` comme fournisseur.

---

## ✍️ Comment contribuer aux traductions

Le dictionnaire est un stockage clé-valeur JSON. Le mod vérifie automatiquement les ressources brutes GitHub au démarrage.

### Emplacement des fichiers
- **Dictionnaire par défaut** : `src/main/resources/assets/skyblock_translator/dictionary/dictionary_fr.json` (clés de traduction françaises).
- **Dictionnaire utilisateur** : `.minecraft/config/skyblock_translator/user_dictionary.json` (vos remplacements personnalisés).

### Format du dictionnaire
1.  **Phrases directes** :
    Les clés doivent être en minuscules :
    ```json
    "auction house": "Hôtel des ventes",
    "skyblock menu": "Menu SkyBlock"
    ```
2.  **Expressions régulières** :
    Les clés regex doivent commencer par `r:` suivi d'une expression régulière compatible Java. Les groupes de capture sont référencés avec `$1`, `$2` :
    ```json
    "r:lowest bin: ([\\d,kKmM]+) coins": "Prix BIN le plus bas : $1 pièces"
    ```

### Proposer des corrections de traduction
Si vous repérez des traductions incorrectes ou des termes manquants :
1.  Allez dans l'interface graphique du mod (`/translator`), ouvrez la catégorie **Cache & Infos**, et cliquez sur **Proposer une traduction**.
2.  Votre navigateur ouvrira notre suivi de tickets GitHub.
3.  Notez la phrase anglaise, et votre traduction proposée.

---

## ❓ FAQ & Dépannage

### Q : Pourquoi mon chat affiche-t-il des doubles espaces dans les lignes de traduction ?
**R** : Les versions précédentes avaient un bug où les codes de formatage (`§`) ajoutaient des espaces de remplissage durant l'extraction. Ce problème a été entièrement résolu. Assurez-vous d'utiliser la dernière version du mod.

### Q : Pourquoi certains messages de chat apparaissent-ils deux fois en anglais ?
**R** : Lorsque l'API de traduction échoue ou renvoie un texte identique (ex. elle ne peut pas traduire certains termes), le mod le vérifie via le validateur `isTranslationSame` et écarte la traduction en doublon. Assurez-vous que vos clés de fournisseur de traduction sont valides ou choisissez `GOOGLE_FREE`.

### Q : Garder « Noms des objets » activé casse-t-il les recherches ?
**R** : Oui. Les menus SkyBlock recherchent les objets par leurs termes anglais. Nous recommandons de garder **Noms des objets** désactivé dans les paramètres. Cela conserve les noms d'objets en anglais tout en traduisant leur description et leurs capacités.

---

## 🔒 Sécurité & Conformité aux règles du serveur

*   🎮 **100% côté client** : Le mod fonctionne entièrement sur votre système. Il n'envoie aucun paquet à Hypixel en dehors des paquets de jeu standards.
*   ⏱️ **Délai anti-spam** : Les traductions de messages sortants sont mises en file d'attente avec un délai aléatoire (100ms - 300ms). Cela imite une vitesse de frappe organique et empêche le watchdog de Hypixel de vous signaler pour envoi de paquets de chat automatisés.
*   💾 **Sécurité des threads** : Les configurations locales et les caches de traduction sont chargés avec des garde-fous UTF-8 explicites pour éviter la corruption de fichiers sur différents systèmes d'exploitation.
*   🛡️ **Prévention des conflits de threads** : Toutes les mises à jour de rendu du chat sont exécutées en sécurité dans le planning du thread principal du moteur Minecraft via `Minecraft.getInstance().execute()`, évitant les conditions de concurrence et les glitchs de rendu.

---

## 🧩 Compiler depuis les sources (Multi-version)

Ce projet utilise [Stonecutter](https://stonecutter.kikugie.dev/) pour compiler une base de code unique et partagée pour plusieurs versions de Minecraft (1.21.11, 26.1.2, 26.2) avec Fabric Loom. Il n'y a pas de copie du code source par version — `src/main/` est partagé, et le code spécifique à une version (quand nécessaire) vit directement dans le code via des commentaires Stonecutter `//? if`.

```bash
# Compiler toutes les versions ciblées en une fois
./gradlew ":1.21.11:build" ":26.1.2:build" ":26.2:build"

# Ou compiler/lancer une seule version
./gradlew ":26.1.2:build"
```

```bash
# Changer la version active pour l'édition/l'exécution dans l'IDE, puis resynchroniser Gradle
./gradlew stonecutterSwitchTo26.1.2
```

Fichiers clés :
- `settings.gradle.kts` — déclare les versions de Minecraft compilées (`stonecutter { create(rootProject) { versions(...) } }`).
- `stonecutter.properties.toml` — coordonnées de dépendances par version (Fabric API, ModMenu, YACL) et id/version/groupe du mod partagés.
- `build.gradle.kts` (racine) — le script de compilation Loom unique appliqué à chaque sous-projet de version ; `dev.kikugie.loom-back-compat` fait le pont entre l'API à mappings obfusqués (<26.1) et l'API non obfusquée (26.1+) pour que ce script unique fonctionne partout.
- `stonecutter.gradle.kts` — indique quelle version est actuellement active pour l'édition/l'exécution.

Pour ajouter une future version de Minecraft : ajoutez-la à `versions(...)` dans `settings.gradle.kts`, ajoutez une section `["x.y.z"]` correspondante dans `stonecutter.properties.toml` avec les coordonnées de dépendances de cette version, puis résolvez les erreurs de compilation que Stonecutter signale pour cette version avec un bloc `//? if` dans le code source partagé.
