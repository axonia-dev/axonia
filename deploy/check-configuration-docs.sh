#!/usr/bin/env bash
# Every variable an installation can be given is named in the first cell of a table row of
# docs/selfhosted/configuration.md: each one the backend's configuration files read, each one the edge reads, each one
# the compose file sets for the backend or the edge or reads from .env, and each one the Helm chart renders. In reverse,
# every name in such a first cell comes from one of those sources, so a renamed variable leaves no stale row. A source
# that yields no name fails the check, so a moved file or a changed pattern cannot turn it vacuous. Run by CI's
# selfhosted-edition-deploy job and by the pre-push hook.
set -Eeuo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=helm/tools.sh
. "$root/deploy/helm/tools.sh"
helm="$(tool helm)"
docs="$root/docs/selfhosted/configuration.md"

from_properties() {
	cat "$root/backend/core/src/main/resources/axonia-core.properties" \
		"$root/backend/selfhosted-app/src/main/resources/axonia-selfhosted.properties" |
		grep -vE '^[[:space:]]*#' | grep -oE '\$\{[A-Z][A-Z0-9_]*' | sed 's/^\${//'
}

from_edge() {
	sed -n '/^const REQUIRED = \[/,/\]/p' "$root/selfhosted-edge/src/config.ts" | grep -oE '"[A-Z][A-Z0-9_]*"' | tr -d '"'
	grep -oE 'name: "[A-Z][A-Z0-9_]*"' "$root/selfhosted-edge/src/config.ts" | sed 's/name: //' | tr -d '"'
}

# The keys compose sets in a container, and the names an operator sets in .env that compose reads (${NAME...}). The
# Postgres container's own settings are the image's, not axonia's.
from_compose() {
	local compose="$root/deploy/compose.yaml"
	grep -oE '^ +(- )?[A-Z][A-Z0-9_]*[:=]' "$compose" | sed -E 's/^ +(- )?//; s/[:=]$//' | grep -v '^POSTGRES_'
	grep -vE '^[[:space:]]*#' "$compose" | grep -oE '\$\{[A-Z][A-Z0-9_]*' | sed 's/^\${//'
}

# Every optional block on, once per media store, so no variable depends on what an example happens to set.
from_chart() {
	local media
	for media in local s3 azure-blob; do
		"$helm" template axonia "$root/deploy/helm/axonia" --values "$root/deploy/helm/test/every-variable.yaml" \
			--set media.type="$media"
	done | grep -oE '^ +- name: [A-Z][A-Z0-9_]*$' | sed -E 's/^ +- name: //'
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# set -E carries the trap into each pipeline's subshells too; only the shell itself says it, once.
trap '[ "$BASH_SUBSHELL" -eq 0 ] && echo "check-configuration-docs.sh: reading the $source failed" >&2' ERR

for source in properties edge compose chart; do
	"from_$source" >"$work/$source"
	[ -s "$work/$source" ] || { echo "check-configuration-docs.sh: no variable read from the $source" >&2; exit 1; }
done
names="$(sort -u "$work"/*)"

missing=""
for name in $names; do
	grep -qE "^\| [^|]*\`$name\`" "$docs" || missing="$missing $name"
done

if [ -n "$missing" ]; then
	echo "docs/selfhosted/configuration.md names none of:$missing" >&2
	exit 1
fi

documented="$(grep -E '^\| ' "$docs" | cut -d'|' -f2 | grep -oE '`[A-Z][A-Z0-9_]*`' | tr -d '`' | sort -u)"
stale=""
for name in $documented; do
	printf '%s\n' "$names" | grep -qx "$name" || stale="$stale $name"
done

if [ -n "$stale" ]; then
	echo "docs/selfhosted/configuration.md has a row for what no source reads:$stale" >&2
	exit 1
fi
echo "[docs] configuration.md names all $(echo "$names" | wc -l | tr -d ' ') variables, and no other"
