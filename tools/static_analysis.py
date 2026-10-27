#!/usr/bin/env python3
"""Direct pinned static analyzers; no application or execution service required."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "quality/static-analysis"
CACHE = ROOT / ".cache/static-analysis"
FIXTURES = CONFIG / "fixtures"
EXCLUDED = {"engineering-log", ".git", ".cache", ".idea", "node_modules",
            "target", "dist", "build", "__pycache__"}


def guarded(path):
    if any(part in {"engineering-log", ".git"} for part in Path(path).parts):
        raise ValueError("Protected path")
    path = Path(os.path.abspath(path))
    try:
        parts = path.relative_to(ROOT).parts
    except ValueError as error:
        raise ValueError("Source must be inside this repository") from error
    current = ROOT
    for part in parts:
        if part in {"engineering-log", ".git"}:
            raise ValueError("Protected path")
        current = current / part
        if current.is_symlink():
            raise ValueError("Symlinks are not source inputs")
    return path


def collect(source, suffixes):
    source = guarded(ROOT / source)
    if source.is_file():
        paths = [source] if source.suffix in suffixes else []
    elif source.is_dir():
        paths = []
        for directory, names, files in os.walk(source, followlinks=False):
            names[:] = sorted(name for name in names
                              if name not in EXCLUDED
                              and not (Path(directory) / name).is_symlink())
            for name in sorted(files):
                if name == "engineering-log":
                    continue
                path = Path(directory) / name
                if path.suffix in suffixes:
                    paths.append(guarded(path))
    else:
        raise ValueError("Source path does not exist")
    if not paths:
        raise ValueError("No matching source files; empty checks cannot pass")
    return paths


def digest(path):
    with guarded(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def metadata():
    return json.loads((CONFIG / "toolchain.json").read_text())


def run(command, timeout=120):
    return subprocess.run(command, cwd=ROOT, text=True, capture_output=True,
                          timeout=timeout)


def node_ready():
    meta = metadata()
    for command, expected in [("node", meta["node"]), ("npm", meta["npm"])]:
        result = run([command, "--version"])
        if result.returncode or result.stdout.strip().removeprefix("v") != expected:
            raise ValueError("Use pinned " + command + " " + expected)


def java_ready():
    home = os.environ.get("JAVA_HOME")
    if not home:
        raise ValueError("Supply JAVA_HOME selecting JDK " + metadata()["jdk"])
    java = str(Path(home) / "bin/java")
    result = run([java, "-version"])
    version = re.search(r'version "([^" ]+)', result.stderr + result.stdout)
    if result.returncode or not version or version.group(1) != metadata()["jdk"]:
        raise ValueError("JAVA_HOME must select JDK " + metadata()["jdk"])
    return java


def verify_assets():
    meta = metadata()
    for name in ("checkstyle", "pmd"):
        target = CACHE / Path(meta[name]["url"]).name
        if not target.is_file() or digest(target) != meta[name]["sha256"]:
            raise ValueError("Missing/unverified analyzer; run npm run quality:setup")
    if not (CACHE / ("pmd-bin-" + meta["pmd"]["version"]) / "lib").is_dir():
        raise ValueError("PMD distribution missing; run npm run quality:setup")


def setup():
    node_ready()
    guarded(CACHE).mkdir(parents=True, exist_ok=True)
    meta = metadata()
    for name in ("checkstyle", "pmd"):
        item = meta[name]
        target = CACHE / Path(item["url"]).name
        if not target.exists():
            temporary = guarded(target.with_suffix(target.suffix + ".download"))
            with urllib.request.urlopen(item["url"], timeout=60) as response:
                with temporary.open("wb") as output:
                    while block := response.read(1024 * 1024):
                        output.write(block)
            if digest(temporary) != item["sha256"]:
                raise ValueError("Analyzer checksum mismatch: " + name)
            temporary.replace(target)
        if digest(target) != item["sha256"]:
            raise ValueError("Analyzer checksum mismatch: " + name)
        if name == "pmd":
            with zipfile.ZipFile(target) as archive:
                for member in archive.infolist():
                    path = Path(member.filename)
                    if (path.is_absolute() or ".." in path.parts
                            or any(x in {"engineering-log", ".git"} for x in path.parts)
                            or (member.external_attr >> 16) & 0o170000 == 0o120000):
                        raise ValueError("Unsafe analyzer archive member")
                    guarded(CACHE / path)
                archive.extractall(CACHE)
    result = run(["npm", "ci", "--prefix", str(CONFIG), "--ignore-scripts",
                  "--no-audit", "--no-fund", "--cache", str(ROOT / ".cache/npm")], 180)
    if result.returncode:
        raise ValueError("Locked analyzer installation failed: " + result.stderr[-1000:])
    print("Pinned static analyzers installed; no runtime services required.")


def java_check(paths, analyzer, report):
    verify_assets()
    java = java_ready()
    meta = metadata()
    if analyzer == "checkstyle":
        command = [java, "-jar", str(CACHE / Path(meta["checkstyle"]["url"]).name),
                   "-c", "/google_checks.xml", "-p", str(CONFIG / "checkstyle.properties"),
                   "-f", "xml", "-o", str(report), *map(str, paths)]
    else:
        inputs = report.with_suffix(".files")
        inputs.write_text("".join(str(path) + "\n" for path in paths))
        command = [java, "-cp", str(CACHE / ("pmd-bin-" + meta["pmd"]["version"]) / "lib/*"),
                   "net.sourceforge.pmd.cli.PmdCli", "check", "--file-list", str(inputs),
                   "-R", str(CONFIG / "pmd.xml"), "-f", "xml", "-r", str(report),
                   "--no-cache", "--no-progress", "--show-suppressed", "--use-version", "java-23",
                   "--aux-classpath", str(Path(java).parent.parent / "lib/jrt-fs.jar")]
    result = run(command)
    if not report.is_file():
        raise ValueError(analyzer + " produced no report: " + result.stderr[-1000:])
    xml = ET.parse(report).getroot()
    if analyzer == "pmd":
        errors = [element for element in xml.iter()
                  if element.tag.split("}")[-1] in {"error", "configerror", "suppressedviolation"}]
        if result.returncode not in {0, 4} or errors:
            raise ValueError("PMD tool/configuration error: " + result.stderr[-1000:])
        findings = [(element.attrib["name"], child.attrib.get("rule", ""))
                    for element in xml.iter() if element.tag.split("}")[-1] == "file"
                    for child in element if child.tag.split("}")[-1] == "violation"]
    else:
        findings = [(element.attrib["name"], child.attrib.get("source", ""))
                    for element in xml.iter("file") for child in element.iter("error")]
        if result.returncode and not findings:
            raise ValueError("Checkstyle tool error: " + result.stderr[-1000:])
    return findings


def lint(paths):
    node_ready()
    result = run(["node", str(CONFIG / "node_modules/eslint/bin/eslint.js"),
                  "--config", str(CONFIG / "eslint.config.mjs"), "--no-config-lookup",
                  "--no-ignore", "--no-inline-config", "--max-warnings", "0",
                  "--format", "json", *map(str, paths)])
    if result.returncode not in {0, 1}:
        raise ValueError("ESLint tool/configuration error: " + result.stderr[-1000:])
    reports = json.loads(result.stdout)
    if any(item.get("fatalErrorCount") for item in reports):
        raise ValueError("ESLint parser/tool failure")
    return [(item["filePath"], message.get("ruleId"))
            for item in reports for message in item.get("messages", [])]


def fixtures(directory):
    cases = json.loads((FIXTURES / "cases.json").read_text())
    for analyzer in ("checkstyle", "pmd"):
        good = java_check(collect(str(FIXTURES / "java/good"), {".java"}),
                          analyzer, directory / (analyzer + "-good.xml"))
        bad = java_check(collect(str(FIXTURES / "java/bad"), {".java"}),
                         analyzer, directory / (analyzer + "-bad.xml"))
        if good or not bad:
            raise ValueError(analyzer + " fixture expectations failed")
        if analyzer == "pmd":
            for case in cases["pmd_negative_cases"]:
                found = {rule for file, rule in bad if Path(file) == FIXTURES / case["file"]}
                if not set(case["expected_pmd_rules"]).issubset(found):
                    raise ValueError("Missing expected PMD findings: " + case["file"])
    if lint(collect(str(FIXTURES / "react/good"), {".tsx"})):
        raise ValueError("Positive ESLint fixture failed")
    bad_rules = {rule for _, rule in lint(collect(str(FIXTURES / "react/bad"), {".tsx"}))}
    if cases["react_negative_rule"] not in bad_rules:
        raise ValueError("Expected React Hooks violation missing")
    print("PASS: Checkstyle, PMD and ESLint positive/negative fixtures; all named PMD cases.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["setup", "java", "lint", "fixtures"])
    parser.add_argument("source", nargs="?")
    args = parser.parse_args()
    if (args.action in {"java", "lint"}) != bool(args.source):
        parser.error("java/lint require an explicit source; setup/fixtures take no source")
    try:
        if args.action == "setup":
            setup()
            return 0
        if args.action == "lint":
            findings = lint(collect(args.source, {".js", ".jsx", ".ts", ".tsx", ".mjs"}))
        else:
            guarded(CACHE).mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(prefix="checks-", dir=CACHE) as temporary:
                directory = Path(temporary)
                if args.action == "fixtures":
                    fixtures(directory)
                    return 0
                paths = collect(args.source, {".java"})
                findings = []
                for analyzer in ("checkstyle", "pmd"):
                    findings.extend(java_check(paths, analyzer, directory / (analyzer + ".xml")))
        for file, rule in findings:
            print(file + ": " + str(rule))
        return 1 if findings else 0
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired, ET.ParseError,
            zipfile.BadZipFile) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
