# fitness

The fitness tests of PLAN.md section 8, run against a deployed stack: warm latency client-side,
the gateway's integration latency, and SnapStart restore and cold-init durations read from the
function's logs. The script needs only Python 3 and the AWS CLI with credentials for the account.

```
export UAC_TOKEN="$(...)"
python3 -I fitness/measure.py \
  --base-url https://api.dev.nva.aws.unit.no/uac \
  --function nva-uac-dev-Function \
  --api-id a1b2c3d4
```

- **Token.** Read from `UAC_TOKEN`, so it stays out of the process list and shell history;
  `--token` remains as a fallback. The token is one the stack's JWT authoriser accepts: issued
  by the NVA Cognito user pool for the backend client, with the read scope. Fetching it inside
  the script waits on the backend client's credentials being available to the runner, so for
  Phase 0 it is obtained by hand.
- **Function.** The full name, or a prefix that matches exactly one function in the account;
  the script lists the functions and refuses an ambiguous prefix.
- **Log group.** Not an argument: the script reads it from the function's logging
  configuration, which the stack sets explicitly, and falls back to the Lambda default.

It writes `results/<date>.md`, resolved relative to the script, with each measure against its
budget, and exits non-zero when the gate fails: the warm p95 over budget, no samples, any
response status outside 200 and 404, or any connection error or timeout. Results are committed,
so the numbers that settle D10 in the Phase 0 record are in the repository.

The gateway integration latency is measured over one CloudWatch period spanning the whole
window, so it covers the same hours the header states.

Until the first rehearsal migration has loaded data, the default path answers 404; that still
exercises token validation, routing and the database round trip, so the figures are meaningful,
if slightly optimistic. Re-run against real identifiers once data is there.

To measure cold starts deliberately, publish a new version or change an environment variable so
new execution environments are created, send a burst of parallel requests, then run the script;
restores appear as `Restore Duration` in the REPORT lines, inits without SnapStart as
`Init Duration`.

The unit tests of the gate, the error counting and the function resolution run with the
standard library:

```
python3 -I -m unittest discover -s fitness
```
