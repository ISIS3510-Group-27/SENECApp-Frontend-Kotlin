"""Profile Events on the physical Xiaomi and save results under captures/sprint4."""
import argparse
import csv
import json
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

PACKAGE = "com.senecapp"
ACTIVITY = PACKAGE + "/.benchmark.EventsBenchmarkActivity"
HERE = Path(__file__).resolve().parent


def percentile(values, percentile):
    ordered = sorted(values)
    rank = (len(ordered) - 1) * percentile / 100
    low = int(rank)
    return ordered[low] + (rank - low) * (ordered[min(low + 1, len(ordered) - 1)] - ordered[low])


def frames(raw):
    since = field_int(raw, "Stats since:")
    header = None
    for line in raw.splitlines():
        line = line.strip()
        if line.startswith("Flags,"):
            header = line.rstrip(",").split(",")
        elif header and re.match(r"^\d+,", line):
            row = dict(zip(header, map(int, line.rstrip(",").split(","))))
            start, finish = row.get("IntendedVsync", 0), row.get("FrameCompleted", 0)
            # A completed frame can carry other flags on this device.
            if not row["Flags"] & (1 << 3) and since <= start < finish < 2**63 - 1:
                yield (finish - start) / 1e6


def field_int(text, label):
    match = re.search(r"(?m)^\s*" + re.escape(label) + r"\s*(\d+)", text)
    return int(match[1]) if match else 0


def summarize(runs):
    summary = {"frames": {}, "memory": {}}
    for group in ("open", "scroll-before", "apply-filter", "clear-filter", "scroll-after"):
        phases = [p for details, _ in runs for p in details["phases"] if p["phase"].startswith(group)]
        samples = [sample for _, values in runs for name, frames in values.items()
                   if name.startswith(group) for sample in frames]
        rendered = sum(p["rendered"] for p in phases)
        janky = sum(p["janky"] for p in phases)
        summary["frames"][group] = dict(windows=len(phases), rendered=rendered, janky=janky,
            raw_frames=len(samples), jank_pct=100 * janky / rendered if rendered else None,
            average_ms=statistics.mean(samples) if samples else None,
            p95_ms=percentile(samples, 95) if samples else None,
            max_ms=max(samples) if samples else None,
            cpu_mean_ms=statistics.mean(p["cpu_ms"] for p in phases),
            wall_mean_s=statistics.mean(p["wall_s"] for p in phases))
    for stage in ("before", "opened", "after"):
        snapshots = [details["memory"][stage] for details, _ in runs]
        summary["memory"][stage] = {key: statistics.mean(s[key] for s in snapshots)
                                     for key in snapshots[0] if all(key in s for s in snapshots)}
    return summary


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--adb", default="adb")
    cli.add_argument("--serial", required=True)
    cli.add_argument("--output", required=True, type=Path)
    cli.add_argument("--runs", type=int, default=3)
    cli.add_argument("--trace", action="store_true", help="Record a separate diagnostic run after timed runs")
    args = cli.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, "-s", args.serial]

    def call(*tokens, raw=False):
        stdout = subprocess.check_output([*adb, *tokens], timeout=60)
        return stdout if raw else stdout.decode("utf-8", errors="replace").strip()

    def shell(*cmd):
        return call("shell", *cmd)

    def save(path, data):
        path.write_text(data + "\n", encoding="utf-8")

    def screenshot(path):
        path.write_bytes(call("exec-out", "screencap", "-p", raw=True))

    def nodes():
        target = "/data/local/tmp/senec-sprint4-ui.xml"
        shell("rm", "-f", target)
        for _ in range(2):
            try:
                if "dumped to" in shell("uiautomator", "dump", target):
                    return list(ET.fromstring(shell("cat", target)).iter("node"))
            except (ET.ParseError, subprocess.CalledProcessError):
                continue
        raise RuntimeError("Cannot inspect screen through uiautomator")

    def check_title(expected):
        if not any(node.get("text") == expected for node in nodes()):
            raise RuntimeError(f"Expected {expected!r} on the phone")

    def tap(x, y):
        shell("input", "tap", str(x), str(y))

    def swipe(up):
        a, b = (1230, 305) if up else (280, 1280)
        shell("input", "swipe", "360", str(a), "360", str(b), "500")

    def start():
        shell("am", "force-stop", PACKAGE)
        response = shell("am", "start", "-W", "-n", ACTIVITY)
        if "Status: ok" not in response:
            raise RuntimeError(response)
        time.sleep(2)
        return response

    def memory(directory, step):
        raw = shell("dumpsys", "meminfo", PACKAGE)
        save(directory / f"memory-{step}.txt", raw)
        values = {}
        for label, key in (("TOTAL PSS:", "pss_kib"), ("TOTAL RSS:", "rss_kib")):
            match = re.search(re.escape(label) + r"\s*(\d+)", raw)
            if match:
                values[key] = int(match[1])
        for label in ("Dalvik", "Native"):
            match = re.search(rf"(?m)^\s*{label} Heap\s+((?:\d+\s+){{7}})\d+", raw)
            if match:
                values[f"{label.lower()}_heap_alloc_kib"] = int(match[1].split()[-1])
        if "pss_kib" not in values:
            raise RuntimeError("dumpsys meminfo did not report PSS")
        return values

    def thermal():
        battery = shell("dumpsys", "battery")
        thermal_lines = shell("dumpsys", "thermalservice").splitlines()
        return "\n".join([line for line in battery.splitlines() if re.match(
            r"\s*(level|temperature|USB powered):", line)] +
            [line for line in thermal_lines if "Thermal Status" in line or "mName=CPU" in line])

    if shell("getprop", "ro.product.manufacturer").lower() != "xiaomi":
        raise RuntimeError("This coordinate profile is only for the Xiaomi")
    if shell("getprop", "ro.product.model") != "25078RA3EL":
        raise RuntimeError("Unexpected Xiaomi model")
    size = shell("wm", "size")
    density = shell("wm", "density")
    if size.splitlines()[-1].split()[-1] != "720x1600" or density.splitlines()[-1].split()[-1] != "320":
        raise RuntimeError("Incorrect device display geometry")
    ticks_per_second = int(shell("getconf", "CLK_TCK"))
    save(out / "device.txt", "\n".join(
        [shell("getprop", name) for name in ("ro.product.manufacturer", "ro.product.model",
            "ro.build.version.release", "ro.build.version.sdk", "ro.product.cpu.abi")]
        + [size, density, str(ticks_per_second), thermal()]
        + [line.strip() for line in shell("dumpsys", "package", PACKAGE).splitlines()
            if re.search(r"versionName=|versionCode=|pkgFlags=", line)]
        + [line.strip() for line in shell("dumpsys", "display").splitlines()
            if "DisplayMode{id=" in line or ("mDisplayInfo=" in line and "720" in line)][:10]))

    def run(number, trace=False):
        label = "diagnostic" if trace else f"run-{number:02d}"
        directory = out / label
        directory.mkdir(exist_ok=True)
        save(directory / "launch.txt", start())
        save(directory / "thermal-before.txt", thermal())
        pid = shell("pidof", "-s", PACKAGE)
        trace_pid = None
        remote_trace = f"/data/misc/perfetto-traces/senec-sprint4-{time.time_ns()}.perfetto-trace"
        if trace:
            trace_cmd = (
                "shell", "perfetto", "--background-wait", "--txt", "-c", "-", "-o", remote_trace
            )
            trace_start = subprocess.run([*adb, *trace_cmd], input=(HERE / "events.pbtxt").read_bytes(),
                capture_output=True, timeout=45, check=True)
            trace_pid = trace_start.stdout.decode().strip().splitlines()[-1]
            if not trace_pid.isdigit():
                raise RuntimeError("Cannot identify Perfetto session")
            save(directory / "perfetto-start.txt", trace_start.stderr.decode())

        def ticks():
            raw = shell("run-as", PACKAGE, "cat", f"/proc/{pid}/stat")
            fields = raw.rsplit(")", 1)[1].split()
            return int(fields[11]) + int(fields[12])

        phases = []
        samples = {}
        memories = {}

        def phase(name, action, settle=.32):
            shell("dumpsys", "gfxinfo", PACKAGE, "reset")
            cpu_before = ticks()
            start_time = time.monotonic()
            action()
            time.sleep(settle)
            elapsed = time.monotonic() - start_time
            cpu = (ticks() - cpu_before) * 1000 / ticks_per_second
            raw = shell("dumpsys", "gfxinfo", PACKAGE, "framestats")
            save(directory / f"{name}-gfxinfo.txt", raw)
            values = list(frames(raw))
            samples[name] = values
            phases.append(dict(phase=name, rendered=field_int(raw, "Total frames rendered:"),
                janky=field_int(raw, "Janky frames:"), raw_frames=len(values),
                average_ms=statistics.mean(values) if values else None,
                p95_ms=percentile(values, 95) if values else None,
                max_ms=max(values) if values else None, cpu_ms=cpu, wall_s=elapsed))

        try:
            memories["before"] = memory(directory, "before")
            phase("open", lambda: tap(270, 1458), 6)
            memories["opened"] = memory(directory, "opened")
            check_title("160 EVENTS")
            if number == 1 or trace:
                screenshot(directory / "events-160.png")
            for n in range(1, 4):
                phase(f"scroll-before-{n}", lambda: swipe(True))
            # Return to the start so the search header is visible. This navigation is not timed.
            for _ in range(5):
                swipe(False)
            time.sleep(.5)
            tap(460, 159)
            time.sleep(.35)
            tap(345, 773)
            time.sleep(.55)
            shell("input", "text", "Robotics")
            shell("input", "keyevent", "KEYCODE_BACK")
            time.sleep(.6)
            phase("apply-filter", lambda: tap(570, 937), 5)
            check_title("40 EVENTS")
            if number == 1 or trace:
                screenshot(directory / "filtered-40.png")
            phase("clear-filter", lambda: tap(173, 1056), 6)
            check_title("160 EVENTS")
            for n in range(1, 4):
                phase(f"scroll-after-{n}", lambda: swipe(True))
            memories["after"] = memory(directory, "after")
        finally:
            if trace_pid:
                subprocess.run([*adb, "shell", "kill", "-TERM", trace_pid], check=False)
                time.sleep(1)
                call("pull", remote_trace, str(directory / "events.perfetto-trace"))
                shell("rm", "-f", remote_trace)

        save(directory / "thermal-after.txt", thermal())
        with (directory / "frames.csv").open("w", newline="", encoding="utf-8") as file:
            writer = csv.writer(file)
            writer.writerow(("phase", "frame_ms"))
            for name, values in samples.items():
                writer.writerows((name, round(value, 6)) for value in values)
        details = {"phases": phases, "memory": memories}
        save(directory / "metrics.json", json.dumps(details, indent=2))
        print(label, json.dumps(details), flush=True)
        return details, samples

    raw = [run(n) for n in range(1, args.runs + 1)]
    if raw:
        save(out / "runs.json", json.dumps([details for details, _ in raw], indent=2))
        save(out / "summary.json", json.dumps(summarize(raw), indent=2))
    if args.trace:
        run(0, trace=True)

    shell("am", "force-stop", PACKAGE)
    response = shell("am", "start", "-W", "-n", ACTIVITY, "--ez", "compute", "true")
    pid = shell("pidof", "-s", PACKAGE)
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        time.sleep(1)
        logs = call("logcat", "-d", "--pid", pid, "-s", "Sprint4EventsBench:I", "-v", "raw")
        if "COMPLETE" in logs:
            break
    else:
        raise RuntimeError("Algorithm benchmark did not finish")
    save(out / "algorithm-logcat.txt", response + "\n" + logs)
    operations = defaultdict(list)
    for line in logs.splitlines():
        if re.match(r"^[a-z_0-9]+,\d+,\d+,\d+,\d+$", line):
            name, _, elapsed, cpu, result = line.split(",")
            operations[name].append((int(elapsed) / 1e6, int(cpu) / 1e6, int(result)))
    if len(operations) != 4 or any(len(samples) != 40 for samples in operations.values()):
        raise RuntimeError("Incomplete algorithm data")
    save(out / "algorithms.json", json.dumps({
        name: {"samples": len(rows), "mean_ms": statistics.mean(r[0] for r in rows),
            "p95_ms": percentile([r[0] for r in rows], 95),
            "cpu_mean_ms": statistics.mean(r[1] for r in rows), "result_size": rows[0][2]}
        for name, rows in operations.items()
    }, indent=2))


if __name__ == "__main__":
    main()
