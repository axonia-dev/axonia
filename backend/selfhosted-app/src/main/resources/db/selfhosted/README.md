Migrations only the Self-hosted edition runs.
Another edition may add its own chain beside this one; this one never depends on it.
A new migration takes the next number after the highest one in any chain.
A change to anything the open core owns belongs in `db/migration`.
Once released, a file here is a public contract: it is never edited, renumbered or removed.
This file also keeps the folder in the built jar; a missing folder refuses the start (`fail-on-missing-locations`).
