package com.elephenman.lifetrack.ui.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.elephenman.lifetrack.ui.home.TimelineSegment
import java.util.*

/**
 * 24小时横向时间轴视图
 * 彩色条带表示每个地点/行程
 */
class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var segments: List<TimelineSegment> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1AFFFFFF
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB0BEC5FF.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setData(segments: List<TimelineSegment>) {
        this.segments = segments
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 画背景条
        canvas.drawRoundRect(0f, 8f, w, h - 8f, 12f, 12f, bgPaint)

        if (segments.isEmpty()) return

        // 计算今日0:00和24:00的时间戳
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000

        // 画每个segment
        segments.forEach { seg ->
            val startRatio = ((seg.startTime - dayStart).toFloat() / (dayEnd - dayStart)).coerceIn(0f, 1f)
            val endRatio = ((seg.endTime - dayStart).toFloat() / (dayEnd - dayStart)).coerceIn(0f, 1f)

            rect.left = startRatio * w + 2f
            rect.top = 10f
            rect.right = endRatio * w - 2f
            rect.bottom = h - 10f

            barPaint.color = seg.color
            barPaint.style = Paint.Style.FILL

            canvas.drawRoundRect(rect, 6f, 6f, barPaint)
        }

        // 画时间刻度 (0, 6, 12, 18, 24)
        val hours = intArrayOf(0, 6, 12, 18, 24)
        hours.forEach { hour ->
            val x = (hour.toFloat() / 24f) * w
            textPaint.color = 0x80FFFFFF.toInt()
            canvas.drawText("${hour}", x, h - 1f, textPaint)
        }
    }
}
