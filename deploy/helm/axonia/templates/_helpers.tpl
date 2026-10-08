{{- define "axonia.fullname" -}}
{{- .Values.fullnameOverride | default .Release.Name | trunc 50 | trimSuffix "-" -}}
{{- end -}}

{{- define "axonia.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "axonia.selectorLabels" -}}
app.kubernetes.io/name: {{ .context.Chart.Name }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "axonia.backendName" -}}
{{- printf "%s-backend" (include "axonia.fullname" .) -}}
{{- end -}}

{{- define "axonia.edgeName" -}}
{{- printf "%s-edge" (include "axonia.fullname" .) -}}
{{- end -}}

{{- define "axonia.image" -}}
{{- $repository := required (printf "%s.image.repository is required" .component) .image.repository -}}
{{- printf "%s:%s" $repository (.image.tag | default .context.Chart.AppVersion) -}}
{{- end -}}

{{- define "axonia.settingsKeySecretName" -}}
{{- .Values.settingsKey.existingSecret | default (printf "%s-settings-key" (include "axonia.fullname" .)) -}}
{{- end -}}

{{- define "axonia.mediaClaimName" -}}
{{- .Values.media.local.existingClaim | default (printf "%s-media" (include "axonia.fullname" .)) -}}
{{- end -}}

{{- define "axonia.audience" -}}
{{- .Values.oidc.audience | default .Values.oidc.clientId -}}
{{- end -}}

{{- define "axonia.secretEnv" -}}
- name: {{ .name }}
  valueFrom:
    secretKeyRef:
      name: {{ .secret }}
      key: {{ .key | default .name }}
      {{- if .optional }}
      optional: true
      {{- end }}
{{- end -}}

{{- define "axonia.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: 10001
runAsGroup: 10001
fsGroup: 10001
fsGroupChangePolicy: OnRootMismatch
seccompProfile:
  type: RuntimeDefault
{{- end -}}

{{- define "axonia.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop:
    - ALL
{{- end -}}

{{/*
Refusals no schema can express, because each one depends on two values together.
*/}}
{{- define "axonia.validate" -}}
{{- $values := .Values -}}
{{- if and (gt (int $values.backend.replicas) 1) (eq $values.rateLimit.store "memory") -}}
{{- fail "backend.replicas above 1 needs rateLimit.store=redis: the memory store keeps each instance's budgets apart, so every rate limit would multiply by the instance count." -}}
{{- end -}}
{{- if and (eq $values.rateLimit.store "redis") (not $values.rateLimit.redis.existingSecret) -}}
{{- fail "rateLimit.store=redis needs rateLimit.redis.existingSecret, the Secret holding the Redis URL." -}}
{{- end -}}
{{- if and (eq $values.media.type "local") (gt (int $values.backend.replicas) 1) (not (has "ReadWriteMany" $values.media.local.accessModes)) -}}
{{- fail "backend.replicas above 1 with media.type=local needs media.local.accessModes to include ReadWriteMany, or a bucket: a ReadWriteOnce volume mounts on one node only." -}}
{{- end -}}
{{- if and (eq $values.media.type "s3") (not $values.media.s3.bucket) -}}
{{- fail "media.type=s3 needs media.s3.bucket." -}}
{{- end -}}
{{- if and (eq $values.media.type "s3") (not $values.media.s3.region) (not $values.media.s3.endpoint) -}}
{{- fail "media.type=s3 needs media.s3.region for AWS S3, or media.s3.endpoint for another store." -}}
{{- end -}}
{{- if and (eq $values.media.type "azure-blob") (or (not $values.media.azureBlob.endpoint) (not $values.media.azureBlob.container)) -}}
{{- fail "media.type=azure-blob needs media.azureBlob.endpoint and media.azureBlob.container." -}}
{{- end -}}
{{- if and $values.entraGraph.clientId (not $values.entraGraph.clientSecret.existingSecret) -}}
{{- fail "entraGraph.clientId needs entraGraph.clientSecret.existingSecret, the Secret holding its client secret." -}}
{{- end -}}
{{- include "axonia.validateNames" . -}}
{{- end -}}

{{/*
The names of an installed release never change: a new fullnameOverride would start the backend on a new settings key,
a new media volume and a new service account, the old ones kept but unread. The release's own backend Deployment, found
by its labels, holds the names it was installed with. Without a cluster (helm template) lookup answers nothing and
there is nothing to compare.
*/}}
{{- define "axonia.validateNames" -}}
{{- if .Release.IsUpgrade -}}
{{- $wanted := include "axonia.backendName" . -}}
{{- range (lookup "apps/v1" "Deployment" .Release.Namespace "").items | default list -}}
{{- $labels := .metadata.labels | default dict -}}
{{- if and (eq (get $labels "app.kubernetes.io/name") $.Chart.Name) (eq (get $labels "app.kubernetes.io/instance") $.Release.Name) (eq (get $labels "app.kubernetes.io/component") "backend") (ne .metadata.name $wanted) -}}
{{- fail (printf "fullnameOverride changed since the install: this release's backend is %s, and the chart would now create %s with a new settings key, media volume and service account. Set fullnameOverride back to what the install used." .metadata.name $wanted) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}
