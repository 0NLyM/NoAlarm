# NoAlarm — Memoria Progetto

## Stile di Coding — Senior Dev Minimalista

Prima di scrivere qualsiasi codice, percorri questa scala decisionale (fermarsi al primo gradino che risolve il problema):

1. **Saltalo** — il codice deve proprio esistere? (YAGNI)
2. **Stdlib/builtin** — c'è una funzione nativa o primitiva della piattaforma?
3. **Dipendenza esistente** — una libreria già installata lo gestisce?
4. **One-liner** — si risolve in una singola espressione?
5. **Codice minimo funzionante** — solo a questo punto scrivi qualcosa, niente di più.

Nessun boilerplate, no astrazioni gratuite, no commenti che spiegano l'ovvio. Scegli sempre la soluzione più breve e corretta per prima.

## Workflow Git Stabilito

- **Branch develop**: `claude/noalarm-glyph-matrix-zntmka`
- **Ciclo**: code change → commit (Italian message + attribution footer) → push → CI verde → bump `versionCode`/`versionName` → commit/push → CI verde → dispatch `release.yml` con `inputs: {"version": "X.Y.Z"}` → verifica via `get_release_by_tag`
- **Attribution footer (commit messages)**:
  ```
  Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_011Vy4kSGNSnK8LvXhLwPYau
  ```
- **Attribution footer (PRs)**: 
  ```
  🤖 Generated with [Claude Code](https://claude.com/claude-code)
  https://claude.ai/code/session_011Vy4kSGNSnK8LvXhLwPYau
  ```

## Architettura Tecnica Chiave

### Moduli
- `:app` — app phone (Kotlin/Jetpack Compose, API 33+)
- `:wear` — app watch (Kotlin/Jetpack Compose, API 30+, Wear OS 3+)

### Bluetooth Echo via RFCOMM Classico
**Problema**: Google's Wearable Data Layer API richiede watch abbinato via "Wear OS by Google" companion app. Con pairing di terze parti (Mobvoi Health su Ticwatch) il watch non è mai registrato come "nodo" → `connectedNodes` vuoto → messaggi non arrivano mai.

**Soluzione**: Bluetooth Classic RFCOMM diretto — due-direzionale su un'unica socket.
- **Phone** (`WearBridge.kt`): client RFCOMM, itera `getBondedDevices()`, crea socket, invia `ACTION_RING + id + label`, rimane in ascolto di `ACTION_SNOOZE`/`ACTION_DISMISS`.
- **Watch** (`BridgeService.kt`): server RFCOMM permanente (`listenUsingRfcommWithServiceRecord()`), accetta connessioni, fa suonare `RingActivity`, scrive la risposta sulla stessa socket.
- **UUID costante** (duplicato in entrambi i file — nessun shared module): `a4f7f228-8f1a-4b8e-9c7b-6b6c6f6e6f77`

### Fallback di Connessione Bluetooth
Su stack Bluetooth di terze parti (Ticwatch/Mobvoi), la ricerca SDP dell'UUID appena registrato può fallire silenziosamente. **Fallback in `WearBridge.connect()`**: se `createRfcommSocketToServiceRecord()` fallisce, bypassa SDP e connette direttamente al canale RFCOMM 1 via reflection:
```kotlin
device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
    .invoke(device, 1) as BluetoothSocket
```

### Vibrazione sul Watch
**Bug risolto in v1.4.15**: `RingActivity` usava `createWaveform(..., repeat=0)` (loop infinito) senza mai chiamare `vibrator.cancel()` → continuava a vibrare anche dopo Spegni/Posticipa o stop dal telefono.
**Fix**: memorizzare il `Vibrator` e chiamare `vibrator.cancel()` in `onDestroy()`.

### Permessi Bluetooth (API-level-gated)
- **API 31+**: `BLUETOOTH_CONNECT` (runtime/dangerous)
- **API ≤30**: `BLUETOOTH`/`BLUETOOTH_ADMIN` (normal/install-time, con `android:maxSdkVersion="30"`)

**Regola critica**: ogni `checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)` su pre-31 deve essere guarded da `Build.VERSION.SDK_INT < 31 || ...` altrimenti riporta falso-negativo.

### Foreground Service Contract (Android 8+)
Qualsiasi servizio avviato via `startForegroundService()` DEVE chiamare `startForeground()` entro pochi secondi o l'OS crasha l'app. **Watch crash v1.4.13-14**: permission check in `start()` non matching quello in `onStartCommand()` → su API ≤30 si bloccava senza `startForeground()`. **Fix v1.4.14**: unificare in `hasPermission(context)` riusato da entrambi.

### Metadata Wear OS
`android:name="com.google.android.wearable.standalone"` governa **solo** l'auto-install del Play Store (non la logica app). Con `value="false"`, alcuni launcher Wear OS (Ticwatch/Mobvoi) **nascondono** app sideload dal drawer (anche se installate) perché non risultano "paired" via quel meccanismo. **Fix v1.4.14**: `value="true"`.

## Versioni Attuali

- **App**: v1.4.16 (versionCode 37)
- **Watch**: v1.1.3 (versionCode 6)

Nota: il versionCode della watch era hardcoded a 1 per ogni build fino a v1.4.14 — ora incrementa correttamente.

## Errori Comuni & Soluzioni

### Watch App Crasha all'Avvio
1. ✅ **Causa**: service startato via `startForegroundService()` ma permesso mancante → `onStartCommand()` stoppa senza `startForeground()`.
2. **Fix**: unificare permission check in `hasPermission(context)` con bypass SDK<31.

### Vibrazione Infinita dopo Dismiss/Snooze
1. ✅ **Causa**: waveform con `repeat=0` mai cancellato.
2. **Fix**: `vibrator.cancel()` in `onDestroy()`.

### Sveglia Non Arriva sul Watch
1. ✅ **Causa 1**: SDP fallisce silenziosamente su stack terze parti.
   - **Fix**: fallback al canale RFCOMM 1.
2. ✅ **Causa 2**: Discovery in corso blocca connessioni.
   - **Fix**: `adapter.cancelDiscovery()` prima di tentare connect.
3. ✅ **Causa 3 (v1.4.16)**: `cancelDiscovery()` richiede `BLUETOOTH_SCAN` su API 31+ (mai dichiarato: l'eco usa solo `BLUETOOTH_CONNECT`) → `SecurityException` non catturata dentro `executor.execute()` crashava l'intero processo telefono (app + `AlarmService` gia' in riproduzione) ogni volta che "Suona anche sull'orologio" era attivo, e la connessione RFCOMM non superava mai quel punto.
   - **Fix**: `runCatching { adapter.cancelDiscovery() }` in `WearBridge.connect()` — e' solo un'ottimizzazione facoltativa, non deve essere fatale.

### Lint Failure `wear:lintVitalRelease`
✅ **Risolto in v1.4.13**: `play-services-wearable` tirava transitive fragment old, aggiunto `libs.androidx.fragment.ktx`. Poi rimosso tutto `play-services-wearable` quando passato a RFCOMM.

## Data Model

- `Alarm.ringOnWatch: Boolean` (default true) → sveglia suona anche sul watch
- `Settings.defaultRingOnWatch` → preferenza per nuove sveglie
- UI: switch in `AlarmScreen` riga 647, chip selector per gruppi esistenti

## CI/Release Pipeline

- **CI** (`.github/workflows/ci.yml`): lint, build, test su ogni push
- **Release** (`.github/workflows/release.yml`): build signed apk/aab (phone) + apk (watch), upload assets, crea tag git
- **Signing**: credenziali da `keystore.properties` (locale, non versionato) o env vars (`NOALARM_STORE_*`)
- **Release asset format**: 
  - `NoAlarm-VERSION.apk` (phone)
  - `NoAlarm-VERSION.aab` (phone, Play Store)
  - `NoAlarm-Watch-VERSION.apk` (watch)

## File Critici

| Path | Ruolo |
|------|-------|
| `app/build.gradle.kts` | Versioning app, dipendenze phone |
| `wear/build.gradle.kts` | Versioning watch, signing, dipendenze watch |
| `app/src/main/java/com/noalarm/wear/WearBridge.kt` | RFCOMM client (phone) |
| `wear/src/main/java/com/noalarm/watch/BridgeService.kt` | RFCOMM server (watch), foreground service |
| `wear/src/main/java/com/noalarm/watch/RingActivity.kt` | UI ring, vibrazione watch |
| `wear/src/main/java/com/noalarm/watch/BootReceiver.kt` | Restart BridgeService su boot |
| `wear/src/main/AndroidManifest.xml` | Permessi, FGS type, meta-data standalone |
| `app/src/main/java/com/noalarm/alarm/AlarmService.kt` | Ring logic phone, chiama `WearBridge.ringOnWatches()` |

---

**Ultima revisione**: v1.4.16/1.1.3 (15 Sep 2026) — crash echo watch (cancelDiscovery senza BLUETOOTH_SCAN) fixed, autosalvataggio non riattiva piu' le sveglie disabilitate, suoneria "Nessuna" supportata, orario di default +1 min, foglio creazione sveglia si apre a meta', UndoBar non piu' coperta dalla barra inferiore.
