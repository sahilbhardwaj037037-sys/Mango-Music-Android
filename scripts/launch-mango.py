#!/usr/bin/env python3
"""Launch the existing Mango36 AVD in a graphical window, then open Mango Music."""
import fcntl
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / '.local' / 'launcher'
AVD = 'Mango36'
PACKAGE = 'com.mangomusic'
ACTIVITY = PACKAGE + '/.MainActivity'
APK = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
SDK = ROOT / '.tools/sdk'
HOST_SDK = Path.home() / 'Library/Android/sdk'
ENV = os.environ.copy()
ENV.update(ANDROID_AVD_HOME=str(ROOT / '.local/avd'), ANDROID_SDK_ROOT=str(SDK),
           ANDROID_HOME=str(SDK), ANDROID_USER_HOME=str(ROOT / '.local/android'),
           ANDROID_EMULATOR_HOME=str(ROOT / '.local/emulator'))
keys = [ROOT / '.local/emulator/adbkey', ROOT / '.local/android/adbkey']
ENV['ADB_VENDOR_KEYS'] = os.pathsep.join(str(p) for p in keys if p.is_file())
ADB = next((p for p in [HOST_SDK / 'platform-tools/adb', SDK / 'platform-tools/adb'] if p.is_file()), None)
EMULATOR = next((p for p in [HOST_SDK / 'emulator/emulator', SDK / 'emulator/emulator'] if p.is_file()), None)

def fail(message):
    raise RuntimeError(message)

def run(args, timeout=20, check=True):
    result = subprocess.run([str(x) for x in args], env=ENV, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    if check and result.returncode:
        fail(f"Command failed: {' '.join(str(x) for x in args)}\n{result.stdout.strip()}")
    return result

def adb(*args, timeout=20, check=True):
    return run([ADB, *args], timeout, check)

def processes():
    return run(['/bin/ps', '-ax', '-o', 'pid=,command=']).stdout.splitlines()

def mango_processes():
    result = []
    for line in processes():
        if 'qemu-system' in line and re.search(r'-avd\s+Mango36(?:\s|$)', line):
            result.append((int(line.split(None, 1)[0]), line))
    return result

def device():
    for line in adb('devices').stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 2 or not parts[0].startswith('emulator-'):
            continue
        serial, status = parts[:2]
        if status == 'device':
            name = adb('-s', serial, 'emu', 'avd', 'name', check=False).stdout.splitlines()
            if name and name[0].strip() == AVD:
                return serial
        elif serial == 'emulator-5556' and mango_processes():
            if status == 'unauthorized':
                fail('Mango36 is unauthorized in ADB. Accept the USB debugging prompt in the emulator window, then rerun this launcher. It will reuse the same emulator.')
    return None

def main():
    STATE.mkdir(parents=True, exist_ok=True)
    lock = (STATE / 'launch.lock').open('w')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        fail('Another Mango launcher is already starting the app. Wait for it to finish.')
    if not ADB or not EMULATOR:
        fail('ADB or the emulator binary is missing. Check ~/Library/Android/sdk/platform-tools and emulator directories.')
    if not (SDK / 'system-images/android-36/google_apis/arm64-v8a/system.img').is_file():
        fail(f'The Android 16 system image is missing under {SDK}. No components were downloaded. Restore/select that SDK before retrying.')
    if not (ROOT / '.local/avd/Mango36.ini').is_file():
        fail('Project-local Mango36 AVD registration is missing. Restore .local/avd/Mango36.ini; existing device data has not been changed.')
    if not APK.is_file():
        fail('Debug APK is missing. Build once with ./scripts/gradle-env.sh assembleDebug, then rerun this launcher.')
    existing = mango_processes()
    if len(existing) > 1:
        fail('Multiple Mango36 processes already exist. Close the extra emulator windows before retrying; this launcher will not delete data or terminate them.')
    if existing and ('-no-window' in existing[0][1] or 'aarch64-headless' in existing[0][1]):
        fail('Mango36 is already running without a graphical window. Close that headless instance first, then rerun; a duplicate was not started.')
    serial = device()
    log = STATE / 'emulator.log'
    child = None
    if existing or serial:
        print('Reusing the running Mango36 graphical emulator.', flush=True)
    else:
        # Reserve the familiar port; do not collide with an unrelated AVD.
        attached = adb('devices').stdout
        if 'emulator-5556' in attached:
            fail('Port 5556 belongs to another emulator. Close it or choose another port; no duplicate was started.')
        print('Starting Mango36 in a visible graphical window…', flush=True)
        with log.open('a') as output:
            output.write('\n--- Mango graphical launch ---\n'); output.flush()
            child = subprocess.Popen([str(EMULATOR), '-avd', AVD, '-port', '5556',
                                      '-gpu', 'swiftshader', '-no-snapshot', '-no-boot-anim',
                                      '-no-metrics', '-skip-adb-auth'], env=ENV, cwd=ROOT,
                                     stdout=output, stderr=subprocess.STDOUT,
                                     stdin=subprocess.DEVNULL, start_new_session=True)
    deadline = time.monotonic() + 180
    print('Waiting for Android to boot…', flush=True)
    while time.monotonic() < deadline:
        if child and child.poll() is not None:
            fail(f'Emulator exited with code {child.returncode}. See {log}\n' + '\n'.join(log.read_text(errors='replace').splitlines()[-18:]))
        serial = device()
        if serial and adb('-s', serial, 'shell', 'getprop', 'sys.boot_completed', check=False).stdout.strip() == '1':
            break
        time.sleep(2)
    else:
        fail(f'Android was not ready within 180 seconds. See {log}; the emulator was left running for inspection.')
    # Ask the package manager for a hash instead of reinstalling the same APK.
    installed = adb('-s', serial, 'shell', 'pm', 'path', PACKAGE, check=False).stdout
    remote = next((line.removeprefix('package:') for line in installed.splitlines() if line.startswith('package:') and line.endswith('/base.apk')), None)
    local_hash = hashlib.sha256(APK.read_bytes()).hexdigest()
    remote_hash = adb('-s', serial, 'shell', 'sha256sum', remote, check=False).stdout.split() if remote else []
    if not remote_hash or remote_hash[0] != local_hash:
        print('Installing the existing debug APK (preserving app data)…', flush=True)
        adb('-s', serial, 'install', '-r', APK, timeout=90)
    else:
        print('Matching debug APK is already installed.', flush=True)
    adb('-s', serial, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('-s', serial, 'shell', 'wm', 'dismiss-keyguard', check=False)
    launched = adb('-s', serial, 'shell', 'am', 'start', '-W', '-n', ACTIVITY).stdout
    if 'Error:' in launched or 'Error type' in launched:
        fail('Android could not launch Mango Music:\n' + launched)
    time.sleep(2)
    resumed = adb('-s', serial, 'shell', 'dumpsys', 'activity', 'activities').stdout
    if not any(PACKAGE + '/.MainActivity' in line and ('ResumedActivity' in line or 'topResumedActivity' in line) for line in resumed.splitlines()):
        fail('Mango Music did not remain the foreground activity. Inspect adb logcat -b crash and the emulator window.')
    print(f'Mango Music is running on {serial} (Android 16).', flush=True)
    print(f'Emulator log: {log}', flush=True)

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.TimeoutExpired, OSError) as error:
        print(f'Mango launcher: {error}', file=sys.stderr)
        sys.exit(1)
