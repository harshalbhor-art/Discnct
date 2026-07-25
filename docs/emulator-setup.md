# Running Discnct on an Android emulator in a cloud session

This is only usable once the environment's **Network access** is set to
**Custom** with `dl.google.com` added to the allowed domains (see below) —
plain "Trusted" access does **not** include it, only `developer.android.com`
(the docs site, not the package host) is on that list by default.

## 0. One-time environment change (do this in the claude.ai/code UI, not here)

1. Open the environment for editing (cloud icon wherever you start a session
   or configure a routine — there is no separate Environments page).
2. Network access → **Custom**.
3. Check "Also include default list of common package managers" (keeps
   npm/PyPI/GitHub/gradle.org etc. working).
4. Add to Allowed domains:
   ```
   dl.google.com
   ```
5. Save, then start a **new** session in this environment — the policy is
   applied at container start, so an already-running session won't pick it
   up.

## 1. Known unresolved risk: no /dev/kvm

As of this writing, `/dev/kvm` is absent from these containers
(`ls /dev/kvm` → No such file or directory) and there's no nested-virtualization
guarantee documented. Without KVM the emulator falls back to software
rendering/execution, which is dramatically slower but still usually boots —
try `-no-accel` (or let the emulator auto-detect and fall back) rather than
assuming it's a hard blocker. If it's unusably slow or refuses to boot at
all, that's the actual verdict on whether this path works here.

## 2. Bootstrap the SDK (run once network access confirmed)

```bash
# sanity check the previously-blocked host is now reachable
curl -sSI https://dl.google.com/ | head -1

export ANDROID_SDK_ROOT=$HOME/android-sdk
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
cd "$ANDROID_SDK_ROOT/cmdline-tools"
curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdline-tools.zip && rm cmdline-tools.zip
mv cmdline-tools latest

export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
  "emulator" "system-images;android-34;google_apis;x86_64"
```

## 3. Build the real app for the first time

```bash
cd /home/user/Discnct
./gradlew :app:assembleDebug
```

This is the first time the `app` module (as opposed to the standalone
`game-logic` module) will have actually been compiled — expect to iterate on
real AGP/manifest/resource errors that never surfaced in the JVM-only test
setup.

## 4. Create and boot an AVD

```bash
echo "no" | avdmanager create avd -n discnct_test -k "system-images;android-34;google_apis;x86_64" -d pixel_5

# Try hardware accel first; if it fails/hangs, retry with -no-accel and reduced RAM.
emulator -avd discnct_test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &

adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
```

## 5. Install and manually exercise the app

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.discnct.app/.MainActivity
```

Things to actually exercise manually (this is UI/behavioral verification the
`game-logic` unit tests can't cover):

- Onboarding flow and permission grants (overlay + usage-stats).
- Level 1 (Reel Blocker): opening a Reels/Shorts feed in a guarded app raises
  the block screen, while the rest of that app stays usable. Specifically check
  the *negative* cases, since they're what a too-loose rule breaks: Instagram's
  DMs, search and profile tabs, and YouTube's home timeline with its Shorts
  shelf, must all stay untouched.
- The reel-blocker shortcut: long-pressing the launcher icon and tapping the
  Quick Settings tile both flip the master switch, and both refuse to turn it
  *off* while Strict Mode holds a PIN.
- Level 2 (App Blocker + Games): opening a blocklisted app triggers the block
  overlay; the hold-to-unlock timer works; and each of the 7 mini-games runs
  end-to-end (Tic-Tac-Toe, Minesweeper, Wordle, 2048, Chess puzzle, Breathing,
  Fidget Spinner) awarding the correct unlock minutes on win/loss.
- Game sizing: every board is centred on screen and grows to fill the width
  without overflowing it — worth checking on both a small phone and a tablet,
  since the scale factor is clamped per screen.
- The TRY button on each game row plays that game without awarding anything.
- Level 3 (Total Disconnect): restricted-launcher mode shows only the allowed
  apps, and blocked ones still route through the block screen.

Use `adb logcat *:E` alongside manual taps to catch runtime exceptions the
compiler won't.
