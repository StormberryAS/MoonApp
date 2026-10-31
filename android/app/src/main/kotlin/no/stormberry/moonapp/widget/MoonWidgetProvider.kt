package no.stormberry.moonapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * The three home screen widgets (1.0.8). Each size is its own provider, because a launcher lists
 * one picker entry per provider; all three draw from [MoonWidgets], which redraws every placed
 * widget of every size at once.
 *
 * `updatePeriodMillis` is 0 in all three provider files: the system's own periodic update cannot
 * be shorter than 30 minutes and wakes the phone. [MoonWidgets] arms an inexact, non-wakeup alarm
 * instead. After a reboot (once the phone is unlocked) and after an app update, the system sends
 * APPWIDGET_UPDATE itself, which lands in [onUpdate] and arms that alarm again, so no
 * BOOT_COMPLETED receiver and no permission is needed.
 */
abstract class MoonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MoonWidgets.refreshInBackground(this, context)
    }

    /** A resize or rotation: Android 7 to 11 choose the large arrangement from the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        MoonWidgets.refreshInBackground(this, context)
    }

    /**
     * The first widget of this size is placed: enable [WidgetEventReceiver] at once (the
     * [onUpdate] that follows enables it too, on its thread, before it arms the refresh alarm).
     */
    override fun onEnabled(context: Context) {
        try {
            MoonWidgets.setEventReceiver(context, true)
        } catch (e: RuntimeException) {
            android.util.Log.w("MoonWidgets", "could not enable the widget receiver", e)
        }
    }

    /**
     * The last widget of this size is gone; the refresh alarm and [WidgetEventReceiver] stay
     * while another size is placed.
     */
    override fun onDisabled(context: Context) {
        try {
            MoonWidgets.cancelIfNone(context)
        } catch (e: RuntimeException) {
            android.util.Log.w("MoonWidgets", "could not cancel the widget refresh", e)
        }
    }
}

/** 1x1: the moon and the lit percentage. */
class MoonWidgetSmall : MoonWidgetProvider()

/** 2x1: the moon, the percentage, and today's moonrise, transit and moonset. */
class MoonWidgetMedium : MoonWidgetProvider()

/** 4x2, resizable: everything the 2x1 shows, plus the dates of the next four principal phases. */
class MoonWidgetLarge : MoonWidgetProvider()
