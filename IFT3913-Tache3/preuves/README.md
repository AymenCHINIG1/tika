# Preuves de validation — tâche 3

## État au 14 novembre 2026

Les rapports copiés ici sont des résultats locaux. Ils ne sont pas les archives téléchargées depuis GitHub Actions. Les copies conservent les règles utilisées pour chaque paire de scans. `sha256.json` contient les empreintes des rapports, des règles et de la capture archivés.

### Validation initiale locale

Dossier : `validation-initiale-locale/`.

- Semgrep 1.184.0.
- Référence : 17 alertes, aucune erreur d'analyse.
- Candidat : 17 alertes, aucune erreur d'analyse.
- Comparaison : 0 nouvelle détection, succès.
- Référence demandée au script : `origin/tache3` (SHA récupéré au début de la session : `07d8d790ded9325de482447069cde204b36d2360`). Les rapports ne consignent pas eux-mêmes le SHA effectivement analysé.

### GitHub Actions — sans nouvelle vulnérabilité

- Exécution : https://github.com/AymenCHINIG1/tika/actions/runs/43645617417
- Commit : `21ed03f1fa86e0cf1b29230bc927080fc9696df6`.
- Branche : `aymen/tache3`, événement : push.
- Résumé public consulté : contrôle Semgrep réussi, jobs `regex-capture-parser` et `pdf-parser` réussis.
- Archive affichée : `rapports-semgrep-tache3` ; trois archives PIT également présentes.
- Les nombres précis de détections de cette exécution n'ont pas été vérifiés dans ses logs.

### Validation locale de l'injection temporaire

Dossier : `validation-injection-locale/`.

- Fichier modifié : `tika-core/src/main/java/org/apache/tika/parser/external/ExternalParser.java`.
- Modification : remplacement de `new ProcessBuilder(thisCommandLine)` par `new ProcessBuilder("sh", "-c", String.join(" ", thisCommandLine))`.
- Référence : 17 alertes ; candidat : 17 alertes ; erreurs d'analyse : 0 dans les deux rapports.
- Comparaison : 1 nouvelle détection, sur la ligne modifiée (193, colonne 21).
- Règle : `java.lang.security.audit.command-injection-process-builder.command-injection-process-builder` ; sévérité : ERROR.
- Limite : l'ancien appel était déjà signalé. Cet essai démontre la classification d'une alerte sur du code modifié comme nouvelle, et non une augmentation du nombre total d'alertes.

### GitHub Actions — blocage après injection

- Exécution : https://github.com/AymenCHINIG1/tika/actions/runs/43652245331
- Commit : `eb9c1dcace217cd16ca9cd0d49dce0e2704edd26`.
- Branche : `aymen/tache3`, événement : push.
- Contrôle Semgrep en échec ; les deux jobs de build/tests sont ignorés.
- Capture fournie par Aymen : `github-injection-echec.png`.
- La capture confirme : référence 17, candidat 17, nouvelles 1 ; règle d'injection de commande sur `ExternalParser.java:193:21` ; message `ECHEC: nouvelles detections` ; code de sortie 1.
- L'archive `rapports-semgrep-tache3` est affichée dans le résumé public malgré l'échec.

## Rapport historique d'Anas

Le fichier `../rapports-semgrep/baseline.json` contient 11 alertes et un avertissement d'analyse partielle, avec des fichiers hors du périmètre des cinq modules du workflow actuel. Il est conservé comme historique ; il ne doit pas être présenté comme le rapport de référence des essais à 17 alertes ci-dessus.

## Preuves restant à collecter

- Télécharger et archiver les rapports GitHub Actions des deux exécutions, avant leur expiration (rétention configurée à 90 jours).
- Retirer la modification temporaire et conserver le lien et les rapports du retour au vert.
- Valider aussi le déclenchement via une PR vers `tache3`.
- Pour les sept vulnérabilités finales : conserver la référence exacte, le rapport avant, le rapport après et la comparaison, les règles utilisées, les SHA et les justifications par vulnérabilité.
- Valider le rickroll sur un échec de test et son absence lorsque les tests réussissent.

L'aide de l'IA à la préparation des modifications et de cette documentation devra être déclarée conformément aux consignes du cours.

## Limite confirmee lors du retrait de l’injection

La comparaison locale contre `eb9c1dcace217cd16ca9cd0d49dce0e2704edd26` retourne encore 17 alertes de chaque cote et 1 nouvelle detection sur `ExternalParser.java:193:21`, apres retablissement de l’appel sans shell. Le comparateur ne fait correspondre que les lignes textuellement inchangees : il classe donc ici une alerte sur une ligne corrigee comme nouvelle. Ce resultat ne signifie pas que l’injection shell subsiste. Voir `validation-correction-locale/`. Le retour au vert n’est pas valide ; le controle doit etre revu avant de presenter la validation comme complete.

## Ajustement de la politique et verification locale

La regle generale ProcessBuilder reste dans les rapports mais devient informative pour le blocage. Une nouvelle regle locale cible les commandes dynamiques via un shell ; les autres regles ne sont pas ignorees. Cette politique cible le scenario shell, pas toutes les injections d’arguments ou d’executables. Le comparateur reste conservateur pour les autres regles.

Dans `validation-regle-shell/` : scan reel de trois copies d’ExternalParser (seule l’injection est signalee par la regle locale), sept tests de regression reussis et rejeu des rapports historiques enrichis du resultat cible (0 / 1 / 0 nouvelle detection). Le rejeu n’est pas trois scans complets. Un scan complet reel de la version corrigee sur les cinq modules donne 17 alertes, aucune erreur et aucune alerte de la regle shell. Une nouvelle validation GitHub Actions avec cette configuration reste necessaire.

## Comparaison complete apres correction de la politique

Execution locale de `analyser.py --base-ref eb9c1dcace217cd16ca9cd0d49dce0e2704edd26 --output IFT3913-Tache3/rapports-semgrep/validation-correction-v2` : reference 18 alertes, candidat 17, nouvelles 0, succes. Les rapports complets et les deux fichiers de regles sont archives dans `validation-correction-v2-locale/`. La validation distante du retour au vert reste a effectuer.

## Archivage des regles et protection GitHub

Le push du commit local `bd13d4da7687950251f8d55ba82b1eee8ff78031` a ete rejete : une exclusion `pattern-not` de la regle Semgrep `generic.secrets.security.detected-slack-webhook.detected-slack-webhook`, telechargee depuis le registre, contient une chaine reconnue comme webhook Slack. Les copies brutes `auto-regles.yml` ont ete retirees du dossier de preuves destine au depot, sans modifier les regles executees. Chaque copie est remplacee par sa provenance et son empreinte SHA-256. Les copies de travail restent dans `rapports-semgrep/`, desormais ignore localement par Git. Les rapports JSON, captures, regles locales et resultats restent conserves. Les anciennes mentions de copies auto dans ce document decrivent leur collecte initiale ; elles sont remplacees ici par les fichiers de provenance. Ne pas contourner la protection GitHub.
