#!/usr/bin/env python3
"""Render report.md from a run dir's collected artifacts (k6 summary, top, events, slow queries,
screenshots). Stdlib only. Also appends a comparison row vs the previous run of the same scenario+stage.

    python3 perf/report/report.py [run-dir]
"""
import json, os, sys, glob, re

run = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob("perf/results/2*"))[-1]
run = run.rstrip("/")
name = os.path.basename(run)
m = re.match(r"(\d+-\d+)-([a-z]+)-([a-z0-9]+)", name)
_, scenario, stage = (m.groups() if m else ("", "?", "?"))

def load_summary(d):
    p = os.path.join(d, "summary.json")
    if not os.path.exists(p): return {}
    with open(p) as f: return json.load(f)

s = load_summary(run)
metrics = s.get("metrics", {})
def g(metric, field, default=None):
    return metrics.get(metric, {}).get(field, default)

reqs   = g("http_reqs", "count", 0)
rate   = g("http_reqs", "rate", 0)
failed = g("http_req_failed", "value", 0)
dur    = metrics.get("http_req_duration", {})
p50, p95, p99 = dur.get("med"), dur.get("p(95)"), dur.get("p(99)")
vus_max = g("vus_max", "value") or g("vus", "max")

def read(f):
    p = os.path.join(run, f)
    return open(p).read().strip() if os.path.exists(p) else ""

shots = sorted(glob.glob(os.path.join(run, "screenshots", "*.png")))

def fmt(x, suffix=""):
    return f"{x:.1f}{suffix}" if isinstance(x, (int, float)) else "n/a"

out = [f"# Performance Report — `{name}`", ""]
out += [f"**Scenario:** {scenario}  **Stage:** {stage}", ""]
out += ["## Headline", "",
        "| Metric | Value |", "|---|---|",
        f"| Requests | {int(reqs):,} |",
        f"| Throughput | {fmt(rate,' req/s')} |",
        f"| Error rate | {fmt((failed or 0)*100,'%')} |",
        f"| Latency P50 | {fmt(p50,' ms')} |",
        f"| Latency P95 | {fmt(p95,' ms')} |",
        f"| Latency P99 | {fmt(p99,' ms')} |",
        f"| Peak VUs | {vus_max if vus_max else 'n/a'} |", ""]

# comparison vs previous run of same scenario+stage
prev = [d for d in sorted(glob.glob(f"perf/results/*-{scenario}-{stage}")) if d.rstrip('/') != run]
if prev:
    ps = load_summary(prev[-1])
    pm = ps.get("metrics", {})
    pv = lambda a, b: pm.get(a, {}).get(b)
    out += ["## vs previous run", "",
            f"_baseline: `{os.path.basename(prev[-1])}`_", "",
            "| Metric | Previous | Current |", "|---|---|---|",
            f"| Throughput (req/s) | {fmt(pv('http_reqs','rate'))} | {fmt(rate)} |",
            f"| P95 (ms) | {fmt(pv('http_req_duration','p(95)'))} | {fmt(p95)} |",
            f"| Error rate | {fmt((pv('http_req_failed','value') or 0)*100,'%')} | {fmt((failed or 0)*100,'%')} |", ""]

if shots:
    out += ["## Dashboards", ""]
    for png in shots:
        rel = os.path.relpath(png, run)
        title = os.path.basename(png).replace(".png", "").replace("-", " ").title()
        out += [f"### {title}", "", f"![{title}]({rel})", ""]

slow = read("slow-queries.txt")
if slow:
    out += ["## Slowest SQL (pg_stat_statements)", "", "```", slow, "```", ""]
top = read("pods-top.txt")
if top:
    out += ["## Pod resource usage (end of run)", "", "```", top, "```", ""]
ev = read("events.txt")
if ev:
    out += ["## Kubernetes events (scaling / restarts / OOM)", "", "```", ev, "```", ""]

path = os.path.join(run, "report.md")
with open(path, "w") as f: f.write("\n".join(out) + "\n")
print(f">> {path}")
