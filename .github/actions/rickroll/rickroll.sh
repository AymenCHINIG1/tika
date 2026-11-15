#!/usr/bin/env bash
set -euo pipefail

video='https://www.youtube.com/watch?v=dQw4w9WgXcQ'
contexte="${RICKROLL_CONTEXTE:-Tests Tika}"

cat <<'DANCE'
     o/       \o       o
    /|         |\     /|\
    / \       / \     / \

  Un test a chute. Rick, lui, garde le rythme.
DANCE
printf '\nSuite : %s\n' "$contexte"
printf 'Votre recompense musicale : %s\n' "$video"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    printf '## Un test a chute. Rick garde le rythme.\n\n'
    printf '**Suite :** %s\n\n' "$contexte"
    printf '[▶ Ouvrir la surprise musicale](%s)\n\n' "$video"
    printf 'La pause musicale ne corrige pas le test : son echec reste visible.\n'
  } >> "$GITHUB_STEP_SUMMARY"
fi
