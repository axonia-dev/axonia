# axonia, Self-hosted edition

The Self-hosted edition is axonia for one company, run on the company's own infrastructure: its communities of practice, their events, the expertise of their members and the messaging around them.
Members sign in through the company's own OpenID Connect identity provider, and every piece of data stays in the company's own database and storage.

## What it is not

- One installation serves one Organization: the company named at the first start, permanently.
- There is no billing, no plan, no tenant sign-up and no connection to any service the publisher runs: nothing leaves the installation except mail through the company's own server, the calls to the company's own identity provider (and to a photo URL its `picture` claim names), and, when configured, Microsoft Graph, the media store, a community's chat webhook, and an agent's client metadata document from a domain `AXONIA_MCP_OAUTH_TRUSTED_CLIENT_DOMAINS` lists (`claude.ai` by default).
- Members sign in only through the company's identity provider; axonia keeps no password.

## What it runs

- The backend, a container listening on port 8080, which holds every rule and talks to the database, the media store and the mail server.
- The edge, a container listening on port 8080 behind the company's TLS termination, which serves the application, signs members in and forwards their requests to the backend.
- The company's Postgres.
- A store for images: a volume, an S3-compatible bucket or an Azure Blob Storage container.
- The company's mail server, configured in the application by an Owner.

```text
browser -> TLS -> edge -> backend -> Postgres
                              \-> media store, mail server
```

## Where to start

- [`install.md`](install.md): what an installation needs, and installing it with Docker Compose or the Helm chart.
- [`entra.md`](entra.md): signing in through Microsoft Entra ID, and the optional Microsoft Graph reads.
- [`other-identity-providers.md`](other-identity-providers.md): what any other OpenID Connect provider must offer.
- [`configuration.md`](configuration.md): every variable the two containers read, in three tiers.
- [`storage.md`](storage.md): where uploaded images live, and how to set up each store.
- [`backup.md`](backup.md): what to back up, and what a restore needs.
- [`upgrading.md`](upgrading.md): moving to a new release, and rotating the settings key and every other secret.

## Versions

Each release names the Postgres major versions it is tested with (PostgreSQL 18 today, the major the compose file ships), and the Kubernetes versions its chart supports (1.29 to 1.37 today).
