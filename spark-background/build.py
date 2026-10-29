from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent
BUILD = ROOT / "build"
CLASSES = BUILD / "classes"
CLASSES.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser(description="Bundle, test and optionally build the Android diagnostic prototype")
parser.add_argument("--test-only", action="store_true", help="run the 22 offline tests without Android tools")
args = parser.parse_args()

def run(*items):
    subprocess.run([str(item) for item in items], cwd=ROOT, check=True)

policy = (PROJECT / "background-streak-policy/streak-policy.mjs").read_text(encoding="utf-8-sig")
probe = (ROOT / "read-probe.js").read_text(encoding="utf-8-sig")
script = '(function(){\n"use strict";\n' + policy.replace("export function ", "function ") + "\n" + probe + "\n})()"
(BUILD / "probe.js").write_text(script, encoding="utf-8")
session = (ROOT / "vendor/sm3.mjs").read_text(encoding="utf-8-sig") + "\n" + (ROOT / "vendor/nativeSignature.mjs").read_text(encoding="utf-8-sig") + "\n" + (ROOT / "session-check.mjs").read_text(encoding="utf-8-sig")
session = session.replace('import { sm3Digest } from "./sm3.mjs";', '').replace("export function ", "function ")
(BUILD / "session-check.js").write_text('(function(){"use strict";\n' + session + '\nglobalThis.SparkSession={sign:signSessionCheck,classify:classifySessionCheck};})();', encoding="utf-8")
run("node", "--test", PROJECT / "background-streak-policy/streak-policy.test.mjs", ROOT / "probe.test.mjs", ROOT / "session-check.test.mjs")
if args.test_only:
    raise SystemExit(0)

try:
    TOOLS = Path(os.environ["SPARK_BUILD_TOOLS"])
    ANDROID = Path(os.environ["SPARK_ANDROID_JAR"])
except KeyError as exc:
    raise SystemExit("Set SPARK_BUILD_TOOLS and SPARK_ANDROID_JAR to your local Android tools") from exc
suffix = ".exe" if os.name == "nt" else ""
java_home = os.environ.get("JAVA_HOME")
JAVA = Path(java_home) / "bin" / ("java" + suffix) if java_home else (shutil.which("java") or "java")
JAVAC = Path(java_home) / "bin" / ("javac" + suffix) if java_home else (shutil.which("javac") or "javac")
run(TOOLS / ("aapt2" + suffix), "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip")
run(TOOLS / ("aapt2" + suffix), "link", "-I", ANDROID, "--manifest", ROOT / "AndroidManifest.xml", "-o", BUILD / "resources.apk", BUILD / "resources.zip")
run(JAVAC, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options", "-classpath", ANDROID, "-d", CLASSES, *sorted((ROOT / "src").rglob("*.java")))
with zipfile.ZipFile(BUILD / "classes.jar", "w") as out:
    for file in CLASSES.rglob("*.class"):
        out.write(file, file.relative_to(CLASSES).as_posix())
run(JAVA, "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "30", "--lib", ANDROID, "--output", BUILD, BUILD / "classes.jar")
unsigned = BUILD / "unsigned.apk"
unsigned.write_bytes((BUILD / "resources.apk").read_bytes())
with zipfile.ZipFile(unsigned, "a") as out:
    out.write(BUILD / "classes.dex", "classes.dex")
    out.write(BUILD / "probe.js", "assets/probe.js")
    out.write(BUILD / "session-check.js", "assets/session-check.js")
    out.write(ROOT / "THIRD_PARTY_NOTICES.txt", "assets/THIRD_PARTY_NOTICES.txt")
run(TOOLS / ("zipalign" + suffix), "-f", "4", unsigned, BUILD / "aligned.apk")
artifacts = PROJECT / "artifacts"
artifacts.mkdir(exist_ok=True)
keystore = os.environ.get("SPARK_KEYSTORE")
if keystore:
    if not os.environ.get("SPARK_KEYSTORE_PASSWORD"):
        raise SystemExit("Set SPARK_KEYSTORE_PASSWORD locally; never commit it")
    output = artifacts / "SparkBackground-local.apk"
    signing = ["--ks", keystore, "--ks-pass", "env:SPARK_KEYSTORE_PASSWORD"]
    if os.environ.get("SPARK_KEY_ALIAS"):
        signing += ["--ks-key-alias", os.environ["SPARK_KEY_ALIAS"]]
    run(JAVA, "-jar", TOOLS / "lib/apksigner.jar", "sign", *signing, "--out", output, BUILD / "aligned.apk")
    run(JAVA, "-jar", TOOLS / "lib/apksigner.jar", "verify", "--verbose", output)
else:
    output = artifacts / "SparkBackground-unsigned.apk"
    shutil.copyfile(BUILD / "aligned.apk", output)
print("APK:", output)
