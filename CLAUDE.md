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
- `:wear` — app watch (Kotlin/Jetpack Compose, API 30+, Wear OS 3+) — **non piu' usato dal telefono da v1.4.26, vedi sotto**: i file restano nel repo ma nessun codice in `:app` li contatta piu'.

### Eco sul Watch via Notification Bridging di Sistema (v1.4.26 — sostituisce tutto il bridge Bluetooth custom sotto)
**Problema con la v1.2.0/1.4.25 (Bluetooth RFCOMM custom + sync via AlarmManager)**: funzionava, ma per farlo serviva un intero secondo modulo app (`:wear`), un protocollo binario duplicato su due file, un server RFCOMM sempre in ascolto sul watch e un canale di sync che poteva perdere colpi se il processo del watch era congelato nell'istante sbagliato — tanta superficie per un problema che Android risolve gia' da solo a un livello piu' basso.

**Soluzione**: la notifica della sveglia che il telefono mostra gia' (`NotificationHelper.ringing()` — `CATEGORY_ALARM`, `PRIORITY_MAX`, `ongoing`, azioni "Posticipa"/"Spegni" con `PendingIntent.getBroadcast`) viene bridgeata automaticamente sul watch da Wear OS stesso, via il Bluetooth di sistema, **senza alcun codice o dipendenza aggiuntiva**: e' lo stesso meccanismo che mostra sul watch le notifiche di qualunque altra app. Toccare "Posticipa"/"Spegni" sul watch fa eseguire lo stesso `PendingIntent` sul telefono — e' il sistema stesso a fare da staffetta, non serve una connessione BT gestita a mano.

- **Rispetto di `Alarm.ringOnWatch`**: dato che la notifica non ha piu' un destinatario esplicito da scegliere, il controllo per-sveglia si ottiene con `NotificationCompat.Builder.setLocalOnly(!alarm.ringOnWatch)` (flag `Notification.FLAG_LOCAL_ONLY`, gia' su `Builder`, non su `WearableExtender` — primo tentativo sbagliato, `Unresolved reference` in CI) — se `ringOnWatch` e' `false` la notifica non viene bridgeata; se `true` (default) viene bridgeata senza bisogno di fare nulla in piu'. Nessuna nuova dipendenza: fa parte di `androidx.core`, non della Wear Data Layer/Play Services.
- **Rimosso interamente**: `WearBridge.kt` (client RFCOMM), `WearDevicePicker.kt`/`WearTestSheet.kt` (UI legata al bridge custom), `Settings.watchDeviceAddress`/`watchDeviceName`, il permesso `BLUETOOTH_CONNECT` e la sua richiesta in `MainActivity.kt`. Tutte le chiamate `WearBridge.*` in `AlarmScheduler.kt`/`AlarmService.kt` (sync/eco/stop) sono sparite insieme al file.
- **Il modulo `:wear` non riceve piu' nulla dal telefono**: `BridgeService`, `WatchAlarmScheduler`/`WatchAlarmReceiver`, `RingActivity` restano nel repo ma sono orfani — se l'app watch e' ancora installata, `BootReceiver` la rimette in ascolto lo stesso a ogni riavvio del watch, consumando batteria per un server RFCOMM che nessuno contattera' mai piu'. **Per avere davvero il beneficio in batteria bisogna disinstallare l'app NoAlarm dal watch** (non solo aggiornare il telefono): il bridging di sistema non richiede nessuna app sul watch.
- **Incognita reale, da verificare sul TicWatch E3**: la Data Layer API (nodi) falliva con pairing di terze parti come Mobvoi Health (vedi sotto), ma il bridging di notifiche e' un sottosistema Wear OS diverso e di livello piu' basso — in teoria indipendente da quale app ha gestito l'abbinamento iniziale. Non e' garantito che il watch mostri la notifica come schermata sveglia a schermo intero (dipende da come Wear OS/Mobvoi gestisce le notifiche bridgeate di categoria `ALARM`) invece che come semplice card — questo e' il test da fare.
- **Prova reale (v1.4.26-v1.4.29, utente su TicWatch E3): la notifica non arriva affatto sul watch**, nonostante tre iterazioni (permesso Mobvoi Health non ancora verificato dall'utente in queste prove). Ipotesi ancora valida da controllare: permesso "Accesso alle notifiche" per Mobvoi Health (vedi sotto).
- **Tentativo v1.4.30, annullato in v1.4.31 — regressione senza guadagno**: si era rimosso `setOngoing(true)` e `setFullScreenIntent(...)` da `NotificationHelper.ringing()` (entrambi teoricamente esclusi dal bridging di sistema Wear OS), sostituendo l'apertura a schermo intero con un `context.startActivity()` diretto da `AlarmReceiver.onReceive()` (BAL-exempt perche' innescato da `AlarmManager`, stessa tecnica di `WatchAlarmReceiver` in v1.2.0). **Prova reale**: sul telefono, a schermo spento, la sveglia non si apriva piu' a schermo intero (il `startActivity()` diretto non riproduce l'apertura garantita di `setFullScreenIntent` quando il display e' spento — a differenza del watch, dove pero' la stessa tecnica aveva funzionato, vedi "Causa 8" sotto: differenza non completamente chiarita, forse dovuta al fatto che sul telefono la chiamata segue `AlarmService.ring()`/`startForegroundService()` invece di essere isolata) — e i pulsanti della notifica sono cambiati aspetto (probabile conseguenza collaterale del non essere piu' una notifica full-screen-intent). **E il watch continuava comunque a non ricevere nulla**: nessun beneficio a fronte di una regressione reale. **Fix v1.4.31**: ripristinati `setOngoing(true)` e `setFullScreenIntent(...)` com'erano prima di v1.4.30, rimosso il `startActivity()` diretto da `AlarmReceiver`. Resta confermato che nessuno dei due era la causa del bridging mancante (o comunque non lo era da solo). Verificati e confermati a posto gli altri punti noti: nessun `setLocalOnly(true)` hardcoded (e' condizionale su `!alarm.ringOnWatch`, corretto), nessun meta-data `notificationBridgeMode` nel manifest.
- **Strumento di prova (v1.4.27, v1.4.29 senza audio/vibrazione)**: Impostazioni → sezione "Sveglia" → "Prova la sveglia adesso" fa suonare per davvero una sveglia sintetica (`AlarmScheduler.TEST_ID = -3L`, esclusa dall'elenco sveglie con `it.id >= 0` in `AlarmScreen.kt`) chiamando `AlarmService.ring()` direttamente — stessa notifica, stesso bridging, stesso `Posticipa`/`Spegni` di una sveglia vera, ma senza aspettare l'orario giusto. Utile per iterare rapidamente sul debug del bridging: se non compare sul watch nemmeno con questo, il problema e' nel bridging/permesso, non nello scheduling.
  - **v1.4.28 (annullato in v1.4.29)**: si era corretto `gradualVolume = true` (partiva al 5%, sembrava "silenziata" su un test rapido) mettendolo a `false`. Richiesta successiva: niente audio ne' vibrazione affatto durante il test, per non farla suonare ad ogni iterazione di debug.
  - **v1.4.29**: `Alarm(id = TEST_ID, label = "Prova", soundUri = Alarm.SOUND_NONE, vibrate = false)` — solo notifica/bridging/Glyph, sveglia muta e senza vibrazione. Il tema del volume del v1.4.28 non si pone piu': non c'e' suono da regolare.
  - **v1.4.32 — diagnostica**: dato che le cause note nel codice della notifica sono esaurite (v1.4.30/v1.4.31), il tasto raccoglie anche una diagnosi mostrata sotto il bottone (`collectWearDiagnostics()` in `SettingsScreen.kt`, nessun permesso nuovo): notifiche di sistema attive, importanza del canale `CH_ALARM` (deve restare 4/alta — se l'utente o l'OEM l'ha abbassata manualmente da Impostazioni → Notifiche, il bridging puo' escluderla), stato del Bluetooth, ed **`NotificationManagerCompat.getEnabledListenerPackages()`** — l'elenco delle app con "Accesso alle notifiche" concesso dall'utente. Quest'ultimo e' il dato piu' utile: se Mobvoi Health (o l'app che gestisce il TicWatch) non compare in quell'elenco, il suo `NotificationListenerService` non specchia nulla di nessuna app, non solo di NoAlarm — confermerebbe l'ipotesi "permesso mancante" senza dover indovinare il nome del package (nessun `<queries>` nel manifest, nessuna nuova dipendenza).
  - **Risultato reale (v1.4.32, utente su TicWatch E3): l'ipotesi del permesso mancante e' smentita.** Diagnosi raccolta: notifiche attive `true`, importanza canale `4` (alta, corretta), Bluetooth attivo `true`, e **`com.mobvoi.companion.at` e' presente** nell'elenco delle app con accesso alle notifiche — Mobvoi ha gia' il permesso di sistema. Tutti e 4 i controlli passano, eppure la notifica non arriva sul watch.
  - **Anche la whitelist per-app dentro Mobvoi Health era gia' attiva per NoAlarm** (l'utente l'aveva gia' abilitata prima di tutti questi test) — smentita anche questa ipotesi.
  - **Fatto nuovo e decisivo**: il watch e' connesso e riceve normalmente le notifiche di altre app — quindi il bridging in generale funziona su questo hardware, non e' un problema di permesso/pairing/connessione. Il blocco e' specifico alla notifica di NoAlarm.
  - **v1.4.33 — test isolato**: il tentativo v1.4.30 aveva tolto `setOngoing` e `setFullScreenIntent` **insieme**, confondendo due variabili, e il test era comunque girato con la whitelist Mobvoi gia' attiva (quindi quel "non ha funzionato" era gia' un test valido per la combinazione, ma non isolava `setOngoing` da solo). Con la causa "permesso/whitelist" ormai esclusa e la certezza che il bridging funziona in generale, si isola stavolta `setOngoing(true)` da solo (tolto), lasciando `setFullScreenIntent` intatto — quindi lo schermo intero sul telefono a schermo spento non e' a rischio questa volta. `setOngoing` e' l'unica proprieta' della notifica di NoAlarm che manca dal profilo di una notifica "normale" (le altre app che arrivano sul watch tipicamente non sono legate a un foreground service ongoing) — ipotesi plausibile per una whitelist di mirroring che tratta le notifiche ongoing come "stato in background", non "avviso".
  - **v1.4.34 — causa reale trovata (documentazione ufficiale Android/Wear OS)**: rimuovere `setOngoing(true)` dal `Builder` in v1.4.33 non basta perche' quella notifica e' anche l'argomento di `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` — e le notifiche "media playback" sono l'eccezione esplicita alla modifica Android 14 che rende le notifiche dei foreground service sempre "dismissibili": restano *ongoing* (quindi `FLAG_ONGOING_EVENT` attivo) indipendentemente da cosa imposta il `Builder`. La pagina ufficiale Wear OS ("Bridging options for notifications") elenca **esattamente 4** condizioni che escludono una notifica dal bridging: `setLocalOnly(true)`, `setOngoing(true)`/`FLAG_ONGOING_EVENT`, `FLAG_NO_CLEAR`, o l'app watch che ha disattivato il bridging. Tutte e altre tre erano gia' escluse (nessun `setLocalOnly(true)` hardcoded, nessun `FLAG_NO_CLEAR`, nessuna `BridgingConfig` nel modulo `:wear`) — restava solo questa, mai isolata prima perche' nascosta dietro il vincolo di `startForeground`, non dal `Builder`.
    - **Fix**: la notifica che soddisfa `startForeground()` (`NotificationHelper.foregroundPlaceholder()`, id `ID_RINGING_FGS`) e quella pensata per il bridging (`NotificationHelper.ringing()`, id `ID_RINGING`, `NotificationManagerCompat.notify()` diretto, mai legata al foreground service) sono ora due oggetti `Notification` distinti. Solo la prima ha `setFullScreenIntent` (serve a portare comunque lo schermo intero sul telefono anche a schermo spento) ed e' sempre `setLocalOnly(true)` (mai bridgeata: e' ongoing per forza, quindi Wear OS la scarterebbe comunque, meglio non offrirla proprio come seconda copia). La seconda non e' mai ongoing, non e' mai legata a un FGS, e resta l'unica notifica valutata per il bridging — governata come sempre da `setLocalOnly(!alarm.ringOnWatch)`.
    - **Non ancora verificato su TicWatch E3** — prossimo test reale con "Prova la sveglia adesso".

### (Storico, superato in v1.4.26 — vedi sopra) Bluetooth Echo via RFCOMM Classico
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

### (Storico, superato in v1.4.26 — vedi sopra) Sync Sveglie via AlarmManager (v1.2.0 watch / v1.4.25 app)
**Problema**: la Causa 10 (sotto) risolve l'affidabilita' dell'eco live, ma solo tenendo un `PARTIAL_WAKE_LOCK` per tutta la vita di `BridgeService` — CPU mai sospesa, costo in batteria non piu' accettabile per un uso quotidiano. La causa di fondo era architetturale: la sveglia sul watch dipendeva da una connessione Bluetooth *live* che il telefono deve far arrivare esattamente all'istante giusto, e l'unico modo per garantirla era impedire sempre alla CPU del watch di dormire.

**Soluzione**: il watch non aspetta piu' un push in tempo reale — programma da solo, localmente, l'orario in cui suonare, con lo stesso mezzo Doze-proof che il telefono usa gia' per le sue sveglie (`AlarmManager.setExactAndAllowWhileIdle`). Il Bluetooth resta, ma per due usi molto piu' leggeri:
1. **Sync della programmazione**: ogni volta che sul telefono cambia il prossimo squillo di una sveglia con l'eco attiva (salva, cancella, rinvio, spegnimento, `syncAll` al boot/avvio app), `WearBridge.syncSchedule()` manda al watch l'elenco **completo** (non un diff) delle sveglie attive con eco: id, istante assoluto gia' calcolato (`nextTrigger()`), etichetta (`ACTION_SYNC = 5`). Il watch (`WatchAlarmScheduler.sync()`) calcola il diff da solo (`SharedPreferences` con le id gia' programmate) e programma/cancella i `PendingIntent` di conseguenza.
2. **Eco live + staffetta rinvio/spegni**: `BridgeService`/`WearBridge.ringOnWatches()`/`ACTION_RING` restano com'erano — quando la sveglia suona per davvero sul telefono, se il watch e' raggiungibile in quel momento arriva anche l'eco "live" (ridondante rispetto a quella locale, ma piu' pronta se il watch e' sveglio), e la stessa connessione resta il canale per `ACTION_SNOOZE`/`ACTION_DISMISS` da un dispositivo all'altro. Questo e' l'unico momento in cui serve una connessione BT attiva — esattamente come proposto: "attivare il listener solo quando la sveglia suona su entrambi i dispositivi".

**Chi suona il watch adesso**: `WatchAlarmReceiver`, un `BroadcastReceiver` registrato in manifest e innescato dall'`AlarmManager` — a differenza di un `Service`, un receiver innescato dall'AlarmManager e' esente dai limiti di avvio in background (BAL), quindi puo' chiamare `startActivity()` su `RingActivity` direttamente, senza fullScreenIntent e senza bisogno che nulla sia gia' in esecuzione. Suona quindi anche se `BridgeService` e' inerte o l'app e' stata "congelata" (Causa 10).

**Wake lock ridotto**: con l'orario garantito dall'AlarmManager, `BridgeService` non deve piu' restare sveglio 24/7 solo per non perdere lo squillo — il wake lock permanente della Causa 10/v1.1.9 e' stato tolto. Resta solo un wake lock breve (10 s) dentro `ring()`, come prima della v1.1.9, per il solo caso dell'eco live.

**Compromesso accettato**: la programmazione (`ACTION_SYNC`) arriva ancora via Bluetooth, quindi se il processo del watch e' congelato nel preciso istante in cui il telefono la manda, quella singola sync puo' non arrivare — il watch continuera' a suonare secondo l'ultima programmazione ricevuta con successo (mai silenzio totale, nel peggiore dei casi un'eco con orario non aggiornatissimo). Piu' punti di sync nel tempo (salva/cancella/rinvio/spegni/boot/avvio app) e l'auto-guarigione gia' presente (`screenOnReceiver` ricrea il socket alla riaccensione schermo) coprono la maggior parte dei casi reali senza pagare il costo di un wake lock permanente.

### Schermata Sveglia sul Watch: Storia Completa (fino a v1.1.9, superata in v1.2.0 — vedi sopra)
1. **v1.4.18**: solo notifica, mai `RingActivity`. Causa: da Android 14 (API 34, il watch monta `targetSdk 35`) dichiarare `USE_FULL_SCREEN_INTENT` nel manifest non basta piu' — serve consenso esplicito in Impostazioni, altrimenti `setFullScreenIntent()` degrada in silenzio a notifica normale. **Fix v1.4.19**: richiesta del consenso via `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
2. **v1.1.5**: persisteva anche col consenso concesso E a schermo spento/ambient — la condizione in cui il fullScreenIntent dovrebbe auto-aprirsi da solo. **Fix v1.1.6**: `BridgeService.ring()` tenta anche `startActivity()` diretto su `RingActivity`, IN PARALLELO alla notifica con fullScreenIntent (non al posto di).
3. **v1.1.6**: la doppia strada creava una corsa fra le due UI. Riscontrato dall'utente: **a schermo acceso** l'avvio diretto vinceva sempre (schermata piena, ma con la notifica che si sovrapponeva sopra); **a schermo spento** l'avvio diretto non arrivava mai in tempo (restava solo la notifica) — il vero problema non era un blocco BAL sull'avvio diretto, ma il sistema che lo rimandava mentre la CPU era in doze con lo schermo spento.
   - **Fix v1.1.7**: tolta la notifica dell'eco (`CH_RING`) e il fullScreenIntent — solo piu' `startActivity()` diretto, preceduto da un `PowerManager.PARTIAL_WAKE_LOCK` di 10 s che tiene sveglia la CPU quel tanto che basta perche' il sistema esegua subito l'avvio invece di rimandarlo in doze; da li' `setShowWhenLocked`/`setTurnScreenOn` di `RingActivity` (gia' presenti) accendono davvero lo schermo. Rimossi anche il permesso `USE_FULL_SCREEN_INTENT` e il relativo avviso in `MainActivity.kt`, diventati inutili.
4. **v1.1.7**: la schermata piena ora funzionava anche a schermo spento, ma dopo pochi secondi a schermo spento la sincronizzazione smetteva del tutto di funzionare finche' l'utente non riapriva l'app. Ipotesi: il `BluetoothServerSocket` in ascolto (`acceptLoop()`) resta "vivo" ma inerte (`accept()` bloccato per sempre) se il chip Bluetooth si riavvia durante lo standby a schermo spento — non un errore che l'accept() nota da solo.
   - **Fix v1.1.8**: `BridgeService` registra un `BroadcastReceiver` dinamico su `ACTION_SCREEN_ON` che chiude `serverSocket` alla riaccensione dello schermo, forzando la `IOException` che sblocca `accept()` e lo fa ricreare da capo — nessun polling continuo, scatta solo sugli eventi di riaccensione. Aggiunta anche la richiesta di esenzione dall'ottimizzazione batteria (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, come rete di sicurezza in caso il vero problema fosse il servizio ucciso dal battery manager del produttore invece del socket).
5. **v1.1.8**: non bastava. Prova dell'utente (screenshot `WearTestSheet`): connessione riuscita in 505 ms con ack in 37 ms — quindi quando l'app e' aperta tutto funziona benissimo — ma dopo un po' a schermo spento la sveglia non arriva piu' finche' non si riapre l'app, e **l'icona del servizio in primo piano resta visibile** per tutto il tempo: `BridgeService` non viene distrutto (`onDestroy()` mai chiamato), quindi ne' il ricevitore `ACTION_SCREEN_ON` ne' l'ipotesi "servizio ucciso dal battery manager" (Causa 9) spiegano il sintomo. Piu' probabile: il sistema sospende la CPU/i thread dell'app a schermo spento pur lasciando il `Service` "vivo" (processo congelato, non distrutto) — ne' l'`acceptLoop()` ne' il `BroadcastReceiver` dinamico riescono a girare finche' qualcosa (aprire l'app) non risveglia il processo.
   - **Fix v1.1.9**: wake lock (`PowerManager.PARTIAL_WAKE_LOCK`) acquisito in `onStartCommand()` e tenuto per **tutta la vita del servizio** (non piu' solo per 10 s durante lo squillo, rimosso da `ring()` perche' ridondante), rilasciato in `onDestroy()`. Costa piu' batteria di un approccio "leggero", ma e' l'unico modo per garantire che la CPU non venga mai sospesa mentre il servizio deve restare in ascolto.

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

- **App**: v1.4.34 (versionCode 55)
- **Watch**: v1.2.0 (versionCode 13) — non piu' toccata da v1.4.26, il modulo `:wear` e' orfano (vedi "Eco sul Watch via Notification Bridging di Sistema").

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
6. **Causa 6 (v1.4.19, fix parziale)**: ack ricevuto (connessione BT funzionante), ma sul watch appariva solo una notifica invece della schermata `RingActivity` — vedi "Schermata Sveglia sul Watch: Storia Completa" sopra.
   - **Fix**: `MainActivity.kt` (watch) chiede il consenso `USE_FULL_SCREEN_INTENT` in Impostazioni quando manca.
   - Non bastava: persisteva anche a schermo spento/ambient col permesso concesso, condizione in cui il fullScreenIntent dovrebbe auto-aprirsi da solo. Vedi Causa 7.
7. **Causa 7 (v1.1.6, fix parziale)**: su questo stack Wear OS il fullScreenIntent non auto-apre `RingActivity` a schermo spento, a differenza del telefono.
   - **Fix**: `BridgeService.ring()` tenta anche `startActivity()` diretto, IN PARALLELO alla notifica con fullScreenIntent.
   - Non bastava: le due strade correvano fra loro — a schermo acceso entrambe comparivano sovrapposte, a schermo spento vinceva quasi sempre solo la notifica. Vedi Causa 8.
8. **Causa 8 (v1.1.7, fix parziale)**: la notifica e l'avvio diretto pubblicati insieme creavano la corsa; a schermo spento l'avvio diretto veniva rimandato dal sistema mentre la CPU era in doze, non bloccato.
   - **Fix**: tolta del tutto la notifica dell'eco. Solo `startActivity()` diretto, preceduto da un `PowerManager.PARTIAL_WAKE_LOCK` di 10 s che tiene sveglia la CPU perche' il sistema lo esegua subito invece di rimandarlo.
   - Schermata piena confermata funzionante anche a schermo spento, ma e' emerso un problema nuovo: la sincronizzazione smette di funzionare del tutto dopo pochi secondi a schermo spento. Vedi Causa 9.
9. **Causa 9 (v1.1.8, ipotesi sbagliata)**: si era ipotizzato che il `BluetoothServerSocket` in ascolto restasse "vivo" ma inerte (`accept()` bloccato per sempre) se il chip Bluetooth si riavvia durante lo standby, o che il servizio venisse ucciso dal battery manager del produttore.
   - **Fix**: `BroadcastReceiver` su `ACTION_SCREEN_ON` per ricreare il socket, piu' richiesta di esenzione dall'ottimizzazione batteria.
   - Non bastava: l'icona del servizio in primo piano restava visibile per tutto il tempo (`BridgeService` mai distrutto), quindi non era ne' un socket morto ne' un servizio ucciso. Vedi Causa 10.
10. **Causa 10 (v1.1.9, causa reale)**: il sistema sospende la CPU/i thread dell'app a schermo spento pur lasciando il `Service` "vivo" (processo congelato, non distrutto) — ne' `acceptLoop()` ne' il `BroadcastReceiver` dinamico riescono a girare finche' qualcosa (aprire l'app) non risveglia il processo.
   - **Fix**: `PowerManager.PARTIAL_WAKE_LOCK` acquisito in `onStartCommand()` e tenuto per tutta la vita del servizio (non piu' solo 10 s durante lo squillo), rilasciato in `onDestroy()`.
   - Risolveva l'affidabilita', ma con un costo in batteria non piu' accettabile per un uso quotidiano (CPU mai sospesa). Vedi Causa 11.
11. **Causa 11 (v1.2.0, redesign architetturale)**: la causa di fondo non era un bug da correggere ma un limite del disegno — far dipendere l'orario dello squillo da una connessione Bluetooth *live* imponeva di impedire sempre alla CPU di dormire, l'unico modo per garantirla.
    - **Fix**: il watch programma da solo l'orario via `AlarmManager.setExactAndAllowWhileIdle` (Doze-proof per natura, nessun wake lock permanente necessario), sincronizzato dal telefono a ogni cambio (`WearBridge.syncSchedule()` → `ACTION_SYNC` → `WatchAlarmScheduler`). Il Bluetooth resta solo per l'eco live e la staffetta rinvio/spegni mentre la sveglia suona su entrambi. Vedi "Sync Sveglie via AlarmManager" sopra.
12. **Causa 12 (v1.4.34, causa reale — architettura di notification bridging del v1.4.26)**: la notifica sveglia e' anche l'argomento di `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` — le notifiche "media playback" sono l'eccezione esplicita alla modifica Android 14 che rende dismissibili le notifiche dei foreground service, quindi restano *ongoing* (`FLAG_ONGOING_EVENT`) qualunque cosa imposti il `Builder` (per questo togliere `setOngoing(true)` in v1.4.33 non bastava). La pagina ufficiale Wear OS ("Bridging options for notifications") esclude dal bridging esattamente 4 casi: `setLocalOnly(true)`, `setOngoing`/`FLAG_ONGOING_EVENT`, `FLAG_NO_CLEAR`, bridging disattivato dall'app watch — i primi tre erano gia' verificati a posto sul `Builder`, ma il terzo veniva reintrodotto dal sistema a valle di `startForeground`, non dal codice Kotlin.
    - **Fix**: due notifiche distinte in `NotificationHelper.kt` — `foregroundPlaceholder()` (id `ID_RINGING_FGS`, lega il foreground service, sempre `setLocalOnly(true)`, porta lo schermo intero sul telefono) e `ringing()` (id `ID_RINGING`, postata con `NotificationManagerCompat.notify()` senza mai passare da `startForeground`, mai ongoing, unica candidata al bridging, governata da `setLocalOnly(!alarm.ringOnWatch)` come sempre). Non ancora verificato su TicWatch E3.

### Lint Failure `wear:lintVitalRelease`
✅ **Risolto in v1.4.13**: `play-services-wearable` tirava transitive fragment old, aggiunto `libs.androidx.fragment.ktx`. Poi rimosso tutto `play-services-wearable` quando passato a RFCOMM.

## Data Model

- `Alarm.ringOnWatch: Boolean` (default true) → sveglia suona anche sul watch; da v1.4.26 controlla `WearableExtender().setLocalOnly(!ringOnWatch)` sulla notifica invece del vecchio bridge Bluetooth
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
| `app/src/main/java/com/noalarm/alarm/NotificationHelper.kt` | Costruisce le notifiche sveglia; da v1.4.34 sono due oggetti separati — `ringing()` (bridgeabile, mai ongoing) e `foregroundPlaceholder()` (lega il foreground service, sempre `setLocalOnly(true)`) — vedi "Causa 12" |
| `app/src/main/java/com/noalarm/alarm/AlarmScheduler.kt` | Scheduling phone (nessun bridge Bluetooth da v1.4.26) |
| `app/src/main/java/com/noalarm/alarm/AlarmService.kt` | Ring logic phone (nessun bridge Bluetooth da v1.4.26) |
| `wear/build.gradle.kts` | Versioning watch — modulo orfano da v1.4.26, non piu' contattato dal telefono |
| `wear/src/main/java/com/noalarm/watch/BridgeService.kt` | RFCOMM server (watch) — orfano da v1.4.26 |
| `wear/src/main/java/com/noalarm/watch/WatchAlarmScheduler.kt` | Programma sveglie locali via `AlarmManager` — orfano da v1.4.26 |
| `wear/src/main/java/com/noalarm/watch/WatchAlarmReceiver.kt` | Apre `RingActivity` da `AlarmManager` — orfano da v1.4.26 |
| `wear/src/main/java/com/noalarm/watch/RingActivity.kt` | UI ring, vibrazione watch — orfano da v1.4.26 |
| `wear/src/main/java/com/noalarm/watch/BootReceiver.kt` | Restart BridgeService su boot — orfano da v1.4.26, ma continua a girare se l'app watch resta installata |
| `wear/src/main/AndroidManifest.xml` | Permessi, FGS type, meta-data standalone |
| `wear/src/main/java/com/noalarm/watch/Theme.kt` | Palette Nothing (duplicata dal telefono) per il watch |
| `wear/src/main/java/com/noalarm/watch/Widgets.kt` | `PillButton`, equivalente watch di `DotPillButton` |

---

**Ultima revisione**: v1.4.34 (17 Sep 2026) — causa reale individuata via documentazione ufficiale Android/Wear OS: la notifica sveglia restava sempre *ongoing* perche' e' l'argomento di `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`, e le notifiche "media playback" sono l'eccezione esplicita alla dismissibilita' introdotta in Android 14 — togliere `setOngoing(true)` dal `Builder` (v1.4.33) non basta perche' il sistema la rimette comunque. Fix: notifica del foreground service e notifica bridgeabile sono ora due oggetti `Notification` separati (vedi Causa 12 e `NotificationHelper.kt`). Non ancora verificato su TicWatch E3 — prossimo test con "Prova la sveglia adesso".
