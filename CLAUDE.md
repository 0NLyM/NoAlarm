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
Su stack Bluetooth di terze parti (Ticwatch/Mobvoi), la ricerca SDP dell'UUID appena registrato può restare bloccata per svariati secondi prima di fallire (non fallisce all'istante). `WearBridge.connect()` prova quindi canale RFCOMM 1 via reflection e SDP **in parallelo** (v1.4.19, vedi sotto), non piu' uno dopo l'altro:
```kotlin
device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
    .invoke(device, 1) as BluetoothSocket
```

**v1.4.17** aveva provato a invertire l'ordine (canale diretto prima della SDP) partendo dal presupposto che la SDP fosse sempre lenta/inaffidabile su questo hardware. **Prova reale su dispositivo (v1.4.18, utente con 14 dispositivi Bluetooth accoppiati)**: per il TicWatch E3 il canale diretto ha *fallito* e solo la SDP ha funzionato — l'assunzione era sbagliata, non e' detto quale delle due risponda per prima su un dato watch. La causa vera del ritardo enorme (64 secondi misurati) non era l'ordine SDP/diretto ma **il numero di dispositivi accoppiati**: `connect()` provava tutti e 14 in sequenza (mouse, TV, pompa insulina, auto, cuffie...) prima di arrivare al watch, con due tentativi bloccanti ciascuno.

**Fix v1.4.19**: `connect()` ordina i dispositivi accoppiati mettendo per primi quelli di classe `BluetoothClass.Device.WEARABLE_WRIST_WATCH`, e per ciascun dispositivo prova canale diretto e SDP **in parallelo** (`ExecutorCompletionService`, timeout 4 s a testa) invece che in sequenza — elimina sia la scommessa sull'ordine sia il costo dei dispositivi non-watch accoppiati.

### Scelta Manuale del Watch (v1.4.20)
La priorita' per classe rimane un'euristica: puo' sbagliare, e resta comunque un tentativo (anche se parallelo) su un dispositivo alla volta. `Settings.watchDeviceAddress`/`watchDeviceName` (Impostazioni → "Orologio da usare per l'eco", `WearDevicePicker.kt`) lascia scegliere a mano il dispositivo accoppiato: se impostato, `WearBridge.connect()` lo mette sempre per primo nell'ordinamento (`compareByDescending { it.address == preferred }.thenByDescending { classe watch }`), quindi un solo tentativo diretto invece di scorrere gli altri. Vuoto = rilevamento automatico (comportamento v1.4.19). `WearBridge.bondedDevices(context)` per popolare il selettore non si connette a nulla: legge solo `BluetoothAdapter.bondedDevices`, gia' in cache di sistema.

### Strumento di Prova (v1.4.18)
Impostazioni → sezione "Sveglia" → "Prova la connessione con l'orologio" (`WearTestSheet.kt`), stesso schema della prova Glyph: `WearBridge.status` (`StateFlow<WearStatus>`) espone permesso, dispositivi accoppiati, a chi/con che metodo si è connesso, tempo di connessione, se il messaggio è stato scritto, se e in quanto è arrivato l'ack, ultimo errore. `WearBridge.test()` manda lo stesso `ACTION_RING` id 0 della "Prova" locale sul watch; `BridgeService.handle()` ora rimanda **sempre** lo stesso `ACTION_RING` come conferma di ricezione subito dopo aver fatto suonare l'eco (per una sveglia vera il telefono lo ignora, non c'è branch per `ACTION_RING` in `listenForReply()`). Serve a distinguere "non si connette", "si connette ma non scrive", "scrive ma il watch non risponde" (es. APK watch non aggiornato) invece di scoprirlo solo quando una sveglia vera non arriva.

### Notifiche a Schermo Intero sul Watch (Android 14+)
Il watch monta `targetSdk 35`. Da Android 14 (API 34) dichiarare `USE_FULL_SCREEN_INTENT` nel manifest **non basta piu'**: senza consenso esplicito dell'utente in Impostazioni, `NotificationCompat.Builder.setFullScreenIntent()` (usato da `BridgeService.ring()`) degrada in silenzio a notifica normale — l'eco arriva come notifica invece che come `RingActivity` a tutto schermo con vibrazione e i tasti Rinvia/Spegni. **Sintomo osservato (v1.4.18)**: ack ricevuto in 107 ms (connessione BT ok), ma solo una notifica sul watch, nessuna schermata.
**Fix v1.4.19**: `MainActivity.kt` (watch) controlla `NotificationManager.canUseFullScreenIntent()` (`>= 34`, sempre `true` sotto) a ogni `onResume()`; se `false`, mostra un avviso con pulsante che apre `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` per questo pacchetto.

**Persisteva anche a schermo spento (v1.1.5)**: col permesso concesso, l'utente ha comunque visto la notifica invece della schermata piena con lo schermo del watch spento/in ambient — la condizione in cui il fullScreenIntent dovrebbe auto-aprirsi da solo. Su questo stack Wear OS evidentemente non lo fa.
**Fix v1.1.6**: `BridgeService.ring()` tenta anche `startActivity()` diretto su `RingActivity` PRIMA di postare la notifica (avvolto in `runCatching`: se l'OS lo blocca, resta la notifica come ripiego, nessuna regressione). Non c'e' garanzia formale che un servizio in background possa farlo (da qui il tentativo protetto invece che l'unica strada), ma vale la pena provarlo dato che il meccanismo "ufficiale" (fullScreenIntent) non si comporta come sul telefono.

### Vibrazione sul Watch
**Bug risolto in v1.4.15**: `RingActivity` usava `createWaveform(..., repeat=0)` (loop infinito) senza mai chiamare `vibrator.cancel()` → continuava a vibrare anche dopo Spegni/Posticipa o stop dal telefono.
**Fix**: memorizzare il `Vibrator` e chiamare `vibrator.cancel()` in `onDestroy()`.

### Permessi Bluetooth (API-level-gated)
- **API 31+**: `BLUETOOTH_CONNECT` (runtime/dangerous)
- **API ≤30**: `BLUETOOTH`/`BLUETOOTH_ADMIN` (normal/install-time, con `android:maxSdkVersion="30"`)

**Regola critica**: ogni `checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)` su pre-31 deve essere guarded da `Build.VERSION.SDK_INT < 31 || ...` altrimenti riporta falso-negativo.

### Foreground Service Contract (Android 8+)
Qualsiasi servizio avviato via `startForegroundService()` DEVE chiamare `startForeground()` entro pochi secondi o l'OS crasha l'app. **Watch crash v1.4.13-14**: permission check in `start()` non matching quello in `onStartCommand()` → su API ≤30 si bloccava senza `startForeground()`. **Fix v1.4.14**: unificare in `hasPermission(context)` riusato da entrambi.

### Stile Nothing sul Watch (v1.1.6)
Prima usava il Material3 di default (schema colori chiaro/scuro di sistema), nessuna parentela visiva col telefono. `wear/.../Theme.kt` duplica la stessa palette Nothing di `app/ui/theme/Theme.kt` (nero `Ink`, rosso `NothingRed`, bianco `Chalk` — nessun modulo condiviso, stessa scelta gia' fatta per l'UUID) in un `NoAlarmWatchTheme` sempre scuro (il watch non ha bisogno di seguire il tema chiaro di sistema). `Widgets.kt` aggiunge `PillButton`, l'equivalente senza dot-matrix di `DotPillButton` del telefono: pillola arrotondata, testo maiuscolo, rosso per l'azione enfatizzata ("Spegni"), colore neutro per le altre ("Posticipa", "Prova", "Concedi") — stessa convenzione di `AlarmActivity` sul telefono. Temi delle Activity in `AndroidManifest.xml` passati a `@style/Theme.NoAlarmWatch` (`res/values/themes.xml`, estende `Theme.DeviceDefault` forzando solo `windowBackground` a nero — `Theme.DeviceDefault.Black` non esiste come risorsa, ha rotto la prima CI) per evitare un lampo bianco prima che Compose disegni.

### Metadata Wear OS
`android:name="com.google.android.wearable.standalone"` governa **solo** l'auto-install del Play Store (non la logica app). Con `value="false"`, alcuni launcher Wear OS (Ticwatch/Mobvoi) **nascondono** app sideload dal drawer (anche se installate) perché non risultano "paired" via quel meccanismo. **Fix v1.4.14**: `value="true"`.

## Versioni Attuali

- **App**: v1.4.21 (versionCode 42)
- **Watch**: v1.1.6 (versionCode 9)

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
4. **Causa 4 (v1.4.17, ipotesi poi corretta in v1.4.19)**: si era ipotizzato che l'eco arrivasse tardi perche' `connect()` provava PRIMA `createRfcommSocketToServiceRecord()` (SDP lenta/bloccante su Ticwatch/Mobvoi) e solo dopo il canale diretto, e si era invertito l'ordine.
   - Prova reale (v1.4.18): per il TicWatch E3 dell'utente e' successo il contrario (canale diretto fallito, SDP riuscita) — l'ipotesi sull'ordine era sbagliata, vedi Causa 5.
5. ✅ **Causa 5 (v1.4.19, causa reale)**: con molti dispositivi Bluetooth accoppiati (nel caso reale: 14 — mouse, TV, pompa insulina, auto, cuffie...), `connect()` li provava tutti in sequenza, ciascuno con canale diretto e SDP uno dopo l'altro, prima di arrivare al watch: 64 secondi misurati prima di raggiungere il TicWatch (7° nella lista).
   - **Fix**: dispositivi di classe `WEARABLE_WRIST_WATCH` provati per primi; per ciascun dispositivo canale diretto e SDP in parallelo (`ExecutorCompletionService`, timeout 4 s) invece che in sequenza.
6. **Causa 6 (v1.4.19, fix parziale)**: ack ricevuto (connessione BT funzionante), ma sul watch appariva solo una notifica invece della schermata `RingActivity` — vedi "Notifiche a Schermo Intero sul Watch (Android 14+)" sopra.
   - **Fix**: `MainActivity.kt` (watch) chiede il consenso `USE_FULL_SCREEN_INTENT` in Impostazioni quando manca.
   - Non bastava: persisteva anche a schermo spento/ambient col permesso concesso, condizione in cui il fullScreenIntent dovrebbe auto-aprirsi da solo. Vedi Causa 7.
7. ✅ **Causa 7 (v1.1.6)**: su questo stack Wear OS il fullScreenIntent non auto-apre `RingActivity` nemmeno a schermo spento, a differenza del telefono.
   - **Fix**: `BridgeService.ring()` tenta anche `startActivity()` diretto (protetto da `runCatching`), oltre alla notifica con fullScreenIntent che resta come ripiego.

### Lint Failure `wear:lintVitalRelease`
✅ **Risolto in v1.4.13**: `play-services-wearable` tirava transitive fragment old, aggiunto `libs.androidx.fragment.ktx`. Poi rimosso tutto `play-services-wearable` quando passato a RFCOMM.

## Data Model

- `Alarm.ringOnWatch: Boolean` (default true) → sveglia suona anche sul watch
- `Settings.defaultRingOnWatch` → preferenza per nuove sveglie
- UI: switch in `AlarmScreen` riga 647, chip selector per gruppi esistenti
- `Settings.watchDeviceAddress`/`watchDeviceName` → dispositivo scelto a mano per l'eco (vuoto = automatico), UI in `SettingsScreen`/`WearDevicePicker.kt`

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
| `app/src/main/java/com/noalarm/ui/WearTestSheet.kt` | Schermata di prova connessione watch (Impostazioni) |
| `app/src/main/java/com/noalarm/ui/WearDevicePicker.kt` | Selettore manuale del watch per l'eco (Impostazioni) |
| `wear/src/main/java/com/noalarm/watch/Theme.kt` | Palette Nothing (duplicata dal telefono) per il watch |
| `wear/src/main/java/com/noalarm/watch/Widgets.kt` | `PillButton`, equivalente watch di `DotPillButton` |

---

**Ultima revisione**: v1.4.21/1.1.6 (15 Sep 2026) — stile Nothing sul watch (Theme.kt, Widgets.kt), BridgeService tenta anche l'avvio diretto di RingActivity oltre al fullScreenIntent (non auto-apriva nemmeno a schermo spento). App non toccata in questo giro, versione avanzata solo per continuita' del tag di release.
