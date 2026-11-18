#!/usr/bin/env python3
from pathlib import Path
import os, re, shutil, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def ok(x): print("PASS",x)
def fail(x): print("FAIL",x); errors.append(x)
# Core build files
for f in ["settings.gradle","build.gradle","gradle.properties","app/build.gradle","app/src/main/AndroidManifest.xml","gradle/wrapper/gradle-wrapper.properties"]:
    (ok("file "+f) if (ROOT/f).is_file() else fail("missing "+f))
# Toolchain contract
b=(ROOT/"app/build.gradle").read_text()
r=(ROOT/"gradle/wrapper/gradle-wrapper.properties").read_text()
root=(ROOT/"build.gradle").read_text()
checks=[
    ("AGP 8.6.1", "com.android.application' version '8.6.1" in root),
    ("Gradle 8.7", "gradle-8.7-bin.zip" in r),
    ("compileSdk 35", "compileSdk 35" in b),
    ("targetSdk 35", "targetSdk 35" in b),
    ("minSdk 26", "minSdk 26" in b),
    ("Java 17", "JavaVersion.VERSION_17" in b),
    ("version 4.6.1", "versionName '4.6.1'" in b and "versionCode 56" in b),
]
for n,c in checks: ok(n) if c else fail(n)
# Wrapper sanity: if jar is absent, make the limitation explicit; GitHub uses installed Gradle.
wrapper=(ROOT/"gradlew")
if wrapper.is_file(): ok("gradlew present")
else: print("WARN gradlew not bundled; GitHub workflow uses Gradle 8.7 directly")
# Optional local tool checks
java=shutil.which("java"); gradle=shutil.which("gradle")
if java:
    try:
        proc=subprocess.run([java,"-version"],capture_output=True,text=True,timeout=5)
        out=proc.stderr+proc.stdout
        ok("java executable available")
        if re.search(r'version "17(?:\.|\")', out): ok("Java runtime 17")
        else: print("WARN Java runtime is not 17 in this local environment; GitHub Actions pins JDK 17.")
    except Exception: fail("java executable invocation")
else: print("WARN java not installed in current environment")
if gradle:
    try:
        proc=subprocess.run([gradle,"--version"],capture_output=True,text=True,timeout=10)
        out=proc.stdout+proc.stderr
        ok("gradle executable available")
        if re.search(r'Gradle 8\.7(?:\b|$)', out): ok("Gradle runtime 8.7")
        else: fail("Gradle runtime 8.7")
    except Exception: fail("gradle executable invocation")
else: print("WARN gradle not installed in current environment")
# Workflow contract
wf=(ROOT/".github/workflows/android-apk.yml").read_text()
for n,c in [("setup-java 17","java-version: '17'" in wf),("SDK platform 35","platforms;android-35" in wf),("build tools 35","build-tools;35.0.0" in wf),("lint before assemble",":app:lintDebug :app:assembleDebug" in wf),("source QA", "tools/qa_preflight.py" in wf and "tools/qa_adversarial.py" in wf),("environment QA", "tools/verify_build_env.py" in wf),("artifact upload","actions/upload-artifact@v4" in wf)]: ok(n) if c else fail(n)
print("TOTAL", "PASS" if not errors else "FAIL", "errors=",len(errors))
sys.exit(1 if errors else 0)
