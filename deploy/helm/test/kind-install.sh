#!/usr/bin/env bash
# Installs the chart into a throwaway kind cluster with the images CI just built, the way an operator installs it, and
# proves what only a cluster can: the settings key is generated on the first install and survives an upgrade, an
# uninstall and a reinstall; the backend becomes ready (migrated, founded) as user 10001 on a read-only root filesystem
# with a writable media volume, beside a Service named axonia-mail; the edge reaches it with the shared secret. The
# settings key's lookup is first proven against a namespace that does not exist yet, then against one holding nothing.
# Takes the two local images in AXONIA_BACKEND_IMAGE and AXONIA_EDGE_IMAGE, each as repository:tag, and installs the chart
# with exactly those.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../tools.sh
. "$here/../tools.sh"

helm="$(tool helm)"
kind="$(tool kind)"
kubectl="$(tool kubectl)"

backend_image="${AXONIA_BACKEND_IMAGE:?set AXONIA_BACKEND_IMAGE to the backend image, repository:tag}"
edge_image="${AXONIA_EDGE_IMAGE:?set AXONIA_EDGE_IMAGE to the edge image, repository:tag}"
images=()

# The chart installs exactly the image kind loaded, whatever its name.
add_image() {
	local component="$1" image="$2"
	[[ "$image" =~ ^(.+):([^:/]+)$ ]] || {
		echo "[kind] $component's image must be repository:tag, not $image" >&2
		exit 1
	}
	images+=(--set-string "$component.image.repository=${BASH_REMATCH[1]}"
		--set-string "$component.image.tag=${BASH_REMATCH[2]}")
}
add_image backend "$backend_image"
add_image edge "$edge_image"

cluster="axonia-chart-$$"
namespace=axonia
work="$(mktemp -d)"
export KUBECONFIG="$work/kubeconfig"
forward_pid=""

cleanup() {
	[ -n "$forward_pid" ] && kill "$forward_pid" 2>/dev/null || true
	"$kind" delete cluster --name "$cluster" >/dev/null 2>&1 || true
	rm -rf "$work"
}
trap cleanup EXIT

fail() {
	echo "[kind] FAILED: $*" >&2
	"$kubectl" --namespace "$namespace" get pods -o wide >&2 || true
	"$kubectl" --namespace "$namespace" logs deployment/axonia-backend --tail 200 >&2 || true
	"$kubectl" --namespace "$namespace" logs deployment/axonia-edge --tail 50 >&2 || true
	exit 1
}

settings_key() {
	"$kubectl" --namespace "$namespace" get secret axonia-settings-key -o jsonpath='{.data.AXONIA_SETTINGS_KEY}' | base64 --decode
}

# A server-side dry run lists the namespace's Secrets as a real install does, and must render the settings key.
dry_run_renders_the_key() {
	"$helm" install axonia "$here/../axonia" --namespace "$namespace" --values "$here/kind-values.yaml" "${images[@]}" \
		--dry-run=server >"$work/dry-run.yaml" || fail "helm install --dry-run=server"
	grep -q '^  name: axonia-settings-key$' "$work/dry-run.yaml" || fail "no settings key rendered $1"
}

install() {
	"$helm" "$@" axonia "$here/../axonia" --namespace "$namespace" --values "$here/kind-values.yaml" "${images[@]}" \
		--wait --timeout 10m || fail "helm $1"
}

echo "[kind] cluster"
"$kind" create cluster --name "$cluster" \
	--image kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5 --wait 120s
"$kind" load docker-image --name "$cluster" "$backend_image" "$edge_image"

echo "[kind] the settings key's lookup, before the namespace exists and while it holds nothing"
dry_run_renders_the_key "for a namespace that does not exist"
"$kubectl" create namespace "$namespace"
[ -z "$("$kubectl" --namespace "$namespace" get secrets -o name)" ] || fail "a new namespace already holds a Secret"
dry_run_renders_the_key "for an empty namespace"

echo "[kind] the operator's Secret and database"
edge_secret="$(openssl rand -hex 32)"
"$kubectl" --namespace "$namespace" create secret generic axonia \
	--from-literal=DATABASE_OWNER_PASSWORD="$(openssl rand -hex 32)" \
	--from-literal=DATABASE_PASSWORD="$(openssl rand -hex 32)" \
	--from-literal=DATABASE_COMMUNICATION_READ_PASSWORD="$(openssl rand -hex 32)" \
	--from-literal=AXONIA_UNSUBSCRIBE_SECRET="$(openssl rand -hex 32)" \
	--from-literal=AXONIA_EDGE_SHARED_SECRETS="$edge_secret" \
	--from-literal=EDGE_SHARED_SECRET="$edge_secret" \
	--from-literal=SESSION_COOKIE_KEY="$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')" \
	--from-literal=OIDC_CLIENT_SECRET="$(openssl rand -hex 16)"
"$kubectl" --namespace "$namespace" apply --filename "$here/fixtures.yaml"
"$kubectl" --namespace "$namespace" rollout status deployment/postgres --timeout 180s

echo "[kind] first install"
install install
first_key="$(settings_key)"
[ "$(printf '%s' "$first_key" | base64 --decode | wc -c | tr -d ' ')" = 32 ] || fail "the generated settings key is not 32 bytes"
"$kubectl" --namespace "$namespace" logs deployment/axonia-backend | grep -q "Founded Organization kind" ||
	fail "the backend is ready but never logged its founding"

echo "[kind] the backend's user, its media volume, and both read-only root filesystems"
[ "$("$kubectl" --namespace "$namespace" exec deployment/axonia-backend -- id -u)" = 10001 ] || fail "the backend does not run as 10001"
"$kubectl" --namespace "$namespace" exec deployment/axonia-backend -- \
	sh -c 'touch /var/lib/axonia/media/.write-probe && rm /var/lib/axonia/media/.write-probe' ||
	fail "the backend cannot write its media volume"
for workload in axonia-backend axonia-edge; do
	"$kubectl" --namespace "$namespace" exec "deployment/$workload" -- \
		sh -c 'grep -qE "^[^ ]+ / [^ ]+ ro[, ]" /proc/mounts' || fail "$workload's root filesystem is not mounted read-only"
done

echo "[kind] through the edge"
"$kubectl" --namespace "$namespace" port-forward service/axonia-edge 18080:80 >"$work/forward.log" 2>&1 &
forward_pid=$!
for _ in $(seq 1 30); do
	curl --silent --output /dev/null http://localhost:18080/healthz && break
	sleep 1
done
[ "$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:18080/healthz)" = 200 ] || fail "/healthz"
[ "$(curl --silent --output /dev/null --write-out '%{http_code}' --header 'Accept: text/html' http://localhost:18080/communities)" = 200 ] ||
	fail "a deep link does not get the app"
# 401, not 403: the backend accepted the edge's stamp and asks for a sign-in.
[ "$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:18080/axonia/me)" = 401 ] ||
	fail "the edge does not reach the backend with the shared secret"
kill "$forward_pid"
wait "$forward_pid" 2>/dev/null || true
forward_pid=""

echo "[kind] upgrade"
install upgrade --set-string backend.podAnnotations.upgraded=yes
[ "$(settings_key)" = "$first_key" ] || fail "an upgrade changed the settings key"

echo "[kind] uninstall, then install again"
"$helm" uninstall axonia --namespace "$namespace" --wait
"$kubectl" --namespace "$namespace" get secret axonia-settings-key >/dev/null || fail "an uninstall deleted the settings key"
"$kubectl" --namespace "$namespace" get persistentvolumeclaim axonia-media >/dev/null || fail "an uninstall deleted the media volume"
install install
[ "$(settings_key)" = "$first_key" ] || fail "a reinstall changed the settings key"

echo "[kind] OK"
