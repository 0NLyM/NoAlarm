# NoAlarm

Orologio e sveglia per Android con estetica **Nothing**: nero assoluto, un solo
rosso, tutto scritto in una griglia di punti disegnata a mano — la stessa che
finisce sulla **Glyph Matrix** del Nothing Phone (3) mentre la sveglia suona.

## Cosa fa

**Le funzioni di Google Clock**

| | |
|---|---|
| Sveglia | ripetizione per giorni, etichetta, suoneria di sistema, vibrazione, volume crescente, rinvio e silenziamento automatico configurabili, "ignora una volta", ripristino dopo il riavvio, notifica della sveglia imminente e di quella non udita |
| Calendario | vista mensile con un punto per ogni sveglia prevista, sveglie legate a una data singola, salto di una sola occorrenza |
| Orologio | ora locale a caratteri dot-matrix, fusi orari del mondo con scarto rispetto a casa |
| Timer | tastierino HH:MM:SS, timer multipli, pausa/riprendi, +1 minuto, notifica persistente e suoneria a scadenza |
| Cronometro | giri con delta, notifica persistente, sopravvive alla chiusura dell'app |
| Riposo | ora di andare a letto e di sveglia, giorni, promemoria anticipato; si apre dalla riga fissa in cima all'elenco delle sveglie, come in Google Clock |

**In più**

- **Rinvio regolabile mentre suona.** I pulsanti `−` e `+` sulla schermata della
  sveglia cambiano i minuti di rinvio *prima* di posticipare, come su Samsung.
  Passo, minimo, massimo e numero di rinvii consentiti si regolano **dentro ogni
  singola sveglia**: quella del lavoro e quella del weekend possono comportarsi
  in modo diverso.
- **Selettore dell'orario a rulli dot-matrix.** Due colonne di cifre disegnate a
  punti che scorrono con lo snap e un ritorno aptico: la cifra al centro e'
  piena, quelle sopra e sotto rimpiccioliscono e sfumano nei punti spenti.
- **Pulsanti a sola icona.** Dove il testo era una parola sola c'e' un simbolo,
  barra di navigazione compresa; le descrizioni restano per l'accessibilita'.
- **Glyph Matrix (Nothing Phone 3).** Mentre la sveglia suona la matrice 25×25 sul
  retro mostra l'ora corrente in dot-matrix con i due punti che lampeggiano al
  secondo, alternata a una campanella pulsante e all'etichetta a scorrimento.
  Dopo il rinvio mostra per dieci secondi il conto alla rovescia, poi si spegne.
- **Tasti fisici.** Volume e tasto di accensione posticipano o spengono la
  sveglia (a scelta), e così anche capovolgere o scuotere il telefono.

## Glyph Matrix: come è integrata

NoAlarm usa la SDK ufficiale di Nothing
([`Nothing-Developer-Programme/GlyphMatrix-Developer-Kit`](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)),
vendorizzata in [`app/libs/glyph-matrix-sdk-2.0.aar`](app/libs/glyph-matrix-sdk-2.0.aar).
A differenza di una libreria di sistema, le classi `com.nothing.ketchum.*` vanno
incluse nell'APK: comunicano via AIDL con il servizio Glyph del telefono, che
richiede anche il permesso `com.nothing.ketchum.permission.ENABLE` e un
`meta-data android:name="NothingKey"` nel manifest (`"test"`, il valore del
progetto di esempio ufficiale — una chiave di produzione va richiesta a
`GDKsupport@nothing.tech` prima di una pubblicazione pubblica). Per questo
`minSdk` è 33: e' il minimo che l'AAR stesso dichiara.

Ci sono due canali, entrambi passano da [`GlyphRenderer`](app/src/main/java/com/noalarm/glyph/GlyphRenderer.kt)
cosi' l'animazione e' identica su entrambi:

- **Canale app** (`setAppMatrixFrame`, in [`GlyphController`](app/src/main/java/com/noalarm/glyph/GlyphController.kt)) —
  quello che Nothing raccomanda per un'app che non e' il Glyph Toy attivo. Si
  accende da solo mentre la sveglia suona o e' posticipata, **indipendentemente
  da quale Glyph Toy l'utente ha scelto**. Richiede l'aggiornamento software del
  telefono di agosto 2025.
- **Canale toy** (`setMatrixFrame`, in [`NoAlarmGlyphToyService`](app/src/main/java/com/noalarm/glyph/NoAlarmGlyphToyService.kt)) —
  attivo solo se l'utente seleziona NoAlarm in *Impostazioni > Glyph Interface >
  Glyph Toys*. E' anche l'unico modo di ricevere gli eventi del **pulsante Glyph**
  sul retro del telefono (non e' un tasto Android: il sistema li instrada solo al
  toy selezionato in quel momento) — durante una sveglia, una pressione
  posticipa e una pressione lunga spegne.

`GlyphBridge` non lancia mai verso l'alto: riporta esito ed errore invece di
propagarli, cosi' *Impostazioni > Glyph Matrix > Prova la matrice* puo' mostrare
una diagnosi vera (libreria caricata, servizio connesso, `register()` riuscito,
frame accettati/rifiutati, l'eccezione esatta) invece di indovinare.

## Build

```bash
./gradlew assembleDebug          # APK di debug
./gradlew assembleRelease        # APK di release (firmato se c'è keystore.properties)
```

Richiede JDK 17 e un Android SDK con `compileSdk 35`.

## Firma e aggiornamenti

Android installa un aggiornamento **solo se è firmato con la stessa chiave**
della versione già presente. NoAlarm usa quindi un unico keystore RSA 4096
valido fino al 2056, tenuto **fuori dal repository**.

### In locale

```bash
cp keystore.properties.template keystore.properties
# compila storePassword e keyPassword, metti noalarm-release.jks nella root
./gradlew assembleRelease
```

### Su GitHub Actions

Il workflow [`release.yml`](.github/workflows/release.yml) firma e pubblica una
release a ogni tag `v*`, oppure su richiesta da *Actions → Release → Run
workflow* indicando la versione (il tag viene creato se non esiste).
Servono quattro segreti in *Settings → Secrets and variables → Actions*:

| Segreto | Contenuto |
|---|---|
| `NOALARM_KEYSTORE_BASE64` | il keystore codificato con `base64 -w0 noalarm-release.jks` |
| `NOALARM_STORE_PASSWORD` | password del keystore |
| `NOALARM_KEY_ALIAS` | `noalarm` |
| `NOALARM_KEY_PASSWORD` | password della chiave (uguale a quella del keystore) |

Il workflow si ferma subito se i segreti mancano: meglio nessuna release che una
firmata con una chiave diversa, che poi non si potrebbe piu' aggiornare. Quando ci
sono, verifica la firma con `apksigner` e allega APK e AAB alla release.

**Non perdere il keystore.** Senza, l'unico modo per aggiornare l'app è
disinstallarla e reinstallarla da zero, perdendo sveglie e impostazioni.

## Struttura

```
app/src/main/java/com/noalarm/
  ui/DotFont.kt        font bitmap 5×7, condiviso fra schermo e Glyph Matrix
  ui/DotMatrix.kt      il font disegnato su Canvas
  alarm/               scheduling, servizio che suona, schermata a tutto schermo
  clock/ClockService.kt notifiche e suoneria di timer e cronometro
  glyph/               Matrix 25×25, controller e ponte verso la SDK Nothing
  data/                modello e persistenza (JSON su SharedPreferences)
glyph/                 stub di compilazione della SDK Nothing
```

Nessun database, nessuna dependency injection, nessun layer che non serva:
le sveglie sono poche decine di record.
