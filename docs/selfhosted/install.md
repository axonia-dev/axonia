# Installing

## Before you start

- A host name members reach, with TLS terminated in front of the edge.
- A Postgres server with point-in-time recovery, PostgreSQL 18 (the major the compose file ships; each release's notes name the majors it is tested with), holding an empty database and an owner role with `CREATEROLE` and `CREATE` on that database (the database's owner holds it), because an upgrade creates the `scheduler` schema.
- One Postgres server per installation: the first start creates the roles `axonia_app` and `axonia_communication_read`, which belong to the server, not the database, so a second installation on the same server refuses its start or takes over the first one's roles.
- An OpenID Connect identity provider and an administrator of it: `entra.md` for Microsoft Entra ID, `other-identity-providers.md` for any other.
- A store for images: a volume, an S3-compatible bucket or an Azure Blob Storage container (`storage.md`).
- An SMTP server axonia may send from.
  It is configured in the application after the first start, or by the deployment (`configuration.md`).
- Docker Compose on one host, or a Kubernetes cluster.

## Generate the secrets

```sh
openssl rand -base64 32 | tr '+/' '-_' | tr -d '='   # SESSION_COOKIE_KEY (edge)
openssl rand -hex 32                                 # EDGE_SHARED_SECRET (edge) = AXONIA_EDGE_SHARED_SECRETS (backend)
openssl rand -hex 32                                 # DATABASE_PASSWORD (backend)
openssl rand -hex 32                                 # DATABASE_COMMUNICATION_READ_PASSWORD (backend)
openssl rand -hex 32                                 # AXONIA_UNSUBSCRIBE_SECRET (backend)
openssl rand -hex 32                                 # DATABASE_OWNER_PASSWORD (backend), unless the DBA sets it
openssl rand -base64 32                              # AXONIA_SETTINGS_KEY (backend), unless the Helm chart generates it
```

Never reuse a secret, and keep each where the deployment reads it: a file the compose file reads, or a Kubernetes Secret.
Database passwords must match `[A-Za-z0-9._~-]+`; the hex output does.

## Docker Compose

In `deploy/`:

```sh
cp .env.example .env
# Fill in every line .env.example does not comment out.
docker compose up -d
docker compose logs -f backend
```

Some names in `.env` are the compose file's own, and some feed more than one container:

| `.env` name | Feeds | Default in compose |
| --- | --- | --- |
| `AXONIA_IMAGE_REGISTRY` | the registry part of both `image:` lines (`<registry>/axonia-backend`, `<registry>/axonia-edge`), for a mirror | `ghcr.io/axonia-dev` |
| `AXONIA_VERSION` | the tag of both images: the release to run | none, required |
| `AXONIA_LISTEN` | the host address and port the edge is published on (`ports:`) | `127.0.0.1:8080` |
| `PUBLIC_URL` | the edge's `PUBLIC_URL` and the backend's `AXONIA_PUBLIC_BASE_URL` | none, required |
| `EDGE_SHARED_SECRET` | the edge's `EDGE_SHARED_SECRET`, and the backend's `AXONIA_EDGE_SHARED_SECRETS` unless that is set | none, required |
| `AXONIA_EDGE_SHARED_SECRETS` | the backend's `AXONIA_EDGE_SHARED_SECRETS` when set, for the two-step rotation (`new,old` on the backend first, then the edge's `EDGE_SHARED_SECRET`, then remove it); an empty value counts as unset | `EDGE_SHARED_SECRET` |
| `OIDC_ISSUER` | the edge's `OIDC_ISSUER` and the backend's `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` | none, required |
| `DATABASE_OWNER_PASSWORD` | the backend's `DATABASE_OWNER_PASSWORD` and the Postgres service's `POSTGRES_PASSWORD` | none, required |
| `DATABASE_OWNER_USERNAME` | the backend's `DATABASE_OWNER_USERNAME` and the Postgres service's `POSTGRES_USER` | `axonia_owner` |
| `DATABASE_URL` | the backend's `DATABASE_URL` | `jdbc:postgresql://postgres:5432/axonia` |
| `TRUSTED_PROXY_HOPS` | the edge's `TRUSTED_PROXY_HOPS` | `1` in compose (and the chart), against `0` in the edge itself |

- Put a reverse proxy that terminates TLS on the same host, in front of the edge: members' browsers keep their session only over https.
- The edge listens on `127.0.0.1:8080` by default (`AXONIA_LISTEN`), so only that proxy reaches it, and `TRUSTED_PROXY_HOPS` is `1`: the proxy appends the caller's address to `X-Forwarded-For` and the edge reads it there.
- To publish the edge beyond the host with no proxy in front, set `AXONIA_LISTEN` (for example `0.0.0.0:8080`) and `TRUSTED_PROXY_HOPS=0`; with `1`, a caller writes its own `X-Forwarded-For` and chooses the address its rate limits count against.
- The compose file runs Postgres for the installation, its data in the volume `postgres-data`.
  To use the company's Postgres instead, set `DATABASE_URL` and `DATABASE_OWNER_USERNAME` to it and switch the `postgres` service and the backend's dependency on it off in `compose.override.yaml` (`configuration.md`, Docker Compose).
- Keep every change of the installation's own in `deploy/compose.override.yaml`, never in `compose.yaml`, which the next release replaces (`configuration.md`, Docker Compose).
- `AXONIA_SETTINGS_KEY` in `.env` is the settings key: back it up, kept apart from the database backups (`backup.md`).

## Kubernetes, with the Helm chart

1. Create the namespace:

   ```sh
   kubectl create namespace axonia
   ```

2. Create the Secret holding the installation's secrets, one key per variable, from the values generated above:

   ```sh
   kubectl --namespace axonia create secret generic axonia \
     --from-literal=DATABASE_OWNER_PASSWORD=... \
     --from-literal=DATABASE_PASSWORD=... \
     --from-literal=DATABASE_COMMUNICATION_READ_PASSWORD=... \
     --from-literal=AXONIA_UNSUBSCRIBE_SECRET=... \
     --from-literal=AXONIA_EDGE_SHARED_SECRETS=<the edge secret> \
     --from-literal=EDGE_SHARED_SECRET=<the edge secret> \
     --from-literal=SESSION_COOKIE_KEY=... \
     --from-literal=OIDC_CLIENT_SECRET=...
   ```

3. Copy `deploy/helm/examples/minimal.yaml` to `values.yaml`, fill in the installation's own values, then install the release's chart and wait for the backend:

   ```sh
   helm install axonia oci://ghcr.io/axonia-dev/charts/axonia --version <release> --namespace axonia --values values.yaml
   kubectl --namespace axonia rollout status deployment/axonia-backend --timeout=15m
   ```

   The chart runs `ghcr.io/axonia-dev/axonia-backend` and `ghcr.io/axonia-dev/axonia-edge` at the chart's version; `backend.image.repository` and `edge.image.repository` name a mirror instead, and `backend.image.tag` and `edge.image.tag` another tag.
   The images are built for `linux/amd64` and `linux/arm64`.

   Every object the chart creates is named `<release>-<component>`, here `axonia-backend`, `axonia-edge` (with its Ingress, and `axonia-edge-auth` when sign-in has its own), `axonia-settings-key` and `axonia-media`, unless `fullnameOverride` replaces the release name.
   Like the release name, `fullnameOverride` is set at the first install only: a later change would start the backend on a new settings key, media volume and service account, so the chart refuses an upgrade that changes it.
   The refusal happens under `helm upgrade` only: a tool that renders the chart without a cluster, as Argo CD and `helm template` pipelines do, does not see it, so keep the value fixed there yourself.

   `deploy/helm/examples/` also holds an installation on Microsoft Entra ID with Azure Blob Storage, and one with S3, Redis and every mail field set by the deployment.

- The chart refuses a secret given as a value: every secret comes from the Secret `existingSecret` names, one key per variable, and the mail password, the Redis address, the S3 keys and the Graph client secret from Secrets of their own.
- The chart generates the settings key into the Secret `<release>-settings-key` on the first install and reads it back at every upgrade.
  An uninstall keeps that Secret, so a reinstall under the same release name finds the same key.
  Back it up (`backup.md`).
  Never edit that Secret to rotate the key: a rotation moves it into a Secret of your own named in `settingsKey.existingSecret` (`upgrading.md`).
- A tool that renders the chart without a cluster, as Argo CD and `helm template` pipelines do, cannot read the key back: it renders no settings key Secret, so the backend's pod never starts and stays in `CreateContainerConfigError`, naming the missing Secret `<release>-settings-key`.
  With such a tool, create the Secret yourself and name it in `settingsKey.existingSecret`.
- The chart routes the public host to the edge alone through an Ingress.
  To use the Gateway API instead, set `ingress.enabled: false` and route to the Service `<release>-edge` on port 80:

  ```yaml
  apiVersion: gateway.networking.k8s.io/v1
  kind: HTTPRoute
  metadata:
    name: axonia
    namespace: axonia
  spec:
    parentRefs:
      - name: company-gateway
        namespace: gateway
    hostnames:
      - axonia.example.com
    rules:
      - backendRefs:
          - name: axonia-edge
            port: 80
  ```

- Set `edge.trustedProxyHops` to the number of proxies in front of the edge (`configuration.md`).

The chart's `values.yaml` describes every value, and `configuration.md` names the variable each one becomes.

## The first start

- The first start migrates the database, creates the two application roles and founds the Organization; the backend reports ready only then, and logs `Founded Organization <id> and bound its Owner role to directory group <group>`.
- A person who signs in before that line appears stops on "Signed in, but this server refuses the session." and the backend logs `Rejected a request naming an unknown Organization: <id>`; once the line appears, "Sign in" lets them in.
- Sign in as a member of the Owner group: the settings show the Owner role.
  If they do not, check the token (`entra.md`, "Checking the token", or `other-identity-providers.md`).
- As an Owner, open the organization's Notifications page and set up the mail server: a save is accepted only once a test mail with exactly those values has gone through.
  Until then the card reads "Not configured", and every mail is dropped rather than sent, never delivered later.
- Then create a first community, schedule a session in it, and choose "Send it now" on its reminder chip: the reminder arriving proves mail end to end.

## When something fails

| What a person sees, or the log | What it means | What to do |
| --- | --- | --- |
| The backend does not start and logs `Settings that name a variable which is not set: ...` | A Bootstrap variable is missing or an unresolved `${NAME}` | Set every variable it names (`configuration.md`) |
| The backend does not start with another error: a validation error naming an `AXONIA_SELFHOSTED_*` setting, or a datasource or Flyway error | A variable is set but empty, for example a key left empty in a Secret or a line left empty in `.env` | Give it a value, or leave an optional one out entirely (`configuration.md`) |
| Every API request answers 403 | The edge's `EDGE_SHARED_SECRET` is not one of the backend's `AXONIA_EDGE_SHARED_SECRETS` | Make them equal and restart the one changed |
| The edge logs `"failure":"origin_unreachable"` | The edge cannot reach `BACKEND_ORIGIN` | Check the address, and that the backend is ready |
| The edge logs `"failure":"identity_provider_unreachable"`; sign-in answers 503 | The edge cannot reach the identity provider | Network or provider outage; nobody signed in is signed out meanwhile |
| "The identity provider refused to complete the sign-in. The edge's client settings may be wrong." and `"failure":"token_refused"` with an `oauth_error` | The provider refused the client: its secret, its id or its redirect URI | `invalid_client` is usually an expired secret (`upgrading.md`, "Rotating other secrets") |
| "The identity provider issued no refresh token. OIDC_SCOPES may lack offline_access." and `"failure":"no_refresh_token"` | No refresh token was granted | Add the scope that grants one (`offline_access` with Entra ID) to `OIDC_SCOPES`, restart the edge |
| "Signed in, but this server refuses the session." and the backend logs `Refused a bearer token: <reason> (iss=<iss>, aud=<aud>)` | The token's issuer or audience is not the configured one | Fix the setting the reason names |
| Signed-in requests answer 503 with `issuer_unavailable` | The backend cannot fetch the provider's keys | `entra.md`, "When the identity provider cannot be reached" (the same applies to any provider) |
| The edge logs `"failure":"edge_error"` | An unexpected error inside the edge | Report it with the log line |
| The edge exits with `"failure":"listen_failed"` | Its port is taken or not allowed | Change `PORT` or free the port |
