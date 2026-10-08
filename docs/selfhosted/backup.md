# Backup and restore

## What to back up

- The database, with point-in-time recovery.
  Rehearse one restore before calling the installation live.
- The settings key, `AXONIA_SETTINGS_KEY`, and every previous key a backup still needs, kept apart from the database backups.
  In Kubernetes it is the Secret `<release>-settings-key` unless `settingsKey.existingSecret` names another.
- The secrets the deployment holds (the compose values file, or the operator's Secret): a restore needs the database passwords the roles were created with.
- The images: the media volume, or the bucket or container with its own backup or replication.

Copy the chart's settings key out of the cluster with:

```sh
kubectl --namespace axonia get secret axonia-settings-key -o jsonpath='{.data.AXONIA_SETTINGS_KEY}' | base64 --decode
```

## Losing the settings key

A database restored without its settings key loses only what the key seals: the stored mail server password.
The mail server card then reads "Password can’t be read" and "Mail is not sent until the password is entered again."; an Owner enters it again, and nothing else is lost.
A mail password set by the deployment (`AXONIA_MAIL_PASSWORD`) is not stored, so it is not lost either.

## Erased data in backups

A backup keeps what was erased after it was taken.
Keep backups only as long as the company's retention allows, and a restore brings erased people back until they are erased again.

## Restoring

1. Restore the database to a point in time.
2. Restore the images to the same point or later, so no image the database names is missing.
3. Start the release that took the backup, with the settings key of that time and its previous keys, and the database passwords the roles were created with.
4. Upgrade to a later release if needed (`upgrading.md`).
