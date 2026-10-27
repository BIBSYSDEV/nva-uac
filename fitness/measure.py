#!/usr/bin/env python3
"""Measures nva-uac against the fitness budgets in PLAN.md section 8 and writes a report.

Needs the AWS CLI with credentials for the account, and a bearer token for the API in the
UAC_TOKEN environment variable. Run with python3 -I so nothing is imported from the working
directory.
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Union

WARM_P95_BUDGET_MS = 50
RESTORE_P95_BUDGET_MS = 1000
EXPECTED_STATUSES = frozenset({200, 404})
CONNECTION_ERROR_STATUS = "error"
TOKEN_VARIABLE = "UAC_TOKEN"
REQUEST_TIMEOUT_SECONDS = 30
SECONDS_PER_HOUR = 3600
MILLISECONDS_PER_SECOND = 1000
REPORT_LINE_MARKER = "REPORT RequestId"
RESTORE_DURATION_MARKER = "Restore Duration:"
INIT_DURATION_MARKER = "Init Duration:"
DEFAULT_RESULTS_DIRECTORY = Path(__file__).resolve().parent / "results"
EXIT_PASSED = 0
EXIT_FAILED = 1

Summary = dict[str, Union[float, None]]
Statuses = dict[Union[int, str], int]


def aws(*arguments: str) -> dict:
    command = ["aws", *arguments, "--output", "json"]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return json.loads(completed.stdout) if completed.stdout.strip() else {}


def percentile(samples: list[float], fraction: float) -> float | None:
    if not samples:
        return None
    ordered = sorted(samples)
    index = min(len(ordered) - 1, round(fraction * (len(ordered) - 1)))
    return ordered[index]


def summarise(samples: list[float]) -> Summary:
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


def resolve_function(name_or_prefix: str) -> dict:
    """Returns the configuration of the one function whose name is, or starts with, the argument."""
    functions = aws("lambda", "list-functions").get("Functions", [])
    matches = [function for function in functions if function["FunctionName"].startswith(name_or_prefix)]
    exact = [function for function in matches if function["FunctionName"] == name_or_prefix]
    if exact:
        return exact[0]
    if len(matches) != 1:
        names = ", ".join(sorted(function["FunctionName"] for function in matches)) or "none"
        raise SystemExit(f"{name_or_prefix} must name exactly one function, matches: {names}")
    return matches[0]


def log_group_of(function: dict) -> str:
    """The log group the function writes to: its logging configuration, or the Lambda default."""
    configured = function.get("LoggingConfig", {}).get("LogGroup")
    return configured or f"/aws/lambda/{function['FunctionName']}"


def durations_from_logs(log_group: str, hours: int, marker: str) -> list[float]:
    start = int((time.time() - hours * SECONDS_PER_HOUR) * MILLISECONDS_PER_SECOND)
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


def warm_latencies(base_url: str, path: str, token: str, requests: int) -> tuple[list[float], Statuses]:
    """Latencies of the responses that arrived, and a count per status.

    Connection failures and timeouts count under "error" and contribute no latency.
    """
    latencies: list[float] = []
    statuses: Statuses = {}
    for _ in range(requests):
        request = urllib.request.Request(base_url + path, headers={"Authorization": f"Bearer {token}"})
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                status: int | str = response.status
                response.read()
        except urllib.error.HTTPError as error:
            status = error.code
        except (urllib.error.URLError, TimeoutError):
            status = CONNECTION_ERROR_STATUS
        if status != CONNECTION_ERROR_STATUS:
            latencies.append((time.perf_counter() - started) * MILLISECONDS_PER_SECOND)
        statuses[status] = statuses.get(status, 0) + 1
    return latencies, statuses


def integration_latency(api_id: str, hours: int) -> dict[str, float]:
    """The gateway's integration latency percentiles over one period covering the whole window."""
    end = datetime.datetime.now(datetime.timezone.utc)
    start = end - datetime.timedelta(hours=hours)
    result = aws(
        "cloudwatch", "get-metric-statistics", "--namespace", "AWS/ApiGateway",
        "--metric-name", "IntegrationLatency", "--dimensions", f"Name=ApiId,Value={api_id}",
        "--start-time", start.isoformat(), "--end-time", end.isoformat(),
        "--period", str(hours * SECONDS_PER_HOUR), "--extended-statistics", "p50", "p95", "p99",
    )
    points = result.get("Datapoints", [])
    if not points:
        return {}
    return points[0].get("ExtendedStatistics", {})


def verdict(value: float | None, budget: int) -> str:
    if value is None:
        return "no data"
    return f"{value:.0f} ms, budget {budget} ms, {'within' if value <= budget else 'OVER'}"


def unexpected_statuses(statuses: Statuses) -> Statuses:
    return {status: count for status, count in statuses.items() if status not in EXPECTED_STATUSES}


def passes(warm: Summary, statuses: Statuses) -> bool:
    """The gate: samples arrived, every status was expected, and the warm p95 is within budget."""
    if warm.get("count", 0) == 0 or unexpected_statuses(statuses):
        return False
    return warm["p95"] <= WARM_P95_BUDGET_MS


def write_report(
    path: Path,
    arguments: argparse.Namespace,
    function_name: str,
    restore: Summary,
    init: Summary,
    warm: Summary,
    statuses: Statuses,
    gateway: dict[str, float],
) -> None:
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        f"# Fitness measurement, {now}",
        "",
        f"Function `{function_name}`, API `{arguments.base_url}{arguments.path}`, "
        f"{arguments.requests} warm requests, logs and gateway metrics over the last {arguments.hours} h.",
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
        f"| Gate | {'passed' if passes(warm, statuses) else 'FAILED'} |",
        "",
        "Budgets: PLAN.md section 8 (warm p95 under 50 ms for lookups by id) and the Phase 0 exit"
        " criterion for cold start. The gate fails on any status outside"
        f" {sorted(EXPECTED_STATUSES)}, on connection errors, and on no samples.",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def restore_or_none(summary: Summary) -> str:
    return "no data" if summary.get("p95") is None else f"{summary['p95']:.0f} ms"


def token_from(arguments: argparse.Namespace) -> str:
    token = os.environ.get(TOKEN_VARIABLE) or arguments.token
    if not token:
        raise SystemExit(f"A bearer token is required: set {TOKEN_VARIABLE} or pass --token")
    return token


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="e.g. https://api.dev.nva.aws.unit.no/uac")
    parser.add_argument("--path", default="/organisations/185.0.0.0")
    parser.add_argument("--token", default=None, help=f"a bearer token with uac/organisation.read; prefer {TOKEN_VARIABLE}")
    parser.add_argument("--function", required=True, help="the Lambda function name, or a prefix matching exactly one")
    parser.add_argument("--api-id", required=True, help="the HTTP API id")
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--hours", type=int, default=24)
    parser.add_argument("--report", type=Path, default=None, help=f"defaults to {DEFAULT_RESULTS_DIRECTORY}/<date>.md")
    arguments = parser.parse_args()
    token = token_from(arguments)

    function = resolve_function(arguments.function)
    log_group = log_group_of(function)
    restore = summarise(durations_from_logs(log_group, arguments.hours, RESTORE_DURATION_MARKER))
    init = summarise(durations_from_logs(log_group, arguments.hours, INIT_DURATION_MARKER))
    latencies, statuses = warm_latencies(arguments.base_url, arguments.path, token, arguments.requests)
    warm = summarise(latencies)
    gateway = integration_latency(arguments.api_id, arguments.hours)

    report = arguments.report or DEFAULT_RESULTS_DIRECTORY / f"{datetime.date.today().isoformat()}.md"
    write_report(report, arguments, function["FunctionName"], restore, init, warm, statuses, gateway)
    print(f"Report written to {report}")
    print(f"Warm p95: {verdict(warm.get('p95'), WARM_P95_BUDGET_MS)}")
    print(f"Restore p95: {verdict(restore.get('p95'), RESTORE_P95_BUDGET_MS)}")
    print(f"Statuses: {json.dumps(statuses)}")
    return EXIT_PASSED if passes(warm, statuses) else EXIT_FAILED


if __name__ == "__main__":
    sys.exit(main())
