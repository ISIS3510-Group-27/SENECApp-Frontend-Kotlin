"""Profile the My RSOs tab on an Android Studio emulator and save the results.

Adapted from profile_events.py. Instead of fixed Xiaomi coordinates it finds the bottom
navigation labels with uiautomator, so it works on any AVD. Run it BEFORE and AFTER the
change on the same AVD, same account and same number of groups, with a debug build.

Each run: cold start -> open My RSOs (wait for the list) -> N times "Discover -> My RSOs"
(every return composes all the cards again, which is when eventTime() runs) -> N scrolls.
"""
import argparse
import json
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.senecapp"
ACTIVITY = PACKAGE + "/.MainActivity"


def frames(raw):
    """Frame durations in ms from `dumpsys gfxinfo <pkg> framestats` (FrameCompleted - IntendedVsync)."""
    header = None
    for line in raw.splitlines():
        line = line.strip()
        if line.startswith("Flags,"):
            header = line.rstrip(",").split(",")
        elif header and re.match(r"^\d+,", line):
            row = dict(zip(header, map(int, line.rstrip(",").split(","))))
            start, finish = row.get("IntendedVsync", 0), row.get("FrameCompleted", 0)
            if not row["Flags"] & (1 << 3) and 0 < start < finish < 2**63 - 1:
                yield (finish - start) / 1e6


def field_int(text, label):
    match = re.search(r"(?m)^\s*" + re.escape(label) + r"\s*(\d+)", text)
    return int(match[1]) if match else 0


def percentile(values, pct):
    ordered = sorted(values)
    rank = (len(ordered) - 1) * pct / 100
    low = int(rank)
    return ordered[low] + (rank - low) * (ordered[min(low + 1, len(ordered) - 1)] - ordered[low])


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--adb", default="adb")
    cli.add_argument("--serial", help="Emulator serial, e.g. emulator-5554 (optional if only one device)")
    cli.add_argument("--output", required=True, type=Path)
    cli.add_argument("--runs", type=int, default=3)
    cli.add_argument("--cycles", type=int, default=5, help="Discover -> My RSOs returns per run")
    args = cli.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])

    def shell(*cmd, timeout=60):
        return subprocess.check_output([*adb, "shell", *cmd], timeout=timeout).decode("utf-8", "replace").strip()

    def save(path, text):
        path.write_text(text + "\n", encoding="utf-8")

    def screenshot(path):
        path.write_bytes(subprocess.check_output([*adb, "exec-out", "screencap", "-p"], timeout=60))

    def bottom_label(text):
        """Center of the lowest node whose text is `text` (the bottom navigation item)."""
        target = "/data/local/tmp/senec-myrsos-ui.xml"
        for _ in range(3):
            shell("rm", "-f", target)
            try:
                if "dumped to" not in shell("uiautomator", "dump", target):
                    continue
                matches = []
                for node in ET.fromstring(shell("cat", target)).iter("node"):
                    if node.get("text") == text or node.get("content-desc") == text:
                        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                        matches.append(((x1 + x2) // 2, (y1 + y2) // 2))
                if matches:
                    return max(matches, key=lambda point: point[1])
            except (ET.ParseError, subprocess.CalledProcessError):
                pass
            time.sleep(1)
        raise RuntimeError(f"Cannot find {text!r} on screen. Is the app signed in and on the home screen?")

    def tap(point):
        shell("input", "tap", str(point[0]), str(point[1]))

    width, height = map(int, shell("wm", "size").splitlines()[-1].split()[-1].split("x"))

    def swipe(up):
        top, bottom = int(height * .30), int(height * .75)
        a, b = (bottom, top) if up else (top, bottom)
        shell("input", "swipe", str(width // 2), str(a), str(width // 2), str(b), "400")

    def memory(directory, step):
        raw = shell("dumpsys", "meminfo", PACKAGE)
        save(directory / f"memory-{step}.txt", raw)
        match = re.search(r"TOTAL PSS:\s*(\d+)", raw) or re.search(r"(?m)^\s*TOTAL\s+(\d+)", raw)
        return int(match[1]) if match else None

    # Environment: the wiki must say this is an emulator, with its model, API and RAM.
    props = {name: shell("getprop", name) for name in (
        "ro.product.model", "ro.build.version.release", "ro.build.version.sdk",
        "ro.product.cpu.abi", "ro.kernel.qemu", "ro.boot.qemu.avd_name")}
    mem_total = next((line for line in shell("cat", "/proc/meminfo").splitlines() if line.startswith("MemTotal")), "")
    save(out / "device.txt", json.dumps(props, indent=2) + f"\n{mem_total}\n{shell('wm', 'size')}\n{shell('wm', 'density')}")
    ticks_per_second = int(shell("getconf", "CLK_TCK"))

    def run(number):
        directory = out / f"run-{number:02d}"
        directory.mkdir(exist_ok=True)
        shell("am", "force-stop", PACKAGE)
        save(directory / "launch.txt", shell("am", "start", "-W", "-n", ACTIVITY))
        time.sleep(4)
        my_rsos, discover = bottom_label("My RSOs"), bottom_label("Discover")
        tap(my_rsos)
        time.sleep(6)  # first load of /me/groups + /me/saved-groups
        if number == 1:
            screenshot(directory / "my-rsos.png")
        pid = shell("pidof", "-s", PACKAGE)

        def cpu_ticks():
            fields = shell("run-as", PACKAGE, "cat", f"/proc/{pid}/stat").rsplit(")", 1)[1].split()
            return int(fields[11]) + int(fields[12])  # utime + stime of the whole process

        result = {"pss_kib_before": memory(directory, "before")}
        for phase, action in (("reopen", lambda: (tap(discover), time.sleep(1.5), shell("dumpsys", "gfxinfo", PACKAGE, "reset"), tap(my_rsos))),
                              ("scroll", lambda: (swipe(True), swipe(False)))):
            samples, rendered, janky, cpu = [], 0, 0, 0.0
            for cycle in range(args.cycles):
                if phase == "scroll":
                    shell("dumpsys", "gfxinfo", PACKAGE, "reset")
                before = cpu_ticks()
                action()
                time.sleep(1.5)
                cpu += (cpu_ticks() - before) * 1000 / ticks_per_second
                raw = shell("dumpsys", "gfxinfo", PACKAGE, "framestats")
                save(directory / f"{phase}-{cycle + 1}-gfxinfo.txt", raw)
                samples += list(frames(raw))
                rendered += field_int(raw, "Total frames rendered:")
                janky += field_int(raw, "Janky frames:")
            result[phase] = dict(frames=len(samples), rendered=rendered, janky=janky,
                average_ms=statistics.mean(samples) if samples else None,
                p95_ms=percentile(samples, 95) if samples else None,
                max_ms=max(samples) if samples else None,
                cpu_ms_per_cycle=cpu / args.cycles)
        result["pss_kib_after"] = memory(directory, "after")
        save(directory / "metrics.json", json.dumps(result, indent=2))
        print(f"run {number}:", json.dumps(result), flush=True)
        return result

    runs = [run(n) for n in range(1, args.runs + 1)]
    save(out / "runs.json", json.dumps(runs, indent=2))

    def median(phase, key):
        values = [r[phase][key] for r in runs if r[phase][key] is not None]
        return statistics.median(values) if values else None

    summary = {phase: {key: median(phase, key) for key in ("average_ms", "p95_ms", "max_ms", "cpu_ms_per_cycle")}
               for phase in ("reopen", "scroll")}
    summary["pss_kib_after"] = statistics.median(r["pss_kib_after"] for r in runs if r["pss_kib_after"])
    summary["note"] = "Median of the runs. Emulator: compare only before vs after on the same AVD."
    save(out / "summary.json", json.dumps(summary, indent=2))
    print("summary:", json.dumps(summary, indent=2))
    shell("am", "force-stop", PACKAGE)


if __name__ == "__main__":
    main()
