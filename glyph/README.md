# glyph

Stub API della Glyph Matrix SDK di Nothing (`com.nothing.ketchum`).

Serve solo a **compilare**: viene linkata con `compileOnly`, quindi non finisce
nell'APK. L'implementazione reale è la shared library di sistema presente sui
Nothing Phone, dichiarata in `AndroidManifest.xml` con:

```xml
<uses-library android:name="com.nothing.ketchum" android:required="false" />
```

Su un dispositivo non-Nothing le classi non esistono: `GlyphController` intercetta
`Throwable` all'inizializzazione e resta semplicemente inattivo.
