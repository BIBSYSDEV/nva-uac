#!/usr/bin/env python3
"""Measures nva-uac against the fitness budgets in PLAN.md section 8 and writes a report.

Needs the AWS CLI with credentials for the account, and a bearer token for the API. Run with
python3 -I so nothing is imported from the working directory.
"""
import argparse
import datetime
import json
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request

WARM_P95_BUDGET_MS = 50
RESTORE_P95_BUDGET_MS = 1000
REPORT_LINE_MARKER = "REPORT RequestId"
RESTORE_DURATION_MARKER = "Restore Duration:"
INIT_DURATION_MARKER = "Init Duration:"


def aws(*arguments):
    command = ["aws", *arguments, "--output", "json"]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return json.loads(completed.stdout) if completed.stdout.strip() else {}


def percentile(samples, fraction):
    if not samples:
        return None
    ordered = sorted(samples)
    index = min(len(ordered) - 1, round(fraction * (len(ordered) - 1)))
    return ordered[index]


def summarise(samples):
    if not samples:
        return {"count": 0}
    return {
        "count": len(samples),
        "p50": percentile(samples, 0.50),
        "p95": percentile(samples, 0.95),
        "p99": percentile(samples, 0.99),
        "max": max(samples),
        "mean": statistics.fmean(samples),
    }


def durations_from_logs(log_group, hours, marker):
    start = int((time.time() - hours * 3600) * 1000)
    events = aws(
        "logs", "filter-log-events", "--log-group-name", log_group,
        "--start-time", str(start), "--filter-pattern", f'"{REPORT_LINE_MARKER}" "{marker}"',
    ).get("events", [])
    durations = []
    for event in events:
        message = event["message"]
        if marker in message:
            after = message.split(marker, 1)[1].strip()
            durations.append(float(after.split(" ", 1)[0]))
    return durations


def warm_latencies(base_url, path, token, requests):
    latencies, statuses = [], {}
    for _ in range(requests):
        request = urllib.request.Request(base_url + path, headers={"Authorization": f"Bearer {token}"})
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status = response.status
                response.read()
        except urllib.error.HTTPError as error:
            status = error.code
        latencies.append((time.perf_counter() - started) * 1000)
        statuses[status] = statuses.get(status, 0) + 1
    return latencies, statuses


def integration_latency(api_id, hours):
    end = datetime.datetime.now(datetime.timezone.utc)
    start = end - datetime.timedelta(hours=hours)
    result = aws(
        "cloudwatch", "get-metric-statistics", "--namespace", "AWS/ApiGateway",
        "--metric-name", "IntegrationLatency", "--dimensions", f"Name=ApiId,Value={api_id}",
        "--start-time", start.isoformat(), "--end-time", end.isoformat(),
        "--period", "3600", "--extended-statistics", "p50", "p95", "p99",
    )
    points = result.get("Datapoints", [])
    if not points:
        return {}
    latest = max(points, key=lambda point: point["Timestamp"])
    return latest.get("ExtendedStatistics", {})


def verdict(value, budget):
    if value is None:
        return "no data"
    return f"{value:.0f} ms, budget {budget} ms, {'within' if value <= budget else 'OVER'}"


def write_report(path, arguments, restore, init, warm, statuses, gateway):
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        f"# Fitness measurement, {now}",
        "",
        f"Function `{arguments.function}`, API `{arguments.base_url}{arguments.path}`, "
        f"{arguments.requests} warm requests, logs over the last {arguments.hours} h.",
        "",
        "| Measure | Result |",
        "|---|---|",
        f"| Warm p95, client-side | {verdict(warm.get('p95'), WARM_P95_BUDGET_MS)} |",
        f"| Warm p50 / p99 / max | {warm.get('p50', 0):.0f} / {warm.get('p99', 0):.0f} / {warm.get('max', 0):.0f} ms |",
        f"| Gateway integration latency p50 / p95 / p99 | "
        f"{gateway.get('p50', float('nan')):.0f} / {gateway.get('p95', float('nan')):.0f} / {gateway.get('p99', float('nan')):.0f} ms |",
        f"| SnapStart restore p95 | {verdict(restore.get('p95'), RESTORE_P95_BUDGET_MS)} ({restore.get('count', 0)} restores) |",
        f"| Cold init p95, without SnapStart | {restore_or_none(init)} ({init.get('count', 0)} inits) |",
        f"| Response statuses | {json.dumps(statuses)} |",
        "",
        "Budgets: PLAN.md section 8 (warm p95 under 50 ms for lookups by id) and the Phase 0 exit"
        " criterion for cold start.",
    ]
    with open(path, "w", encoding="utf-8") as report:
        report.write("\n".join(lines) + "\n")


def restore_or_none(summary):
    return "no data" if summary.get("p95") is None else f"{summary['p95']:.0f} ms"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="e.g. https://api.dev.nva.aws.unit.no/uac")
    parser.add_argument("--path", default="/organisations/185.0.0.0")
    parser.add_argument("--token", required=True, help="a bearer token with uac/organisation.read")
    parser.add_argument("--function", required=True, help="the Lambda function name")
    parser.add_argument("--log-group", required=True, help="the function's log group")
    parser.add_argument("--api-id", required=True, help="the HTTP API id")
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--hours", type=int, default=24)
    parser.add_argument("--report", default=None)
    arguments = parser.parse_args()

    restore = summarise(durations_from_logs(arguments.log_group, arguments.hours, RESTORE_DURATION_MARKER))
    init = summarise(durations_from_logs(arguments.log_group, arguments.hours, INIT_DURATION_MARKER))
    latencies, statuses = warm_latencies(arguments.base_url, arguments.path, arguments.token, arguments.requests)
    warm = summarise(latencies)
    gateway = integration_latency(arguments.api_id, arguments.hours)

    report = arguments.report or f"fitness/results/{datetime.date.today().isoformat()}.md"
    write_report(report, arguments, restore, init, warm, statuses, gateway)
    print(f"Report written to {report}")
    print(f"Warm p95: {verdict(warm.get('p95'), WARM_P95_BUDGET_MS)}")
    print(f"Restore p95: {verdict(restore.get('p95'), RESTORE_P95_BUDGET_MS)}")
    return 0 if (warm.get("p95") or 0) <= WARM_P95_BUDGET_MS else 1


if __name__ == "__main__":
    sys.exit(main())
