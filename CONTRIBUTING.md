# Contributing

Issues and pull requests are welcome.
For anything larger than a fix, open an issue first, so the approach can be agreed before the work.

## How changes reach this repository

This repository is published from a private repository that also holds the hosted Cloud edition.
Every commit on `main` arrives through that export, with its message and its author (the maintainer's commits under one public identity); nobody pushes to `main` directly.

A pull request is reviewed here as usual.
Once it is approved, the maintainer imports it into the private repository, where it runs every gate of both editions, and lands it there.
The next export publishes it, with its commits and their authors kept, and GitHub then shows the pull request as merged.
A pull request is imported at the head it was reviewed at: a commit pushed after the import is not part of it, and needs the pull request imported again.

A pull request can change only files this repository carries.
Some files are the Self-hosted edition's view of code both editions share, so the maintainer may ask for a change to be made differently, or make it in the private repository and credit the pull request.

## Contributor License Agreement

Every contributor signs the [Contributor License Agreement](CLA.md) once, through the CLA check on their first pull request.
It grants a license to the contributions and keeps their copyright with the contributor.
A contribution made for an employer may also need the [corporate agreement](CLA-corporate.md), signed by the employer.
A pull request is imported only once its CLA check is green.

## Building and testing

| Part | Directory | Needs | Gate |
| --- | --- | --- | --- |
| Backend | `backend/` | Java 26, Docker (for Testcontainers) | `./mvnw verify` |
| Frontend | `frontend/` | Node 24, pnpm | `pnpm install && pnpm run verify:selfhosted` |
| Edge | `selfhosted-edge/` | Node 24, pnpm | `pnpm install && pnpm run verify` |
| Compose file and Helm chart | `deploy/` | curl (the tools download themselves, pinned) | `bash deploy/helm/check.sh` |

Every gate is a hard gate: lint, formatting, type checks, tests with coverage thresholds and duplication checks all fail the build.
CI runs all of them on every pull request.

In `frontend/`, `pnpm dev:selfhosted` serves the app, `pnpm verify:selfhosted` runs its gate, and `pnpm exec playwright test --config playwright.selfhosted.config.ts` runs its browser specs.
[`deploy/dev/README.md`](deploy/dev/README.md) runs the whole edition on one machine, with PostgreSQL, a stand-in identity provider and a mail catcher.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org): a type, an optional scope, and a lowercase subject that says what changes for whoever uses the software, for example `fix(frontend): the event page keeps its scroll position after a reply`.
Messages are published as written, so they name no customer, person or internal system.

## Code of conduct and security

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).
Report a vulnerability as [`SECURITY.md`](SECURITY.md) says, never in an issue or a pull request.
