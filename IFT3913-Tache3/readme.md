## Tâche 3 – Intégration de Semgrep

### Démarche
Intégration de Semgrep pour l'analyse statique de sécurité de cinq modules Apache Tika (core, serialization, PDF, OCR et Microsoft). Développement de deux scripts Python pour automatiser les analyses et comparer les détections, ainsi que d'un workflow GitHub Actions pour vérifier les Pull Requests vers tache3. Prise en compte des identifiants de règles et des déplacements de lignes pour limiter les faux positifs de comparaison.

### Méthode et résultats
Choix d'une **baseline dynamique** permettant d'analyser la version de référence et la version modifiée avec les mêmes règles Semgrep auto à chaque exécution.

**Résultats des tests locaux :**
- 17 détections dans la version de référence.
- 17 détections dans la version modifiée.
- 0 nouvelle détection après correction du problème de normalisation des identifiants.

### Tests restants
- Introduction volontaire de vulnérabilités pour vérifier leur détection.
- Tests des déplacements de lignes et de la gestion des erreurs techniques.
- Validation du workflow sur GitHub Actions.
- Rickroll
