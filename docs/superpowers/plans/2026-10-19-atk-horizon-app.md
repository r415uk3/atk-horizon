# ATK Horizon Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android app that does what ATK Hub does for the ATK Horizon USB-C headset (TTGK, PID 0x120C): presets and an 8-band parametric EQ verified by reading back from the device, mic noise cancelling, gain offsets, soft reset, firmware version. Liquid-glass UI, RU/EN.

**Architecture:** One Gradle module `app`. The `protocol` package is pure Kotlin with no Android imports: biquad math, packet codecs, request/response matching over an abstract `HidTransport`, and high-level `HorizonDevice` operations. All of it is unit-tested on the JVM against a fake device. `usb` implements `HidTransport` with `UsbManager`. `data` holds presets and settings in DataStore. `ui` is Compose with one `HorizonViewModel`.

**Tech Stack:**
- Kotlin, Jetpack Compose (Material 3 as the base, custom glass components on top).
- kotlinx-coroutines, kotlinx-serialization-json.
- AndroidX DataStore (Preferences).
- Haze (`dev.chrisbanes.haze`) for backdrop blur, AGSL `RuntimeShader` for the specular rim.
- JUnit 4 + kotlinx-coroutines-test.
- Use the newest stable versions at execution time and pin them in `gradle/libs.versions.toml`. AGP must be ≥ 8.9 for compileSdk 36.

**Spec:** `docs/superpowers/specs/2026-10-19-atk-horizon-app-design.md`. Read it alongside this plan. Its "Протокол" section is the byte-level source of truth. The minified Hub bundle is in `.reference/` (gitignored, never commit it, read with `dd` at the spec's offsets, never execute it).

## Global Constraints

**Platform**
- `applicationId` / package: `io.github.r415uk3.atkhorizon`.
- `minSdk 33`, `compileSdk 36`, `targetSdk 36`. Test device: Android 16.
- JDK 21 is installed. Gradle is not installed: run `brew install gradle` once, then `gradle wrapper` to generate the wrapper. After that, always use `./gradlew`.

**Device and transport**
- Only VID `0x373B` / PID `0x120C` is supported. KTMicro (PID `0x129F`) is out of scope: show it as an unsupported device.
- Report ID `0x4B`. 63 data bytes, zero-padded. Byte 0 is the direction: `0x01` write, `0x80` read.
- Commands allowed: `0x01 0x02 0x03 0x09 0x0A 0x0C 0x1B`.
- Commands forbidden, with no code path that can send them: `0x05` (erase flash), `0x0D`, `0x17`, `0xFF`.

**EQ**
- 8 bands. Sample rate 96000. Coefficients are quantized ×2^30 with JS `Math.round` semantics: `floor(x + 0.5)`. Do **not** use `kotlin.math.round`, which rounds half to even.
- Coefficients wrap to int32 like JS `>>0`, then are written little-endian.
- UI limits: fc 20–20000 Hz (integer), gain −10…+10 dB step 0.1, Q 0.1–10. Defaults: fc 31/62/125/250/500/1k/2k/4k, Q 0.75, gain 0, peak.

**Timing**
- Reads: 500 ms timeout, 3 retries.
- The flash-save scheme runs 500 ms after the last edit: band `0x09` → `0x0A` (`01 0A 04 00 00 FF FF`) → `0x03` (current DAC offset) → wait 1000 ms → `0x01`.

**UI**
- Strings in `values/strings.xml` (EN, default) and `values-ru/strings.xml` (RU), language follows the system.
- Theme: Auto / Light / Dark, persisted. Auto follows the system.
- Built-in presets come from the spec's table: Default, Custom1, CS2, R&B, Valorant, Hip-hop, Rock, Delta, APEX. They cannot be deleted.

## Review Focus

1. **Headset unplugged mid-apply or mid-drag.** The pending operation fails with a visible "disconnected" state. Nothing crashes, nothing keeps retrying forever. On re-plug, state is re-read from the device. (Task 5 test `unplugDuringApplyFailsCleanly`, Task 8 test `disconnectMovesToDisconnectedState`.)
2. **Device holds a curve that matches no preset, or a band with type > 5 or fc 0** (for example, set by the official app). The app shows "Custom" and renders unknown types as Off, without crashing. (Task 2 test `parseBandUnknownTypeMapsToDisabled`, Task 6 test `curveMatchingNoPresetReturnsNull`.)
3. **USB permission denied, or a different ATK device plugged in.** A clear screen with a retry button. The app never sits on an empty spinner. (Task 8 test `permissionDeniedState`, Task 7 manual step.)
4. **Extreme band values.** Highshelf clamping and the stability check would throw in Hub. The UI clamps its inputs, and an unstable filter is rejected with a message rather than sent. (Task 1 test `highshelfUnstableThrows`, Task 8 test `unstableBandShowsErrorAndIsNotSent`.)
5. **Rapid input:** switching presets quickly, or dragging while an apply is still running. All device I/O is serialized, the last request wins, and flash is saved once after the debounce, not once per event. (Task 8 tests `rapidPresetSwitchSavesOnce`, `dragThrottlesLiveWritesAndSavesOnceAfterRelease`.)

---

### Task 0: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/io/github/r415uk3/atkhorizon/MainActivity.kt`, `app/src/test/java/io/github/r415uk3/atkhorizon/SanityTest.kt`, `README.md`
- Modify: `.gitignore` (add `build/`, `.gradle/`, `local.properties`, `*.apk`, `.idea/`)

- [ ] **Step 1:** Generate the Gradle wrapper (`brew install gradle && gradle wrapper`).
- [ ] **Step 2:** Configure the app module:
  - Compose enabled, kotlinx-serialization plugin applied.
  - `minSdk 33`, compile/target 36.
  - `MainActivity` shows a `Text("ATK Horizon")`.
  - `local.properties` sets `sdk.dir=$HOME/Library/Android/sdk`.
- [ ] **Step 3:** Add `SanityTest` asserting `1 + 1 == 2`.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: BUILD SUCCESSFUL, and `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] **Step 5:** Add a `README.md`: one paragraph on what the app is, plus "build: `./gradlew assembleDebug`".
- [ ] **Step 6:** Commit: `chore: scaffold Android project`.

### Task 1: Biquad coefficient port

**Files:**
- Create: `app/src/main/java/io/github/r415uk3/atkhorizon/protocol/Biquad.kt`
- Test: `app/src/test/java/io/github/r415uk3/atkhorizon/protocol/BiquadTest.kt`

**Interfaces:**
- Produces:
  - `enum class FilterType(val code: Int) { Off(0), LowShelf(1), Peak(2), HighShelf(3), LowPass(4), HighPass(5); companion object { fun fromCode(c: Int): FilterType } }`. `fromCode` maps unknown codes to `Off`.
  - `object Biquad { const val SAMPLE_RATE = 96000; fun coefficientBytes(type: FilterType, fc: Int, q: Double, gainDb: Double): ByteArray /* 20 bytes */ }`
  - `class UnstableFilterException(msg: String) : IllegalArgumentException(msg)`

- [ ] **Step 1: Write the failing tests.** Expected hex was computed from the Hub formulas. The peak 1 kHz vector matches the protocol recon independently.

```kotlin
private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it) }
@Test fun peak1k() = assertEquals("6d fe b4 40 03 bf b1 83 44 8a dd 3b fd 40 4e 7c 4f 77 6d c3", hex(Biquad.coefficientBytes(FilterType.Peak, 1000, 1.0, 3.0)))
@Test fun peakFlat() = assertEquals("00 00 00 40 98 54 2c 80 a7 bc d3 3f 68 ab d3 7f 59 43 2c c0", hex(Biquad.coefficientBytes(FilterType.Peak, 31, 0.75, 0.0)))
@Test fun peakCut() = assertEquals("44 b9 ea 3f a5 8e 73 80 8c f8 a1 3f 5b 71 8c 7f 31 4e 73 c0", hex(Biquad.coefficientBytes(FilterType.Peak, 60, 0.7, -4.0)))
@Test fun lowshelf() = assertEquals("2c 9b 13 40 67 88 97 80 94 bd 55 3f 25 a1 68 7f cc d0 96 c0", hex(Biquad.coefficientBytes(FilterType.LowShelf, 100, 0.7, 4.0)))
@Test fun highshelfCut() = assertEquals("92 5c f5 ae 1b 24 7b 66 25 a0 03 dd 4c 86 af 4a 86 9a c4 e7", hex(Biquad.coefficientBytes(FilterType.HighShelf, 8000, 0.7, -3.0)))
@Test fun highshelfBoost() = assertEquals("6e a3 0a 51 e5 db 84 99 db 5f fc 22 4c 86 af 4a 86 9a c4 e7", hex(Biquad.coefficientBytes(FilterType.HighShelf, 8000, 0.7, 3.0)))
@Test fun lowpass() = assertEquals("7b 60 9f 04 f6 c0 3e 09 7b 60 9f 04 52 c7 fc 46 c2 b6 85 e6", hex(Biquad.coefficientBytes(FilterType.LowPass, 10000, 0.707, 0.0)))
@Test fun highpass() = assertEquals("83 b1 e1 3f f9 9c 3c 80 83 b1 e1 3f ae 54 c3 7f a0 8e 3c c0", hex(Biquad.coefficientBytes(FilterType.HighPass, 40, 0.707, 0.0)))
@Test fun off() = assertEquals("00 00 00 01 " + List(16) { "00" }.joinToString(" "), hex(Biquad.coefficientBytes(FilterType.Off, 1000, 1.0, 0.0)))
@Test(expected = UnstableFilterException::class) fun highshelfUnstableThrows() { Biquad.coefficientBytes(FilterType.HighShelf, 20, 5.0, 12.0) } // sqrt(a2) ≈ 0.99970
```

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*BiquadTest'`. Expected: compilation FAIL.
- [ ] **Step 3: Implement `Biquad.coefficientBytes`** as a 1:1 port of the bundle at offset ~627700–631300 (classes `BW` peak, `NW` lowshelf, `GW` highshelf, `WW` lowpass, `qW` highpass; factory `Pb.calculateByBytes` at ~631300). Quirks to preserve exactly:
  - **Lowshelf** ignores Q and uses a fixed slope of 0.8.
  - **Highshelf** clamps and scales its inputs: freq is clamped to [20, 0.99·fs/2], Q to [0.5, 5], gain to ±12, then gain is multiplied by 0.85.
  - **Highshelf** uses |gain| and multiplies the b terms by sign(gain).
  - **Highshelf** throws `UnstableFilterException` if `sqrt(a2) >= 0.999`.
  - **Quantizer output order:** `[b0, b1, b2, -a1, -a2]`.
  - **Off** returns the literal 20 bytes `[0,0,0,1,0…]`.
- [ ] **Step 4:** Run the same command. Expected: PASS (10 tests).
- [ ] **Step 5:** Commit: `feat(protocol): port Hub biquad coefficient math`.

### Task 2: Packet codecs

**Files:**
- Create: `protocol/Packets.kt`, `protocol/EqBand.kt`
- Test: `protocol/PacketsTest.kt`
- All paths are under `app/src/{main,test}/java/io/github/r415uk3/atkhorizon/`.

**Interfaces:**
- Consumes: `Biquad`, `FilterType` (Task 1).
- Produces:
  - `data class EqBand(val type: FilterType, val fc: Int, val q: Double, val gainDb: Double)`
    - `fun clamped(): EqBand` clamps to fc 20..20000, q 0.1..10, gain −10..10, and rounds gain to 0.1.
    - `companion object { val DEFAULTS: List<EqBand> }`
  - `data class DeviceBand(val index: Int, val band: EqBand, val qRaw: Int, val gainRaw: Int, val coeff: ByteArray)`
    - The raw read-back values, used for exact comparison.
    - Override `equals`/`hashCode` to use `coeff.contentEquals`.
  - `object Packets` (each function returns 63 bytes, with no report ID):
    - `fun writeBand(index: Int, b: EqBand): ByteArray`
    - `fun readBand(index: Int): ByteArray`
    - `fun commitCoefficients(): ByteArray`
    - `fun saveToFlash(): ByteArray`
    - `fun setDacOffset(db: Double): ByteArray`
    - `fun readDacOffset(): ByteArray`
    - `fun setAdcOffset(db: Double): ByteArray`
    - `fun readAdcOffset(): ByteArray`
    - `fun setNoiseCancelling(on: Boolean): ByteArray`
    - `fun readNoiseCancelling(): ByteArray`
    - `fun readFirmware(): ByteArray`
  - `object Responses` (each takes a 63-byte response, with no report ID):
    - `fun band(r: ByteArray): DeviceBand`
    - `fun offsetDb(r: ByteArray): Double`
    - `fun noiseCancelling(r: ByteArray): Boolean`
    - `fun firmware(r: ByteArray): String`
  - `const val REPORT_ID: Byte = 0x4B`

- [ ] **Step 1: Write failing tests.**

  `writeBandLayout`: `Packets.writeBand(3, EqBand(Peak, 1000, 1.0, 3.0))` produces this layout (indices relative to the 63-byte array):

  | Bytes | Value |
  |---|---|
  | `[0..4]` | `01 09 18 00 03` |
  | `[5..6]` | `00 00` |
  | `[7..26]` | the Task 1 peak1k bytes |
  | `[27..28]` | `e8 03` |
  | `[29..30]` | `00 01` |
  | `[31..32]` | `00 03` |
  | `[33]` | `02` |
  | `[34]` | `00` |
  | `[35]` | `00` |
  | everything after | zero |
  | size | 63 |

  The other tests:

  | Test | Assertion |
  |---|---|
  | `writeBandNegativeGain` | gain −4.0 → bytes `[31..32]` = `00 fc` |
  | `commitLayout` | `01 0a 04 00 00 ff ff` then zeros |
  | `saveLayout` | `01 01` then zeros |
  | `dacOffsetLayout` | `setDacOffset(-1.5)` → `01 03 02 80 fe` |
  | `adcOffsetLayout` | `setAdcOffset(2.0)` → `01 02 02 00 02` |
  | `readBandLayout` | `80 09 02 00 05` |
  | `ncLayout` | `01 1b 01 01`; read `80 1b 00` |
  | `firmwareLayout` | read `80 0c 00` |
  | `parseBandRoundTrip` | `Responses.band(Packets.writeBand(3, b).also { it[0] = 0x80.toByte() })` returns index 3, the same `EqBand`, `qRaw = 256`, `gainRaw = 768`, and coeff equal to the written coeff |
  | `parseBandUnknownTypeMapsToDisabled` | byte 33 = 9 → `type == Off` |
  | `parseFirmware` | bytes 3..5 = `"0.9"` → `"0.9"` |
  | `parseOffset` | bytes 3..4 = `80 fe` → −1.5 |
  | `parseNc` | byte3 = 1 → true |
  | `clampedLimits` | `EqBand(Peak, 5, 20.0, 15.04).clamped()` == `EqBand(Peak, 20, 10.0, 10.0)` |

- [ ] **Step 2:** Run `--tests '*PacketsTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use the offsets from the spec. Byte 2 of `writeBand` is always `0x18`. Encoding rules:
  - q and gain are `floor(x*256 + 0.5)` (JS `Math.round`).
  - Parsing gain divides by 256 and rounds to 1 decimal. Parsing q rounds to 2 decimals (Hub's `q88ToDecimal`).
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Add a test, `forbiddenCommandsUnreachable`: reflect over `Packets`' public functions, call each with sample args, and assert byte 1 of every result is in `{0x01,0x02,0x03,0x09,0x0A,0x0C,0x1B}`.
- [ ] **Step 6:** Run. Expected: PASS.
- [ ] **Step 7:** Commit: `feat(protocol): packet codecs`.

### Task 3: Transport and request matching

**Files:**
- Create: `protocol/HidTransport.kt`, `protocol/HorizonLink.kt`
- Test: `protocol/HorizonLinkTest.kt`, `protocol/FakeTransport.kt` (in the test source set)

**Interfaces:**
- Produces:
  - `interface HidTransport`, with only report payloads crossing it (no report ID):
    - `suspend fun send(payload: ByteArray)`
    - `val incoming: Flow<ByteArray>`
    - `fun close()`
  - `class DeviceGoneException : IOException()`
  - `class HorizonLink(transport: HidTransport, scope: CoroutineScope, timeoutMs: Long = 500, retries: Int = 3)`:
    - `suspend fun write(p: ByteArray)` is fire-and-forget.
    - `suspend fun request(p: ByteArray): ByteArray` sends and waits for the first incoming report whose byte 0 equals `p[0]` and byte 1 equals `p[1]`. Incoming reports with byte 0 == 0, or unmatched ones, are dropped.
    - On timeout it resends, up to `retries` times, then throws `TimeoutException`.
    - All calls are serialized by one `Mutex`.
    - `suspend fun delay(ms: Long)` is a test seam over `kotlinx.coroutines.delay`.

  Matching on byte 1 as well as byte 0 is my decision. It is stricter than Hub, which matches only on byte 0. If the hardware spike (Task 7) shows that responses do not echo the command in byte 1, relax this to byte 0 and record the change.

- [ ] **Step 1: Write failing tests** with `FakeTransport`, which records sent payloads and lets the test emit responses. Use `runTest` and virtual time.

  | Test | Assertion |
  |---|---|
  | `requestReturnsMatchingResponse` | the matching response is returned |
  | `ignoresZeroAndUnmatched` | an emitted `00 …` and an `01 09 …` before `80 0c …` are skipped |
  | `retriesThenTimesOut` | no response → 4 sends total, then `TimeoutException` after about 2000 ms virtual |
  | `retrySucceeds` | the response arrives after the 2nd send → returns |
  | `requestsAreSerialized` | two concurrent `request`s → the second send happens only after the first response |
  | `transportErrorPropagates` | `send` throws `DeviceGoneException` → the request throws `DeviceGoneException`, with no retries |

- [ ] **Step 2:** Run `--tests '*HorizonLinkTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Use a `Channel`/`SharedFlow` collector started in `scope` and `withTimeoutOrNull` per attempt.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(protocol): HID request/response link`.

### Task 4: Fake Horizon device (test double)

**Files:**
- Create (in the test source set): `protocol/FakeHorizon.kt`
- Test: `protocol/FakeHorizonTest.kt`

**Interfaces:**
- Produces:
  - `class FakeHorizon : HidTransport`. It simulates the device and holds state:

    | Field | Initial value |
    |---|---|
    | `bands` | `MutableList<DeviceBand>` of 8 defaults |
    | `flashBands` | snapshot taken on `0x01` |
    | `dacOffset` | 0.0 |
    | `adcOffset` | 0.0 |
    | `nc` | false |
    | `firmware` | `"0.9"` |

  - It answers reads by echoing bytes 0–1 with the payload, the same layout as the writes.
  - It records every received packet as `(byte0, byte1)` with its virtual timestamp in `log: List<LogEntry>`, where `data class LogEntry(val dir: Int, val cmd: Int, val atMs: Long)`.
  - Knobs:
    - `dropWritesFor: Set<Int>` silently ignores writes to those bands (simulates "didn't apply").
    - `unplugAfter: Int?` throws `DeviceGoneException` after N sends.
    - `failIfForbidden` makes any byte 1 in `{0x05,0x0D,0x17,0xFF}` fail the test.

- [ ] **Step 1: Write the tests.**
  - `readsWhatWasWritten`: `HorizonLink` over a `FakeHorizon`; `writeBand` then `request(readBand)` returns the written band.
  - `saveSnapshotsFlash`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `test(protocol): fake Horizon device`.

### Task 5: HorizonDevice operations

**Files:**
- Create: `protocol/HorizonDevice.kt`
- Test: `protocol/HorizonDeviceTest.kt`

**Interfaces:**
- Consumes: `HorizonLink`, `Packets`, `Responses` (Tasks 2–3).
- Produces:
  - `data class DeviceSnapshot(val firmware: String, val bands: List<DeviceBand>, val dacOffset: Double, val adcOffset: Double, val noiseCancelling: Boolean)`
  - `class VerifyException(val mismatchedBands: List<Int>) : IllegalStateException()`
  - `class HorizonDevice(link: HorizonLink)`:

    | Function | What it does |
    |---|---|
    | `suspend fun readSnapshot(): DeviceSnapshot` | Reads everything from the device. |
    | `suspend fun liveBand(index: Int, band: EqBand)` | Sends `0x09` and `0x0A` only. No flash save. |
    | `suspend fun commitBands(bands: Map<Int, EqBand>): List<DeviceBand>` | Runs the full flash scheme for the given bands: each band `0x09` → `0x0A` → `0x03` with the current DAC offset (read it first) → delay 1000 → `0x01`. Then reads those bands back and compares them. Throws `VerifyException` on any mismatch in fc, qRaw, gainRaw, type, or coeff bytes. Returns the read-back bands. |
    | `suspend fun applyPreset(bands: List<EqBand>): List<DeviceBand>` | `commitBands` for all 8. |
    | `suspend fun setDacOffset(db: Double)` / `suspend fun setAdcOffset(db: Double)` | Writes `0x03`/`0x02`, then `0x01`, then reads back. Throws `VerifyException(emptyList())` if the read-back differs by more than 1/256. |
    | `suspend fun setNoiseCancelling(on: Boolean)` | Writes, then reads back. Throws `VerifyException` if the value differs. |
    | `suspend fun softReset(): DeviceSnapshot` | `applyPreset(EqBand.DEFAULTS)`, then both offsets set to 0, then `readSnapshot()`. |

- [ ] **Step 1: Write failing tests** against `FakeHorizon`:

  | Test | Assertion |
  |---|---|
  | `applyPresetCommandOrder` | The log of `(byte0, byte1)` pairs equals 8×`(01,09)`, `(01,0A)`, `(80,03)`, `(01,03)`, `(01,01)`, 8×`(80,09)`, and `(01,01)` comes ≥ 1000 ms of virtual time after `(01,03)`. |
  | `applyPresetVerifies` | Returns 8 bands equal to the input. |
  | `applyPresetDetectsDroppedWrite` | `dropWritesFor = {2}` → `VerifyException(mismatchedBands = [2])`. |
  | `liveBandDoesNotSave` | No `0x01` in the log. |
  | `softResetRestoresDefaults` | Starts from a non-default state, ends with default bands and zero offsets in both `bands` and `flashBands`. |
  | `softResetNeverErases` | `failIfForbidden = true` passes. |
  | `unplugDuringApplyFailsCleanly` | `unplugAfter = 3` → `DeviceGoneException`, and no further sends. |
  | `ncVerify` | Covers `setNoiseCancelling`. |
  | `offsetsVerify` | Covers both offset setters. |

- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(protocol): verified Horizon operations`.

### Task 6: Presets and settings storage

**Files:**
- Create: `data/BuiltInPresets.kt`, `data/PresetRepository.kt`, `data/SettingsRepository.kt`
- Test: `data/BuiltInPresetsTest.kt`, `data/PresetMatchTest.kt`

**Interfaces:**
- Produces:
  - `data class Preset(val id: String, val name: String, val bands: List<EqBand>, val builtIn: Boolean)`
    - Built-in ids: `default`, `custom1`, `cs2`, `rnb`, `valorant`, `hiphop`, `rock`, `delta`, `apex`.
    - Built-in names are string resource keys, resolved in the UI.
  - `object BuiltInPresets { val all: List<Preset> }`
  - `fun matchPreset(device: List<DeviceBand>, presets: List<Preset>): Preset?`
    - Matches when, for every band: fc is equal, |gain − g| < 0.01, |q − q'| < 0.01, and the type is equal.
    - Returns the first match.
  - `class PresetRepository(dataStore: DataStore<Preferences>)`:
    - `val presets: Flow<List<Preset>>` lists built-ins first, then custom presets in creation order.
    - `suspend fun create(name: String, bands: List<EqBand>): Preset`
    - `suspend fun rename(id: String, name: String)`
    - `suspend fun delete(id: String)` is a no-op for built-ins.
    - Custom presets are stored as a JSON list (kotlinx-serialization) under the key `custom_presets`.
  - `enum class ThemeMode { Auto, Light, Dark }`
  - `class SettingsRepository(dataStore)`: `val themeMode: Flow<ThemeMode>`, `suspend fun setThemeMode(m: ThemeMode)`.

- [ ] **Step 1: Write failing tests.**

  | Test | Assertion |
  |---|---|
  | `builtInsMatchSpecTable` | Spot-checks every preset's first and last band against the spec table. Example: CS2 band 0 = (Peak, 60, 0.7, −4.0), band 7 = (Peak, 6800, 1.2, 1.0). APEX band 2 = (Peak, 355, 0.9, 3.5). All presets have 8 bands and all bands are Peak. |
  | `curveMatchingFindsCs2` | A device curve built from CS2 via `Responses.band(Packets.writeBand(…))` round-trips to `cs2`. |
  | `curveMatchingNoPresetReturnsNull` | — |
  | `curveMatchingToleratesQ88Rounding` | e.g. Q 0.7 → raw 179 → 0.6992 still matches. |

  Repository tests are optional (DataStore needs an Android context). If you skip them, cover the repository in the Task 8 ViewModel tests with an in-memory fake that implements the same interface: extract `interface PresetStore`.

- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(data): presets, matching, settings`.

### Task 7: USB transport and hardware spike

**Files:**
- Create: `usb/UsbHidTransport.kt`, `usb/HorizonConnector.kt`, `app/src/main/res/xml/device_filter.xml`
- Modify: `AndroidManifest.xml` (`<uses-feature android:name="android.hardware.usb.host"/>`, no `USB_DEVICE_ATTACHED` launcher intent-filter)
- Temporary: a debug button row in `MainActivity` (removed in Task 9)

**Interfaces:**
- Consumes: `HidTransport`, `HorizonLink`, `HorizonDevice`.
- Produces:
  - `sealed interface UsbState { NoDevice; Unsupported(pid: Int); NeedsPermission; PermissionDenied; Connected(device: HorizonDevice) }`
  - `class HorizonConnector(context: Context, scope: CoroutineScope)`:
    - `val state: StateFlow<UsbState>`
    - `fun requestPermission()`
    - `fun open()` claims the interface.
    - `fun release()` releases the interface and closes the connection.
    - It listens for `ACTION_USB_DEVICE_ATTACHED`/`DETACHED` through a runtime receiver.
  - `class UsbHidTransport(conn: UsbDeviceConnection, iface: UsbInterface) : HidTransport`:
    - **Interface:** the one with class HID (3) whose endpoint layout carries the vendor report. If there are several HID interfaces, pick the one where sending `80 0C 00` returns a reply; that is decided in the spike.
    - **Send:** `[0x4B] + payload`, 64 bytes, through `bulkTransfer` on the interrupt OUT endpoint if one exists. Otherwise use `controlTransfer(0x21, 0x09, 0x0200 or 0x4B, ifaceId, buf, 64, 500)`.
    - **Receive:** a coroutine on `Dispatchers.IO` loops on `bulkTransfer(inEp, buf, 64, 200)`. Reports whose first byte is `0x4B` are emitted with that byte stripped.
    - **Errors:** a negative return after detach → `DeviceGoneException`.

- [ ] **Step 1:** Implement, with `claimInterface(iface, true)` held from `open()` to `release()`. This follows user decision 2a: the inline buttons may stop working while the app is in the foreground.
- [ ] **Step 2:** `./gradlew :app:installDebug` with the phone on adb. If the phone's only USB-C port is taken by the headset, use `adb tcpip` / wireless debugging.
- [ ] **Step 3: Manual hardware checks.** Put the debug buttons on screen, log to logcat tag `Horizon`, and record each result in `docs/hardware-notes.md`:
  1. The device enumerates with VID `373B` / PID `120C`. Log the interfaces and endpoints.
  2. The permission dialog appears. Granting it gives `Connected`; denying it gives `PermissionDenied`.
  3. `readSnapshot()` returns firmware `"0.9"` and 8 bands.
  4. **Coefficient cross-check:** in the official ATK app, apply CS2, then read in our app. For each band, compare the device's coeff bytes with `Biquad.coefficientBytes(...)` for the read-back params. All 8 must be identical. Repeat for Valorant. If they differ, stop and report to the user before going further.
  5. Byte 1 echo: confirm that read replies echo the command in byte 1 (Task 3 assumption).
  6. `applyPreset(BuiltInPresets cs2)` from our app: the sound audibly changes, the read-back verifies, and the official app shows CS2 afterwards.
  7. Inline buttons while the interface is claimed versus after `release()`. Record what happens. Also check that audio playback keeps working while claimed.
  8. Live write rate: `liveBand` every 50 ms for 5 s. There must be no errors, and the device must still respond.
- [ ] **Step 4:** Commit: `feat(usb): USB HID transport + hardware notes`.

### Task 8: HorizonViewModel

**Files:**
- Create: `ui/HorizonViewModel.kt`, `ui/UiState.kt`
- Test: `ui/HorizonViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `HorizonDevice`, `PresetStore` (or `PresetRepository`), `SettingsRepository`, `matchPreset`, `UsbState`.
  - The ViewModel takes `usbState: StateFlow<UsbState>` so tests can drive it with `FakeHorizon`-backed devices.
- Produces:
  - `sealed interface ConnState { NoDevice; Unsupported; NeedsPermission; PermissionDenied; Loading; Ready; Disconnected }`
  - `data class UiState(...)`:

    | Field | Type |
    |---|---|
    | `conn` | `ConnState` |
    | `firmware` | `String?` |
    | `bands` | `List<EqBand>`, what the device reports, overlaid with in-flight edits |
    | `presets` | `List<Preset>` |
    | `activePresetId` | `String?` (null = custom) |
    | `dacOffset`, `adcOffset` | `Double` |
    | `noiseCancelling` | `Boolean` |
    | `busy` | `Boolean` |
    | `error` | `UiError?` |
    | `themeMode` | `ThemeMode` |

  - `sealed interface UiError { VerifyFailed(bands: List<Int>); Disconnected; Timeout; UnstableFilter(band: Int) }`
  - Methods:
    - `selectPreset(id)`
    - `onBandDrag(index, band)` is called continuously while dragging.
    - `onBandDragEnd(index)`
    - `setBand(index, band)` for edits from the panel.
    - `saveAsPreset(name)`, `renamePreset(id, name)`, `deletePreset(id)`
    - `setNoiseCancelling(on)`, `setDacOffset(db)`, `setAdcOffset(db)`
    - `softReset()`, `setThemeMode(m)`, `dismissError()`, `retryPermission()`
- Behaviour:
  - **On `Connected`:** `Loading`, then `readSnapshot()`, then `Ready`. The UI always reflects the snapshot, and `activePresetId = matchPreset(...)`.
  - **Drag:** each band is clamped. `liveBand` is throttled to at most one call per 60 ms, and the latest value wins.
  - **Commit:** 500 ms after the last drag event or `setBand`, `commitBands` runs for all bands changed since the last commit. Its read-back replaces `bands`. `VerifyException` → `UiError.VerifyFailed`, and the bands show the device's values.
  - **Preset select:** cancels any pending commit, runs `applyPreset`, and sets `activePresetId` from `matchPreset` on the result.
  - **Unstable filter:** `UnstableFilterException` from `Biquad` → `UiError.UnstableFilter`. That band is not sent.
  - **Errors:** `DeviceGoneException` / `Detached` → `Disconnected`. `TimeoutException` → `UiError.Timeout`.

- [ ] **Step 1: Write failing tests** with `StandardTestDispatcher` and virtual time:
  - `loadsSnapshotOnConnect`
  - `presetMatchedOnConnect`
  - `rapidPresetSwitchSavesOnce`: three `selectPreset` calls within 100 ms → the last preset ends up on the device, and `flashBands` equals it.
  - `dragThrottlesLiveWritesAndSavesOnceAfterRelease`: 40 drag events 10 ms apart → ≤ 8 `liveBand` sends, then exactly one `0x01` after 500 ms + 1000 ms.
  - `verifyFailureSurfacesErrorAndShowsDeviceValues`: uses `dropWritesFor`.
  - `unstableBandShowsErrorAndIsNotSent`
  - `disconnectMovesToDisconnectedState`
  - `permissionDeniedState`
  - `softResetShowsDefaults`
  - `savedPresetBecomesActive`
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ui): HorizonViewModel`.

### Task 9: Theme, glass components, navigation shell, strings

**Files:**
- Create: `ui/theme/Theme.kt`, `ui/glass/GlassSurface.kt`, `ui/glass/RimShader.kt`, `ui/AppShell.kt`, `ui/screens/StatusScreens.kt`, `res/values/strings.xml`, `res/values-ru/strings.xml`
- Modify: `MainActivity.kt`:
  - Remove the debug buttons.
  - Host `AppShell`.
  - Call `connector.open()` in `onStart` and `connector.release()` in `onStop` (decision 2a: release on background so the inline buttons come back).

**Interfaces:**
- Produces:
  - `@Composable fun HorizonTheme(mode: ThemeMode, content: @Composable () -> Unit)` with light and dark palettes from the mockup:

    | Theme | Background | Blobs (alpha) | Glass fill | Glass border | Ink |
    |---|---|---|---|---|---|
    | Light | `#E9ECF3` | `#BF5AF2` (0.35), `#64D2FF` (0.40) | white 42% | white 70% | `#111111` |
    | Dark | `#0B0D12` | `#5E5CE6` (0.35), `#0A84FF` (0.25) | white 8% | white 18% | white |

  - `@Composable fun GlassSurface(modifier, shape, content)`: a Haze backdrop blur of about 22 dp, the fill and border colours above, and a 1 px top highlight through the AGSL rim shader.
  - `@Composable fun BlobBackground()`
  - `@Composable fun AppShell(vm: HorizonViewModel)`:
    - A floating glass pill tab bar: Sound / Mic / Device.
    - A ⚙︎ button opens Settings.
    - For `ConnState` other than `Ready`/`Loading`, it shows `StatusScreens`: "Connect ATK Horizon", "Unsupported device (PID …)", "Allow USB access" + button, "Permission denied" + retry, "Disconnected".

- [ ] **Step 1:** Add every user-facing string to both locales, including preset names, error messages and the reset confirmation. Add a unit test `stringsHaveRuAndEn` that parses both XML files and asserts they have identical key sets.
- [ ] **Step 2:** Run `--tests '*Strings*'`. Expected: PASS.
- [ ] **Step 3:** Implement the theme, glass and shell. Compare against `docs/design/mockup-main-screen.html` in an emulator or on the device, in light and dark.
- [ ] **Step 4:** `./gradlew :app:assembleDebug` → install, then check that Auto follows a system dark-mode toggle.
- [ ] **Step 5:** Commit: `feat(ui): glass theme, shell, status screens, RU/EN`.

### Task 10: Sound screen (EQ curve, presets, band panel)

**Files:**
- Create: `ui/eq/FrequencyResponse.kt`, `ui/eq/EqCurve.kt`, `ui/eq/BandSheet.kt`, `ui/screens/SoundScreen.kt`, `ui/screens/PresetPills.kt`
- Test: `ui/eq/FrequencyResponseTest.kt`, `ui/eq/AxisMappingTest.kt`

**Interfaces:**
- Produces:
  - `object FrequencyResponse { fun magnitudeDb(bands: List<EqBand>, freqHz: Double): Double }`
    - Evaluates `|H(e^jw)|` of the cascaded biquads, using the same coefficients `Biquad` produces (decode the int32/2^30 back to doubles).
  - `object Axis`:
    - `fun freqToX(f: Double, width: Float): Float` maps log10 over 20…20000.
    - `fun xToFreq(x: Float, width: Float): Int`
    - `fun gainToY(g: Double, height: Float): Float` maps ±12 dB, with 0 at the centre.
    - `fun yToGain(y: Float, height: Float): Double` rounds to 0.1.

- [ ] **Step 1: Write failing tests.**

  | Test | Assertion |
  |---|---|
  | `flatIsZero` | DEFAULTS at 1 kHz → \|dB\| < 0.01 |
  | `peakAtCentre` | one Peak +3 dB at 1 kHz → 3.0 ± 0.05 at 1 kHz, and < 0.5 at 20 Hz |
  | `axisRoundTrip` | `xToFreq(freqToX(1000, 300f), 300f) == 1000 ± 1%` |
  | `axisEnds` | 20 → 0, 20000 → width |
  | `gainRoundTrip` | — |

- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement `FrequencyResponse` and `Axis`.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5: Build the UI.**
  - **`EqCurve`:** a Canvas curve sampled at 128 log-spaced points with a gradient fill, and 8 draggable knobs. Dragging changes fc on X and gain on Y. It calls `vm.onBandDrag` / `onBandDragEnd`. Tapping a knob opens `BandSheet`.
  - **`BandSheet`:** a glass bottom sheet with a type selector (6 types) and steppers/sliders for fc, gain (0.1 step) and Q. It calls `vm.setBand`.
  - **`PresetPills`:** a horizontal row with the active pill filled and "Custom" shown when `activePresetId == null`. Long-press on a custom pill gives rename/delete. A "+" pill opens a "Save current as…" dialog.
  - **Errors:** shown as a glass snackbar with the text for the `UiError`.
- [ ] **Step 6:** Manual check on the device:
  - Drag a knob and hear the change live.
  - Release it, wait about 1.5 s, unplug and replug. The curve persists, and it is re-read from the device.
- [ ] **Step 7:** Commit: `feat(ui): sound screen with live EQ curve`.

### Task 11: Mic, Device and Settings screens

**Files:**
- Create: `ui/screens/MicScreen.kt`, `ui/screens/DeviceScreen.kt`, `ui/screens/SettingsScreen.kt`

**Content:**
- **Mic:**
  - AI noise-cancelling switch → `vm.setNoiseCancelling`.
  - Mic gain offset slider, −10…+10 dB in 0.5 steps, applied on release → `vm.setAdcOffset`.
  - The range is my call; Hub's exact UI range is at bundle offset ~1376234 onward. Check it there and use Hub's range if it differs.
- **Device:**
  - Model "ATK Horizon", VID/PID, and firmware, all read-only.
  - Playback gain offset slider (same rules as the mic slider) → `vm.setDacOffset`.
  - "Reset settings" button → a confirmation dialog explaining that it resets the EQ to Default and the offsets to 0 → `vm.softReset()`.
- **Settings:**
  - A theme segmented control: Auto / Light / Dark.
  - A GitHub link that opens `https://github.com/r415uk3/atk-horizon`. The repository may not exist yet; the link simply 404s until it is published.
  - The app version from `BuildConfig.VERSION_NAME`.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Manual check on the device:
  - Toggle noise cancelling, close the app, open it again. The state is re-read, and the official app agrees.
  - Run a reset and confirm that the curve and offsets return to their defaults.
- [ ] **Step 3:** Commit: `feat(ui): mic, device and settings screens`.

### Task 12: End-to-end hardware pass and release APK

**Files:**
- Modify: `docs/hardware-notes.md`, `README.md` (install from the APK, supported device, what is intentionally not supported)

- [ ] **Step 1:** Run the whole checklist on the phone and record the pass/fail of each item in `docs/hardware-notes.md`:
  1. Open the app with no headset → the "Connect" screen.
  2. Plug in → permission → current curve and active preset shown.
  3. Switch through all 9 presets → each one verifies, and the sound changes.
  4. Unplug mid-drag → "Disconnected"; replug → state re-read.
  5. Put the app in the background → the inline buttons work again.
  6. RU and EN system language.
  7. Light, dark and auto themes.
  8. Reset.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest :app:assembleRelease`. Use debug signing for a personal build: set `signingConfig = signingConfigs.getByName("debug")` for release in this personal project. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit: `docs: hardware pass, README`.
