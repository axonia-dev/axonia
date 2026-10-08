# Security policy

## Reporting a vulnerability

Report a vulnerability privately, through this repository's **Security** tab: **Report a vulnerability**.
Where GitHub is not an option, write to security@axonia.dev.

Never report a vulnerability in a public issue, pull request or discussion.

A report is most useful with:

- the release, and whether the installation runs Docker Compose or the Helm chart;
- the steps that reproduce the issue, or a proof of concept;
- what an attacker gains, and what they need first (a member account, an Owner, network access).

## What happens next

- An acknowledgement within five working days.
- An assessment, and a fix released as a new patch version of every supported minor release.
- A security advisory once the fix is released, crediting the reporter unless they prefer otherwise.

Please allow the fix to be released before disclosing the issue.

## Supported versions

The latest minor release receives security fixes.
Upgrading (`docs/selfhosted/upgrading.md`) is the remedy for any earlier one.

## Scope

Everything in this repository: the backend, the edge, the frontend, the compose file and the Helm chart.
The hosted Cloud edition takes reports through the same channels.
