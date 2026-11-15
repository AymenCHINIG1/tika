# Sept vulnérabilités pédagogiques

Les sept changements sont préparés ensemble dans les modules autorisés. Aucun
commit ni push n'a été effectué par l'assistant. Ces changements affaiblissent
volontairement les protections ; ils sont destinés au fork pédagogique.

## Référence et méthode

La référence précède les sept changements : dernier retour au vert consulté,
commit `ff4ba23e3e41796e6ed64b75e552870f423d8b16`. `reference.json` contient les
empreintes des sept sources locales sauvegardées avant modification ; leurs
copies sont dans `sources-reference/`. Aucun appel Git n'a été utilisé pour cette
sauvegarde. Les règles finales ont été préparées **avant** le scan `avant.json`.

Les cinq modules du workflow sont analysés dans les deux scans avec la même copie
des règles du registre (conservée localement dans le dossier ignoré
`rapports-semgrep/validation-correction-v2/auto-regles.yml`) et les mêmes règles
locales, copiées dans `regles-locales.yml`. L'empreinte et l'origine des règles du
registre sont dans `provenance-regles-auto.json` ; leur contenu brut n'est pas
committé à cause de la chaîne reconnue comme webhook dans une règle du registre.

Semgrep : 1.184.0 ; Java : OpenJDK 21.0.11 ; Maven : wrapper du projet.
Les rapports JSON gardent les identifiants, chemins, lignes et messages complets.
Le comparateur utilise les sources de référence pour associer les lignes
inchangées ; sa sortie est enregistrée dans `comparaison.json` et `comparaison.txt`.

## V1 — Injection de commande, CWE-78

**Fichier :** `tika-core/src/main/java/org/apache/tika/parser/external/ExternalParser.java`.

**Choix :** cette classe exécute réellement des programmes externes. Les arguments
séparés sont maintenant joints puis interprétés par `sh -c`. Un chemin de fichier
contenant des métacaractères peut donc devenir du code shell. Le scénario suppose
qu'un appelant fournit un chemin contrôlable à `TikaInputStream.get(Path)` puis au
parseur ; le nom original d'un document mis en cache dans un fichier aléatoire ne
suffit pas forcément à satisfaire cette condition.

**Détection :** règle locale `java.lang.security.ift3913.dynamic-shell-command`.
La règle générale ProcessBuilder reste informative, conformément à la politique
déjà documentée. **Vérification :** appel du vrai `ExternalParser` avec un nom
contenant un `printf` inoffensif ; le marqueur apparaît à la fin de stdout.
**Correction :** passer directement la liste d'arguments à ProcessBuilder.

## V2 — Entités XML externes, CWE-611

**Fichier :** `tika-core/src/main/java/org/apache/tika/utils/XMLReaderUtils.java`.

**Choix :** cette classe construit les analyseurs XML. La factory DOM développe
les entités, autorise les entités externes et l'accès aux DTD externes. Le résolveur
qui ignore ces entités a aussi été retiré de `getDocumentBuilder()` ; changer un
seul drapeau n'aurait pas suffi à prouver la faiblesse.

**Détection :** règle locale `xml-external-entities-enabled` et règles du registre
`documentbuilderfactory-external-general-entities-true` et
`documentbuilderfactory-external-parameter-entities-true`.
**Vérification :** le vrai builder inclut le contenu d'un fichier marqueur créé
dans un dossier temporaire. Aucun fichier privé n'est lu. Les autres chemins
(SAX, StAX, builders avec résolveur spécifique) ne sont pas tous affaiblis.
**Correction :** rétablir les restrictions et le résolveur.

## V3 — Traversée de répertoires, CWE-22

**Fichier :** `tika-core/src/main/java/org/apache/tika/io/FilenameUtils.java`.

**Choix :** `resolveWithin` est une API publique qui promet de confiner un nom à un
dossier. Elle retourne désormais le chemin normalisé sans vérifier le confinement
ni les liens symboliques. Une entrée `../outside.txt` sort du dossier autorisé.

**Détection :** règle locale `unchecked-path-resolution`.
**Vérification :** appel réel de l'API ; le résultat est en dehors du dossier.
La recherche n'a pas identifié d'appel interne de production dans les modules
inspectés : l'impact lecture/écriture suppose un appelant utilisant ce résultat
pour une opération sur fichier. Il ne faut pas prétendre avoir démontré une
écriture arbitraire dans un parseur existant. **Correction :** rétablir les deux
contrôles de confinement, lexical et réel.

## V4 — Désérialisation polymorphe sans restriction, CWE-502

**Fichier :** `tika-serialization/src/main/java/org/apache/tika/config/loader/TikaObjectMapperFactory.java`.

**Choix :** la factory fournit le mapper partagé des chemins de configuration.
Le typage NON_FINAL avec `LaissezFaireSubTypeValidator` autorise les données à
sélectionner des sous-types sans liste blanche. Après correction de la compatibilité
PDF, il est activé seulement lorsqu'une factory explicite non nulle est fournie ;
le mapper JSON standard reste compatible avec les configurations existantes.

**Détection :** règle locale `unrestricted-polymorphic-deserialization`.
**Vérification :** le vrai mapper, construit avec une factory explicite, instancie une classe marqueur du programme de
validation, choisie par l'entrée JSON. Cela prouve le choix du type et l'exécution
de son constructeur ; cela ne prouve pas une exécution de commande arbitraire.
Un impact plus grave dépend des classes présentes et des points d'entrée
acceptant des configurations non fiables. Le format polymorphe diffère du JSON habituel sur ce chemin explicite.
Le problème initial de compatibilité des configurations standard est corrigé et
documenté dans `../correction-pdf/README.md`. **Correction :** supprimer le typage global ou
employer une liste explicite de types autorisés.

## V5 — Validation TLS désactivée, CWE-295

**Fichier :** `tika-core/src/main/java/org/apache/tika/io/TikaInputStream.java`.

**Choix :** la méthode récupère réellement une URL. Pour HTTPS, elle installe un
TrustManager dont les contrôles de certificats sont vides. Un certificat non
approuvé peut alors être accepté. Le changement est limité à cette connexion,
sans modifier les paramètres TLS globaux. TLS 1.3 reste utilisé ; la faiblesse
est la confiance, pas un ancien protocole.

**Détection :** règle du registre `insecure-trust-manager` (deux alertes, un seul
type de vulnérabilité). **Vérification :** compilation et analyse statique ; aucune
interception réseau n'a été effectuée. La vérification du nom d'hôte n'est pas
désactivée ; un scénario d'usurpation doit aussi satisfaire cette vérification.
**Correction :** conserver le TrustManager standard.

## V6 — Permissions trop larges, CWE-732

**Fichier :** `tika-core/src/main/java/org/apache/tika/io/TemporaryResources.java`.

**Choix :** les fichiers temporaires peuvent contenir le document en cours de
traitement. Après leur création, `setReadable(true, false)` élargit la lecture à
tous les utilisateurs, si le système et les permissions du propriétaire le
permettent.

**Détection :** règle locale `world-readable-temporary-file`.
**Vérification :** le fichier créé par la vraie API possède le bit POSIX
OTHERS_READ. Le risque de lecture par un autre utilisateur nécessite aussi des
dossiers parents traversables, par exemple un dossier temporaire partagé. Une
arborescence parent privée limite l'impact. Aucun accès par un autre compte n'a
été testé. **Correction :** ne pas élargir les permissions initiales.

## V7 — Mot de passe de document dans les logs, CWE-532

**Fichier :** `tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module/src/main/java/org/apache/tika/parser/pdf/PDFParser.java`.

**Choix :** le parseur obtient le mot de passe via son PasswordProvider. Le code
ajouté l'envoie directement au logger INFO avant l'ouverture du document, y
compris si cette ouverture échoue ensuite. Un lecteur des logs peut connaître ce
secret si un mot de passe non vide est fourni et que INFO est activé.

**Détection :** règle locale `document-password-in-log`, associant le résultat de
`getPassword(...)` à l'appel INFO. **Vérification :** compilation et analyse
statique ; pas de test PDF complet avec collecte des logs dans cette étape.
**Correction :** supprimer la valeur sensible du message.

## Vérifications et limites

La compilation des modules modifiés et de leurs prérequis réussit, sans désactiver
Checkstyle :

```bash
./mvnw -o -B -pl tika-core,tika-serialization,tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module -am compile -DskipTests=true -DskipITs=true -Dossindex.skip=true --no-transfer-progress
```

Le journal est `compilation.txt`. Les cinq vérifications de comportement
inoffensives sont dans `verification-comportements.txt` et se reproduisent depuis
la racine avec `python3 IFT3913-Tache3/verification/verifier.py`, après compilation.
Elles ne nécessitent pas Git. Les dépendances du script correspondent à cette
version du projet et doivent être déjà dans le cache Maven.

La suite complète de tests n'a pas été exécutée. Des tests de sécurité existants
peuvent échouer précisément parce que les protections ont été retirées. Le CI
doit bloquer sur les nouvelles détections avant les builds ; la compilation a
donc été contrôlée séparément en local. La validation GitHub de ces sept
changements, et le rickroll, restent à réaliser par l'utilisateur.

Les règles ajoutées ont été vérifiées sur les versions avant et après ; ce sont
des règles ciblées pour ces formes de code, pas une couverture exhaustive de
chaque famille de vulnérabilités. Les rapports distinguent les alertes des sept
types : plusieurs alertes peuvent correspondre au même type.

Ces modifications, règles, vérifications et explications ont été préparées avec
l'aide de l'IA ; cette utilisation doit être déclarée dans la remise finale.

## Resultat final mesure

Reference : **17** alertes ; apres : **27** alertes ; **10** nouvelles detections pour les **7 types**. Aucune erreur Semgrep dans les deux scans. Les 6 regles locales apparaissent uniquement apres modification ; TLS est signale par la regle du registre. Les alertes supplementaires XXE et TLS ne sont pas des types additionnels.

## Validation GitHub des sept modifications

- Execution : https://github.com/AymenCHINIG1/tika/actions/runs/43675203088
- Commit : `fc77859b6f12094e927e93d5acf6b5b312441a3c`, branche `aymen/tache3`, evenement push.
- Resume public consulte : controle Semgrep en echec avec code 1 ; les deux jobs regex-capture-parser et pdf-parser sont ignores. Duree totale : 2 min 1 s.
- Archive affichee : `rapports-semgrep-tache3`, 343 KB ; SHA-256 `2cfc094ea40f9d42dc8a1cf5a78dd756c0f825df05cb1f1c5795f54d1eca1b02`. Non telechargee.
- Les logs detailles et le nombre exact de nouvelles detections dans le CI restent a confirmer par capture ou rapport fourni par Aymen. Le resultat local mesure est 17 / 27 / 10.

### Confirmation par capture des resultats distants

La capture `sept-vulnerabilites/github-sept-vulnerabilites-comparaison.png`, fournie par Aymen pour l’execution 43675203088, confirme : reference 17, candidat 27, nouvelles 10, echec avec code 1. Les dix alertes couvrent les sept types : traversal (1), permissions (1), TLS (2), injection shell (1), XXE (3), mot de passe dans les logs (1), deserialisation polymorphe (1). Les nombres et les types correspondent au resultat local. Cette capture leve l’incertitude precedente sur les logs distants de cette execution.
