# IFT3913 — Tâche 2 : ChatUniTest et analyse de mutation d’Apache Tika pdf-parser

## Présentation du travail

Pour cette tâche, j’ai choisi la classe `org.apache.tika.parser.pdf.PDFParser` du module `tika-parser-pdf-module` d’Apache. Cette classe avait déjà une suite de tests assez importante, mais elle contenait encore plusieurs branches peu ou pas testées. Elle était aussi intéressante parce que sa méthode principale, `parse()`, regroupe plusieurs comportements importants : la gestion de la mémoire, l’OCR, le rendu des pages, les métadonnées, la restauration du `ParseContext` et la fermeture des ressources.


Le premier rapport PITEST conservé dans `IFT3913-Tache2/pit-reports-avant/` contenait 201 mutants :

- 101 mutants tués;
- 62 mutants survivants;
- 38 mutants sans couverture;
- une couverture des mutations de 50 %;
- une couverture des lignes de 78 %;
- une force des tests de 62 %.

J’ai donc choisi `PDFParser` parce qu’il y avait beaucoup de place pour améliorer les tests, en particulier autour de `parse()`.

## Environnement et configuration

Le travail a été réalisé sous Windows avec PowerShell, IntelliJ IDEA, Maven, JUnit 5, Mockito et Ollama, Codex. ChatUniTest a été ajouté au POM avec la configuration suivante :

```xml
<configuration>
  <apiKeys>ollama</apiKeys>
  <model>code-llama</model>
  <url>http://localhost:11434/v1/chat/completions</url>
  <thread>false</thread>
  <testNumber>1</testNumber>
</configuration>
```

Le nom `code-llama` était un alias Ollama, car ChatUniTest acceptait difficilement certains noms de modèles. Cet alias n’a pas toujours pointé vers le même modèle pendant les essais. Les modèles ouverts essayés localement ont été :

- Qwen 2.5 Coder 7B;
- CodeLlama 7B;
- CodeQwen 1.5 Chat.

Certains essais avaient une limite de 4096 tokens. Les tests acceptés par le plugin étaient placés dans `chatunitest-tests/`. Les réponses qui ne compilaient pas se retrouvaient dans les fichiers `records.json` sous `C:\tmp\chatunitest-info\`. Ces fichiers étant hors du dépôt, toutes les réponses brutes historiques ne sont pas disponibles dans Git, mais les tests retenus et les corrections sont conservés.

Exemple de commande utilisée pour une méthode :

```powershell
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module `
  io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1:method `
  '-DselectMethod=org.apache.tika.parser.pdf.PDFParser#shouldSpool'
```

Pour PITEST, le POM utilise `org.pitest:pitest-maven:1.22.0` et `pitest-junit5-plugin:1.2.3`. La seule classe mutée est `PDFParser`. Le périmètre et les mutateurs n’ont pas été réduits pour améliorer artificiellement le résultat : les analyses avant et après portent toujours sur les mêmes 201 mutants.

## Mon expérience avec ChatUniTest et les modèles Ollama

Dans l’ensemble, ChatUniTest a eu de la difficulté avec la majorité des méthodes de `PDFParser`. La classe est longue, plusieurs méthodes sont privées et son comportement dépend de PDFBox, du contexte Tika, du rendu, de l’OCR et de nombreux objets difficiles à construire correctement. Changer de modèle Ollama n’a pas réglé ce problème de manière générale.

Avec Qwen 2.5 Coder 7B, les résultats étaient meilleurs pour les petites méthodes avec peu de dépendances. CodeLlama 7B a aussi produit du code pour `scanXRefOffsets()`, mais ce code n’était pas directement compilable. CodeQwen 1.5 Chat a été essayé, sans produire de meilleur test utilisable pour les méthodes complexes. Pour `parse()`, plusieurs modèles ont surtout renvoyé une description JSON de la classe au lieu d’un fichier JUnit. Sur cinq réponses examinées, trois ont atteint la limite de 4096 tokens et aucune ne contenait une classe de test exploitable.

Les problèmes les plus fréquents étaient les suivants :

- réponses trop longues ou interrompues à la limite de tokens;
- JSON descriptif à la place de code JUnit;
- imports, constructeurs ou méthodes qui n’existaient pas;
- mauvaise utilisation des tableaux retournés par `Metadata.getValues()`;
- mocks inutiles qui faisaient échouer Mockito;
- scénarios construits avec de fausses données qui ne correspondaient pas au fonctionnement réel de PDFBox;
- tests sans véritable oracle, dont un corps de test vide;
- difficulté à appeler correctement les méthodes privées.

### Méthodes pour lesquelles j’ai obtenu un résultat utile

J’ai retenu trois méthodes avant l’ajout des tests manuels plus avancés :

| Méthode | Pourquoi elle a été choisie | Résultat de ChatUniTest | Catégorie finale |
|---|---|---|---|
| `shouldHandleXFAOnly()` | La condition combine deux booléens et avait un oracle simple et précis. C’était un bon cas pour vérifier si le modèle couvrait toutes les combinaisons. | Quatre scénarios corrects : vrai/vrai, vrai/faux, faux/vrai et faux/faux. Le code n’a pas nécessité de correction fonctionnelle, mais il n’a pas amélioré le score PITEST, car cette logique était déjà couverte par les tests existants. | **IA — génération ChatUniTest sans correction** |
| `shouldSpool()` | Cette méthode décide si le document doit être mis en cache selon le rendu, l’OCR, les images et les mises à jour incrémentales. Elle avait plusieurs conditions mutées et restait assez petite pour le modèle. | Le fichier a été généré, mais il contenait un stub Mockito inutile, une mauvaise injection du parser et un test vide. J’ai corrigé ces problèmes et ajouté une vraie assertion pour `RENDER_PAGES_AT_PAGE_END`. | **IA + humain** |
| `scanXRefOffsets()` | Le rapport PITEST montrait plusieurs mutants autour des offsets XRef et des métadonnées de mises à jour incrémentales. C’était un comportement observable avec un vrai PDF. | CodeLlama a produit du Java dans `records.json`, mais ChatUniTest n’a pas réussi à le compiler ni à l’exporter correctement. J’ai récupéré le code, puis je l’ai fortement corrigé. | **IA + humain** |

Le fichier `PDFParser_shouldHandleXFAOnly_21_0_Test.java` contient les quatre combinaisons booléennes. Le fichier `PDFParser_shouldSpool_8_0_Test.java` contient deux tests après correction. Le fichier `PDFParser_scanXRefOffsets_4_0_Test.java` contient trois tests après correction.

Pour `shouldSpool()`, j’ai dû supprimer le stub OCR qui provoquait `UnnecessaryStubbingException`, remplacer `@InjectMocks` par `new PDFParser()` et écrire le corps du test qui était vide avec un véritable `assertTrue`.

Pour `scanXRefOffsets()`, j’ai récupéré le code dans `records.json`, retiré un BOM UTF-8, supprimé les appels à des API `StartXRefOffset` inexistantes, utilisé la réflexion pour appeler la méthode privée et corrigé les assertions sur les `String[]`. Le changement le plus important a été de remplacer les offsets artificiels du modèle par des données provenant d’un véritable PDF, car la méthode lit les octets du document et non une liste inventée dans le `ParseContext`. Après le retrait du BOM, la version générée produisait encore 20 erreurs de compilation, ce qui montre que ce test ne peut pas être considéré comme une génération autonome réussie.

### Comparaison des problèmes selon le type de méthode

| Type de méthode | Ce qui s’est passé avec ChatUniTest | Ce qui a finalement été nécessaire |
|---|---|---|
| Petite logique booléenne (`shouldHandleXFAOnly`) | Génération correcte avec Qwen 2.5 Coder 7B | Vérifier la valeur avec des assertions simples; le test était toutefois redondant pour PITEST |
| Décision avec plusieurs configurations (`shouldSpool`) | Scénarios partiels et un test vide | Corriger Mockito, construire directement le parser et ajouter l’oracle manquant |
| Lecture interne d’un PDF (`scanXRefOffsets`) | API imaginées et données incompatibles avec le vrai comportement | Utiliser un vrai PDF, corriger les types et appeler la méthode privée par réflexion |
| Méthode longue et très dépendante (`parse`) | JSON, réponses tronquées et aucun JUnit utilisable avec les différents modèles | Concevoir les tests manuellement à partir des mutants PITEST |

Cette comparaison montre que ChatUniTest a surtout été utile comme point de départ pour des méthodes courtes. Pour les méthodes qui manipulent de vraies ressources PDF ou qui ont beaucoup de dépendances, le temps de correction était important et les oracles proposés n’étaient pas assez fiables.

## Tests écrits manuellement avant l’utilisation de Codex

Comme ChatUniTest ne réussissait pas à produire un test exploitable pour `parse()`, j’ai ajouté manuellement deux classes guidées par les premiers rapports PITEST.

`PDFParser_parse_ContextRestorationTest.java` contient trois tests. Ils vérifient que les objets présents dans le `ParseContext` sont restaurés après une analyse réussie ou après une exception, y compris lorsque les valeurs initiales sont nulles. Les oracles utilisent `assertSame`, `assertNull` et `assertThrows`, car il fallait vérifier l’identité exacte des objets restaurés et pas seulement l’absence d’exception.

`PDFParser_parse_MutationFocusedTest.java` contient six tests. Ils couvrent la restauration du contexte, `PDFRenderingState`, le spooling, la configuration KCMS et les différents chemins de configuration mémoire. Pendant leur mise au point, il a fallu ajouter l’import de `PDFRenderingState`, utiliser `clearInvocations(context)` pour ne pas mélanger les appels de préparation avec ceux de `parse()`, puis retirer une vérification incorrecte de `RenderResults.close()` dans un scénario qui n’atteignait pas cette fermeture.

Ces neuf tests étaient entièrement manuels. Ils ne sont pas présentés comme des générations de ChatUniTest. Après les tests ChatUniTest corrigés et ces premiers tests manuels, le meilleur résultat historique était de 117 mutants tués sur 201, soit 58 %.

## Amélioration réalisée avec Codex

Après cette première phase, j’ai utilisé Codex pour analyser systématiquement les fichiers XML et HTML de PITEST, repérer les mutants `SURVIVED` et `NO_COVERAGE`, puis proposer et exécuter des tests ciblés. Codex n’a pas modifié `PDFParser.java`. Son travail a porté sur les tests, leur mise au point, les exécutions Maven et la comparaison des rapports.

Deux nouvelles classes de tests manuels ont été ajoutées :

- `PDFParser_MutationCoverageTest.java`, avec 8 tests pour le spooling, le contenu marqué, les collections, les métadonnées, XFA, les signatures, les offsets XRef et l’initialisation du renderer;
- `PDFParser_ParseResourceTest.java`, avec 16 tests pour les ressources utilisées par `parse()`, les chemins mémoire, la fermeture, les exceptions, l’OCR, le contenu marqué, le rendu et les charges chiffrées.

Ces tests sont classés **MANUEL**, même lorsqu’ils ont été écrits avec l’aide de Codex, car ils ont été conçus après l’analyse des mutants et non générés par ChatUniTest. Chaque groupe a été compilé et exécuté avant de relancer PITEST. La progression observée a été la suivante :

| Étape | Mutants tués | Couverture des mutations | Couverture des lignes | Force des tests |
|---|---:|---:|---:|---:|
| Rapport initial | 101/201 | 50 % | 78 % | 62 % |
| Première progression | 104/201 | 52 % | non conservée | non isolée |
| Progression suivante | 110/201 | 55 % | 79 % | 67 % |
| Avant l’utilisation de Codex | 117/201 | 58 % | 80 % | 70 % |
| Premier groupe ciblé | 168/201 | 84 % | 86 % | 92 % |
| Deuxième groupe ciblé | 189/201 | 94 % | 95 % | 96 % |
| Troisième groupe ciblé | 197/201 | 98 % | 96 % | 99 % |
| Tests des chemins `finally` | 200/201 | 99 % | 96 % | 99 % |
| Résultat final | **201/201** | **100 %** | **96 % (345/358)** | **100 %** |

Le gain total par rapport au rapport initial est donc de 100 mutants tués et de 50 points de couverture des mutations. Le rapport final ne contient aucun mutant survivant, sans couverture ou en timeout. Les preuves sont conservées dans `IFT3913-Tache2/pit-reports-avant/mutations.xml` et `IFT3913-Tache2/pit-reports-apres/mutations.xml`.

Les nouveaux tests ont notamment tué des mutants dans les familles suivantes :

- restauration du contexte et des états de rendu;
- conditions de spooling et limites de mémoire;
- lecture des offsets XRef d’un PDF incrémental réel;
- contenu marqué, collections et XFA;
- extraction des signatures, permissions et métadonnées;
- choix du renderer et chargement depuis un fichier ou un flux;
- chemins OCR et rendu avant l’analyse;
- fermeture du document et de `RenderResults`, y compris lors d’exceptions;
- choix des noms Unicode ou de secours dans une charge chiffrée.

Le fichier XML final contient, pour chaque mutation, la méthode, la ligne, l’index bytecode, l’opérateur et le champ `killingTest`. Il faut cependant rester prudent : PITEST peut attribuer un mutant à un test existant exécuté avant le test spécialement conçu pour lui. Je n’attribue donc pas artificiellement chaque mutant à un seul test lorsque le rapport ne le démontre pas.

## Critique des tests produits

Les tests générés par ChatUniTest ont un avantage clair : pour une petite méthode, ils permettent d’obtenir rapidement une structure JUnit et plusieurs cas simples. Le meilleur exemple est `shouldHandleXFAOnly()`, où les quatre combinaisons des deux booléens sont correctes et faciles à comprendre.

Par contre, un test qui compile ou qui passe n’est pas nécessairement utile. Les quatre tests de `shouldHandleXFAOnly()` n’ont tué aucun mutant supplémentaire. Dans `shouldSpool()`, un test vide passait sans vérifier de comportement. Pour `scanXRefOffsets()`, le modèle avait inventé des API et un scénario qui ne correspondait pas à la manière dont la méthode lit un PDF. Ces cas montrent que la présence d’un `assertEquals` ne garantit pas que l’oracle soit pertinent.

Les tests humains et ceux conçus avec l’aide de Codex utilisent des oracles plus précis : identité des objets avec `assertSame`, métadonnées exactes, nombre d’appels Mockito, événements SAX, type du cache mémoire et fermeture réelle des ressources. Ils ont été guidés par les mutants encore vivants et leur efficacité a été confirmée en relançant PITEST.

Ces tests ont aussi une limite. Certains utilisent la réflexion, des spies Mockito ou des détails précis de PDFBox. Ils sont donc plus couplés à l’implémentation et pourraient demander des ajustements lors d’une future évolution de Tika. Le score de 100 % signifie que tous les mutants générés par la configuration actuelle sont détectés; il ne prouve pas que tous les défauts possibles de `PDFParser` sont couverts. La couverture de lignes reste d’ailleurs à 96 %, même si la couverture des mutations est de 100 %.

Mon bilan est donc que ChatUniTest a été utile pour démarrer sur quelques méthodes simples, mais qu’il n’a pas été suffisant pour cette classe. L’analyse humaine du comportement réel, l’utilisation de vrais documents PDF et les rapports PITEST ont été indispensables. Codex a ensuite permis d’automatiser une partie de cette analyse et d’atteindre le résultat final, mais les tests obtenus doivent toujours être relus et critiqués comme n’importe quel code généré.

## Validation et reproduction

Depuis la racine du dépôt, les commandes principales sont :

```powershell
.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module test

.\mvnw.cmd -pl tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module `
  test-compile org.pitest:pitest-maven:1.22.0:mutationCoverage `
  '-Dcheckstyle.skip=true'
```

Dans mon environnement PowerShell, le script du Maven Wrapper 3.3.4 échouait sur `(Get-Item ...).Target[0]`. Pour les validations finales, j’ai donc appelé directement Maven 3.9.12 déjà téléchargé par le wrapper, sans modifier le wrapper ni le projet.

Résultats locaux finaux :

- 130 tests exécutés dans le module;
- 0 échec et 0 erreur;
- 4 tests ignorés, dont des tests qui dépendent de Poppler;
- Checkstyle : 0 violation;
- Spotless : aucun changement nécessaire;
- PITEST : 201 mutants tués sur 201, soit 100 %;
- force des tests : 100 %.

## Historique et limites de l’expérience

Le premier commit de la tâche est `78539c02d` (`test: l'ajout de ChatUniTest tests, mutation et README`). Il a ajouté la configuration Maven, les trois fichiers issus des essais ChatUniTest, les deux premières classes de tests manuels, le README et le rapport PITEST initial. Les modifications actuelles complètent ce travail sans supprimer le rapport historique.

précision:
- toutes les réponses brutes de ChatUniTest n’ont pas été ajoutées au dépôt;
- les étapes intermédiaires à 52 % et 55 % n’ont pas chacune un rapport XML conservé;
- l’alias Ollama `code-llama` a changé de modèle pendant l’expérience;

Utilisation de l'ai:
Utilisation de codex après plusieurs tentatives (beaucoup) de génération de test avec ChatUniTest qui a réussi pour une seul méthode, pour la majorité il fournissait du code pas en java ou il compilait pas, aussi pour les test manuelles certaines méthodes sont plus complexe que d'autre d'où l'utilisation d'autre ai pour comprendre ce que fait la fonction et générer des test adéquat, ils s'agit de la 4 éme version du readme pour pdfparser reformater à l'aide de chatgpt pour une meilleure lisibilité et ne pas ometre de detail important.



