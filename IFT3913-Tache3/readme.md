# Tâche 3 — Tests de sécurité de Tika

Aymen Chinig et Anas El Farkh

## Le workflow

On a commencé par modifier les deux workflows pour que le [workflow de build](../.github/workflows/tache2-tests.yml) appelle le [workflow Semgrep](../.github/workflows/workflow-tache3-semgrep.yml) à chaque push et PR. Les deux jobs de build/tests attendent son succès pour démarrer. La première exécution a réussi : Semgrep passait, puis les tests se lançaient.

On voulait ensuite vérifier que le build échoue si on ajoute une nouvelle vulnérabilité. Pour faire la différence avec les alertes déjà présentes, on analyse le code avant et après avec les mêmes règles. Pour un push, on compare avec le commit précédent. Pour une PR, on compare avec le commit de la branche cible. Le comparateur tient aussi compte des lignes déplacées ou réindentées pour ne pas les compter comme de nouvelles alertes. Les rapports restent disponibles même quand le workflow échoue.

## Le premier test avec une injection de commande

Pour choisir les vulnérabilités à introduire, on s’est appuyés sur trois conditions :

- **Elle est détectable** par les règles utilisées dans le workflow.
- **Elle correspond au rôle du fichier**, comme une injection de commande dans une classe qui lance des programmes externes.
- **Elle est nouvelle et explicable** : on peut montrer ce qu’on a changé, pourquoi c’est vulnérable et ce que Semgrep détecte.

On a choisi de commencer par une injection de commande car Tika lance déjà des outils externes : on pouvait modifier un appel existant et vérifier directement le blocage du build.

On a ensuite retiré l’injection pour voir si tout repassait au vert. Sauf que la règle générale signalait aussi l’appel initial, même sans shell. Le comparateur prenait donc la correction pour une nouvelle alerte. On a gardé cette règle comme information dans les rapports et ajouté une [règle ciblée](regles-semgrep.yml) qui bloque les commandes dynamiques exécutées par un shell. Les autres règles restent bloquantes.

Après ça, le résultat était celui attendu : l’injection bloque le build, et son retrait permet de repasser au vert. On l’a ensuite remise avec les six autres vulnérabilités.

## Les sept vulnérabilités

- **Injection de commande — CWE-78.** Dans [ExternalParser.java][external], les arguments séparés sont remplacés par une chaîne exécutée avec `sh -c`. Un chemin contrôlable contenant des caractères spéciaux peut alors être interprété comme une commande. On l’a vérifié avec un marqueur inoffensif. **Semgrep détecte une nouvelle alerte.**

- **XXE — CWE-611.** Dans [XMLReaderUtils.java][xml], on a activé les entités XML externes et retiré le résolveur qui les bloquait dans `getDocumentBuilder()`. Ce fichier prépare les analyseurs XML, donc le changement touche directement la lecture des documents. Un XML peut maintenant récupérer une ressource externe : on l’a vérifié en faisant lire un fichier temporaire. **Semgrep détecte trois nouvelles alertes.**

- **Traversée de répertoires — CWE-22.** Dans [FilenameUtils.java][filename], on a retiré les contrôles de `resolveWithin`, qui doit normalement garder un chemin dans le dossier autorisé. Avec `../outside.txt`, le chemin retourné sort maintenant de ce dossier. Ça devient dangereux si l’appelant utilise ce chemin pour lire ou écrire un fichier. **Semgrep détecte une nouvelle alerte.**

- **Désérialisation non fiable — CWE-502.** On a modifié [TikaObjectMapperFactory.java][mapper], qui construit les mappers de configuration. Quand une factory explicite est utilisée, les données peuvent maintenant choisir un sous-type sans liste blanche. On a vérifié qu’un JSON pouvait faire instancier une classe marqueur présente dans l’application. Ça confirme le choix de la classe par les données, pas une exécution de commande arbitraire. **Semgrep détecte une nouvelle alerte.**

- **Validation des certificats TLS — CWE-295.** Dans [TikaInputStream.java][stream], qui récupère les ressources par URL, on a ajouté un TrustManager avec des contrôles vides. Une connexion HTTPS peut donc accepter un certificat non approuvé. Le contrôle du nom d’hôte est toujours présent, mais celui du certificat ne protège plus la connexion. **Semgrep détecte deux nouvelles alertes.**

- **Permissions trop larges — CWE-732.** On a choisi [TemporaryResources.java][temporary] car les fichiers temporaires peuvent contenir les documents traités. On a ajouté `setReadable(true, false)`, ce qui les rend lisibles par les autres utilisateurs locaux. Il faut aussi que les dossiers parents soient accessibles pour qu’ils puissent les lire. On a vérifié que la permission de lecture était bien élargie. **Semgrep détecte une nouvelle alerte.**

- **Mot de passe dans les logs — CWE-532.** Dans [PDFParser.java][pdf], on a ajouté un message INFO qui affiche le mot de passe avant l’ouverture du document. Ce parseur le reçoit déjà pour lire les PDF protégés. Si un mot de passe est fourni et que les logs INFO sont actifs, une personne qui lit les logs peut le récupérer. **Semgrep détecte une nouvelle alerte.**

On a complété les règles du registre avec des règles locales adaptées à ces changements.

## Ce que donnent les scans et les tests

On obtient **17 alertes avant les modifications**, puis **27 après**, soit **10 nouvelles détections pour sept types de vulnérabilités**. Ce n’est pas une erreur dans le compte : plusieurs règles signalent les mêmes faiblesses XML et TLS.

- [Rapport avant les modifications](preuves/avant.json).
- [Rapport après les modifications](preuves/apres.json).
- [Comparaison des deux rapports, avec les règles et les lignes signalées](preuves/comparaison.json).

Sur [l’exécution avec les sept vulnérabilités](https://github.com/AymenCHINIG1/tika/actions/runs/43675203088), Semgrep passe au rouge et les deux builds sont ignorés.

Les modules modifiés compilent et les [vérifications locales](preuves/verification.txt) réussissent. Pour TLS et les logs, on a vérifié le code et la détection, sans simuler une interception HTTPS ni un scénario complet de fuite de mot de passe.

On a aussi rencontré trois erreurs dans les tests PDF. Notre changement de désérialisation modifiait le format attendu des configurations JSON habituelles. On l’a donc limité au chemin avec une factory explicite : la vulnérabilité reste présente sur ce chemin et les configurations standard fonctionnent à nouveau. Les [tests locaux](preuves/tests.json) passent après cette correction.

## Le rickroll

Pour la partie humour, on a ajouté une [action réutilisable](../.github/actions/rickroll/action.yml) qui se lance quand une étape de tests ou d’analyse de mutations échoue. Elle met un lien vers la vidéo dans les logs et le résumé du job, avec ces messages :

> Le test a échoué. Rick n’a pas apprécié.
>
> Rick sait chanter, mais pas encore corriger les tests : l’échec reste signalé.

On a ajouté temporairement une assertion fausse dans un test pour vérifier que ça se déclenche bien. Sur [cette exécution](https://github.com/AymenCHINIG1/tika/actions/runs/43677310775), le message et le lien apparaissent, et le job reste rouge ([capture](preuves/rickroll.png)). On a ensuite retiré le test volontairement faux. Si c’est Semgrep ou le build préalable qui échoue, le rickroll ne se lance pas.

Sur [la dernière exécution](https://github.com/AymenCHINIG1/tika/actions/runs/43679221888), tout est vert : Semgrep, les tests core, les mutations et les tests PDF. Les sept vulnérabilités sont toujours là, mais ce dernier push n’en ajoute pas par rapport au précédent.

Sur la [PR vers `tache3`](https://github.com/AymenCHINIG1/tika/pull/7), le [contrôle Semgrep échoue](https://github.com/AymenCHINIG1/tika/actions/runs/43758282959) avec les dix nouvelles détections attendues et les deux builds sont ignorés ([capture](preuves/semgrep.png)).

## Utilisation de l’IA

On a utilisé ChatGPT pour nous aider sur le code, et nous guider dans les blocages et leurs solutions. Les rapports et les exécutions liés dans ce README montrent les résultats obtenus.

[external]: ../tika-core/src/main/java/org/apache/tika/parser/external/ExternalParser.java
[xml]: ../tika-core/src/main/java/org/apache/tika/utils/XMLReaderUtils.java
[filename]: ../tika-core/src/main/java/org/apache/tika/io/FilenameUtils.java
[mapper]: ../tika-serialization/src/main/java/org/apache/tika/config/loader/TikaObjectMapperFactory.java
[stream]: ../tika-core/src/main/java/org/apache/tika/io/TikaInputStream.java
[temporary]: ../tika-core/src/main/java/org/apache/tika/io/TemporaryResources.java
[pdf]: ../tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module/src/main/java/org/apache/tika/parser/pdf/PDFParser.java
