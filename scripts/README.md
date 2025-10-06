# Preflight duplicate report (Oracle)

This folder contains a non-destructive preflight SQL and a small helper script to run it against a local Oracle XE container named `oracle-xe`.

Files
- `preflight-report-duplicates-oracle.sql` — non-destructive SQL that lists duplicate `(session_id, payload_id)` groups and sample rows.
- `run-preflight-xe.sh` — convenience script that copies the SQL into a running `oracle-xe` container and executes it, saving output to `artifacts/`.

Usage

1. Ensure a local Oracle XE container is running and reachable as `oracle-xe` (we use `gvenzl/oracle-xe` in the dev container).

2. Run the helper (it will create `scripts/artifacts/` and write a timestamped output file):

```bash
chmod +x scripts/run-preflight-xe.sh
./scripts/run-preflight-xe.sh
# or specify an output filename
./scripts/run-preflight-xe.sh scripts/artifacts/preflight-output.txt
```

3. Inspect the output file for duplicate groups and sample rows. If duplicates exist in production, export them and back them up before running the destructive dedupe migration.

Notes
- The helper assumes the XE container is named `oracle-xe` and that the SYS password is `Password123` (the defaults used for local testing in this workspace). Adjust the script or run manually for other setups.
- The SQL file is intentionally non-destructive and safe to run in read-only preflight checks.

If you want, I can open a small PR adding these files to the repo (committed locally here). Let me know if you'd prefer different defaults for the helper.
