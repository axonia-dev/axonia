# Pinned tools for the chart's checks, downloaded once into a cache and verified against the checksums below, so CI
# and a developer machine run the same versions. Sourced by check.sh and test/kind-install.sh.

HELM_VERSION=4.3.0
KUBECONFORM_VERSION=0.8.0
HELM_UNITTEST_VERSION=1.1.2
KIND_VERSION=0.33.0
KUBECTL_VERSION=1.37.1
# The commit of yannh/kubernetes-json-schema kubeconform validates against, rather than its moving master; move it
# forward to validate against a newer Kubernetes.
KUBERNETES_SCHEMAS_COMMIT=8df8a883b68a24a104b4a9e43c1288090ae60b3b

TOOLS_DIR="${AXONIA_TOOLS_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/axonia-deploy-tools}"

platform() {
	case "$(uname -s)/$(uname -m)" in
	Linux/x86_64) echo linux-amd64 ;;
	Darwin/arm64) echo darwin-arm64 ;;
	*)
		echo "No pinned tools for $(uname -s)/$(uname -m); add its checksums to deploy/helm/tools.sh." >&2
		return 1
		;;
	esac
}

checksum() {
	case "$1/$2" in
	helm/linux-amd64) echo 86584a54def73570558f66f5111cc53dfed56689637ae32c1201205d494f54fb ;;
	helm/darwin-arm64) echo d3870437e1e95b67f8edbde964156c84a26503f560821d40c542441658934fba ;;
	kubeconform/linux-amd64) echo 9bc2bffbf71f261128533edaf912153948b7ff238f9a531ae6d34466ec287883 ;;
	kubeconform/darwin-arm64) echo f84f4dfbebf4a6b0b230385fa065a39ea35e02608c2b50d025dcf64775a69d67 ;;
	untt/linux-amd64) echo de03d6b7ba7683282064702d0b36fbbe812ab2a22691fa3a8b2ce57c2682609c ;;
	untt/darwin-arm64) echo d78208521e6407287870345aa38f7e37eb588c74fb110cf27d1758852089e7fb ;;
	kind/linux-amd64) echo aee6151561422756b764a4ae28e7f44cda5af5a9eead3cc9985112b1de8d8e0d ;;
	kind/darwin-arm64) echo 0c8c7dbe5e23594a198b786c4bc13dacc101fa6196b0cb0b23a1ca44e61f4b4f ;;
	kubectl/linux-amd64) echo 65691ff77eb6fa44c908b77a1082c9f092c3b9733b5cefabec0d1104890e21a8 ;;
	kubectl/darwin-arm64) echo fd65982c97ddad3106754b69ffa196d0e543aa591930ae52aed1adfb92f8c77f ;;
	esac
}

download_url() {
	local os="${2%-*}" arch="${2#*-}"
	case "$1" in
	helm) echo "https://get.helm.sh/helm-v$HELM_VERSION-$2.tar.gz" ;;
	kubeconform) echo "https://github.com/yannh/kubeconform/releases/download/v$KUBECONFORM_VERSION/kubeconform-$2.tar.gz" ;;
	untt)
		[ "$os" = darwin ] && os=macos
		echo "https://github.com/helm-unittest/helm-unittest/releases/download/v$HELM_UNITTEST_VERSION/helm-unittest-$os-$arch-$HELM_UNITTEST_VERSION.tgz"
		;;
	kind) echo "https://github.com/kubernetes-sigs/kind/releases/download/v$KIND_VERSION/kind-$2" ;;
	kubectl) echo "https://dl.k8s.io/release/v$KUBECTL_VERSION/bin/$os/$arch/kubectl" ;;
	esac
}

version_of() {
	case "$1" in
	helm) echo "$HELM_VERSION" ;;
	kubeconform) echo "$KUBECONFORM_VERSION" ;;
	untt) echo "$HELM_UNITTEST_VERSION" ;;
	kind) echo "$KIND_VERSION" ;;
	kubectl) echo "$KUBECTL_VERSION" ;;
	esac
}

# Prints the path of the named tool, downloading and verifying it first if the cache does not hold it.
tool() {
	local name="$1" target download expected actual
	target="$TOOLS_DIR/$name-$(version_of "$name")/$name"
	if [ -x "$target" ]; then
		echo "$target"
		return 0
	fi
	local plat
	plat="$(platform)" || return 1
	mkdir -p "$(dirname "$target")"
	download="$(mktemp)"
	curl --fail --silent --show-error --location --retry 3 --output "$download" "$(download_url "$name" "$plat")"
	expected="$(checksum "$name" "$plat")"
	actual="$( (sha256sum "$download" 2>/dev/null || shasum -a 256 "$download") | cut -d' ' -f1)"
	if [ "$actual" != "$expected" ]; then
		echo "Checksum mismatch for $name on $plat: expected $expected, got $actual." >&2
		rm -f "$download"
		return 1
	fi
	case "$name" in
	helm) tar -xzf "$download" -O "$plat/helm" >"$target" ;;
	kubeconform) tar -xzf "$download" -O kubeconform >"$target" ;;
	untt) tar -xzf "$download" -O "untt-$(echo "$plat" | sed 's/^darwin/macos/')" >"$target" ;;
	*) cp "$download" "$target" ;;
	esac
	rm -f "$download"
	chmod +x "$target"
	echo "$target"
}
