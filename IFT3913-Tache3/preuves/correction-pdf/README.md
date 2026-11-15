# Correction de la compatibilité des configurations PDF

## Cause reproduite

Les tests `PDFParserTest.testConfiguringMoreParams`,
`testCustomGraphicsEngineFactory` et `testInitializationViaConfig` échouaient
tous lors du chargement de PDFParser depuis une configuration JSON.
L'exécution locale ciblée a reproduit les trois erreurs : le typage polymorphe
NON_FINAL activé globalement attendait START_ARRAY / As.WRAPPER_ARRAY, alors
que les configurations existantes commencent par un objet JSON START_OBJECT.
Voir `trois-tests-avant.txt`.

## Changement

`TikaObjectMapperFactory.createMapper(JsonFactory)` n'active désormais le typage
permissif que si une factory explicite non nulle est fournie. Le mapper JSON
standard, utilisé par ces trois tests, conserve son ancien format. La faiblesse
CWE-502 reste présente pour les appelants utilisant une factory explicite et des
entrées non fiables ; la démonstration inoffensive utilise maintenant ce chemin.
Ni les tests PDF ni leurs assertions n'ont été supprimés ou ignorés.

## Comparaison des alertes

L'ajout du bloc conditionnel indente la même instruction déjà signalée. Le
comparateur associe maintenant les lignes identiques hors indentation initiale,
avec des colonnes relatives à cette indentation. Il ne généralise pas aux lignes
dont le contenu change et ne décide pas si un changement de contexte est sûr.
Deux tests de régression supplémentaires vérifient ce cas et le maintien du
blocage lorsque l'instruction change. Total : 9 tests réussis, voir
`tests-comparateur.txt`.

Le scan final conserve 27 alertes, sans erreur, dont les sept types introduits.
Comparaison avec la version précédente : 27 / 27 / 0 nouvelle détection, voir
`comparaison-correction.json`. Les cinq vérifications de comportement restent
réussies, en passant par la factory explicite pour la désérialisation.

## Validation complète

Une première exécution locale a confirmé le succès des 65 tests de PDFParserTest
(dont un ignoré), mais d'autres tests ont rencontré une restriction locale de
l'instrumentation Mockito. Ces erreurs locales ne sont pas les trois erreurs de
configuration GitHub. La suite complète a ensuite été relancée avec l'accès
nécessaire à cette instrumentation ; son résultat final est consigné ci-dessous.

Aucun commit ni push n'a été effectué par l'assistant. La validation distante
après correction reste à effectuer par l'utilisateur.

### Resultat final

La suite PDF complete a termine avec BUILD SUCCESS : **130 tests, 0 echec, 0 erreur, 1 ignore** dans cet environnement local. Le nombre de tests ignores peut differer du CI selon les outils disponibles. Voir `suite-pdf-apres.txt` et `resultat-tests.json`. Les cinq verifications inoffensives de comportement restent reussies (`verification-comportements.txt`). Les neuf tests du comparateur reussissent. La validation GitHub apres cette correction reste a effectuer.
