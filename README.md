# NoAlarm

Orologio e sveglia per Android con estetica **Nothing**: nero assoluto, un solo
rosso, tutto scritto in una griglia di punti disegnata a mano — la stessa che
finisce sulla **Glyph Matrix** del Nothing Phone (3) mentre la sveglia suona.

## Cosa fa

**Le funzioni di Google Clock**

| | |
|---|---|
| Sveglia | ripetizione per giorni, etichetta, suoneria di sistema, vibrazione, volume crescente, rinvio e silenziamento automatico configurabili, "ignora una volta", ripristino dopo il riavvio, notifica della sveglia imminente e di quella non udita |
| Orologio | ora locale a caratteri dot-matrix, fusi orari del mondo con scarto rispetto a casa |
| Timer | tastierino HH:MM:SS, timer multipli, pausa/riprendi, +1 minuto, notifica persistente e suoneria a scadenza |
| Cronometro | giri con delta, notifica persistente, sopravvive alla chiusura dell'app |
| Riposo | ora di andare a letto e di sveglia, giorni, promemoria anticipato |

**In più**

- **Rinvio regolabile mentre suona.** I pulsanti `−` e `+` sulla schermata della
  sveglia cambiano i minuti di rinvio *prima* di posticipare, come su Samsung.
  Passo, minimo, massimo e numero di rinvii consentiti si impostano nelle
  impostazioni.
- **Glyph Matrix (Nothing Phone 3).** Mentre la sveglia suona la matrice 25×25 sul
  retro mostra l'ora corrente in dot-matrix con i due punti che lampeggiano al
  secondo, alternata a una campanella pulsante e all'etichetta a scorrimento.
  Dopo il rinvio mostra per dieci secondi il conto alla rovescia, poi si spegne.
- **Tasti fisici.** Volume e tasto di accensione posticipano o spengono la
  sveglia (a scelta), e così anche capovolgere o scuotere il telefono.

## Glyph Matrix: come è integrata

Le classi `com.nothing.ketchum.*` esistono solo nel framework dei Nothing Phone.
Il modulo [`glyph/`](glyph/) ne contiene i **soli stub di compilazione**
(`compileOnly`, non finiscono nell'APK) e il manifest dichiara
`<uses-library android:name="com.nothing.ketchum" android:required="false"/>`.

`GlyphController` isola ogni chiamata alla SDK dietro un `runCatching`: su un
dispositivo che non espone la libreria l'inizializzazione fallisce e la funzione
resta semplicemente inattiva, senza incidere sul resto dell'app.

> Se una firma della SDK dovesse cambiare, l'effetto è che la matrice non si
> accende — la sveglia continua a suonare normalmente.

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
release a ogni tag `v*`. Servono quattro segreti in
*Settings → Secrets and variables → Actions*:

| Segreto | Contenuto |
|---|---|
| `NOALARM_KEYSTORE_BASE64` | il keystore codificato con `base64 -w0 noalarm-release.jks` |
| `NOALARM_STORE_PASSWORD` | password del keystore |
| `NOALARM_KEY_ALIAS` | `noalarm` |
| `NOALARM_KEY_PASSWORD` | password della chiave (uguale a quella del keystore) |

Il workflow verifica la firma con `apksigner` e allega APK e AAB alla release.

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
