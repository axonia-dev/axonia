# Running the Self-hosted edition locally

A development run of the Self-hosted edition, from this repository alone.
Its database, a stand-in identity provider and a mail catcher run in containers; the backend and the frontend's dev server run on the machine.
It needs Docker with Compose, Java 26, Node 24 and pnpm.

| Service | Address |
| --- | --- |
| The app, through the dev server | http://localhost:3200 |
| The backend | http://localhost:8090 |
| Postgres | `localhost:5433`, user `admin`, password `admin` |
| The identity provider | http://localhost:8091/axonia |
| Mailpit, SMTP | `localhost:1026` |
| Mailpit, inbox | http://localhost:8026 |

## Start

From the repository root, in two terminals:

```bash
(cd backend && ./mvnw -pl selfhosted-app -am -DskipTests spring-boot:run -Dspring-boot.run.profiles=DEV)
(cd frontend && pnpm install && pnpm dev:selfhosted)
```

The DEV profile starts the containers itself before the backend, and stops them when the backend stops; their data stays.
Starting them by hand first, with `docker compose -f deploy/dev/compose.yaml up -d --wait`, works too, and the backend then leaves them running.

The backend reads `backend/selfhosted-app/src/main/resources/application-DEV.properties` and the dev server reads `frontend/.env.selfhosted`; both name the addresses above.
An IDE runs the backend with main class `AxoniaSelfHostedApplication`, module `axonia-selfhosted`, working directory `backend` and active profile `DEV`.
Uploaded images land in `backend/build/media-selfhosted`.

## Sign in

Open http://localhost:3200.
The identity provider shows a form rather than a password prompt: it signs in whoever fills it, with the claims typed there.
Enter `alice` as the username and these claims:

```json
{ "email": "alice@example.com", "name": "Alice", "groups": ["axonia-owners"] }
```

The first start founds the Organization `axonia-local`, and the group `axonia-owners` makes alice its Owner.
Another username with other claims signs in as another person, and leaving out the group signs in a member who is not an Owner.

## Mail

The mail server is set in the app, in the Mail server card of the Notifications settings: host `localhost`, port `1026`, no encryption and no user name.
Every mail the run sends then lands in Mailpit's inbox at http://localhost:8026.

## Ports

A variable moves each published port, for a machine where one is taken: `AXONIA_DEV_POSTGRES_PORT`, `AXONIA_DEV_ISSUER_PORT`, `AXONIA_DEV_SMTP_PORT` and `AXONIA_DEV_MAILPIT_PORT`.
A moved database port is also given to the backend, as `spring.datasource.url`, `spring.flyway.url` and `axonia.tenant-discovery.datasource.url`.
A moved identity provider port is also given to the backend, as `axonia.selfhosted.oidc.issuer-uri`, and to the dev server, as `DEV_OIDC_ISSUER` in `frontend/.env.selfhosted.local`.

## Start over

```bash
docker compose -f deploy/dev/compose.yaml down --volumes
```

It removes the database and nothing else; `backend/build/media-selfhosted` keeps the run's images until it is deleted too.
