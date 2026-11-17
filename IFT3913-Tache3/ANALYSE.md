# Reproduire l'analyse Semgrep

Le snapshot automatique exact de l'execution GitHub Actions
`43758282959` est conserve, compresse sans perte, dans
`preuves/regles-auto/semgrep-auto-99fecfb3.yml.gz`.

Depuis la racine du depot, la comparaison peut etre reproduite avec :

```bash
python IFT3913-Tache3/analyser.py \
  --base-ref 07d8d790ded9325de482447069cde204b36d2360 \
  --repo . \
  --output IFT3913-Tache3/rapports-semgrep/reproduction \
  --rules-snapshot IFT3913-Tache3/preuves/regles-auto/semgrep-auto-99fecfb3.yml.gz \
  --rules-sha256 99fecfb388687fe4b079edae4657891bc1fed672a0cc33e095d848f2d300a02e
```

Semgrep 1.184.0 doit etre installe. Le script decompresse le snapshot, verifie
son SHA-256, applique les memes regles a la reference et au code candidat, puis
appelle `script-comparaison.py`. Le code de sortie est `0` sans nouvelle
detection, `1` avec de nouvelles detections et `2` en cas d'erreur technique.

Sans `--rules-snapshot`, le script telecharge une seule copie de
`https://semgrep.dev/c/auto` pour les deux scans. Ce mode ne reproduit pas
necessairement les rapports historiques, car le registre Semgrep evolue.
