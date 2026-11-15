#!/usr/bin/env bash
set -euo pipefail

video='https://www.youtube.com/watch?v=dQw4w9WgXcQ'
contexte="${RICKROLL_CONTEXTE:-Tests Tika}"

cat <<'DANCE'
     o/       \o       o
    /|         |\     /|\
    / \       / \     / \

  Le test a échoué. Rick n’a pas apprécié.
DANCE
printf '\nSuite : %s\n' "$contexte"
printf 'Votre recompense musicale : %s\n' "$video"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    printf '## Le test a échoué. Rick n’a pas apprécié.\n\n'
    printf '**Suite :** %s\n\n' "$contexte"
    printf '[▶ Ouvrir la surprise musicale](%s)\n\n' "$video"
    printf 'Rick sait chanter, mais pas encore corriger les tests : l’échec reste signalé.\n'
  } >> "$GITHUB_STEP_SUMMARY"
fi
