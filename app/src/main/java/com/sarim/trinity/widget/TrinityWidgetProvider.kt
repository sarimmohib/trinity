package com.sarim.trinity.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.sarim.trinity.MainActivity
import com.sarim.trinity.R
import com.sarim.trinity.data.QuarantineStore
import com.sarim.trinity.mms.MMS_TYPE_INCOMING
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * A small, non-collection home-screen widget: unread texts (SMS + MMS), missed calls
 * since midnight, and held texts. Each row is a shortcut into the matching part of
 * Trinity. No message body ever leaves the process — the widget only shows numbers.
 */
class TrinityWidgetProvider : AppWidgetProvider() {

    /** Standard update path. Bounded direct queries; the broadcast path uses goAsync(). */
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != AppWidgetManager.ACTION_APPWIDGET_UPDATE && action != ACTION_REFRESH) {
            super.onReceive(context, intent)
            return
        }
        // Content-provider reads must not run on the main thread. goAsync() keeps the
        // receiver alive until a single bounded worker has finished updating every
        // widget instance, then the pending result is finished in a finally block.
        val pending = goAsync()
        val ids = if (action == ACTION_REFRESH) {
            allWidgetIds(context)
        } else {
            intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: allWidgetIds(context)
        }
        if (ids.isEmpty()) {
            pending.finish()
            return
        }
        WORKER.execute {
            try {
                updateWidgets(context, AppWidgetManager.getInstance(context), ids)
            } finally {
                pending.finish()
            }
        }
    }

    private fun allWidgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TrinityWidgetProvider::class.java))

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val counts = readCounts(context)
        val views = RemoteViews(context.packageName, R.layout.widget_trinity)
        views.setTextViewText(R.id.count_unread, counts.unread.toString())
        views.setTextViewText(R.id.count_missed, counts.missed.toString())
        views.setTextViewText(R.id.count_held, counts.held.toString())
        views.setOnClickPendingIntent(R.id.row_unread, tabIntent(context, DEST_MESSAGES))
        views.setOnClickPendingIntent(R.id.row_missed, tabIntent(context, DEST_CALLS))
        views.setOnClickPendingIntent(R.id.row_held, tabIntent(context, DEST_HELD))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private data class Counts(val unread: Int, val missed: Int, val held: Int)

    /** Each provider read is independent: a device with no MMS or call log still works. */
    private fun readCounts(context: Context): Counts {
        var unread = 0
        if (hasPermission(context, Manifest.permission.READ_SMS)) {
            var sms = 0
            var mms = 0
            runCatching {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID),
                    "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
                    null,
                    null,
                )?.use { sms = it.count }
            }
            runCatching {
                context.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID),
                    "${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_TYPE} = $MMS_TYPE_INCOMING",
                    null,
                    null,
                )?.use { mms = it.count }
            }
            unread = sms + mms
        }

        var missed = 0
        if (hasPermission(context, Manifest.permission.READ_CALL_LOG)) {
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            runCatching {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls._ID),
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(), startOfToday.toString()),
                    null,
                )?.use { missed = it.count }
            }
        }

        val held = runCatching { QuarantineStore(context).all().size }.getOrDefault(0)
        return Counts(unread, missed, held)
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * The extra is read by MainActivity: 0 = Calls tab, 1 = Messages tab,
     * 3 = Held screen. 2 is the Contacts tab and must not be used for Held.
     */
    private fun tabIntent(context: Context, destination: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TAB, destination)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            destination,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Intent extra consumed by MainActivity when a widget row is tapped. */
        const val EXTRA_TAB = "trinity.widget.tab"
        /** Custom refresh action delivered to this receiver by [refresh]. */
        const val ACTION_REFRESH = "com.sarim.trinity.widget.REFRESH"

        private const val DEST_CALLS = 0
        private const val DEST_MESSAGES = 1
        private const val DEST_HELD = 3

        private val WORKER = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "trinity-widget")
        }

        /** Ask every widget instance to re-read its counts. Safe to call from any thread. */
        fun refresh(context: Context) {
            runCatching {
                context.sendBroadcast(
                    Intent(context, TrinityWidgetProvider::class.java).setAction(ACTION_REFRESH),
                )
            }
        }
    }
}
