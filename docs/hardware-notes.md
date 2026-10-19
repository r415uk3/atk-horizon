# Hardware notes: ATK Horizon (VID 373B / PID 120C)

Manual checks for the Task 7 hardware session. They have not been run yet: the transport was built
without a phone attached. Use the debug buttons in `MainActivity` and read the logs with
`adb logcat -s Horizon` (raw IN/OUT reports are logged in hex at debug level). Record each result below.
For item 1, also confirm which HID interface `HidInterfaceChooser` picked (log line
`using HID interface N`) and that its report descriptor declares report ID `0x4B`.

1. The device enumerates with VID `373B` / PID `120C`. Log the interfaces and endpoints.
   Result:
2. The permission dialog appears. Granting it gives `Connected`; denying it gives `PermissionDenied`.
   Result:
3. `readSnapshot()` returns firmware `"0.9"` and 8 bands.
   Result:
4. **Coefficient cross-check:** in the official ATK app, apply CS2, then read in our app. For each band, compare the device's coeff bytes with `Biquad.coefficientBytes(...)` for the read-back params. All 8 must be identical. Repeat for Valorant. If they differ, stop and report to the user before going further.
   Result:
5. Byte 1 echo: confirm that read replies echo the command in byte 1 (Task 3 assumption).
   Result:
6. `applyPreset(BuiltInPresets cs2)` from our app: the sound audibly changes, the read-back verifies, and the official app shows CS2 afterwards.
   Result:
7. Inline buttons while the interface is claimed versus after `release()`. Record what happens. Also check that audio playback keeps working while claimed.
   Result:
8. Live write rate: `liveBand` every 50 ms for 5 s. There must be no errors, and the device must still respond.
   Result:
9. Apply a preset, unplug and replug: the curve persisted (flash save and 0x0A cannot be read back by protocol).
   Result:
10. Responses to fire-and-forget writes: does the device send an input report for 01-direction writes? (log raw IN reports)
    Result:
