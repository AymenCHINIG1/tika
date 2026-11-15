## Tâche 3 – Intégration de Semgrep

### Démarche
Intégration de Semgrep pour l'analyse statique de sécurité de cinq modules Apache Tika (core, serialization, PDF, OCR et Microsoft). Développement de deux scripts Python pour automatiser les analyses et comparer les détections, ainsi que d'un workflow GitHub Actions pour vérifier les Pull Requests vers tache3. Prise en compte des identifiants de règles et des déplacements de lignes pour limiter les faux positifs de comparaison.

### Méthode et résultats
Choix d'une **baseline dynamique** permettant d'analyser la version de référence et la version modifiée avec les mêmes règles Semgrep auto à chaque exécution.

**Résultats des tests locaux :**
- 17 détections dans la version de référence.
- 17 détections dans la version modifiée.
- 0 nouvelle détection après correction du problème de normalisation des identifiants.

### Etat du travail
- Cycle vert / rouge / vert valide sur les push avec la regle shell ciblee ; liens et captures dans `preuves/README.md`.
- Sept modifications vulnerables preparees ensemble et documentees dans [le dossier des sept vulnerabilites](preuves/sept-vulnerabilites/README.md).
- Compilation locale verifiee ; rapports avant/apres et comparaison conserves.
- Sept types confirmes dans le CI : 17 alertes avant, 27 apres, 10 nouvelles detections ; capture conservee.
- Action reutilisable de rickroll ajoutee ; validation locale et procedure dans [les preuves rickroll](preuves/rickroll/README.md). La demonstration distante est confirmee par capture ; le test volontairement faux a ensuite ete retire localement.
- Restent : validation distante apres retrait du test temporaire, validation distante de la correction PDF, validation via PR, finalisation et remise.

### Ajustement du controle d'injection de commande

L'essai dans `ExternalParser` a bloque le build, mais son retrait a aussi ete
classe comme une nouvelle alerte : la regle generale `command-injection-process-builder`
signale deja l'appel direct sans shell, et le comparateur traite prudemment une
alerte sur une ligne modifiee comme nouvelle.

Cette regle d'audit reste dans les rapports, mais devient informative pour le
blocage. La regle locale `java.lang.security.ift3913.dynamic-shell-command`
signale les commandes dynamiques transmises a un shell via les formes explicites
de `ProcessBuilder` et `command` prises en charge dans `regles-semgrep.yml`.
Les deux versions sont analysees avec les memes regles, en local et dans le CI.
Les regles locales et telechargees sont archivees avec les rapports du CI.

Ce choix cible l'injection par shell ; il ne couvre pas toutes les injections
d'arguments ou la selection d'un executable non fiable sans shell. La regle
locale ne prouve pas a elle seule que l'entree est controlable par un attaquant.
Les autres regles restent bloquantes. Le comparateur conserve son comportement
conservateur sur leurs alertes modifiees ; il ne determine pas si une modification
est une correction semantique.

Verification : sept tests de regression (`python IFT3913-Tache3/test_comparaison.py`)
et scan de trois copies du fichier, sans execution Java. La regle locale detecte
uniquement la copie avec injection. Un rejeu des rapports existants, enrichis du
resultat de ce scan cible, donne 0 / 1 / 0 nouvelle detection pour les etats avant,
injection et correction. Ce rejeu ne remplace pas une nouvelle execution complete
dans GitHub Actions. Les preuves et limites sont dans `preuves/README.md`.

### Compatibilite PDF apres les sept changements

Les trois erreurs de configuration PDF ont ete reproduites et attribuees au typage polymorphe global. Celui-ci est maintenant limite aux mappers construits avec une factory explicite ; le chemin JSON standard conserve son format. La faiblesse pedagogique reste detectee et verifiee sur ce chemin explicite. Le comparateur tolere desormais la seule indentation initiale, avec neuf tests de regression reussis. Voir [les preuves de correction](preuves/correction-pdf/README.md).

Verification PDF locale apres correction : 130 tests, zero echec, zero erreur, un ignore. Les sept types restent detectes ; la correction ne cree pas de nouvelle alerte.
