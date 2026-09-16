package com.noalarm.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Sveglie con eco programmate localmente via AlarmManager (esente da Doze),
 * non piu' solo un'eco "live" che il telefono deve far arrivare esattamente
 * all'istante giusto via Bluetooth: il watch squilla da solo anche se in quel
 * momento la connessione non c'e' (schermo spento, telefono fuori portata...).
 * Il telefono resta l'unica fonte di verita' (ricorrenze, giorni, rinvii): qui
 * arriva solo l'elenco completo di id + istante assoluto gia' calcolato +
 * etichetta (WearBridge.ACTION_SYNC), non le regole per calcolarlo.
 */
object WatchAlarmScheduler {
    private const val PREFS = "watch_alarms"
    private const val KEY_IDS = "scheduled_ids"

    private fun manager(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun pending(c: Context, id: Long, label: String = ""): PendingIntent {
        val i = Intent(c, WatchAlarmReceiver::class.java)
            .putExtra(WatchAlarmReceiver.EXTRA_ID, id)
            .putExtra(WatchAlarmReceiver.EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(
            c, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Risincronizzazione completa (non un diff incrementale): il chiamante
     * manda sempre l'elenco intero delle sveglie attive con eco, qui si
     * cancellano le id non piu' presenti e si (ri)programmano le altre.
     */
    fun sync(c: Context, entries: List<Triple<Long, Long, String>>) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = entries.map { it.first }.toSet()
        val prev = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        (prev - next).forEach { manager(c).cancel(pending(c, it)) }
        entries.forEach { (id, at, label) ->
            manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, id, label))
        }
        prefs.edit().putStringSet(KEY_IDS, next.map { it.toString() }.toSet()).apply()
    }
}
