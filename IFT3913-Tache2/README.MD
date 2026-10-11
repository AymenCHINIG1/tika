# IFT3913 — Tâche 2 : génération de tests et analyse de mutation d'Apache Tika

> **État : document de travail au 11 octobre 2026.** Les résultats chiffrés et les exécutions JUnit ci-dessous ont été observés. Les points à confirmer sont indiqués explicitement. Ce fichier doit être placé sous le nom `README.md` **à la racine du dépôt de la tâche du binôme** (et non dans le module Apache Tika).

## 1. Sélection de la classe et objectif

- **Cas d'étude :** Apache Tika, version `4.0.0-SNAPSHOT`.
- **Classe sélectionnée :** `org.apache.tika.parser.pdf.PDFParser`.
- **Module Maven :** `tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module`.
- **Tests préexistants :** principalement `org.apache.tika.parser.pdf.PDFParserTest`.
- **Environnement :** Windows, PowerShell, IntelliJ IDEA, Maven Wrapper et Ollama exécuté localement.

`PDFParser` a été retenue parce qu'elle dispose déjà d'une suite de tests substantielle, mais n'est pas couverte à 100 %. La première analyse PITEST a produit **201 mutants**, dont **62 survivants** et **38 non couverts**. Ces résultats prouvent qu'il existait des comportements et des branches à mieux vérifier, particulièrement dans `parse()`, `shouldSpool()` et `scanXRefOffsets()`. La sélection d'une seule classe respecte la limite de une à trois classes.

Un premier contrôle des tests existants dans IntelliJ avait recensé **65 tests : 64 réussis et 1 ignoré**. Les pourcentages obtenus avec l'outil de couverture IntelliJ ne sont pas directement comparables aux pourcentages PITEST; la comparaison principale ci-dessous utilise donc les résultats PITEST.

## 2. Installation et configuration

### 2.1. ChatUniTest et LLM local

Le plugin Maven `io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1` a été configuré dans le `pom.xml` du module :

```xml
<plugin>
    <groupId>io.github.zju-aces-ise</groupId>
    <artifactId>chatunitest-maven-plugin</artifactId>
    <version>2.1.1</version>
    <configuration>
        <apiKeys>ollama</apiKeys>
        <model>code-llama</model>
        <url>http://localhost:11434/v1/chat/completions</url>
        <thread>false</thread>
        <testNumber>1</testNumber>
    </configuration>
</plugin>
```

Les modèles ouverts exécutés localement ont inclus **Qwen 2.5 Coder 7B**, **CodeLlama 7B** et un essai de **CodeQwen 1.5 Chat**. Le nom `code-llama` dans le POM est un **alias Ollama**, rendu nécessaire par les restrictions de noms du plugin; **cet alias a pointé vers des modèles différents au cours des essais**. Ainsi, les tests `shouldHandleXFAOnly` et `shouldSpool` ont été travaillés avec Qwen 2.5 Coder 7B, tandis que la tentative `scanXRefOffsets` finalement exportée de `records.json` a été produite avec **CodeLlama 7B**. On ne peut donc pas déduire le modèle réel du seul nom `code-llama` présent dans le POM.

Exemples des commandes Ollama utilisées :

```powershell
ollama cp qwen2.5-coder:7b code-llama
# Pour les essais ultérieurs avec CodeLlama :
ollama cp codellama:7b code-llama
ollama list
```

Commande générale de génération :

```powershell
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1:method '-DselectMethod=org.apache.tika.parser.pdf.PDFParser#shouldSpool'
```

Pour certaines tentatives, la limite de réponse a été augmentée avec `'-DmaxResponseTokens=4096'`. Des essais ont été réalisés avec `'-DnoExecution=true'` : cette option ne supprimait pas la phase de compilation du plugin. Les prompts standards du plugin ont été conservés.

Les fichiers de tests que ChatUniTest accepte sont déposés dans **`chatunitest-tests/`**; ses journaux et réponses, y compris les générations rejetées, sont conservés sous **`C:\tmp\chatunitest-info\...\records.json`**. Il faut distinguer la réponse produite par le LLM de sa validation automatique par ChatUniTest : `BUILD SUCCESS` du plugin ne garantit pas à lui seul un test Java compilé et exécuté.

### 2.2. PITEST

Le module inclut :
- `org.pitest:pitest-maven:1.22.0`;
- le plugin d'intégration JUnit 5 `org.pitest:pitest-junit5-plugin:1.2.3`;
- le ciblage de `org.apache.tika.parser.pdf.PDFParser`;
- deux threads et des rapports HTML/XML.

Les dépendances Mockito de test (`mockito-core` et `mockito-junit-jupiter`, version `5.18.0`) ont également été ajoutées pour permettre la compilation des tests qui les utilisent.

**Classes à inclure dans le ciblage final des tests PITEST** (à vérifier dans le POM réellement commité) :

```xml
<targetClasses>
    <param>org.apache.tika.parser.pdf.PDFParser</param>
</targetClasses>
<targetTests>
    <param>org.apache.tika.parser.pdf.PDFParserTest</param>
    <param>org.apache.tika.parser.pdf.PDFParser_shouldHandleXFAOnly_21_0_Test</param>
    <param>org.apache.tika.parser.pdf.PDFParser_shouldSpool_8_0_Test</param>
    <param>org.apache.tika.parser.pdf.PDFParser_scanXRefOffsets_4_0_Test</param>
    <param>org.apache.tika.parser.pdf.PDFParser_parse_ContextRestorationTest</param>
    <param>org.apache.tika.parser.pdf.PDFParser_parse_MutationFocusedTest</param>
</targetTests>
```

Commande utilisée depuis la racine d'Apache Tika :

```powershell
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module test-compile org.pitest:pitest-maven:1.22.0:mutationCoverage '-Dcheckstyle.skip=true'
```

L'option `-Dcheckstyle.skip=true` a été utilisée lors des mesures, en raison d'erreurs de style rencontrées durant l'intégration. Ce contournement ne signifie pas que l'exécution normale de CI passe sans cette option.

## 3. Résultat de la génération par ChatUniTest

| Méthode | Nature de la sortie | Compilation et exécution sans retouches ? | État retenu |
|---|---|---|---|
| `getSupportedTypes()` | Test simple avec oracle sur `application/pdf` | Oui pour le code retenu, sous réserve des dépendances Mockito du projet | Génération réussie, mais test redondant au regard de PITEST; présence dans le ciblage final à vérifier |
| `shouldHandleXFAOnly()` | Quatre scénarios des combinaisons booléennes | Oui, après intégration des dépendances de test | Fichier `PDFParser_shouldHandleXFAOnly_21_0_Test.java` conservé |
| `shouldSpool()` | Tests JUnit générés, dont un corps de test initialement vide | **Non** | Fichier `PDFParser_shouldSpool_8_0_Test.java` corrigé humainement |
| `scanXRefOffsets()` | Java récupéré d'une réponse `records.json` malgré l'échec de compilation par ChatUniTest | **Non** | Fichier `PDFParser_scanXRefOffsets_4_0_Test.java` exporté puis fortement corrigé humainement |
| `parse()` | Réponses descriptives JSON au lieu de tests JUnit | **Non** | Aucune classe de test `parse()` générée automatiquement et utilisable; tests ciblés rédigés manuellement |
| Autres essais, notamment `getDefaultConfig()` | Réponses parfois incomplètes, imports/API inexistants, compilation échouée | Non | Non retenus |

### 3.1. Tests `shouldHandleXFAOnly()` et leurs oracles

La méthode vérifie la conjonction de deux conditions, `config.isIfXFAExtractOnlyXFA()` et `hasXFA`. Le code IA vérifie les quatre combinaisons `(true,true)`, `(true,false)`, `(false,true)` et `(false,false)` avec les oracles respectifs `true`, `false`, `false` et `false`. Les vérifications sont spécifiques et logiquement pertinentes. Toutefois, ces tests n'ont **pas amélioré le score PITEST**, ce qui indique une redondance avec les tests existants pour les mutants considérés.

### 3.2. Retouches humaines : `shouldSpool()`

Dans `PDFParser_shouldSpool_8_0_Test.java`, le code généré a nécessité **au moins trois corrections humaines distinctes** :

1. Suppression du `@BeforeEach` contenant un stub Mockito inutilisé (`when(config.getOcr()).thenReturn(ocrConfig)`), qui causait `UnnecessaryStubbingException`.
2. Remplacement de `@InjectMocks private PDFParser pdfParser` par une instanciation explicite `new PDFParser()` pour éviter la sélection incorrecte du constructeur `JsonConfig` et l'échec associé.
3. Ajout d'un véritable corps au test initialement vide `testShouldSpool_WithRenderPagesAtPageEnd` : configuration de `RENDER_PAGES_AT_PAGE_END`, invocation de la méthode privée par réflexion et `assertTrue` du résultat.

L'oracle du test vide était **inexistant**. Sa réussite initiale n'apportait aucune preuve sur `shouldSpool()`. Le nouvel `assertTrue` constitue un ajout manuel substantiel, non une réussite autonome du modèle.

### 3.3. Retouches humaines : `scanXRefOffsets()`

ChatUniTest a produit du code Java, mais **n'a pas réussi à le compiler ni à l'exporter automatiquement comme test accepté**. Le code de la première tentative a été extrait de la propriété `.code` de `records.json` et sauvegardé en `.java` par PowerShell. Une première compilation Maven a été bloquée par un **BOM UTF-8** ajouté à l'export. Après correction de l'encodage, Maven a relevé **20 erreurs Java**.

Au moins **cinq catégories de corrections humaines** ont été nécessaires :

1. Correction de l'encodage en UTF-8 sans BOM.
2. Suppression des appels au constructeur `StartXRefOffset()` sans arguments et aux setters inexistants.
3. Invocation de `scanXRefOffsets`, méthode privée, par réflexion (`getDeclaredMethod`, `setAccessible`, `invoke`).
4. Correction des oracles sur `Metadata.getValues()`, qui renvoie `String[]` : `.length` et non `.size()`; accès `[0]` et non `.get(0)`.
5. Refonte des données et attentes : la méthode inspecte **les octets du PDF réel**, et non la liste artificielle d'offsets placée dans `IncrementalUpdateRecord` au sein du `ParseContext`. Les assertions originales sur des offsets fictifs ne pouvaient pas être fiables.

Les **trois tests finaux** sont :
- `testScanXRefOffsetsNoIncrementalUpdates` : quand les deux options sont désactivées, aucun offset ni compteur d'updates n'est produit; oracle : tableau d'offsets vide et compteur absent.
- `testScanXRefOffsetsAlreadyIncrementalUpdate` : un marqueur `IsIncrementalUpdate` préexistant dans le contexte est retiré; oracle : valeur `null` après l'appel, métadonnées d'offset absentes.
- `testScanXRefOffsetsEmptyInputRecordsZeroUpdates` : fichier PDF vide, analyse d'informations incrémentales activée; oracle : zéro offset et valeur du compteur `"0"`.

**Validation observée :** `3 tests`, `0 failure`, `0 error`, `BUILD SUCCESS`.

### 3.4. Échec de ChatUniTest sur `parse()`

La méthode `parse()` est centrale mais longue et dépend de nombreux services (PDFBox, métadonnées, OCR, rendu et extraction). Cinq réponses inspectées dans `records.json` portaient sur la description JSON de la classe, pas sur des fichiers JUnit. Trois réponses ont atteint **4 096 tokens**; les cinq avaient `hasCode=False` et aucune déclaration de classe de test Java exploitable. Plutôt que de présenter des tests fabriqués comme générés par l'IA, nous avons **développé manuellement** des tests additionnels dirigés par PITEST.

## 4. Comparaison qualitative des oracles IA / humains

| Dimension | Oracles produits par IA | Oracles corrigés ou ajoutés humainement |
|---|---|---|
| Pertinence | Bonne pour la logique booléenne simple (`shouldHandleXFAOnly`) | Ciblent des comportements qui posaient problème dans le rapport de mutation |
| Spécificité | `assertEquals`, `assertTrue/False` parfois appropriés, mais parfois données et sorties imaginaires | Identité d'objet (`assertSame`), absence de métadonnées (`assertNull`), interactions (`verify`) et effets de configuration |
| Risques | Test vide, mauvaise API, mocks inutiles, scénarios contraires au comportement réel | Risque inverse de tests trop couplés aux détails d'implémentation; il faut confirmer leur valeur avec PITEST |
| Valeur démontrée | `shouldHandleXFAOnly()` n'a pas changé les mutants tués | Les groupes de nouveaux tests intégrés ont fait progresser la suite de 50 % à 58 %, sans attribution individuelle complète à ce stade |

Une assertion qui vérifie simplement l'absence d'exception ou la présence de métadonnées générales peut être insuffisante pour distinguer deux branches. À l'inverse, `assertSame` ou `verify(context).set(...)` fournit un oracle plus discriminant contre les mutants qui suppriment une restauration de contexte, mais peut rendre le test dépendant de la structure interne du code. Nous avons vérifié les résultats JUnit **et** réexécuté PITEST au lieu de supposer qu'un test réussi tuait nécessairement un mutant.

## 5. Tests supplémentaires écrits manuellement

Les classes ci-dessous ne sont **pas** des générations autonomes de ChatUniTest. Elles ont été créées après inspection des mutants `SURVIVED` et `NO_COVERAGE` de `PDFParser.parse()`.

### 5.1. `PDFParser_parse_ContextRestorationTest.java` — 3 cas

| Test | Intention | Données choisies et motivation | Oracle |
|---|---|---|---|
| `restoresExistingContextEntriesAfterSuccessfulParse` | Garantir la restauration des valeurs d'entrée du contexte après une analyse valide (lignes 234 et 236) | PDF à une page construit en mémoire; compteur `OCRPageCounter` et `IncrementalUpdateRecord` initiaux non nuls | `assertSame` pour les deux objets d'origine |
| `restoresExistingContextEntriesWhenParsingFails` | Garantir la restauration même quand le traitement échoue (`finally`) | Flux de 0 octet pour provoquer une `IOException`, avec deux objets initiaux présents | `assertThrows(IOException.class)` et `assertSame` après exception |
| `restoresNullContextEntriesWhenParsingFails` | Garantir que les entrées initialement absentes demeurent absentes | Flux vide et `ParseContext` sans compteur ni enregistrement | `assertThrows(IOException.class)` et `assertNull` sur les deux entrées |

**Résultat observé :** 3 tests exécutés, 0 échec, 0 erreur.

### 5.2. `PDFParser_parse_MutationFocusedTest.java` — 6 cas

| Test | Intention / lignes visées | Données de test choisies et motivation | Oracle |
|---|---|---|---|
| `parseRestoresOriginalContextObjectsAndClearsIncrementalRecordTemporarily` | Vérifier initialisation du renderer et restauration du compteur et de l'enregistrement incrémental (172, 173, 234, 236) | PDF réel d'une page en mémoire; `ParseContext` espionné et deux objets initiaux distincts | `getRenderer()` non nul, `assertSame`, et `verify` des appels `set(null)` puis des remises en place |
| `parseRestoresOriginalRenderingState` | Vérifier la restauration de `PDFRenderingState` (247) | Ancien état simulé placé dans un `ParseContext` espionné | `assertSame` et `verify(context).set(PDFRenderingState.class, oldState)` |
| `parseCreatesTemporaryRenderingStateWhenSpoolingIsRequired` | Exercer la branche de création/effacement de l'état temporaire (181, 182, 247) | Stratégie `RENDER_PAGES_AT_PAGE_END` qui rend le spooling nécessaire; PDF d'une page | Vérification Mockito qu'un état non nul a été placé dans le contexte, puis qu'une valeur nulle a été rétablie |
| `parseHonorsKcmsConfiguration` | Exercer la branche activée de `isSetKCMS()` (168) | Option `setSetKCMS(true)`; propriété système préalable sauvegardée et restaurée | `assertEquals` sur `sun.java2d.cmm`, avec nettoyage dans `finally` |
| `parseAcceptsMainMemoryOnlySetting` | Exercer le chemin mémoire négative (191) | `setMaxMainMemoryBytes(-1)` et PDF valide minimal | `assertNotNull` du compteur OCR dans les métadonnées; oracle de réussite, moins discriminant sur le mode mémoire lui-même |
| `parseAcceptsZeroMemoryThresholdBoundary` | Exercer la frontière de la condition mémoire (191) | `setMaxMainMemoryBytes(0)`, valeur limite | `assertNotNull` du compteur OCR; oracle à renforcer si le mutant de frontière reste vivant |

**Résultat observé :** 6 tests exécutés, 0 échec, 0 erreur.

**Corrections humaines durant la mise au point :**
1. Ajout de l'import `org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState` après 8 erreurs `cannot find symbol`.
2. Ajout de `clearInvocations(context)` sur les `ParseContext` espionnés pour ne pas compter les appels de préparation dans les vérifications Mockito.
3. Retrait de `verify(renderResults).close()` d'un cas qui n'amenait pas réellement le parser à fermer ces résultats; cette assertion était incorrecte. Le mutant de **ligne 240 n'est pas déclaré tué par ce seul test**.

**Limite importante :** un test qui exerce le chemin d'allocation mémoire avec -1 ou 0 et ne vérifie que le compteur OCR ne garantit pas la détection d'un mutant sur la sélection de `MemoryUsageSetting`. Les mutants exacts tués doivent être vérifiés dans les rapports PITEST avant de leur attribuer un test précis.

### 5.3. Tests ciblant encore des mutants non détectés

Au moment de l'analyse détaillée initiale, les exemples suivants étaient signalés dans `parse()` :

| Ligne | Mutation ou comportement | État constaté avant les derniers ajouts | Vérification complémentaire pertinente |
|---|---|---|---|
| 168 | Condition `isSetKCMS()` | `SURVIVED` | Effet sur propriété JVM avec option activée et désactivée |
| 172 | Suppression du reset initial de `IncrementalUpdateRecord` | `SURVIVED` | Vérifier la valeur nulle pendant le traitement, pas seulement en fin d'appel |
| 173 | Suppression de `initRenderer()` | `SURVIVED` | Vérifier que le renderer a été initialisé et employé |
| 181–182 | Inversion de `shouldSpool` / absence de l'état temporaire | `SURVIVED` | Espionner les `set` pendant un scénario de spooling |
| 191 | Inversion de condition et changement de frontière mémoire | deux `SURVIVED` | Oracle distinct sur la voie d'allocation sélectionnée |
| 212 | Suppression de `renderPagesBeforeParse()` | `SURVIVED` | Construire un scénario où le rendu préalable produit un effet observable |
| 218 | Suppression de `OCR2XHTML.process()` | `NO_COVERAGE` | PDF et configuration OCR_ONLY avec résultat observable |
| 220–222 | Branche `hasMarkedContent && isExtractMarkedContent()` et `PDFMarkedContent2XHTML.process()` | `SURVIVED` / `NO_COVERAGE` | PDF contenant du contenu marqué et assertion sur le texte extrait |
| 239–240 | Condition et fermeture de `RenderResults` | `SURVIVED` / `NO_COVERAGE` | Préparer un état dont `getRenderResults()` n'est pas nul et vérifier `close()` |
| 243 | Suppression de `PDDocument.close()` | `SURVIVED` | Vérification spécifique de la fermeture sans masquer la fuite par la fermeture du flux |
| 247 | Restaurations de `PDFRenderingState` | `SURVIVED` / `NO_COVERAGE` | Scénarios avec état initial nul/non nul, succès/exception |

**Ces états datent du rapport détaillé fourni avant les nouveaux tests**. Il faut relire le **rapport PITEST final** pour savoir quelles mutations sont encore vivantes. Nous ne prétendons ni couvrir 100 % de `parse()`, ni avoir tué tous les mutants.

## 6. Résultats PITEST : avant / après

La référence officielle est **la toute première analyse : 50 % (101 mutants tués sur 201)**. Le passage à 52 %, puis 55 %, est intermédiaire.

| Mesure PITEST — `PDFParser` | Référence initiale | Intermédiaire (55 %) | Dernière mesure |
|---|---:|---:|---:|
| Mutations générées | 201 | 201 | 201 |
| Mutants tués | **101** | 110 | **117** |
| Mutants survivants + non couverts | 100 | 91 | 84 |
| Couverture de mutation | **50 %** | 55 % | **58 %** |
| Couverture des lignes | **78 %** | 79 % (284/358) | **80 % (287/358)** |
| Test strength | **62 %** | 67 % (110/165) | **70 % (117/166)** |

La dernière mesure indique **16 mutants tués supplémentaires**, soit **+8 points** de couverture de mutation et **+2 points** de couverture des lignes depuis la référence. Entre les rapports intermédiaire (55 %) et final actuel (58 %), les 6 tests de `PDFParser_parse_MutationFocusedTest` ont accompagné une augmentation de **7 mutants tués**, sous réserve d'un périmètre PITEST identique.

Les dénominateurs de *test strength* (`165` puis `166`) peuvent varier selon la couverture des mutants. Le nombre de mutants `SURVIVED` et `NO_COVERAGE` **séparément** dans la dernière mesure n'a pas été fourni; seul leur total (**84**) est déductible de 201 − 117.

### 6.1. Pourquoi des mutants sont détectés

Il ne suffit pas de constater la variation globale. Les mécanismes de détection attendus sont :
- **Suppression de `ParseContext.set(...)`** : les valeurs d'origine ne sont pas restaurées, donc `assertSame` ou `verify` peut échouer.
- **Inversion de condition de spooling** : l'état `PDFRenderingState` est ajouté ou non dans le mauvais scénario; les vérifications Mockito peuvent échouer.
- **Suppression de l'effet KCMS** : avec l'option activée, l'attente sur la propriété système peut échouer.
- **Suppression de `scanXRefOffsets()`** : les sorties de métadonnées et le traitement du marqueur incrémental peuvent diverger.

Ces explications décrivent **comment** les oracles peuvent tuer les mutants; elles ne constituent **pas une preuve que chaque mutation visée est déjà `KILLED`**. Une documentation exhaustive « mutant → test tueur » exige la lecture du rapport final, ligne par ligne (`PDFParser.java.html` ou XML), et idéalement une comparaison des identifiants PITEST avant/après.

### 6.2. Limites de l'attribution du gain

Les analyses successives ont intégré **plusieurs classes de tests**. Les rapports agrégés ne permettent pas d'affirmer que l'intégralité des 16 nouveaux mutants tués provient d'un seul fichier ou que chacun provient directement de tests créés par ChatUniTest. Les tests `parse()` sont manuels. Nous attribuons donc le gain à **l'ensemble des tests supplémentaires et corrections intégrés**, pas exclusivement au modèle.

## 7. Reproduction et validation

Pour valider séparément les tests concernés :

```powershell
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module test '-Dtest=PDFParser_scanXRefOffsets_4_0_Test' '-Dcheckstyle.skip=true'
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module test '-Dtest=PDFParser_parse_ContextRestorationTest' '-Dcheckstyle.skip=true'
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module test '-Dtest=PDFParser_parse_MutationFocusedTest' '-Dcheckstyle.skip=true'
```

Les sorties Maven communiquées confirment respectivement **3/3**, **3/3** et **6/6**, sans échec, pour ces trois classes. Les avertissements Mockito/Byte Buddy sur le chargement dynamique de l'agent ont été observés, mais n'ont pas bloqué ces exécutions.

**À confirmer avant remise :** exécution intégrale de la suite concernée, absence de dépendance à `-Dcheckstyle.skip=true`, exécution réussie dans **GitHub Actions**, puis publication des liens demandés.

## 8. Lien vers le travail et livraison

- **Repository GitHub du binôme :** `À RENSEIGNER — https://github.com/...`
- **README.md GitHub à la racine de la tâche :** `À RENSEIGNER — https://github.com/.../blob/.../README.md`
- **Pull request de remise :** `À RENSEIGNER — lien de la seconde PR`

La remise nécessite une **nouvelle pull request** dans le sous-répertoire du binôme et les liens vers le dépôt et le présent README dans la PR, selon le modèle `tache2-readme.md` du cours. La date limite doit être vérifiée sur la page officielle du cours.

## 9. Vérification des consignes et critères

| Consigne / critère | Statut | Justification ou action restante |
|---|---|---|
| Choisir 1 à 3 classes déjà testées mais incomplètement couvertes (10 %) | **Couvert** | `PDFParser`, tests existants et mutants survivants démontrés |
| Installer ChatUniTest dans Maven et utiliser un LLM ouvert local (10 %) | **Couvert** | Plugin v2.1.1 + Ollama + Qwen/CodeLlama |
| Générer des tests avec ChatUniTest (10 %) | **Couvert** | `shouldHandleXFAOnly`, `shouldSpool` et code `scanXRefOffsets` récupéré avec retouches |
| Dire où sont les tests, s'ils compilent sans intervention, combien de corrections | **Couvert pour les cas retenus** | Dossiers, résultats et catégories de retouches détaillés; compter les **catégories documentées**, pas prétendre à un total exact de petites modifications |
| Expliquer et critiquer les tests IA, comparer leurs oracles aux oracles humains (20 %) | **Couvert** | Sections 3–5; points forts, échecs et limites des oracles |
| Installer et exécuter PITEST avec tests originaux puis nouveaux (15 %) | **Couvert** | 201 mutants dans les analyses initiale et successive |
| Calculer les scores avant/après et voir si tous les mutants sont détectés | **Partiellement couvert** | 50 % → 58 % établi; 84 mutants pas tués au total, ventilation finale à récupérer |
| Documenter les mutants détectés par les tests générés et **pourquoi** (15 %) | **À compléter** | Exemples de mécanismes expliqués, mais tableau précis mutants `KILLED` avant/après et tests responsables encore nécessaire |
| Tests manuels : nom, intention, choix des données, oracle (10 %) | **Couvert** | 9 tests `parse()` détaillés individuellement; autres ajouts annotés |
| Tous les nouveaux tests réussissent dans GitHub Actions (10 %) | **Non vérifié** | Succès local pour 12 tests; pas de preuve de réussite du workflow GitHub Actions |
| Un seul `README.md` à la racine du dépôt de tâche | **Document prêt, intégration à faire** | Copier ce fichier à l'emplacement demandé et retirer la documentation parallèle éventuelle |
| Nouvelle PR et liens vers repo + README | **À faire** | Ajouter les URL, ouvrir la deuxième PR selon le modèle du cours |

**Bilan :** les exigences techniques principales sont mises en œuvre et documentées. Les points prioritaires à terminer sont : (1) identifier précisément les mutants tués par les nouveaux tests, (2) prouver la réussite du pipeline GitHub Actions, (3) ajouter les liens GitHub et ouvrir la PR de remise.
