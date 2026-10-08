# Upgrading and rotating secrets

## Versions

Releases follow Semantic Versioning: a minor or patch release needs no change to the configuration; a major release lists every change it needs in its release notes.

## Upgrading

- Read the release notes of every release between the current and the target.
- Back up first (`backup.md`): the database migrations run forward only, and the only way back from a migration is a restore.
- The owner role (`DATABASE_OWNER_USERNAME`) needs `CREATE` on the database, which the database's owner holds: an upgrade that adds a schema, such as `scheduler`, fails its migration without it.
  As that role, `SELECT has_database_privilege(current_user, current_database(), 'CREATE')` answers `t` when it holds it.
- Move both images to the same release together: a backend and an edge of different releases are not supported.
- The first start of a new release migrates the database before it answers; a long migration delays readiness; on Kubernetes with a bucket, the old pod serves until the new one is ready.
- With images on a volume (`media.type: local`), the chart replaces the pod rather than rolling it, so an upgrade is down from the old pod's stop until the new one is ready, the migration included.
- Downgrading is a restore of the backup taken before the upgrade, with the earlier images.
- A release whose compose file moves Postgres to a new major version says so in its release notes: the data volume of one major does not start under the next, so the move is a dump with the old image and a restore into an empty volume with the new one.

With Docker Compose, set `AXONIA_VERSION` in `deploy/.env` to the new release, then:

```sh
docker compose pull && docker compose up -d
```

With the Helm chart, the release's chart and the installation's values:

```sh
helm upgrade axonia oci://ghcr.io/axonia-dev/charts/axonia --version <release> --namespace axonia --values values.yaml
```

## Rotating the settings key

The settings key, `AXONIA_SETTINGS_KEY`, encrypts the secrets an Owner stores in the application: today, the mail server's password.

- Each backend instance re-seals a stored secret under its own current key, at its start and at its check of the stored settings every five seconds, when the secret was sealed under a key `AXONIA_SETTINGS_PREVIOUS_KEYS` lists.
  Each re-seal logs a line of this shape, the key identifiers varying: `Re-sealed the stored mail server password from settings key e4caaa0c to settings key bff04208.`
  An instance that finds the secret already under its current key logs nothing about the key.
- An instance that holds no key opening the stored password logs `The stored mail server password was sealed with a settings key this installation no longer holds; an Owner must enter it again.` and the mail server card reads "Password can’t be read": put the old key back in `AXONIA_SETTINGS_PREVIOUS_KEYS` and restart, or enter the password again.
- With one backend instance: set the new key as `AXONIA_SETTINGS_KEY` and the old one in `AXONIA_SETTINGS_PREVIOUS_KEYS`, restart, and drop the old key once no backup you may restore still needs it (`backup.md`).
- With more than one instance, in three deploys, each finished on every instance before the next: add the new key to `AXONIA_SETTINGS_PREVIOUS_KEYS`; then make it `AXONIA_SETTINGS_KEY`, the old one previous; then drop the old one, as above.
- Before dropping the old key, wait for the deploy that made the new key current to finish on every instance, then at least ten more seconds.
  While instances on the old and the new current key run together, each may re-seal the password under its own, and only once the last old one is gone does the next five-second check leave it under the new key.
  The same holds for one instance whose update rolls, starting the new instance before stopping the old one, as the Helm chart does with a bucket.
- A malformed key refuses the start and names the variable, never the value.

With Docker Compose, the keys are lines in `deploy/.env`; recreate the backend after each change (`docker compose up -d backend`).

With the Helm chart, rotate through a Secret of your own named in `settingsKey.existingSecret`, never by editing the Secret the chart generated.
The chart re-renders its generated Secret into every release revision, so `helm rollback` to a revision rendered before an edit puts the old key back, and every secret sealed under the new key since reads "Password can’t be read".
A Secret the chart only names is the operator's: no upgrade and no rollback rewrites it.

The first rotation moves the key into that Secret, `axonia-settings-keys` here, for a release named `axonia` with one backend instance:

```sh
NEW=$(openssl rand -base64 32)
OLD=$(kubectl --namespace axonia get secret axonia-settings-key -o jsonpath='{.data.AXONIA_SETTINGS_KEY}' | base64 -d)
kubectl --namespace axonia create secret generic axonia-settings-keys \
  --from-literal=AXONIA_SETTINGS_KEY="$NEW" --from-literal=AXONIA_SETTINGS_PREVIOUS_KEYS="$OLD"
```

Then set `settingsKey.existingSecret: axonia-settings-keys` in `values.yaml` and upgrade, which restarts the backend on the new key:

```sh
helm upgrade axonia oci://ghcr.io/axonia-dev/charts/axonia --version <release> --namespace axonia --values values.yaml
```

The chart's generated Secret stays where it was, holding the old key, because an uninstall or an upgrade never deletes it.
Once that upgrade's rollout has finished (`kubectl --namespace axonia rollout status deployment/axonia-backend`), wait at least ten more seconds.
Once no backup you may restore still needs the old key, drop it and restart:

```sh
kubectl --namespace axonia patch secret axonia-settings-keys --type json \
  -p '[{"op":"remove","path":"/data/AXONIA_SETTINGS_PREVIOUS_KEYS"}]'
kubectl --namespace axonia rollout restart deployment/axonia-backend
```

A later rotation changes the same Secret: the new key current, the old one previous, then a restart.

With more than one backend instance, the same steps in three deploys, each rollout finished before the next:

```sh
NEW=$(openssl rand -base64 32)
OLD=$(kubectl --namespace axonia get secret axonia-settings-key -o jsonpath='{.data.AXONIA_SETTINGS_KEY}' | base64 -d)

# 1. The new key, as a previous key only; then settingsKey.existingSecret: axonia-settings-keys in values.yaml.
kubectl --namespace axonia create secret generic axonia-settings-keys \
  --from-literal=AXONIA_SETTINGS_KEY="$OLD" --from-literal=AXONIA_SETTINGS_PREVIOUS_KEYS="$NEW"
helm upgrade axonia oci://ghcr.io/axonia-dev/charts/axonia --version <release> --namespace axonia --values values.yaml
kubectl --namespace axonia rollout status deployment/axonia-backend

# 2. The new key current, the old one previous.
kubectl --namespace axonia patch secret axonia-settings-keys --type merge \
  -p "{\"stringData\":{\"AXONIA_SETTINGS_KEY\":\"$NEW\",\"AXONIA_SETTINGS_PREVIOUS_KEYS\":\"$OLD\"}}"
kubectl --namespace axonia rollout restart deployment/axonia-backend
kubectl --namespace axonia rollout status deployment/axonia-backend
# Old and new instances may re-seal the password back and forth until the rollout ends; one five-second check after
# it, every instance has left it under the new key.
sleep 10

# 3. Once no backup you may restore still needs it, the old key dropped.
kubectl --namespace axonia patch secret axonia-settings-keys --type json \
  -p '[{"op":"remove","path":"/data/AXONIA_SETTINGS_PREVIOUS_KEYS"}]'
kubectl --namespace axonia rollout restart deployment/axonia-backend
```

Keep `NEW` and `OLD` until the last step, or read them back from the Secret.

A rollback across a rotation needs the newer key among the previous keys of the Secret the rolled-back release reads.
`helm rollback` to a revision from before `settingsKey.existingSecret` was set points the backend at the chart's generated Secret again, which holds only the old key: add the newer key there as `AXONIA_SETTINGS_PREVIOUS_KEYS`, and restart the backend.

```sh
kubectl --namespace axonia patch secret axonia-settings-key --type merge \
  -p "{\"stringData\":{\"AXONIA_SETTINGS_PREVIOUS_KEYS\":\"$NEW\"}}"
kubectl --namespace axonia rollout restart deployment/axonia-backend
```

Every later upgrade keeps that previous key, as it found it.

## Rotating other secrets

- `DATABASE_PASSWORD` and `DATABASE_COMMUNICATION_READ_PASSWORD`: the first start created the roles with them, and a later start does not change them.
  Run `ALTER ROLE axonia_app PASSWORD '<new>'` (or `axonia_communication_read`) as the owner role, then change the variable and restart the backend.
- `AXONIA_EDGE_SHARED_SECRETS`: add the new secret to the backend's list, restart it, change the edge's `EDGE_SHARED_SECRET` and restart it, then drop the old one from the list.
- With Docker Compose, where the backend's list defaults to `EDGE_SHARED_SECRET`: set `AXONIA_EDGE_SHARED_SECRETS=<new>,<old>` in `.env` and recreate the backend (`docker compose up -d backend`); then set `EDGE_SHARED_SECRET` to the new one and recreate the edge (`docker compose up -d edge`); then remove the `AXONIA_EDGE_SHARED_SECRETS` line and recreate the backend.
- With Docker Compose, `DATABASE_OWNER_USERNAME` and `DATABASE_OWNER_PASSWORD` also create the Postgres service's owner role, and only on an empty volume: changing either in `.env` afterwards changes what the backend sends, not the role, and the start fails.
  Change the role first (`ALTER ROLE axonia_owner PASSWORD '<new>'`, as that role), then `.env`, then recreate the backend.
- `SESSION_COOKIE_KEY`: changing it signs everybody out; every edge instance needs the same key.
- `OIDC_CLIENT_SECRET`: add the new secret at the identity provider, change the variable, restart the edge, then delete the old secret there.
  An expired secret signs everybody out at their next renewal.
- `AXONIA_UNSUBSCRIBE_SECRET`: changing it breaks the unsubscribe links of mail already sent.

With the Helm chart, each of these is a key of the Secret `existingSecret` names: change it there (`kubectl --namespace axonia edit secret axonia`, or the tool that manages it), then restart the container that reads it (`kubectl --namespace axonia rollout restart deployment/axonia-backend` or `deployment/axonia-edge`).
