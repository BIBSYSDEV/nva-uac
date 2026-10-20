# fitness

The fitness tests of PLAN.md section 8, run against a deployed stack: warm latency client-side,
the gateway's integration latency, and SnapStart restore and cold-init durations read from the
function's logs. The script needs only Python 3 and the AWS CLI with credentials for the account.

```
python3 -I fitness/measure.py \
  --base-url https://api.dev.nva.aws.unit.no/uac \
  --token "$TOKEN" \
  --function nva-uac-dev-Function-ABC123 \
  --log-group /aws/lambda/nva-uac-dev-Function-ABC123 \
  --api-id a1b2c3d4
```

It writes `results/<date>.md` with each measure against its budget and exits non-zero when the
warm p95 is over budget, so it can gate a pipeline stage. Results are committed, so the
numbers that settle D10 in the Phase 0 record are in the repository.

Until the first rehearsal migration has loaded data, the default path answers 404; that still
exercises token validation, routing and the database round trip, so the figures are meaningful,
if slightly optimistic. Re-run against real identifiers once data is there.

To measure cold starts deliberately, publish a new version or change an environment variable so
new execution environments are created, send a burst of parallel requests, then run the script;
restores appear as `Restore Duration` in the REPORT lines, inits without SnapStart as
`Init Duration`.
