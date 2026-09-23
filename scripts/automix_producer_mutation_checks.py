#!/usr/bin/env python3
"""Compile isolated semantic mutations. A compiler error is NOT a detection.

Uses only private temporary copies/objects. Never edits tracked source files.
Does not run Android, an emulator, the raw JSON decoder, or JNI.
"""
from __future__ import annotations
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CORE = "planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis planner_regions planner_observation planner_region_algebra planner_candidate_selector planner_candidate_observation planner_input_producers planner_produced_observation".split()
MUTATIONS = [
    ("outgoing threshold becomes exclusive", "planner_input_producers", "s.events[i].songTime >= minimumEnd", "s.events[i].songTime > minimumEnd", "planner_input_producers"),
    ("first incoming removal becomes filter of all", "planner_input_producers", "if (!result.empty() && db(s, result.front()) < bars) result.erase(result.begin());", "while (!result.empty() && db(s, result.front()) < bars) result.erase(result.begin());", "planner_input_producers"),
    ("stable coverage excludes endpoint", "planner_input_producers", "if (a <= end && end <= z)", "if (a <= end && end < z)", "planner_input_producers"),
    ("last stable match replaces first", "planner_input_producers", "clipped = StructureRegion{start >= a ? r.startEvent : stable.events.startEvent, r.endEvent};\n      break;", "clipped = StructureRegion{start >= a ? r.startEvent : stable.events.startEvent, r.endEvent};", "planner_input_producers"),
    ("bar tolerance incorrectly reused for beats", "planner_input_producers", "std::abs(duration - mean) > 0.031", "std::abs(duration - mean) > 0.04", "planner_input_producers"),
    ("loudness threshold becomes inclusive", "planner_input_producers", "return mean && *mean > -30.0;", "return mean && *mean >= -30.0;", "planner_input_producers"),
    ("present empty loudness silently allowed", "planner_input_producers", "window(r); if (!map) return true;", "window(r); if (!map || map->empty()) return true;", "planner_input_producers"),
    ("four-bar default applied as minimum", "planner_input_producers", "return largest.value_or(4);", "return std::max<std::int64_t>(4, largest.value_or(4));", "planner_input_producers"),
    ("directional tonality used instead of main", "planner_input_producers", "if (map) result.tonality = map->main;", "if (map) result.tonality = map->beginning;", "planner_input_producers"),
    ("descriptor order reversed", "planner_input_producers", "r.maximumBars = maximumBars(r.styles);", "std::reverse(r.styles.begin(), r.styles.end()); r.maximumBars = maximumBars(r.styles);", "planner_input_producers"),
    ("producer work not subtracted from selector budget", "planner_produced_observation", "workBudget - produced.workUnits", "workBudget", "planner_produced_observation"),
]


def run(args, *, expected_success=True):
    result = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
    if expected_success and result.returncode:
        raise RuntimeError("Command failed (not a mutation detection):\n" + result.stdout[-12000:])
    return result


def main():
    compiler = os.environ.get("CXX", "c++")
    if not shutil.which(compiler):
        raise SystemExit(f"Compiler unavailable: {compiler}")
    flags = ["-std=c++17", "-O1", "-Wall", "-Wextra", "-Wpedantic", "-Werror", "-ffp-contract=off", "-I", str(ROOT/"native/automix/include")]
    with tempfile.TemporaryDirectory(prefix="lmg-producer-mutations-") as folder:
        work = Path(folder)
        objects = {}
        for name in CORE:
            obj = work/f"{name}.o"
            run([compiler,*flags,"-c",str(ROOT/f"native/automix/src/{name}.cpp"),"-o",str(obj)])
            objects[name] = obj
        tests = {}
        for test in ["planner_input_producers","planner_produced_observation"]:
            obj = work/f"{test}_test.o"
            run([compiler,*flags,"-c",str(ROOT/f"native/automix/tests/{test}_test.cpp"),"-o",str(obj)])
            tests[test] = obj
            exe = work/f"baseline-{test}"
            run([compiler,str(obj),*[str(p) for p in objects.values()],"-o",str(exe)])
            run([str(exe)])
        for i,(name,module,old,new,test) in enumerate(MUTATIONS):
            source = (ROOT/f"native/automix/src/{module}.cpp").read_text()
            if source.count(old)!=1:
                raise RuntimeError(f"Mutation anchor is not unique: {name}")
            altered = work/f"mutation-{i}.cpp"
            altered.write_text(source.replace(old,new,1))
            obj = work/f"mutation-{i}.o"
            run([compiler,*flags,"-c",str(altered),"-o",str(obj)])
            exe = work/f"mutation-{i}"
            link = [str(obj if key==module else value) for key,value in objects.items()]
            run([compiler,str(tests[test]),*link,"-o",str(exe)])
            result = run([str(exe)],expected_success=False)
            if result.returncode != 1 or "FAILED" not in result.stdout:
                raise RuntimeError(f"Semantic mutation not detected by an assertion: {name}\n{result.stdout}")
            print(f"Detected {i+1}/{len(MUTATIONS)}: {name}",flush=True)
    print(f"Producer semantic mutations: {len(MUTATIONS)}/{len(MUTATIONS)} detected; original source unchanged")

if __name__ == "__main__":
    main()
