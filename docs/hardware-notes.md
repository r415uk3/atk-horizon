# Hardware notes: ATK Horizon (VID 373B / PID 120C)

Checklist for the first session with the real headset. Nothing here has been run yet: the app was
built without a phone attached, and the protocol comes from the ATK Hub web bundle.

The order matters. **Phase A is read-only**: the app writes nothing to the headset until you touch
a control (on connect it only reads the firmware, the 8 bands, both gain offsets and noise
cancelling). Finish phase A, including the coefficient cross-check, before anything in phase B.

## Setup

- Install the **debug** build: `./gradlew :app:installDebug`. Only debug builds log the raw
  reports and the coefficient cross-check.
- Keep a log running for the whole session: `adb logcat -s Horizon`. Useful lines:
  - `iface id=… claimed=… endpoints=… reportIds=[…]` and `using HID interface N`: what was opened.
  - `transport: iface=N in=0x.. out=0x..` (or `out=none (SET_REPORT)`).
  - `OUT 4b …` / `IN  4b …`: every report, hex, report ID first. In an IN line the bytes after
    `4b` are the payload: payload byte 0 = direction, byte 1 = command, byte 4 = band index.
  - `band i type=… fc=… q=… gain=… coeff MATCH|MISMATCH|UNSTABLE dev=<hex> calc=<hex>`: one per
    band on every snapshot read. `dev` is what the headset holds, `calc` is our port of the Hub math
    for the same parameters.
  - `snapshot firmware=… dac=… dB (raw …) adc=… dB (raw …) nc=…`
- Volume low and the headset off your ears for every step marked **(off-ear)**.

## Phase A: read only

1. **Enumeration.** Plug the headset in and open the app. Copy the `iface …` lines into the
   result: interfaces, endpoints, report IDs. The chosen interface (`using HID interface N`) must
   declare report ID `0x4b`.
   Result:
2. **Permission.** The Android USB dialog appears. Deny once: the app shows "Permission denied"
   with "Try again". Then grant: the app reads the headset and shows the Sound tab.
   Result:
3. **Input report prefix.** Every `IN` line starts with `4b` (report ID 0x4B).
   Result:
4. **Firmware.** Device tab: Firmware shows `0.9` (also `snapshot firmware=0.9` in the log).
   Result:
5. **Band read replies.** For each of the 8 `OUT 4b 80 09 02 00 0i` requests, the matching `IN`
   line is `4b 80 09 …` with band index `0i` in payload byte 4 (the 6th byte of the line). Also
   note whether direction/command are echoed as expected (payload bytes 0 and 1).
   Result:
6. **Offsets before any write.** Device tab: note the Playback gain offset. Microphone tab: note
   the Mic gain offset. Both must be plausible (within ±10 dB, normally 0.0) and no error message
   may appear. If either shows "—" or the "gain offset outside ±10 dB" message: **stop and report**
   (the 0x03/0x02 read parse is wrong; the app refuses EQ writes in that state anyway). Copy the
   `snapshot … dac=… (raw …)` line.
   Result:
7. **Coefficient cross-check: CS2.** Close our app (swipe it away). In the **official** ATK app
   apply CS2, close it, open our app. In the log, all 8 `band` lines must say `coeff MATCH`, and the
   Sound tab must show the CS2 pill as active. **If any line says MISMATCH or UNSTABLE: stop and
   report** (copy the 8 lines) before doing anything in phase B.
   Result:
8. **Coefficient cross-check: Valorant.** Same as item 7 with Valorant in the official app.
   **Stop and report on any MISMATCH.**
   Result:

## Phase B: writes (only after phase A passed)

9. **Apply CS2 from our app.** Start from a different preset (e.g. Default set in the official
   app). Tap CS2. The sound audibly changes; no error appears. After `4b`, the OUT lines are `80 03` (DAC read),
   8× `01 09`, `01 0a`, `01 03`, about 1 s later `01 01`, then 8× `80 09`. Close our app, open the
   official app: it shows CS2.
   Result:
10. **Responses to writes.** In the log of item 9: does the headset send an `IN` report after
    `01`-direction writes (`01 09`, `01 0a`, `01 03`, `01 01`)? Record what comes back, if
    anything.
    Result:
11. **Persistence.** Apply a preset in our app, unplug, replug: the same curve is read back and
    the same pill is active.
    Result:
12. **All 9 presets.** Tap Default, Custom1, CS2, R&B, Valorant, Hip-hop, Rock, Delta, APEX in
    turn. Each one: sound changes, no error, its pill stays active.
    Result:
13. **Band set to Off (off-ear).** Tap a point, set its type to Off. Listen: R4 — the Off
    coefficients from Hub have b0 = 1/64, which may attenuate the whole signal by about 36 dB.
    Record whether the overall level drops.
    Result:
14. **Negative-gain high shelf (off-ear).** Set a band to High shelf, 4000 Hz, gain −6 dB.
    Listen: the Hub math for this case may produce a polarity-inverted boost rather than a cut.
    Record whether the treble is cut, boosted or sounds odd.
    Result:
15. **Playback gain offset (off-ear).** Device tab: set +1.0 dB, then −1.0 dB, then 0. Each one
    is accepted without error and the loudness changes accordingly. Replug: the value is kept.
    Only then try larger values.
    Result:
16. **Mic gain offset (off-ear).** Microphone tab: +1.0 dB, −1.0 dB, back to 0, checked with a
    voice recording. Replug: the value is kept.
    Result:
17. **Noise cancelling.** Microphone tab: toggle on/off, test in a recording. Replug: is the
    state kept? Compare with what the official app shows.
    Result:
18. **Live drag.** Drag a point on the curve: the sound follows while dragging; after release the
    log shows exactly one `01 01` about 1.5 s later. Drag continuously for about 5 s: no error, the
    headset keeps answering.
    Result:
19. **Unplug mid-drag.** Unplug while dragging: the app shows "Disconnected" with "Try again",
    nothing crashes. Replug: the curve is re-read from the headset.
    Result:
20. **Inline buttons.** While the app is open (interface claimed): do volume/play buttons work?
    Does audio playback keep working? Press Home: the buttons work again.
    Result:
21. **Force-stop while connected.** With the app open, run
    `adb shell am force-stop io.github.r415uk3.atkhorizon`. Do the inline buttons work again
    without replugging?
    Result:
22. **Soft reset.** Device tab → Reset settings → Reset. The curve goes to Default, both offsets
    to 0.0 dB, no error. The official app shows Default afterwards.
    Result:
23. **Languages.** Switch the system language between Russian and English: all texts follow.
    Result:
24. **Themes.** Settings: Light, Dark, Auto (Auto follows the system theme).
    Result:
25. **Band card vs curve.** Tap a point: the band card opens. Note whether it covers the curve or
    the point being edited, on a small and a large screen if available.
    Result:
26. **Gesture feel.** Dragging points (frequency on X, gain on Y), tapping, scrolling the screens:
    note anything that feels off.
    Result:
