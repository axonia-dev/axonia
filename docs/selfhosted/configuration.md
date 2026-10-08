# Configuration

An installation runs two containers, the backend and the edge, and reads every setting from its environment.
Settings fall into three tiers.

| Tier | Where it is set | Without it |
| --- | --- | --- |
| Bootstrap | The environment only | The container refuses to start and names every missing or malformed setting |
| Integration | In the application, by an Owner, each field overridable by a variable | The feature is off and says so; the start is never blocked |
| Deployment | The environment only | The default named below |

The Helm chart sets every variable below from its values (`deploy/helm/axonia/values.yaml`), except `STATIC_DIR`, which the edge's image sets, and `AXONIA_MCP_OAUTH_TRUSTED_CLIENT_DOMAINS`, set through `backend.extraEnv`.
`deploy/compose.yaml` passes, from `deploy/.env`:
- every Bootstrap variable, except `STATIC_DIR`, which the edge's image sets, and `BACKEND_ORIGIN`, which compose fixes to its backend service;
- of the Integration tier, the Microsoft Graph variables, and the mail server's only through `compose.override.yaml` (Docker Compose, below);
- every Deployment variable, except `MEDIA_LOCAL_PATH`, which the backend's image sets, and `PORT`, left at the edge's default, and `AZURE_TOKEN_CREDENTIALS` and `JAVA_TOOL_OPTIONS`, which only `compose.override.yaml` adds.

A Bootstrap value left unset is absent, never an empty string: an unresolved `${NAME}` refuses the start like a missing one, and an empty value refuses it too, with an error of its own (`install.md`, "When something fails"), except `AXONIA_SETTINGS_PREVIOUS_KEYS`, where empty means none.

## Bootstrap

### Backend

| Variable | Value |
| --- | --- |
| `AXONIA_PUBLIC_BASE_URL` | The address members reach the installation at, for example `https://axonia.example.com`, equal to the edge's `PUBLIC_URL`. Links in mail are built from it, so it must be the address a mail client reaches |
| `DATABASE_URL` | The JDBC URL of the installation's database, for example `jdbc:postgresql://db.example.com:5432/axonia?sslmode=verify-full` |
| `DATABASE_OWNER_USERNAME` | The role that owns the schema and runs the migrations; it needs `CREATEROLE`, and `CREATE` on the database, which the database's owner holds, because an upgrade creates the `scheduler` schema |
| `DATABASE_OWNER_PASSWORD` | That role's password |
| `DATABASE_PASSWORD` | The password of `axonia_app`, the role the application connects as. The first start creates the role with it; changing it later needs `ALTER ROLE axonia_app PASSWORD '<new>'` as the owner first |
| `DATABASE_COMMUNICATION_READ_PASSWORD` | The password of `axonia_communication_read`, the role scheduled work discovers the Organization with, created and changed the same way |
| `AXONIA_EDGE_SHARED_SECRETS` | The edge's `EDGE_SHARED_SECRET`, which the compose file sets it to unless `.env` sets it. A comma-separated list during a rotation: add the new secret, restart the backend, change the edge, then drop the old one (`upgrading.md`) |
| `AXONIA_UNSUBSCRIBE_SECRET` | Signs the unsubscribe link of every mail; changing it breaks the links in mail already sent |
| `AXONIA_SETTINGS_KEY` | 32 random bytes in base64 (`openssl rand -base64 32`), the key stored integration secrets are encrypted with. Back it up, kept apart from the database backups (`backup.md`); the Helm chart generates it unless told otherwise |
| `AXONIA_SETTINGS_PREVIOUS_KEYS` | Optional. Earlier settings keys, comma-separated, during a rotation (`upgrading.md`) |
| `AXONIA_SELFHOSTED_ORGANIZATION_ID` | A short lowercase id, for example `acme`. Permanent: a later start with another id is refused, naming both |
| `AXONIA_SELFHOSTED_ORGANIZATION_NAME` | The company's display name; a new value renames the Organization at the next start |
| `AXONIA_SELFHOSTED_OWNER_GROUP` | The directory group whose members are Owners, as the token's groups claim writes it. A new value is bound at the next start beside the earlier ones, which keep the role until an Owner removes them |
| `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` | The identity provider's issuer, character for character as its tokens write `iss` |
| `AXONIA_SELFHOSTED_OIDC_AUDIENCE` | The audience every access token must carry; tokens without it are refused |
| `AXONIA_SELFHOSTED_OIDC_SUBJECT_CLAIM` | Default `sub`; `oid` with Microsoft Entra ID. Permanent: a later start with another claim is refused, naming both |
| `AXONIA_SELFHOSTED_OIDC_GROUPS_CLAIM` | Default `groups` |
| `AXONIA_SELFHOSTED_OIDC_EMAIL_CLAIMS` | Default `email,preferred_username`: the first present is the person's address |
| `AXONIA_SELFHOSTED_OIDC_NAME_CLAIM` | Default `name` |

Database passwords must match `[A-Za-z0-9._~-]+`; `openssl rand -hex 32` does.

### Edge

| Variable | Value |
| --- | --- |
| `PUBLIC_URL` | The address members reach the installation at: https, or http on localhost, with no path. The identity provider's redirect URIs are registered under it |
| `BACKEND_ORIGIN` | The backend's address inside the platform, with no path, for example `http://axonia-backend:8080`. The edge forwards `/axonia`, `/public`, `/mcp`, `/scim`, `/oauth/token`, `/oauth/revoke` and `/.well-known/oauth-*` to it |
| `OIDC_ISSUER` | Same as the backend's `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` |
| `OIDC_CLIENT_ID` | The client the edge signs in as |
| `OIDC_CLIENT_SECRET` | That client's secret |
| `OIDC_SCOPES` | Space-separated, including `openid` and whatever makes the provider issue a refresh token and an access token for axonia |
| `OIDC_SESSION_END` | `revoke` (RFC 7009, the standard) or `refresh-token-post` (Keycloak's own) |
| `SESSION_COOKIE_KEY` | 32 random bytes in unpadded base64url (`openssl rand -base64 32 \| tr '+/' '-_' \| tr -d '='`). Every edge instance needs the same one; changing it signs everybody out |
| `SESSION_MAX_AGE_SECONDS` | How long a session lives unused: the provider's refresh token lifetime, `7776000` with Microsoft Entra ID |
| `EDGE_SHARED_SECRET` | The secret the edge proves itself to the backend with, equal to one of the backend's `AXONIA_EDGE_SHARED_SECRETS` |
| `STATIC_DIR` | Set by the image to the built application, `/srv/app`; never changed |

## Configured in the application

### The mail server

An Owner configures the mail server in its card on the organization Notifications page: "Set up", "Send test mail", then "Save".
A save is accepted only after a test of exactly the values saved passes, and every save and clear is recorded with who made it.
Until the mail server is configured, every mail is dropped rather than sent, and none of it is delivered once the server is set: the card reads "Not configured", and a viewer of that page who cannot read the mail server reads "Mail not configured." in its place.

Each field can be set by the deployment instead.
A variable that is set overrides the stored value and locks its field in the application, which names the variable beneath it.
An installation that sets every field this way is configured without anyone opening the application.
An empty variable counts as unset.
With Docker Compose, set them in `compose.override.yaml` (Docker Compose, below).

| Variable | Field it sets and locks |
| --- | --- |
| `AXONIA_MAIL_HOST` | SMTP server: host |
| `AXONIA_MAIL_PORT` | SMTP server: port, for example `587` |
| `AXONIA_MAIL_SECURITY` | SMTP server: security, `STARTTLS`, `TLS` (implicit TLS, usually port `465`) or `NONE` |
| `AXONIA_MAIL_USERNAME` | SMTP server: username |
| `AXONIA_MAIL_PASSWORD` | SMTP server: password |
| `AXONIA_MAIL_FROM_ADDRESS` | Sender: from address |
| `AXONIA_MAIL_FROM_NAME` | Sender: from name |

`AXONIA_MAIL_PASSWORD` applies only when `AXONIA_MAIL_HOST`, `AXONIA_MAIL_PORT`, `AXONIA_MAIL_USERNAME` and `AXONIA_MAIL_SECURITY` are also set by the deployment, or are already stored with the same values, so a password the deployment sets is only ever sent to its own server.
`AXONIA_MAIL_PASSWORD` is used as written, spaces included, and must be one line: the start is refused when it holds a line break, which a Kubernetes Secret made with `--from-file` keeps from the end of the file.
When a deployment changes a locked value after the last test, delivery carries on with the new value and the card reads "Changed by the deployment since the last test." until an Owner tests it again.
`SPRING_MAIL_*` and `AXONIA_COMMUNICATION_FROM` are not read by the Self-hosted edition.

### Microsoft Graph

For a Microsoft Entra ID issuer, Graph reads suggest directory groups when an Owner binds a role and keep members' photos in step with the directory (`entra.md`).
They are set by the deployment only, for now.

| Variable | Value |
| --- | --- |
| `AXONIA_ENTRA_CLIENT_ID` | The client id of an app registration holding the `GroupMember.Read.All` and `ProfilePhoto.Read.All` application permissions |
| `AXONIA_ENTRA_CLIENT_SECRET` | A client secret of that registration |

Without both, Graph reads are off: group suggestions come from SCIM alone, and no photo is read from the directory.

## Deployment

### Backend

| Variable | Default | Value |
| --- | --- | --- |
| `APP_MEDIA_TYPE` | `local` | Where uploaded images live: `local`, `s3` or `azure-blob` (`storage.md`) |
| `MEDIA_LOCAL_PATH` | `/var/lib/axonia/media` | With `local`, the directory images are written to. The image sets it to the path the compose file and the chart mount a volume at; a path that is not a mounted volume loses every image at the next restart |
| `APP_MEDIA_S3_BUCKET`, `APP_MEDIA_S3_REGION`, `APP_MEDIA_S3_ENDPOINT`, `APP_MEDIA_S3_PATH_STYLE`, `APP_MEDIA_S3_ACCESS_KEY`, `APP_MEDIA_S3_SECRET_KEY` | | With `s3` (`storage.md`) |
| `APP_MEDIA_AZURE_BLOB_ENDPOINT`, `APP_MEDIA_AZURE_BLOB_CONTAINER`, `AZURE_TOKEN_CREDENTIALS` | | With `azure-blob` (`storage.md`) |
| `AXONIA_RATE_LIMIT_STORE` | `memory` | Where rate-limit budgets are kept. `memory` keeps each backend instance's budgets apart, so more than one instance multiplies every limit by the instance count and needs `redis` |
| `SPRING_DATA_REDIS_URL` | | With `redis`, the Redis address, for example `rediss://:<password>@redis.example.com:6380`. An unreachable Redis lets each call through rather than refusing it |
| `MANAGEMENT_HEALTH_REDIS_ENABLED` | `false` | `true` with `redis`, so the health endpoint reports Redis |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` (the image) | JVM options, so the heap follows the container's memory limit; the chart sets `-XX:MaxRAMPercentage=75.0` from `backend.javaToolOptions`. A value set replaces the image's whole |
| `AXONIA_MCP_OAUTH_TRUSTED_CLIENT_DOMAINS` | `claude.ai` | The hosts, comma-separated, an AI agent may name as its client: when one connects, the backend fetches its client metadata document over https from that host. `claude.ai` lets Claude connect; an empty value trusts no host, and the backend never makes that call. Removing a host refuses the agents already connected from it. Lowercase hosts, matched exactly. With the Helm chart, set it in `backend.extraEnv` |

The backend listens on port 8080 and answers its probes at `/actuator/health/liveness` and `/actuator/health/readiness`.
It is ready only once its Organization is founded at the first start, so a platform that waits for readiness sends no request before the Organization exists.
The migrations run before it listens: a first start on a large database, or an upgrade with a long migration, needs a startup allowance of some minutes before liveness counts.

### Edge

| Variable | Default | Value |
| --- | --- | --- |
| `TRUSTED_PROXY_HOPS` | `0` in the edge; compose and the chart set `1` | The number of trusted proxies in front of the edge, each appending to `X-Forwarded-For`: `1` behind one ingress or load balancer. `0` ignores the header and reads the socket's address, so behind a proxy every member shares the proxy's address for rate limits; a number larger than the real count lets a caller choose its own address |
| `PORT` | `8080` | The port the edge listens on |

The edge answers `/healthz` itself, for probes.

### Docker Compose

`deploy/compose.yaml` reads its values from `deploy/.env`, copied from `deploy/.env.example`.
Some names there are the compose file's own, and some feed more than one container:

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

The Postgres service's own `POSTGRES_DB` (`axonia`) is fixed and read from no `.env` name.
Postgres reads `POSTGRES_USER` and `POSTGRES_PASSWORD` only when its volume is empty: changing `DATABASE_OWNER_USERNAME` or `DATABASE_OWNER_PASSWORD` in `.env` afterwards changes what the backend sends, not the role, and the start fails (`upgrading.md`).
The default `AXONIA_LISTEN` keeps the edge reachable only from the host, through the TLS reverse proxy there, which is what makes `TRUSTED_PROXY_HOPS` `1` safe.
An `AXONIA_LISTEN` that publishes the edge beyond the host with no proxy in front needs `TRUSTED_PROXY_HOPS=0`, or a caller can choose the address its rate limits count against.
A company Postgres replaces the compose file's: set `DATABASE_URL` and `DATABASE_OWNER_USERNAME` to it in `.env`, and switch the `postgres` service and the backend's dependency on it off in `compose.override.yaml`, as below.

Settings `deploy/compose.yaml` does not pass, and any other addition of the installation's own, go in `deploy/compose.override.yaml`.
Docker Compose merges that file into `compose.yaml` on every command run in `deploy/`, and a release replacing `compose.yaml` leaves it as it is.
Its values may read `.env` too.
Settings, for example the mail server's fields and the agent hosts:

```yaml
services:
  backend:
    environment:
      AXONIA_MAIL_HOST: smtp.example.com
      AXONIA_MAIL_PORT: "587"
      AXONIA_MAIL_SECURITY: STARTTLS
      AXONIA_MAIL_USERNAME: axonia
      AXONIA_MAIL_PASSWORD: ${AXONIA_MAIL_PASSWORD:?set AXONIA_MAIL_PASSWORD in .env}
      AXONIA_MAIL_FROM_ADDRESS: axonia@example.com
      AXONIA_MCP_OAUTH_TRUSTED_CLIENT_DOMAINS: claude.ai,agent.example.com
```

With a company Postgres only, added to the same file: the bundled Postgres never starts, and the backend waits for none.

```yaml
services:
  backend:
    depends_on: !reset {}
  postgres:
    profiles: [bundled-postgres]
```

`!reset` needs Docker Compose 2.24 or later.
Both blocks together are one `services:` map in the file.

### Rate limits at the ingress

The edge has no rate limit of its own.
Each sign-in (`/auth/login`, then `/auth/callback`) makes the edge call the identity provider's token endpoint once, whoever sends it, so rate-limit those two routes per client address at the ingress or load balancer in front of the edge.
Leave `/auth/refresh` and `/auth/logout` out of that limit: every page load, new tab and session renewal calls `/auth/refresh`, and a refused one shows "Sign-in is unavailable. Retrying." in place of the page.
The Helm chart gives the two exact paths `/auth/login` and `/auth/callback` an Ingress of its own carrying `ingress.authAnnotations`, and keeps every other route, renewal and sign-out included, on the shared one.
For example, with an NGINX-based controller:

```yaml
ingress:
  authAnnotations:
    nginx.ingress.kubernetes.io/limit-rpm: "120"
    nginx.ingress.kubernetes.io/limit-burst-multiplier: "5"
```

The limit counts per client address the controller sees.
Members behind one office NAT, VPN or forward proxy share one address, and every member shares the load balancer's or the nodes' addresses when the load balancer in front of the controller does not preserve client addresses.
Size it for the members behind one address: each sign-in is two requests, and the burst must absorb the members who sign in at once, for example after an announcement mail.
The values above let 300 sign-ins through at once and 60 a minute after that.

An image upload is up to 6 MB, so a proxy in front of the edge must accept a request body of that size; NGINX refuses one over 1 MB unless `nginx.ingress.kubernetes.io/proxy-body-size` says otherwise.
