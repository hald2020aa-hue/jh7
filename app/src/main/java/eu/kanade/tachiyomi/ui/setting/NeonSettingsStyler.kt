package eu.kanade.tachiyomi.ui.setting

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import eu.kanade.tachiyomi.R

/**
 * Presentation-only styling for all existing AndroidX Preference settings screens.
 * Does not replace Preference instances, adapters, keys, values, listeners or persistence.
 * The same adapter continues to perform clicks and bind the original widgets.
 */
internal class NeonSettingsStyler(
    private val list: RecyclerView,
    private val mainMenu: Boolean,
) {
    private val density = list.resources.displayMetrics.density
    private val cyan = Color.rgb(54, 230, 255)
    private val blue = Color.rgb(90, 126, 255)
    private val purple = Color.rgb(154, 77, 255)
    private val pink = Color.rgb(252, 57, 237)
    private val secondary = Color.rgb(195, 189, 218)

    private val spacing = object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: android.view.View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val pref = preferenceAt(parent.getChildAdapterPosition(view))
            if (pref is PreferenceCategory) {
                if (pref.title.isNullOrBlank()) outRect.set(0, 0, 0, 0)
                else outRect.set(dp(14), dp(9), dp(14), 0)
            } else {
                outRect.set(dp(12), dp(4), dp(12), dp(4))
            }
        }
    }

    // A scrollable decorative banner before preference row zero, with zero changes to
    // PreferenceScreen / PreferenceGroupAdapter / click listeners / persisted values.
    private val settingsHero = object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: android.view.View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            if (parent.getChildAdapterPosition(view) == 0) outRect.top += dp(160)
        }

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val first = parent.findViewHolderForAdapterPosition(0)?.itemView ?: return
            val top = first.top - dp(160)
            if (top + dp(146) <= 0 || top >= parent.height) return
            val area = RectF(dp(12).toFloat(), (top + dp(5)).toFloat(),
                (parent.width - dp(12)).toFloat(), (top + dp(148)).toFloat())
            val round = Path().apply { addRoundRect(area, dp(21).toFloat(), dp(21).toFloat(), Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(round)
            list.context.getDrawable(R.drawable.v23_header_art)?.apply {
                setBounds(area.left.toInt(), area.top.toInt(), area.right.toInt(), area.bottom.toInt())
                draw(canvas)
            }
            val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(area.left, area.top, area.right, area.top,
                    intArrayOf(Color.argb(247, 7, 8, 24), Color.argb(194, 12, 12, 35), Color.argb(18, 24, 12, 44)),
                    null, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(area, dp(21).toFloat(), dp(21).toFloat(), overlay)
            canvas.restore()
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cyan
                strokeWidth = dp(1).toFloat()
                alpha = 170
                style = Paint.Style.STROKE
            }
            canvas.drawRoundRect(area, dp(21).toFloat(), dp(21).toFloat(), outline)
            drawHeroText(canvas, list.context.getString(R.string.v23_settings_title),
                area.left + dp(20), area.top + dp(29),
                (area.width() * 0.54f).toInt(), 25f, Color.WHITE, true)
            drawHeroText(canvas, list.context.getString(R.string.v23_settings_caption),
                area.left + dp(20), area.top + dp(78),
                (area.width() * 0.54f).toInt(), 12f, secondary, false)
        }
    }

    private fun drawHeroText(
        canvas: Canvas, value: String, left: Float, top: Float, width: Int,
        fontSizeSp: Float, color: Int, bold: Boolean,
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = fontSizeSp * list.resources.displayMetrics.scaledDensity
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: android.view.View) {
            style(view)
        }
        override fun onChildViewDetachedFromWindow(view: android.view.View) = Unit
    }

    init {
        list.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.rgb(17, 10, 41), Color.rgb(5, 6, 19), Color.rgb(21, 10, 39),
        ))
        list.clipToPadding = false
        list.addItemDecoration(spacing)
        if (mainMenu) list.addItemDecoration(settingsHero)
        list.addOnChildAttachStateChangeListener(attachListener)
        for (i in 0 until list.childCount) style(list.getChildAt(i))
    }

    fun detach() {
        list.removeOnChildAttachStateChangeListener(attachListener)
        list.removeItemDecoration(spacing)
        if (mainMenu) list.removeItemDecoration(settingsHero)
    }

    private fun dp(value: Int): Int = (value * density + .5f).toInt()

    private fun preferenceAt(position: Int): Preference? {
        // PreferenceController may attach or refresh its adapter after view creation.
        val adapter = list.adapter as? PreferenceGroupAdapter ?: return null
        return if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) null
            else adapter.getItem(position)
    }

    private fun style(view: android.view.View) {
        val preference = preferenceAt(list.getChildAdapterPosition(view)) ?: return
        val category = preference is PreferenceCategory
        val background = NeonPreferenceBackground(density, category)
        view.background = if (category || !preference.isSelectable || !preference.isEnabled) {
            background
        } else {
            RippleDrawable(ColorStateList.valueOf(Color.argb(96, 49, 228, 255)), background, null)
        }
        if (!category) view.minimumHeight = maxOf(view.minimumHeight, dp(if (mainMenu) 70 else 64))

        view.findViewById<TextView>(android.R.id.title)?.apply {
            setTextColor(if (category) cyan else Color.WHITE)
            if (category) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 17f
            } else if (mainMenu) {
                textSize = 15f
            }
        }
        view.findViewById<TextView>(android.R.id.summary)?.setTextColor(secondary)
        // Keep the original icon and click target, but align its visual tint with the app.
        if (mainMenu) {
            view.findViewById<ImageView>(android.R.id.icon)?.imageTintList =
                ColorStateList.valueOf(if (category) pink else cyan)
        }

        // Restore widget state handling to the original Preference; tint only the visuals.
        view.findViewById<MaterialSwitch>(R.id.switchWidget)?.apply {
            val checked = intArrayOf(android.R.attr.state_checked)
            val other = intArrayOf()
            thumbTintList = ColorStateList(arrayOf(checked, other), intArrayOf(Color.WHITE, secondary))
            trackTintList = ColorStateList(arrayOf(checked, other), intArrayOf(purple, Color.rgb(41, 35, 68)))
        }
        view.findViewById<Slider>(R.id.slider)?.apply {
            trackActiveTintList = ColorStateList.valueOf(cyan)
            trackInactiveTintList = ColorStateList.valueOf(Color.rgb(66, 54, 100))
            thumbTintList = ColorStateList.valueOf(pink)
            haloTintList = ColorStateList.valueOf(Color.argb(88, 255, 50, 203))
        }
    }

    private inner class NeonPreferenceBackground(
        density: Float,
        private val category: Boolean,
    ) : Drawable() {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, density * 78,
                Color.rgb(31, 23, 59), Color.rgb(13, 16, 34), Shader.TileMode.CLAMP)
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (category) 0f else density * 1.5f
        }
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density * 4.5f
            alpha = 64
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density * 2f
            color = pink
        }
        private val boundsRect = RectF()
        private var lastWidth = -1
        override fun draw(canvas: Canvas) {
            boundsRect.set(bounds)
            if (boundsRect.width() < 2f || boundsRect.height() < 2f) return
            if (lastWidth != bounds.width()) {
                val gradient = LinearGradient(0f, 0f, bounds.width().toFloat(), 0f,
                    intArrayOf(cyan, blue, purple, pink), null, Shader.TileMode.CLAMP)
                borderPaint.shader = gradient
                haloPaint.shader = gradient
                linePaint.shader = gradient
                lastWidth = bounds.width()
            }
            if (category) {
                canvas.drawLine(dp(6).toFloat(), boundsRect.bottom - dp(1),
                    boundsRect.right - dp(6), boundsRect.bottom - dp(1), linePaint)
                return
            }
            val inset = density * .8f
            boundsRect.inset(inset, inset)
            val radius = density * 20f
            canvas.drawRoundRect(boundsRect, radius, radius, backgroundPaint)
            // Tiny blurred-looking rim without expensive blur or bitmap layers.
            canvas.drawRoundRect(boundsRect, radius, radius, haloPaint)
            canvas.drawRoundRect(boundsRect, radius, radius, borderPaint)
        }
        override fun setAlpha(alpha: Int) {
            backgroundPaint.alpha = alpha
            borderPaint.alpha = alpha
            haloPaint.alpha = (alpha * 52 / 255).coerceIn(0, 255)
            linePaint.alpha = alpha
            invalidateSelf()
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            backgroundPaint.colorFilter = colorFilter
            borderPaint.colorFilter = colorFilter
            haloPaint.colorFilter = colorFilter
            linePaint.colorFilter = colorFilter
            invalidateSelf()
        }
        @Deprecated("Compatibility with Drawable API")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
