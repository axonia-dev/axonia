#!/usr/bin/env bash
# The chart's gate, run by CI's selfhosted-edition-deploy job and by the pre-push hook: lint and render every example,
# validate what it renders against the oldest and newest Kubernetes the chart supports, and run the chart's unit tests.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tools.sh
. "$here/tools.sh"

helm="$(tool helm)"
kubeconform="$(tool kubeconform)"
untt="$(tool untt)"
schemas="https://raw.githubusercontent.com/yannh/kubernetes-json-schema/$KUBERNETES_SCHEMAS_COMMIT"
schemas="$schemas/{{.NormalizedKubernetesVersion}}-standalone{{.StrictSuffix}}/{{.ResourceKind}}{{.KindSuffix}}.json"
schema_cache="$TOOLS_DIR/kubernetes-schemas-$KUBERNETES_SCHEMAS_COMMIT"
mkdir -p "$schema_cache"

chart="$here/axonia"
rendered="$(mktemp -d)"
trap 'rm -rf "$rendered"' EXIT

for example in "$here"/examples/*.yaml; do
	name="$(basename "$example" .yaml)"
	echo "[chart] $name: lint"
	"$helm" lint "$chart" --strict --values "$example"
	echo "[chart] $name: render"
	"$helm" template axonia "$chart" --namespace axonia --values "$example" >"$rendered/$name.yaml"
	for version in 1.29.0 1.37.0; do
		echo "[chart] $name: validate against Kubernetes $version"
		"$kubeconform" -strict -summary -kubernetes-version "$version" -schema-location "$schemas" \
			-cache "$schema_cache" "$rendered/$name.yaml"
	done
done

echo "[chart] unit tests"
"$untt" "$chart"
