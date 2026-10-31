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
parser = argparse.ArgumentParser(description="Bundle, test and optionally build the Android streak assistant")
parser.add_argument("--test-only", action="store_true", help="run offline tests without Android tools")
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
with zipfile.ZipFile(ROOT / "protocol.zip") as archive:
    for item in archive.infolist():
        target=(ROOT / "protocol" / item.filename).resolve()
        if not target.is_relative_to((ROOT / "protocol").resolve()): raise ValueError("Invalid dependency path")
    archive.extractall(ROOT / "protocol")
run("node", "--test", *sorted((PROJECT / "background-streak-policy").glob("*.test.mjs")), ROOT / "probe.test.mjs", ROOT / "session-check.test.mjs", ROOT / "mobile-compat.test.mjs")
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
compiler=[JAVA,"-jar",os.environ["SPARK_ECJ_JAR"],"-proc:none"] if os.environ.get("SPARK_ECJ_JAR") else [JAVAC]
run(*compiler,"-encoding","UTF-8","-source","8","-target","8","-classpath",ANDROID,"-d",CLASSES,*sorted((ROOT / "src").rglob("*.java")))
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
    for name in ["engine-connect.cjs","engine-selftest.cjs","engine-fixture.cjs","engine-read.cjs","engine-send.cjs","engine-nightly.cjs","protocol.zip"]:
        out.write(ROOT / name,"assets/"+name)
    for name in ["streak-policy.mjs","attempt-ledger.mjs","streak-runner.mjs","official-streak-adapter.mjs","discover-private.mjs"]:
        out.write(PROJECT / "background-streak-policy" / name,"assets/"+name)
    for name in ["node-mobile-LICENSE.txt","ndk-NOTICE.txt"]:
        out.write(ROOT / "licenses" / name,"assets/"+name)
    runtime_file=ROOT / "vendor/runtime-arm64.zip"
    if not runtime_file.exists():
        runtime_file=BUILD / "runtime-arm64.zip"
        parts=sorted((ROOT / "vendor").glob("runtime-arm64.zip.part*"))
        if not parts: raise SystemExit("Missing runtime parts")
        with runtime_file.open("wb") as merged:
            for part in parts: merged.write(part.read_bytes())
    with zipfile.ZipFile(runtime_file) as runtime:
        for name in ["libnode.so","libc++_shared.so","libspark_node.so"]:
            out.writestr("lib/arm64-v8a/"+name,runtime.read(name))

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
