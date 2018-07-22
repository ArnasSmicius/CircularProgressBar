package com.arnassmicius.circularprogressbar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.util.AttributeSet
import android.view.View

class CircularProgressBar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_MAX_VALUE = 100f
        private const val DEFAULT_START_ANGLE = 270f
        private const val DEFAULT_ANIMATION_DURATION = 1500L
    }

    private var progress = 0f

    var progressMax = DEFAULT_MAX_VALUE
        set(value) {
            field = if (value >= 0) value else DEFAULT_MAX_VALUE
            reDraw()
        }

    var strokeWidth = resources.getDimension(R.dimen.default_stroke_width)
        set(value) {
            field = value
            foregroundPaint.strokeWidth = value
            reDraw()
        }

    var backgroundStrokeWidth = resources.getDimension(R.dimen.default_background_stroke_width)
        set(value) {
            field = value
            backgroundPaint.strokeWidth = value
            reDraw()
        }

    var color = Color.BLACK
        set(value) {
            field = value
            foregroundPaint.color = value
            reDraw()
        }

    var progressBackgroundColor = Color.GRAY
        set(value) {
            field = value
            backgroundPaint.color = value
            reDraw()
        }

    var roundEdges = false
        set(value) {
            field = value
            foregroundPaint.strokeCap = if (value) Paint.Cap.ROUND else Paint.Cap.BUTT
            reDraw()
        }

    private var rightToLeft = true
    private var indeterminateMode = false
    private var startAngle = DEFAULT_START_ANGLE
    var progressChangeListener: ProgressChangeListener? = null
    var indeterminateModeChangeListener: IndeterminateModeChangeListener? = null
    private var progressAnimator: ValueAnimator? = null
    private var indeterminateModeHandler: Handler? = null

    private val rectF: RectF = RectF()
    private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foregroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.CircularProgressBar, 0, 0)
        try {
            progress = typedArray.getFloat(R.styleable.CircularProgressBar_cpb_progress, progress)
            progressMax = typedArray.getFloat(R.styleable.CircularProgressBar_cpb_progress_max, progressMax)
            indeterminateMode = typedArray.getBoolean(R.styleable.CircularProgressBar_cpb_indeterminate_mode, indeterminateMode)
            strokeWidth = typedArray.getDimension(R.styleable.CircularProgressBar_cpb_progressbar_width, strokeWidth)
            backgroundStrokeWidth = typedArray.getDimension(R.styleable.CircularProgressBar_cpb_background_progressbar_width, backgroundStrokeWidth)
            color = typedArray.getInt(R.styleable.CircularProgressBar_cpb_progressbar_color, color)
            progressBackgroundColor =  typedArray.getInt(R.styleable.CircularProgressBar_cpb_background_progressbar_color, progressBackgroundColor)
            roundEdges = typedArray.getBoolean(R.styleable.CircularProgressBar_cpb_round_edges, roundEdges)
        } finally {
            typedArray.recycle()
        }

        backgroundPaint.color = progressBackgroundColor
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = backgroundStrokeWidth

        foregroundPaint.color = color
        foregroundPaint.style = Paint.Style.STROKE
        foregroundPaint.strokeWidth = strokeWidth
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (indeterminateMode) enableIndeterminateMode(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        indeterminateModeHandler?.removeCallbacks(indeterminateModeRunnable)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.drawOval(rectF, backgroundPaint)
        val realProgress = progress * DEFAULT_MAX_VALUE / progressMax
        val angle = (if (rightToLeft) 360 else -360) * realProgress / 100
        canvas?.drawArc(rectF, startAngle, angle, false, foregroundPaint)
    }

    private fun reDraw() {
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val min = Math.min(width, height)
        setMeasuredDimension(min, min)
        val highStroke = if (strokeWidth > backgroundStrokeWidth) strokeWidth else backgroundStrokeWidth
        rectF.set(0 + highStroke / 2, 0 + highStroke / 2, min - highStroke / 2, min - highStroke / 2)
    }

    fun setProgress(progress: Float) {
        setProgress(progress, false)
    }

    private fun setProgress(progress: Float, fromAnimation: Boolean) {
        if (!fromAnimation && progressAnimator != null) {
            progressAnimator?.cancel()
            if (indeterminateMode) enableIndeterminateMode(false)
        }
        this.progress = if (progress <= progressMax) progress else progressMax
        progressChangeListener?.onProgressChanged(progress)
        invalidate()
    }

    @JvmOverloads
    fun setProgressWithAnimation(progress: Float, duration: Long = DEFAULT_ANIMATION_DURATION) {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(this.progress, progress)
        progressAnimator?.duration = duration
        progressAnimator?.addUpdateListener {
            val progress = it.animatedValue as Float
            setProgress(progress, true)
            if (indeterminateMode) {
                val updateAngle = progress * 360 / 100
                startAngle = DEFAULT_START_ANGLE + (if (rightToLeft) updateAngle else -updateAngle)
            }
        }
        progressAnimator?.start()
    }

    private val indeterminateModeRunnable = object : Runnable {
        override fun run() {
            if (indeterminateMode) {
                indeterminateModeHandler?.postDelayed(this, DEFAULT_ANIMATION_DURATION)
                rightToLeft = !rightToLeft
                if (rightToLeft) {
                    setProgressWithAnimation(0f)
                } else {
                    setProgressWithAnimation(progressMax)
                }
            }
        }
    }

    fun enableIndeterminateMode(enable: Boolean) {
        indeterminateMode = enable
        indeterminateModeChangeListener?.onModeChange(indeterminateMode)
        rightToLeft = true
        startAngle = DEFAULT_START_ANGLE

        indeterminateModeHandler?.removeCallbacks(indeterminateModeRunnable)
        progressAnimator?.cancel()
        indeterminateModeHandler = Handler()

        if (indeterminateMode) {
            indeterminateModeHandler?.post(indeterminateModeRunnable)
        } else {
            setProgress(0f, true)
        }
    }
}