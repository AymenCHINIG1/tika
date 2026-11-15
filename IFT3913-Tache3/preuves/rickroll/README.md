# Rickroll après un échec de test

## Implémentation

L'action composite locale `.github/actions/rickroll/action.yml` appelle
`rickroll.sh`. Elle est réutilisée dans les deux jobs de build/tests du workflow
`tache2-tests.yml`. Elle affiche une scène ASCII et le lien de la vidéo dans les
logs, et ajoute un lien dans le résumé du job. Elle ne lance pas automatiquement
la vidéo dans le navigateur d'un lecteur ni de son sur un runner sans interface.

Chaque étape de tests possède un identifiant. L'appel à l'action exige
`failure()` et l'outcome `failure` d'une des étapes de tests concernées. Un échec
du contrôle Semgrep, de l'installation Java, du build préalable ou du dépôt d'un
rapport ne suffit donc pas à déclencher le rickroll. Dans le job core, les trois
étapes d'analyse de mutations sont également concernées. L'action est appelée
une seule fois par job. Son étape interne utilise `always()` pour s'exécuter même
si le job appelant est déjà en échec.

Les commandes Maven et les dépendances `needs: security` restent en place.
Aucun `continue-on-error` n'est ajouté : le rickroll ne transforme pas un échec
des tests en succès. La condition porte sur l'étape Maven ; un problème de
compilation ou d'environnement à l'intérieur de cette étape peut également la
faire échouer. La validation ci-dessous utilise bien une assertion JUnit.

Documentation de référence :
- https://docs.github.com/en/actions/tutorials/create-actions/create-a-composite-action
- https://docs.github.com/en/actions/reference/workflows-and-actions/expressions#failure

## Vérifications locales

- Syntaxe YAML de l'action et du workflow vérifiée.
- Syntaxe Bash vérifiée avec `bash -n`.
- Script exécuté localement : lien présent dans les logs et le résumé ; code 0.
  Copies dans `sortie-locale.txt` et `resume-local.md`.
- Identifiants des étapes, conditions et absence de `continue-on-error` vérifiés.
- Test temporaire `RegexCaptureParserManualTest.testRickrollValidation` : une
  assertion volontairement fausse. Exécution locale ciblée : 1 test, 1 échec,
  0 erreur technique, 0 test ignoré. Rapport résumé dans `test-local.json` et
  journal dans `test-local.txt`. Ce test est déjà sélectionné par la commande
  Maven core existante, qui inclut `RegexCaptureParserManualTest`.

Le script local ne simule pas le moteur de conditions de GitHub Actions : la
validation distante est encore nécessaire.

## Procédure de validation GitHub restante

1. L'utilisateur committe l'action, le workflow et le test volontairement en
   échec, puis pousse. Le push précédent contient déjà les sept vulnérabilités :
   Semgrep doit trouver zéro nouvelle détection sur ce push de validation et
   laisser les tests démarrer (sous réserve des règles téléchargées).
2. Vérifier l'échec de l'assertion dans le job core, l'exécution de l'action et la
   présence du lien dans le résumé. Le job doit rester rouge. Conserver le lien,
   les logs et une capture. Un échec Semgrep ne valide pas le rickroll.
3. Retirer uniquement la méthode temporaire `testRickrollValidation`, sans retirer
   l'action ni les sept vulnérabilités. L'utilisateur committe et pousse.
4. Vérifier que le job core n'exécute plus le rickroll si ses tests et analyses
   réussissent. Les sept protections affaiblies peuvent faire échouer d'autres
   tests, notamment PDF : un éventuel échec indépendant doit être analysé au lieu
   de prétendre que le workflow entier est vert.

**Important : le test volontairement faux doit être retiré après la démonstration.**
Les sept vulnérabilités de production sont conservées pour le devoir.
