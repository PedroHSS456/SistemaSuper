package br.sistemasuper.chess

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs

/**
 * Reconhecimento por imagem, so em memoria (nada e salvo). Como nao ha modelo pre-treinado,
 * o app aprende o desenho das pecas sozinho na posicao inicial de cada partida: as 32 pecas
 * estao em lugares conhecidos, entao viram os "modelos" usados para classificar o resto.
 * Por isso e preciso ligar o ON com a posicao inicial visivel (ao menos uma vez por sessao).
 */
class ImgReader {
    private class Tpl(val m: FloatArray, val lum: Float)
    private class Cell(val occ: Boolean, val m: FloatArray, val lum: Float)

    private var tpl: Map<Char, Tpl>? = null
    private var whiteBottom = true

    fun read(bmp: Bitmap, r: Rect): Snap? {
        val size = minOf(r.width(), r.height())
        if (size < 200 || r.left < 0 || r.top < 0 || r.left + size > bmp.width || r.top + size > bmp.height) return null
        val crop = Bitmap.createBitmap(bmp, r.left, r.top, size, size)
        val sc = Bitmap.createScaledBitmap(crop, 128, 128, true)
        val px = IntArray(128 * 128)
        sc.getPixels(px, 0, 128, 0, 0, 128, 128)
        val cells = Array(64) { cell(px, it / 8, it % 8) }
        if (isStart(cells)) learn(cells)
        val tp = tpl ?: return null
        val b = CharArray(64) { '.' }
        for (i in 0..7) for (j in 0..7) {
            val c = cells[i * 8 + j]
            if (!c.occ) continue
            var best = '.'
            var bd = Float.MAX_VALUE
            for ((ch, t) in tp) {
                var d = 0f
                for (k in 0 until 256) d += abs(c.m[k] - t.m[k])
                d = d / 256f + 0.8f * abs(c.lum - t.lum) / 255f
                if (d < bd) { bd = d; best = ch }
            }
            val f = if (whiteBottom) j else 7 - j
            val rk = if (whiteBottom) 7 - i else i
            b[rk * 8 + f] = best
        }
        return Snap(b, Geo.fromRect(r.left, r.top, size, whiteBottom))
    }

    /** Analisa uma casa (16x16 px): fundo pelos cantos, mascara de "primeiro plano", luminosidade. */
    private fun cell(px: IntArray, i: Int, j: Int): Cell {
        val x0 = j * 16
        val y0 = i * 16
        val rs = IntArray(16)
        val gs = IntArray(16)
        val bs = IntArray(16)
        var k = 0
        for (cy in intArrayOf(1, 2, 13, 14)) for (cx in intArrayOf(1, 2, 13, 14)) {
            val p = px[(y0 + cy) * 128 + x0 + cx]
            rs[k] = (p shr 16) and 255; gs[k] = (p shr 8) and 255; bs[k] = p and 255
            k++
        }
        rs.sort(); gs.sort(); bs.sort()
        val br = rs[8]
        val bg = gs[8]
        val bb = bs[8]
        val m = FloatArray(256)
        var n = 0
        var ls = 0f
        for (v in 0 until 16) for (u in 0 until 16) {
            val p = px[(y0 + v) * 128 + x0 + u]
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val b = p and 255
            if (maxOf(abs(r - br), abs(g - bg), abs(b - bb)) > 48) {
                m[v * 16 + u] = 1f
                n++
                ls += 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        return Cell(n > 256 * 0.12f, m, if (n > 0) ls / n else 0f)
    }

    private fun isStart(c: Array<Cell>): Boolean {
        for (i in 0..7) for (j in 0..7) {
            if (c[i * 8 + j].occ != (i <= 1 || i >= 6)) return false
        }
        return true
    }

    private fun learn(c: Array<Cell>) {
        fun lum(rows: IntRange): Float {
            var s = 0f
            var n = 0
            for (i in rows) for (j in 0..7) { s += c[i * 8 + j].lum; n++ }
            return s / n
        }
        val wb = lum(6..7) > lum(0..1) // lado de baixo mais claro = brancas
        whiteBottom = wb
        // Girado 180 graus (pretas embaixo) a ordem da esquerda p/ direita fica R N B K Q B N R.
        val back = if (wb) "RNBQKBNR" else "RNBKQBNR"
        val acc = HashMap<Char, FloatArray>()
        val cnt = HashMap<Char, Int>()
        val ls = HashMap<Char, Float>()
        for (i in intArrayOf(0, 1, 6, 7)) for (j in 0..7) {
            val whiteRow = if (wb) i >= 6 else i <= 1
            val pawn = i == 1 || i == 6
            val t = if (pawn) 'P' else back[j]
            val key = if (whiteRow) t else t.lowercaseChar()
            val a = acc.getOrPut(key) { FloatArray(256) }
            val cl = c[i * 8 + j]
            for (x in 0 until 256) a[x] += cl.m[x]
            cnt[key] = (cnt[key] ?: 0) + 1
            ls[key] = (ls[key] ?: 0f) + cl.lum
        }
        tpl = acc.mapValues { (k, a) ->
            val n = cnt[k]!!
            Tpl(FloatArray(256) { a[it] / n }, ls[k]!! / n)
        }
    }
}
