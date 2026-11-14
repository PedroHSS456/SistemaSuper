package br.sistemasuper.yoru

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import java.io.File

object Ui {
    val fontNames = listOf("Fina", "Suave", "Classica", "Casual", "Maquina", "Minha fonte")
    val shapeNames = listOf("Circulo", "Squircle", "Arredondado", "Original")
    val effectNames = listOf("Chuva neon", "Petalas de sakura", "Vagalumes", "Neve", "Nenhum")
    val animNames = listOf("Normal", "Economia", "Parada")

    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun dpi(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun alpha(color: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(color)
        d.cornerRadius = radiusPx
        if (strokePx > 0) d.setStroke(strokePx, strokeColor)
        return d
    }

    fun panelColor(p: Palette): Int = alpha(p.panel, (Prefs.glass * 255) / 100)

    private var cachedFont = -1
    private var cachedFace: Typeface = Typeface.DEFAULT

    fun customFontFile(ctx: Context): File = File(ctx.filesDir, "custom_font")

    fun typeface(ctx: Context): Typeface {
        val f = Prefs.font
        if (f == cachedFont && f != 5) return cachedFace
        val face = when (f) {
            0 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            1 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            2 -> Typeface.create("serif", Typeface.NORMAL)
            3 -> Typeface.create("casual", Typeface.NORMAL)
            4 -> Typeface.create("monospace", Typeface.NORMAL)
            else -> try {
                val file = customFontFile(ctx)
                if (file.exists()) Typeface.createFromFile(file) else Typeface.DEFAULT
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }
        cachedFont = f
        cachedFace = face
        return face
    }

    fun invalidateFont() {
        cachedFont = -1
    }
}
