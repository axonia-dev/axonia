# axonia

axonia is the home of a company's software engineering communities of practice: their communities, events and sessions, the knowledge they share, and the expertise of their members.

This repository is its Self-hosted edition: axonia for one company, run on the company's own infrastructure, with members signing in through the company's own OpenID Connect identity provider and every piece of data kept in the company's own database and storage.
The hosted Cloud edition is at [axonia.dev](https://axonia.dev).

## Install

[`docs/selfhosted/`](docs/selfhosted/README.md) is the operator's guide: what an installation needs, installing it with Docker Compose or the Helm chart, configuration, identity providers, storage, backup and upgrading.

Each release publishes two images and a Helm chart:

- `ghcr.io/axonia-dev/axonia-backend:<version>`
- `ghcr.io/axonia-dev/axonia-edge:<version>`
- `oci://ghcr.io/axonia-dev/charts/axonia`

## Build from source

| Part | Directory | Gate |
| --- | --- | --- |
| Backend (Java 26, Spring Boot, Docker for its tests) | `backend/` | `./mvnw verify` |
| Frontend (Node 24, pnpm) | `frontend/` | `pnpm install && pnpm run verify:selfhosted` |
| Edge (Node 24, pnpm) | `selfhosted-edge/` | `pnpm install && pnpm run verify` |
| Compose file and Helm chart | `deploy/` | `bash deploy/helm/check.sh` |

[`CONTRIBUTING.md`](CONTRIBUTING.md) explains how changes reach this repository.

## License

axonia is [Fair Source](https://fair.io): source-available under the [Functional Source License, Version 1.1, ALv2 Future License](LICENSE) (FSL-1.1-ALv2).
It is not an open source license.

Any use is permitted except a Competing Use, which the license defines as "making the Software available to others in a commercial product or service that: 1. substitutes for the Software; 2. substitutes for any other product or service we offer using the Software that exists as of the date we make the Software available; or 3. offers the same or substantially similar functionality as the Software."
Running it for a company's own communities is internal use, and free.
Each release becomes available under the Apache License, Version 2.0, on the second anniversary of its release; its release notes give the date.

- [`SECURITY.md`](SECURITY.md): reporting a vulnerability.
- [`TRADEMARKS.md`](TRADEMARKS.md): using the name and the logo.
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md): the Contributor Covenant 2.1.
