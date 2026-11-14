package br.sistemasuper.yoru

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Desenha o icone do app dentro da forma escolhida (circulo, squircle, etc). */
object IconMaker {

    fun make(d: Drawable, size: Int, shape: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()

        if (shape == 3) {
            d.setBounds(0, 0, size, size)
            d.draw(c)
            return bmp
        }

        val layer = c.saveLayer(0f, 0f, s, s, null)

        if (d is AdaptiveIconDrawable) {
            // As camadas do icone adaptativo tem 108dp; a parte visivel tem 72dp.
            val big = (size * 1.5f).toInt()
            val off = -((big - size) / 2)
            val bg = d.background
            if (bg != null) {
                bg.setBounds(off, off, off + big, off + big)
                bg.draw(c)
            }
            val fg = d.foreground
            if (fg != null) {
                fg.setBounds(off, off, off + big, off + big)
                fg.draw(c)
            }
        } else {
            val plate = Paint(Paint.ANTI_ALIAS_FLAG)
            plate.color = 0xFF2A2440.toInt()
            c.drawRect(0f, 0f, s, s, plate)
            val inset = (size * 0.14f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(c)
        }

        val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        mask.color = 0xFFFFFFFF.toInt()
        c.drawPath(shapePath(s, shape), mask)
        c.restoreToCount(layer)
        return bmp
    }

    private fun shapePath(s: Float, shape: Int): Path {
        val p = Path()
        when (shape) {
            0 -> p.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
            1 -> {
                val r = s / 2.0
                val steps = 72
                for (i in 0..steps) {
                    val t = 2.0 * PI * i / steps
                    val cs = cos(t)
                    val sn = sin(t)
                    val x = r + r * (if (cs < 0) -1.0 else 1.0) * abs(cs).pow(0.5)
                    val y = r + r * (if (sn < 0) -1.0 else 1.0) * abs(sn).pow(0.5)
                    if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                }
                p.close()
            }
            else -> p.addRoundRect(RectF(0f, 0f, s, s), s * 0.26f, s * 0.26f, Path.Direction.CW)
        }
        return p
    }
}
