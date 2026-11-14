package br.sistemasuper.yoru

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cena noturna desenhada em codigo: skyline japones, placas de neon, lanternas,
 * um gato no telhado e particulas. Sem imagens, sem bibliotecas.
 * A parte parada e desenhada uma vez numa imagem; so o que se mexe e redesenhado,
 * e a animacao so roda enquanto a tela inicial esta visivel.
 */
class SceneView(ctx: Context) : View(ctx) {

    private class Sign(val r: RectF, val phase: Float)

    private var pal: Palette = Themes.current()
    private var effect = 0
    private var bg: Bitmap? = null
    private val signs = ArrayList<Sign>()
    private var rand = Random(7)
    private val prand = Random()
    private val density = ctx.resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tailPath = Path()
    private val earPath = Path()
    private val tmpRect = RectF()

    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var pvx = FloatArray(0)
    private var pvy = FloatArray(0)
    private var psz = FloatArray(0)
    private var pph = FloatArray(0)
    private var rainPts = FloatArray(0)

    private var t = 0f
    private var running = false
    private var lastNanos = 0L

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastNanos != 0L) {
                val dt = minOf((frameTimeNanos - lastNanos) / 1e9f, 0.1f)
                advance(dt)
                invalidate()
            }
            lastNanos = frameTimeNanos
            val delay = if (Prefs.animMode == 1) 52L else 20L
            Choreographer.getInstance().postFrameCallbackDelayed(this, delay)
        }
    }

    fun applyPrefs() {
        pal = Themes.current()
        effect = Prefs.effect
        buildStatic()
        initParticles()
        setRunning(running)
        invalidate()
    }

    fun setRunning(r: Boolean) {
        running = r
        Choreographer.getInstance().removeFrameCallback(frameCb)
        lastNanos = 0L
        if (r && Prefs.animMode != 2 && isAttachedToWindow) {
            Choreographer.getInstance().postFrameCallback(frameCb)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running) setRunning(true)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCb)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        buildStatic()
        initParticles()
    }

    private fun rnd(a: Float, b: Float): Float = a + rand.nextFloat() * (b - a)

    // ---------------------------------------------------------------- cena parada

    private fun buildStatic() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        bg?.recycle()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = pal
        val wf = w.toFloat()
        val hf = h.toFloat()
        rand = Random(7)
        signs.clear()

        // ceu
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, 0f, 0f, hf * 0.78f,
            intArrayOf(p.skyTop, p.skyMid, p.skyBottom),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        // estrelas
        for (i in 0 until 55) {
            paint.color = Color.argb(60 + rand.nextInt(150), 255, 255, 255)
            c.drawCircle(rnd(0f, wf), rnd(0f, hf * 0.42f), rnd(0.5f, 1.5f) * density, paint)
        }

        // lua
        val mx = wf * 0.74f
        val my = hf * 0.17f
        val mr = wf * 0.085f
        paint.shader = RadialGradient(
            mx, my, mr * 3.2f,
            intArrayOf(Ui.alpha(p.accent, 70), 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(mx, my, mr * 3.2f, paint)
        paint.shader = null
        paint.color = 0xFFFFF1C9.toInt()
        c.drawCircle(mx, my, mr, paint)
        paint.color = Color.argb(70, 200, 170, 120)
        c.drawCircle(mx - mr * 0.3f, my - mr * 0.25f, mr * 0.2f, paint)
        c.drawCircle(mx + mr * 0.35f, my + mr * 0.2f, mr * 0.14f, paint)

        // predios distantes
        val farBase = hf * 0.66f
        paint.color = p.buildingFar
        var x = -wf * 0.02f
        while (x < wf) {
            val bw = rnd(wf * 0.05f, wf * 0.11f)
            val bh = rnd(hf * 0.07f, hf * 0.20f)
            c.drawRect(x, farBase - bh, x + bw, hf, paint)
            x += bw + rnd(0f, wf * 0.012f)
        }

        // pagode
        paint.color = Ui.alpha(p.building, 215)
        pagoda(c, wf * 0.2f, farBase + hf * 0.02f, wf * 0.17f)

        // predios do meio, janelas e placas de neon
        val signTexts = if (paint.hasGlyph("夜")) {
            listOf("ラーメン", "居酒屋", "夜", "猫", "茶", "酒", "夢")
        } else {
            listOf("BAR", "CAFE", "24H", "NEKO", "RAMEN")
        }
        val midBase = hf * 0.72f
        val cell = wf * 0.034f
        x = -wf * 0.03f
        var idx = 0
        while (x < wf) {
            val bw = rnd(wf * 0.13f, wf * 0.22f)
            val top = midBase - rnd(0f, hf * 0.14f)
            paint.color = p.building
            c.drawRect(x, top, x + bw, hf, paint)
            paint.color = Ui.alpha(p.accent2, 80)
            c.drawRect(x, top, x + bw, top + 2f * density, paint)

            var wy = top + cell * 0.8f
            while (wy < hf * 0.80f) {
                var wx = x + cell * 0.6f
                while (wx < x + bw - cell * 0.9f) {
                    val r = rand.nextFloat()
                    if (r < 0.34f) {
                        paint.color = Ui.alpha(p.window, if (r < 0.2f) 235 else 110)
                        c.drawRect(wx, wy, wx + cell * 0.5f, wy + cell * 0.62f, paint)
                    }
                    wx += cell
                }
                wy += cell * 1.25f
            }

            if (idx % 2 == 0) {
                val sw = wf * 0.075f
                val color = if ((idx / 2) % 2 == 0) p.accent else p.accent2
                neonSign(c, x + bw - sw * 1.15f, top + cell * 1.1f, sw, signTexts[(idx / 2) % signTexts.size], color)
            }
            x += bw + rnd(0f, wf * 0.01f)
            idx++
        }

        // fio de lanternas
        lanterns(c, wf, hf)

        // neblina colorida
        paint.shader = LinearGradient(
            0f, hf * 0.58f, 0f, hf * 0.82f,
            intArrayOf(0, Ui.alpha(p.accent2, 40), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, hf * 0.58f, wf, hf * 0.82f, paint)
        paint.shader = null

        // telhado em primeiro plano
        val roof = hf * 0.80f
        paint.color = p.building
        c.drawRect(0f, roof, wf, hf, paint)
        paint.color = Ui.alpha(p.accent, 90)
        c.drawRect(0f, roof, wf, roof + 2f * density, paint)
        paint.shader = LinearGradient(
            0f, roof, 0f, hf,
            intArrayOf(0, Color.argb(150, 0, 0, 0)), null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, roof, wf, hf, paint)
        paint.shader = null

        // escurecimento escolhido pela pessoa
        val dimA = Prefs.dim.coerceIn(0, 70) * 255 / 100
        if (dimA > 0) c.drawColor(Color.argb(dimA, 0, 0, 0))

        bg = bmp
    }

    private fun pagoda(c: Canvas, cx: Float, baseY: Float, unit: Float) {
        var y = baseY
        for (tier in 0 until 4) {
            val tw = unit * (1f - tier * 0.2f)
            val bodyH = unit * 0.2f
            val roofH = unit * 0.13f
            val over = unit * 0.1f
            c.drawRect(cx - tw * 0.36f, y - bodyH, cx + tw * 0.36f, y, paint)
            val top = y - bodyH
            val rp = Path()
            rp.moveTo(cx - tw / 2f - over, top)
            rp.lineTo(cx - tw * 0.3f, top - roofH)
            rp.lineTo(cx + tw * 0.3f, top - roofH)
            rp.lineTo(cx + tw / 2f + over, top)
            rp.close()
            c.drawPath(rp, paint)
            y = top - roofH
        }
        c.drawRect(cx - unit * 0.012f, y - unit * 0.18f, cx + unit * 0.012f, y, paint)
    }

    private fun neonSign(c: Canvas, x: Float, y: Float, sw: Float, text: String, color: Int) {
        val lineH = sw * 0.95f
        val pad = sw * 0.35f
        val sh = lineH * text.length + pad
        val r = RectF(x, y, x + sw, y + sh)
        val rad = sw * 0.18f

        glow.reset()
        glow.isAntiAlias = true
        glow.color = pal.building
        c.drawRoundRect(r, rad, rad, glow)

        glow.style = Paint.Style.STROKE
        glow.strokeWidth = sw * 0.1f
        glow.color = color
        glow.maskFilter = BlurMaskFilter(sw * 0.28f, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(r, rad, rad, glow)
        glow.maskFilter = null
        glow.strokeWidth = sw * 0.035f
        c.drawRoundRect(r, rad, rad, glow)

        txt.reset()
        txt.isAntiAlias = true
        txt.textAlign = Paint.Align.CENTER
        txt.textSize = sw * 0.62f
        txt.color = color
        txt.setShadowLayer(sw * 0.2f, 0f, 0f, color)
        for (i in text.indices) {
            c.drawText(text[i].toString(), x + sw / 2f, y + pad / 2f + lineH * (i + 0.78f), txt)
        }
        signs.add(Sign(r, rand.nextFloat() * 6.28f))
    }

    private fun lanterns(c: Canvas, wf: Float, hf: Float) {
        val y0 = hf * 0.43f
        val y1 = hf * 0.47f
        val cy = (y0 + y1) / 2f + hf * 0.07f
        val cxm = wf / 2f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.argb(220, 10, 6, 18)
        val path = Path()
        path.moveTo(0f, y0)
        path.quadTo(cxm, cy, wf, y1)
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        val n = 6
        val lw = wf * 0.05f
        val lh = wf * 0.065f
        for (i in 0 until n) {
            val tt = (i + 0.5f) / n
            val u = 1f - tt
            val lx = u * u * 0f + 2f * u * tt * cxm + tt * tt * wf
            val ly = u * u * y0 + 2f * u * tt * cy + tt * tt * y1

            paint.shader = RadialGradient(
                lx, ly + lh, lh * 1.7f,
                intArrayOf(Color.argb(90, 255, 110, 80), 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(lx, ly + lh, lh * 1.7f, paint)
            paint.shader = null

            paint.color = Color.argb(220, 10, 6, 18)
            c.drawRect(lx - 0.5f * density, ly, lx + 0.5f * density, ly + lh * 0.25f, paint)
            tmpRect.set(lx - lw / 2f, ly + lh * 0.25f, lx + lw / 2f, ly + lh * 1.25f)
            paint.color = 0xFFE8443A.toInt()
            c.drawOval(tmpRect, paint)
            paint.color = Color.argb(120, 60, 10, 10)
            for (k in 1..3) {
                val ry = tmpRect.top + tmpRect.height() * k / 4f
                c.drawRect(tmpRect.left + lw * 0.08f, ry, tmpRect.right - lw * 0.08f, ry + 1.2f * density, paint)
            }
            paint.color = Color.argb(230, 20, 10, 20)
            c.drawRect(lx - lw * 0.28f, tmpRect.top - 1f * density, lx + lw * 0.28f, tmpRect.top + 2.5f * density, paint)
            c.drawRect(lx - lw * 0.28f, tmpRect.bottom - 2.5f * density, lx + lw * 0.28f, tmpRect.bottom + 1f * density, paint)
        }
    }

    // ------------------------------------------------------------ particulas

    private fun initParticles() {
        val w = width.toFloat()
        val h = height.toFloat()
        val n = if (w <= 0f) 0 else when (effect) {
            0 -> 45
            1 -> 26
            2 -> 18
            3 -> 40
            else -> 0
        }
        px = FloatArray(n)
        py = FloatArray(n)
        pvx = FloatArray(n)
        pvy = FloatArray(n)
        psz = FloatArray(n)
        pph = FloatArray(n)
        rainPts = FloatArray(if (effect == 0) n * 4 else 0)
        for (i in 0 until n) {
            px[i] = prand.nextFloat() * w
            py[i] = prand.nextFloat() * h
            pph[i] = prand.nextFloat() * 6.28f
            when (effect) {
                0 -> {
                    pvy[i] = h * (0.9f + prand.nextFloat() * 0.6f)
                    pvx[i] = -pvy[i] * 0.12f
                    psz[i] = h * (0.025f + prand.nextFloat() * 0.03f)
                }
                1 -> {
                    pvy[i] = h * (0.04f + prand.nextFloat() * 0.05f)
                    pvx[i] = 8f * density
                    psz[i] = w * (0.008f + prand.nextFloat() * 0.008f)
                }
                2 -> {
                    pvx[i] = w * (0.02f + prand.nextFloat() * 0.04f)
                    psz[i] = w * (0.004f + prand.nextFloat() * 0.004f)
                }
                3 -> {
                    pvy[i] = h * (0.03f + prand.nextFloat() * 0.04f)
                    pvx[i] = 6f * density
                    psz[i] = w * (0.003f + prand.nextFloat() * 0.004f)
                }
            }
        }
    }

    private fun advance(dt: Float) {
        t += dt
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in px.indices) {
            when (effect) {
                0 -> {
                    py[i] += pvy[i] * dt
                    px[i] += pvx[i] * dt
                    if (py[i] > h) {
                        py[i] = -psz[i]
                        px[i] = prand.nextFloat() * (w * 1.15f)
                    }
                }
                1, 3 -> {
                    py[i] += pvy[i] * dt
                    px[i] += (pvx[i] * sin(t * 1.1f + pph[i]) * 3f) * dt
                    if (py[i] > h + 10f) {
                        py[i] = -10f
                        px[i] = prand.nextFloat() * w
                    }
                }
                2 -> {
                    px[i] += cos(t * 0.5f + pph[i]) * pvx[i] * dt
                    py[i] += sin(t * 0.4f + pph[i] * 1.3f) * pvx[i] * dt
                    if (px[i] < 0f) px[i] += w
                    if (px[i] > w) px[i] -= w
                    if (py[i] < h * 0.35f) py[i] = h * 0.35f
                    if (py[i] > h * 0.8f) py[i] = h * 0.8f
                }
            }
        }
    }

    private fun drawParticles(c: Canvas) {
        val n = px.size
        if (n == 0) return
        val p = pal
        paint.reset()
        paint.isAntiAlias = true
        when (effect) {
            0 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * density
                paint.color = Ui.alpha(p.accent, 95)
                for (i in 0 until n) {
                    rainPts[i * 4] = px[i]
                    rainPts[i * 4 + 1] = py[i]
                    rainPts[i * 4 + 2] = px[i] + pvx[i] * 0.03f
                    rainPts[i * 4 + 3] = py[i] + psz[i]
                }
                c.drawLines(rainPts, paint)
            }
            1 -> {
                paint.color = Ui.alpha(p.accent, 215)
                for (i in 0 until n) {
                    val s = psz[i]
                    c.save()
                    c.rotate(t * 40f + pph[i] * 57f, px[i], py[i])
                    tmpRect.set(px[i] - s, py[i] - s * 0.55f, px[i] + s, py[i] + s * 0.55f)
                    c.drawOval(tmpRect, paint)
                    c.restore()
                }
            }
            2 -> {
                for (i in 0 until n) {
                    val pulse = 0.5f + 0.5f * sin(t * 1.6f + pph[i] * 2f)
                    paint.color = Ui.alpha(p.window, (40 * pulse).toInt())
                    c.drawCircle(px[i], py[i], psz[i] * 3.2f, paint)
                    paint.color = Ui.alpha(p.window, (120 + 130 * pulse).toInt())
                    c.drawCircle(px[i], py[i], psz[i], paint)
                }
            }
            3 -> {
                paint.color = Color.argb(200, 255, 255, 255)
                for (i in 0 until n) c.drawCircle(px[i], py[i], psz[i], paint)
            }
        }
    }

    // ------------------------------------------------------------------ gato

    private fun drawCat(c: Canvas) {
        val cx = width * 0.66f
        val base = height * 0.80f
        val u = width * 0.13f
        val sway = sin(t * 1.3f)
        val p = pal

        catPaint.reset()
        catPaint.isAntiAlias = true
        catPaint.color = p.building

        // rabo
        catPaint.style = Paint.Style.STROKE
        catPaint.strokeWidth = 0.11f * u
        catPaint.strokeCap = Paint.Cap.ROUND
        tailPath.reset()
        tailPath.moveTo(cx - 0.22f * u, base - 0.1f * u)
        tailPath.cubicTo(
            cx - 0.95f * u, base + 0.02f * u,
            cx - 0.9f * u + sway * 0.12f * u, base - 0.5f * u,
            cx - 0.58f * u + sway * 0.25f * u, base - 0.8f * u
        )
        c.drawPath(tailPath, catPaint)
        catPaint.style = Paint.Style.FILL

        // corpo e cabeca
        tmpRect.set(cx - 0.32f * u, base - 0.85f * u, cx + 0.32f * u, base + 0.04f * u)
        c.drawOval(tmpRect, catPaint)
        c.drawCircle(cx + 0.02f * u, base - 1.0f * u, 0.24f * u, catPaint)

        // orelhas
        earPath.reset()
        earPath.moveTo(cx - 0.21f * u, base - 1.12f * u)
        earPath.lineTo(cx - 0.23f * u, base - 1.42f * u)
        earPath.lineTo(cx - 0.02f * u, base - 1.2f * u)
        earPath.close()
        earPath.moveTo(cx + 0.07f * u, base - 1.2f * u)
        earPath.lineTo(cx + 0.27f * u, base - 1.42f * u)
        earPath.lineTo(cx + 0.25f * u, base - 1.12f * u)
        earPath.close()
        c.drawPath(earPath, catPaint)

        // olhos que brilham de leve
        val blink = if (sin(t * 0.9f) > 0.985f) 0.2f else 1f
        catPaint.color = Ui.alpha(p.accent, 70)
        c.drawCircle(cx - 0.08f * u, base - 1.01f * u, 0.09f * u, catPaint)
        c.drawCircle(cx + 0.12f * u, base - 1.01f * u, 0.09f * u, catPaint)
        catPaint.color = p.accent
        tmpRect.set(cx - 0.11f * u, base - 1.01f * u - 0.035f * u * blink, cx - 0.05f * u, base - 1.01f * u + 0.035f * u * blink)
        c.drawOval(tmpRect, catPaint)
        tmpRect.set(cx + 0.09f * u, base - 1.01f * u - 0.035f * u * blink, cx + 0.15f * u, base - 1.01f * u + 0.035f * u * blink)
        c.drawOval(tmpRect, catPaint)
    }

    // ---------------------------------------------------------------- desenho

    override fun onDraw(canvas: Canvas) {
        val b = bg
        if (b != null) canvas.drawBitmap(b, 0f, 0f, null) else canvas.drawColor(pal.skyTop)

        // placas de neon piscando de leve
        paint.reset()
        for (s in signs) {
            var a = 0.8f + 0.2f * sin(t * 3f + s.phase)
            if (sin(t * 0.7f + s.phase * 3f) > 0.985f) a = 0.25f
            val over = ((1f - a) * 170f).toInt()
            if (over > 8) {
                paint.color = Ui.alpha(pal.building, over)
                canvas.drawRoundRect(s.r, 6f * density, 6f * density, paint)
            }
        }

        drawParticles(canvas)
        drawCat(canvas)
    }
}
