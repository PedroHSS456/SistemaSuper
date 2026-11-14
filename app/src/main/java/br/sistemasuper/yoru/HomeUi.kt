package br.sistemasuper.yoru

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/** Paginas lado a lado, com deslize e encaixe na pagina mais proxima. */
class Pager(ctx: Context) : HorizontalScrollView(ctx) {
    val row = LinearLayout(ctx)
    var current = 0
    var onPage: ((Int) -> Unit)? = null
    private var startPage = 0

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        row.orientation = LinearLayout.HORIZONTAL
        addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun pw(): Int = resources.displayMetrics.widthPixels

    private fun pageNow(): Int = (scrollX + pw() / 2) / pw()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) startPage = pageNow()
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) startPage = pageNow()
        val r = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            val d = scrollX - startPage * pw()
            var t = startPage
            if (d > pw() * 0.12f) t++ else if (d < -pw() * 0.12f) t--
            goTo(t.coerceIn(0, maxOf(0, row.childCount - 1)), true)
        }
        return r
    }

    fun goTo(i: Int, animate: Boolean) {
        current = i
        if (animate) smoothScrollTo(i * pw(), 0) else scrollTo(i * pw(), 0)
        onPage?.invoke(i)
    }
}

/** Uma pagina da tela inicial: grade de colunas x linhas, cada filho na sua casa (HomeItem na tag). */
class GridPage(ctx: Context, val cols: Int, val rows: Int) : ViewGroup(ctx) {

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = View.MeasureSpec.getSize(wSpec)
        val h = View.MeasureSpec.getSize(hSpec)
        val cw = w / cols
        val ch = h / rows
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / cols
        val ch = (b - t) / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val item = c.tag as? HomeItem ?: continue
            c.layout(item.x * cw, item.y * ch, item.x * cw + cw, item.y * ch + ch)
        }
    }

    /** Casa (x, y) sob um ponto da tela, ou null se fora da grade. */
    fun cellAt(rawX: Float, rawY: Float): Pair<Int, Int>? {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val x = rawX - loc[0]
        val y = rawY - loc[1]
        if (x < 0 || y < 0 || x >= width || y >= height || width == 0 || height == 0) return null
        val cx = (x / (width / cols)).toInt().coerceIn(0, cols - 1)
        val cy = (y / (height / rows)).toInt().coerceIn(0, rows - 1)
        return Pair(cx, cy)
    }
}

/** Icone de pasta: quadrado de vidro com ate 4 mini icones dentro. */
class FolderIcon(
    ctx: Context,
    private val apps: List<AppInfo>,
    private val pal: Palette,
    sizePx: Int
) : View(ctx) {
    private val bmps = arrayOfNulls<Bitmap>(4)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val mini = (sizePx * 0.36f).toInt()

    init {
        for (i in 0 until minOf(4, apps.size)) {
            val app = apps[i]
            val cached = AppRepo.cachedIcon(app, mini)
            if (cached != null) {
                bmps[i] = cached
            } else {
                AppRepo.loadIcon(ctx, app, mini) {
                    bmps[i] = it
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = w * 0.3f
        rect.set(0f, 0f, w, h)
        paint.style = Paint.Style.FILL
        paint.color = Ui.alpha(pal.panel, 235)
        canvas.drawRoundRect(rect, rad, rad, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.025f
        paint.color = Ui.alpha(pal.accent, 110)
        rect.inset(paint.strokeWidth / 2f, paint.strokeWidth / 2f)
        canvas.drawRoundRect(rect, rad, rad, paint)

        val gap = w * 0.08f
        val start = (w - (mini * 2 + gap)) / 2f
        for (i in 0 until 4) {
            val b = bmps[i] ?: continue
            val cx = start + (i % 2) * (mini + gap)
            val cy = start + (i / 2) * (mini + gap)
            canvas.drawBitmap(b, cx, cy, null)
        }
    }
}
