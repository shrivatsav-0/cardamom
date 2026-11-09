package com.shrivatsav.cardamom

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil

class CardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            deleteCardId(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // NOTE: AppWidgetProvider must be exported=true to receive
        // APPWIDGET_UPDATE from the system. For our custom NEXT_CARD action,
        // validate that the target is actually one of *our* live widget ids
        // so arbitrary apps cannot drive arbitrary widget updates.
        if (intent.action == ACTION_NEXT_CARD) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val manager = AppWidgetManager.getInstance(context)
            val liveIds = manager.getAppWidgetIds(ComponentName(context, CardWidgetProvider::class.java))
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && liveIds.contains(appWidgetId)) {
                val repo = CardRepository(context)
                val cards = repo.cards.value
                if (cards.isNotEmpty()) {
                    val currentId = getCardId(context, appWidgetId)
                    val currentIndex = cards.indexOfFirst { it.id == currentId }
                    val nextIndex = (currentIndex + 1) % cards.size
                    saveCardId(context, appWidgetId, cards[nextIndex].id)
                    updateAppWidget(context, manager, appWidgetId)
                }
            }
        }
    }

    companion object {
        const val ACTION_NEXT_CARD = "com.shrivatsav.cardamom.ACTION_NEXT_CARD"
        private const val PREFS_NAME = "cardamom_widgets"
        private const val PREF_PREFIX_KEY = "card_widget_"

        private var cachedTypeface: Typeface? = null

        private fun getGoogleSansFlex(context: Context): Typeface {
            return cachedTypeface ?: try {
                ResourcesCompat.getFont(context, R.font.google_sans_flex)?.also {
                    cachedTypeface = it
                } ?: Typeface.DEFAULT
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }

        private fun createHeaderBitmap(context: Context, title: String, subtitle: String): Bitmap {
            val density = context.resources.displayMetrics.density
            val maxBoxWidth = (280 * density).toInt().coerceAtLeast(300)
            val sansFlex = getGoogleSansFlex(context)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF161616.toInt()
                textSize = 17f * density
                typeface = sansFlex
                fontVariationSettings = "'wght' 700, 'ROND' 100"
            }

            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99161616.toInt()
                textSize = 12f * density
                typeface = sansFlex
                fontVariationSettings = "'wght' 500, 'ROND' 100"
            }

            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, maxBoxWidth)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()

            val subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, maxBoxWidth)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()

            var actualWidth = 0f
            for (i in 0 until titleLayout.lineCount) {
                actualWidth = maxOf(actualWidth, titleLayout.getLineWidth(i))
            }
            for (i in 0 until subLayout.lineCount) {
                actualWidth = maxOf(actualWidth, subLayout.getLineWidth(i))
            }
            val bmpWidth = (ceil(actualWidth) + 4 * density).toInt().coerceIn(1, maxBoxWidth)
            val spacing = (2 * density).toInt()
            val bmpHeight = (titleLayout.height + spacing + subLayout.height).coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            titleLayout.draw(canvas)
            canvas.save()
            canvas.translate(0f, (titleLayout.height + spacing).toFloat())
            subLayout.draw(canvas)
            canvas.restore()

            return bitmap
        }

        private fun createCodeTextBitmap(context: Context, text: String): Bitmap {
            val density = context.resources.displayMetrics.density
            val sansFlex = getGoogleSansFlex(context)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF161616.toInt()
                textSize = 11f * density
                typeface = sansFlex
                fontVariationSettings = "'wght' 600, 'ROND' 100"
                letterSpacing = 0.05f
            }
            val textWidth = paint.measureText(text)
            val maxW = (280 * density).toInt()
            val bmpWidth = (ceil(textWidth) + 6 * density).toInt().coerceIn(1, maxW)
            val fm = paint.fontMetrics
            val bmpHeight = (fm.bottom - fm.top + 4 * density).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawText(text, 2 * density, -fm.top + 2 * density, paint)
            return bitmap
        }

        private fun createEmptyStateBitmap(context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val width = (260 * density).toInt()
            val sansFlex = getGoogleSansFlex(context)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF161616.toInt()
                textSize = 17f * density
                typeface = sansFlex
                fontVariationSettings = "'wght' 700, 'ROND' 100"
                textAlign = Paint.Align.CENTER
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99161616.toInt()
                textSize = 13f * density
                typeface = sansFlex
                fontVariationSettings = "'wght' 500, 'ROND' 100"
                textAlign = Paint.Align.CENTER
            }
            val height = (70 * density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = width / 2f
            canvas.drawText("Cardamom", cx, 26f * density, titlePaint)
            canvas.drawText("Tap to choose card", cx, 50f * density, subPaint)
            return bitmap
        }

        fun saveCardId(context: Context, appWidgetId: Int, cardId: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_PREFIX_KEY + appWidgetId, cardId)
                .apply()
        }

        fun getCardId(context: Context, appWidgetId: Int): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_PREFIX_KEY + appWidgetId, null)
        }

        fun deleteCardId(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_PREFIX_KEY + appWidgetId)
                .apply()
        }

        fun notifyUpdateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CardWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val repo = CardRepository(context)
            val cards = repo.cards.value
            val savedId = getCardId(context, appWidgetId)
            val card = cards.firstOrNull { it.id == savedId } ?: cards.firstOrNull { it.isPinned } ?: cards.firstOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_card)

            if (card == null) {
                // Empty state
                views.setViewVisibility(R.id.widget_header, View.GONE)
                views.setViewVisibility(R.id.widget_code_box, View.GONE)
                views.setViewVisibility(R.id.widget_empty_image, View.VISIBLE)
                views.setImageViewBitmap(R.id.widget_empty_image, createEmptyStateBitmap(context))

                val launchIntent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // Populate card info
            views.setViewVisibility(R.id.widget_empty_image, View.GONE)
            views.setViewVisibility(R.id.widget_header, View.VISIBLE)
            views.setViewVisibility(R.id.widget_code_box, View.VISIBLE)

            val titleText = card.issuer.ifBlank { card.name.ifBlank { "Card" } }
            val subText = when {
                card.issuer.isNotBlank() && card.name.isNotBlank() && !card.name.equals(card.issuer, true) -> card.name
                else -> card.category
            }

            views.setImageViewBitmap(R.id.widget_header_image, createHeaderBitmap(context, titleText, subText))
            views.setContentDescription(R.id.widget_header_image, "$titleText, $subText")

            views.setImageViewBitmap(R.id.widget_btn_next, null)

            // Respect the app lock: never render card codes on the home-screen
            // widget when the user has enabled the app lock. The widget is
            // outside the locked app process and would otherwise bypass it.
            val lockEnabled = try { Prefs(context).lockEnabled } catch (_: Exception) { false }
            if (lockEnabled) {
                views.setViewVisibility(R.id.widget_code_image, View.GONE)
                views.setViewVisibility(R.id.widget_code_text_image, View.GONE)
                views.setImageViewBitmap(R.id.widget_empty_image, createEmptyStateBitmap(context))
                views.setViewVisibility(R.id.widget_empty_image, View.VISIBLE)
                val launchIntent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, appWidgetId, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // Generate code preview bitmap
            if (card.code.isNotBlank()) {
                val is2D = card.format.is2D
                val codeBmp = if (is2D) {
                    Codes.bitmap(card.code, card.format, 480, 480)
                } else {
                    Codes.bitmap(card.code, card.format, 720, 260)
                }

                if (codeBmp != null) {
                    views.setImageViewBitmap(R.id.widget_code_image, codeBmp)
                    views.setViewVisibility(R.id.widget_code_image, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_code_image, View.GONE)
                }

                if (!is2D && card.code.isNotBlank()) {
                    views.setImageViewBitmap(R.id.widget_code_text_image, createCodeTextBitmap(context, card.code))
                    // Never put the raw code in a content description — TalkBack
                    // would read a sensitive card number aloud / log it.
                    views.setContentDescription(R.id.widget_code_text_image, "Card code")
                    views.setViewVisibility(R.id.widget_code_text_image, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_code_text_image, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_code_image, View.GONE)
                views.setImageViewBitmap(R.id.widget_code_text_image, createCodeTextBitmap(context, "No code on this card"))
                views.setContentDescription(R.id.widget_code_text_image, "No code on this card")
                views.setViewVisibility(R.id.widget_code_text_image, View.VISIBLE)
            }

            // Click action to open card detail directly in Cardamom
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_card_id", card.id)
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)
            views.setOnClickPendingIntent(R.id.widget_code_box, pendingOpen)

            // Switch to next card button action
            if (cards.size > 1) {
                views.setViewVisibility(R.id.widget_btn_next, View.VISIBLE)
                val nextIntent = Intent(context, CardWidgetProvider::class.java).apply {
                    action = ACTION_NEXT_CARD
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingNext = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_next, pendingNext)
            } else {
                views.setViewVisibility(R.id.widget_btn_next, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
