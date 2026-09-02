package com.noalarm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistenza: un solo file di preferenze, JSON scritto a mano.
 * Poche decine di record: un database sarebbe peso morto.
 */
object Store {

    private lateinit var prefs: SharedPreferences

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms

    private val _timers = MutableStateFlow<List<TimerItem>>(emptyList())
    val timers: StateFlow<List<TimerItem>> = _timers

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _stopwatch = MutableStateFlow(Stopwatch())
    val stopwatch: StateFlow<Stopwatch> = _stopwatch

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("noalarm", Context.MODE_PRIVATE)
        _alarms.value = readList("alarms", ::alarmOf)
        _timers.value = readList("timers", ::timerOf)
        _settings.value = prefs.getString("settings", null)?.let { settingsOf(JSONObject(it)) } ?: Settings()
        _stopwatch.value = prefs.getString("stopwatch", null)?.let { stopwatchOf(JSONObject(it)) } ?: Stopwatch()
    }

    // --- sveglie ---------------------------------------------------------

    fun alarm(id: Long): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun putAlarm(alarm: Alarm) {
        val list = _alarms.value.filter { it.id != alarm.id } + alarm
        _alarms.value = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        writeAlarms()
    }

    fun removeAlarm(id: Long) {
        _alarms.value = _alarms.value.filter { it.id != id }
        writeAlarms()
    }

    fun updateAlarm(id: Long, block: (Alarm) -> Alarm) = alarm(id)?.let { putAlarm(block(it)) }

    private fun writeAlarms() = prefs.edit()
        .putString("alarms", JSONArray(_alarms.value.map { json(it) }).toString()).apply()

    // --- timer -----------------------------------------------------------

    fun putTimer(t: TimerItem) {
        _timers.value = (_timers.value.filter { it.id != t.id } + t).sortedBy { it.id }
        writeTimers()
    }

    fun removeTimer(id: Long) {
        _timers.value = _timers.value.filter { it.id != id }
        writeTimers()
    }

    fun updateTimer(id: Long, block: (TimerItem) -> TimerItem) =
        _timers.value.firstOrNull { it.id == id }?.let { putTimer(block(it)) }

    private fun writeTimers() = prefs.edit()
        .putString("timers", JSONArray(_timers.value.map { json(it) }).toString()).apply()

    // --- cronometro ------------------------------------------------------

    fun setStopwatch(s: Stopwatch) {
        _stopwatch.value = s
        prefs.edit().putString("stopwatch", json(s).toString()).apply()
    }

    // --- impostazioni ----------------------------------------------------

    fun update(block: (Settings) -> Settings) {
        _settings.value = block(_settings.value)
        prefs.edit().putString("settings", json(_settings.value).toString()).apply()
    }

    // --- (de)serializzazione ---------------------------------------------

    private fun <T> readList(key: String, of: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { runCatching { of(arr.getJSONObject(it)) }.getOrNull() }
    }

    private fun ints(o: JSONObject, key: String): Set<Int> {
        val a = o.optJSONArray(key) ?: return emptySet()
        return (0 until a.length()).map { a.getInt(it) }.toSet()
    }

    private fun longs(o: JSONObject, key: String): Set<Long> {
        val a = o.optJSONArray(key) ?: return emptySet()
        return (0 until a.length()).map { a.getLong(it) }.toSet()
    }

    private fun strings(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun json(a: Alarm) = JSONObject().apply {
        put("id", a.id); put("hour", a.hour); put("minute", a.minute)
        put("enabled", a.enabled); put("days", JSONArray(a.days.toList()))
        put("dateEpochDay", a.dateEpochDay); put("skipDates", JSONArray(a.skipDates.toList()))
        put("label", a.label); put("soundUri", a.soundUri ?: JSONObject.NULL)
        put("vibrate", a.vibrate); put("gradualVolume", a.gradualVolume)
        put("snoozeMinutes", a.snoozeMinutes); put("autoSilenceMinutes", a.autoSilenceMinutes)
        put("snoozeStepMinutes", a.snoozeStepMinutes)
        put("snoozeMinMinutes", a.snoozeMinMinutes); put("snoozeMaxMinutes", a.snoozeMaxMinutes)
        put("snoozeLimit", a.snoozeLimit)
        put("glyph", a.glyph); put("glyphStyle", a.glyphStyle.name)
        put("skipNext", a.skipNext); put("snoozedUntil", a.snoozedUntil)
    }

    private fun alarmOf(o: JSONObject) = Alarm(
        id = o.getLong("id"),
        hour = o.optInt("hour"), minute = o.optInt("minute"),
        enabled = o.optBoolean("enabled", true), days = ints(o, "days"),
        dateEpochDay = o.optLong("dateEpochDay", 0L), skipDates = longs(o, "skipDates"),
        label = o.optString("label", ""),
        soundUri = if (o.isNull("soundUri")) null else o.optString("soundUri"),
        vibrate = o.optBoolean("vibrate", true),
        gradualVolume = o.optBoolean("gradualVolume", true),
        snoozeMinutes = o.optInt("snoozeMinutes", 10),
        autoSilenceMinutes = o.optInt("autoSilenceMinutes", 10),
        snoozeStepMinutes = o.optInt("snoozeStepMinutes", 1),
        snoozeMinMinutes = o.optInt("snoozeMinMinutes", 1),
        snoozeMaxMinutes = o.optInt("snoozeMaxMinutes", 60),
        snoozeLimit = o.optInt("snoozeLimit", 0),
        glyph = o.optBoolean("glyph", true),
        glyphStyle = runCatching { GlyphStyle.valueOf(o.optString("glyphStyle", "CYCLE")) }
            .getOrDefault(GlyphStyle.CYCLE),
        skipNext = o.optBoolean("skipNext", false),
        snoozedUntil = o.optLong("snoozedUntil", 0L),
    )

    private fun json(t: TimerItem) = JSONObject().apply {
        put("id", t.id); put("label", t.label); put("totalMs", t.totalMs)
        put("endAt", t.endAt); put("remainingMs", t.remainingMs)
        put("running", t.running); put("firedAt", t.firedAt)
    }

    private fun timerOf(o: JSONObject) = TimerItem(
        id = o.getLong("id"), label = o.optString("label", ""),
        totalMs = o.optLong("totalMs"), endAt = o.optLong("endAt"),
        remainingMs = o.optLong("remainingMs"), running = o.optBoolean("running"),
        firedAt = o.optLong("firedAt"),
    )

    private fun json(s: Stopwatch) = JSONObject().apply {
        put("startedAt", s.startedAt); put("accumulated", s.accumulated)
        put("laps", JSONArray(s.laps))
    }

    private fun stopwatchOf(o: JSONObject): Stopwatch {
        val a = o.optJSONArray("laps")
        return Stopwatch(
            startedAt = o.optLong("startedAt"),
            accumulated = o.optLong("accumulated"),
            laps = (0 until (a?.length() ?: 0)).map { a!!.getLong(it) },
        )
    }

    private fun json(s: Settings) = JSONObject().apply {
        put("use24h", s.use24h); put("weekStartsMonday", s.weekStartsMonday)
        put("showSeconds", s.showSeconds)
        put("defaultSnoozeMinutes", s.defaultSnoozeMinutes)
        put("defaultAutoSilenceMinutes", s.defaultAutoSilenceMinutes)
        put("volumeKeyAction", s.volumeKeyAction.name); put("powerKeyAction", s.powerKeyAction.name)
        put("flipAction", s.flipAction.name); put("shakeAction", s.shakeAction.name)
        put("glyphEnabled", s.glyphEnabled); put("glyphAppChannel", s.glyphAppChannel)
        put("glyphToyClock", s.glyphToyClock)
        put("timerSoundUri", s.timerSoundUri ?: JSONObject.NULL)
        put("worldClocks", JSONArray(s.worldClocks)); put("homeZone", s.homeZone)
        put("bedtimeEnabled", s.bedtimeEnabled)
        put("bedtimeHour", s.bedtimeHour); put("bedtimeMinute", s.bedtimeMinute)
        put("wakeHour", s.wakeHour); put("wakeMinute", s.wakeMinute)
        put("bedtimeDays", JSONArray(s.bedtimeDays.toList()))
        put("bedtimeReminderMinutes", s.bedtimeReminderMinutes)
    }

    private fun key(o: JSONObject, k: String, def: KeyAction) =
        runCatching { KeyAction.valueOf(o.optString(k, def.name)) }.getOrDefault(def)

    private fun settingsOf(o: JSONObject) = Settings(
        use24h = o.optBoolean("use24h", true),
        weekStartsMonday = o.optBoolean("weekStartsMonday", true),
        showSeconds = o.optBoolean("showSeconds", false),
        defaultSnoozeMinutes = o.optInt("defaultSnoozeMinutes", 10),
        defaultAutoSilenceMinutes = o.optInt("defaultAutoSilenceMinutes", 10),
        volumeKeyAction = key(o, "volumeKeyAction", KeyAction.SNOOZE),
        powerKeyAction = key(o, "powerKeyAction", KeyAction.DISMISS),
        flipAction = key(o, "flipAction", KeyAction.NONE),
        shakeAction = key(o, "shakeAction", KeyAction.NONE),
        glyphEnabled = o.optBoolean("glyphEnabled", true),
        glyphAppChannel = o.optBoolean("glyphAppChannel", false),
        glyphToyClock = o.optBoolean("glyphToyClock", true),
        timerSoundUri = if (o.isNull("timerSoundUri")) null else o.optString("timerSoundUri"),
        worldClocks = strings(o, "worldClocks"),
        homeZone = o.optString("homeZone", ""),
        bedtimeEnabled = o.optBoolean("bedtimeEnabled", false),
        bedtimeHour = o.optInt("bedtimeHour", 23), bedtimeMinute = o.optInt("bedtimeMinute", 0),
        wakeHour = o.optInt("wakeHour", 7), wakeMinute = o.optInt("wakeMinute", 0),
        bedtimeDays = ints(o, "bedtimeDays").ifEmpty { setOf(1, 2, 3, 4, 5) },
        bedtimeReminderMinutes = o.optInt("bedtimeReminderMinutes", 30),
    )
}

data class Stopwatch(
    /** 0 quando fermo. */
    val startedAt: Long = 0L,
    val accumulated: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    val running get() = startedAt > 0
    fun elapsed(now: Long = System.currentTimeMillis()) =
        accumulated + if (running) now - startedAt else 0L
}
