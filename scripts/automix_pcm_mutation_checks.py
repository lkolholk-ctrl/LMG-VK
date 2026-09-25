#!/usr/bin/env python3
"""Isolated semantic mutations linked to the actual prebuilt native DSP archive.

Build Stage4b with CMake first. No tracked file is modified. A compilation error,
crash, timeout or unrelated failure is NOT counted as a detected mutation.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile

MUTATIONS = (
    ("double-output-gain", "capture.raw[i]*gain;", "capture.raw[i]*gain*gain;", "transport", "Double/missing output gain"),
    ("hidden-output-attenuation", "capture.raw[delivered*f.channels+i]=block[i];", "capture.raw[delivered*f.channels+i]=block[i]*.5f;", "transport", "Unity interior mismatch"),
    ("missing-rate-map", "{&mapping,Mapping::source}", "{}", "latency-tails", "Mapped rate not reflected in accepted source clock"),
    ("shifted-incoming-mix", "(frame-result.incomingStartFrame)*f.channels+c", "(frame-result.incomingStartFrame+1)*f.channels+c", "schedule", "Mix alignment/gain ownership mismatch"),
)

def run(command: list[str], *, timeout: int = 300) -> subprocess.CompletedProcess:
    return subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=timeout, check=False)

def cmake_path(path: Path) -> str:
    # Bracket quoting preserves spaces without interpreting variable syntax.
    text = str(path.resolve())
    if "]]" in text or "\n" in text:
        raise ValueError("Unsupported path")
    return f"[[{text}]]"

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-dir", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    offline = root / "native/automix/offline"
    core = args.build_dir.resolve() / "liblmg_automix.a"
    cache = (args.build_dir / "CMakeCache.txt").read_text()
    if not core.is_file():
        raise ValueError("Build liblmg_automix.a first")
    match = re.search(r"^CMAKE_CXX_COMPILER:FILEPATH=(.+)$", cache, re.M)
    if not match:
        raise ValueError("CMake compiler not found in cache")
    sanitized = bool(re.search(r"^LMG_AUTOMIX_SANITIZERS:BOOL=ON$", cache, re.M))
    original = (offline / "pcm_bench.cpp").read_text()
    for name, anchor, _, _, _ in MUTATIONS:
        if original.count(anchor) != 1:
            raise ValueError(f"Mutation anchor is not unique: {name}")
    with tempfile.TemporaryDirectory(prefix="automix-pcm-mutation-") as temporary:
        work = Path(temporary)
        sources = " ".join(cmake_path(offline / f) for f in ("pcm_support.cpp", "pcm_fixtures.cpp", "pcm_tests.cpp"))
        cmake = f'''cmake_minimum_required(VERSION 3.16)
project(pcm_mutation LANGUAGES CXX)
add_library(original_core STATIC IMPORTED)
set_target_properties(original_core PROPERTIES IMPORTED_LOCATION {cmake_path(core)})
add_executable(probe {sources} pcm_bench.cpp)
target_compile_features(probe PRIVATE cxx_std_17)
target_compile_options(probe PRIVATE -Wall -Wextra -Wpedantic -Werror -ffp-contract=off)
target_include_directories(probe PRIVATE {cmake_path(offline)} {cmake_path(root/'native/automix/include')})
target_link_libraries(probe PRIVATE original_core)
'''
        if sanitized:
            cmake += 'target_compile_options(probe PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)\ntarget_link_options(probe PRIVATE -fsanitize=address,undefined)\n'
        (work / "CMakeLists.txt").write_text(cmake)
        source = work / "pcm_bench.cpp"
        source.write_text(original)
        configured = run(["cmake", "-S", str(work), "-B", str(work / "build"), "-DCMAKE_BUILD_TYPE=Debug", "-DCMAKE_CXX_COMPILER=" + match[1]])
        if configured.returncode:
            raise RuntimeError(configured.stdout)
        outcomes = []
        for name, anchor, replacement, group, assertion in (("baseline", "", "", "", ""), *MUTATIONS):
            source.write_text(original if name == "baseline" else original.replace(anchor, replacement, 1))
            build = run(["cmake", "--build", str(work / "build"), "--parallel", "2"])
            if build.returncode:
                raise RuntimeError(f"{name}: compilation is NOT a mutation detection\n{build.stdout}")
            groups = ("transport", "latency-tails", "schedule") if name == "baseline" else (group,)
            for selected in groups:
                command = [str(work / "build/probe"), selected]
                if selected == "schedule":
                    command.append(str(root / "research/ios26-automix/TransitionStyles.json"))
                result = run(command)
                if name == "baseline":
                    if result.returncode:
                        raise RuntimeError("Unmodified baseline must pass first:\n" + result.stdout)
                else:
                    expected = "Offline real-DSP test FAILED: " + assertion
                    if result.returncode != 1 or expected not in result.stdout:
                        raise RuntimeError(f"{name}: mutation NOT detected by expected assertion\n{result.stdout}")
                    outcomes.append(name)
                    print(f"DETECTED_BY_ASSERTION {name}")
        print(json.dumps({"baselinePassed": True, "detected": outcomes, "count": len(outcomes), "modifiedRepositoryFiles": 0}))
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"PCM mutation checks FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
