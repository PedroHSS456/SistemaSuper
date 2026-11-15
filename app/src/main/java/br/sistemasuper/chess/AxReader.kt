package br.sistemasuper.chess

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Leitura do tabuleiro pela arvore de acessibilidade: so funciona se o jogo expuser cada peca
 * com descricao do tipo "white pawn e2" / "peao branco e2" (en, pt, es).
 */
object AxReader {
    private val sqRe = Regex("(?<![\\p{L}\\p{N}])([a-h])([1-8])(?![\\p{L}\\p{N}])")
    private val splitRe = Regex("[^\\p{L}]+")

    private val typeWords = mapOf(
        "king" to 'k', "rei" to 'k', "rey" to 'k',
        "queen" to 'q', "rainha" to 'q', "dama" to 'q', "reina" to 'q',
        "rook" to 'r', "torre" to 'r',
        "bishop" to 'b', "bispo" to 'b', "alfil" to 'b',
        "knight" to 'n', "cavalo" to 'n', "caballo" to 'n',
        "pawn" to 'p', "peão" to 'p', "peao" to 'p', "peón" to 'p', "peon" to 'p'
    )
    private val whiteW = setOf("white", "branca", "branco", "blanca", "blanco")
    private val blackW = setOf("black", "preta", "preto", "negra", "negro")

    fun wordsFor(c: Char): Set<String> = typeWords.filterValues { it == c.lowercaseChar() }.keys

    private inline fun walk(root: AccessibilityNodeInfo, f: (AccessibilityNodeInfo) -> Unit) {
        val st = ArrayDeque<AccessibilityNodeInfo>()
        st.addLast(root)
        var n = 0
        while (st.isNotEmpty() && n < 6000) {
            val x = st.removeLast()
            n++
            f(x)
            for (i in 0 until x.childCount) x.getChild(i)?.let { st.addLast(it) }
        }
    }

    private fun desc(n: AccessibilityNodeInfo): String {
        val a = n.contentDescription?.toString() ?: ""
        val b = n.text?.toString() ?: ""
        return (a + " " + b).trim().lowercase()
    }

    private class P(val f: Int, val r: Int, val c: Char, val cx: Float, val cy: Float)

    fun scan(root: AccessibilityNodeInfo): Snap? {
        val ps = ArrayList<P>()
        val rect = Rect()
        walk(root) { n ->
            val d = desc(n)
            if (d.isNotEmpty() && d.length < 80) {
                val m = sqRe.find(d)
                if (m != null) {
                    val toks = d.split(splitRe).filter { it.isNotEmpty() }
                    val t = toks.firstNotNullOfOrNull { typeWords[it] }
                    val white = toks.any { it in whiteW }
                    val black = toks.any { it in blackW }
                    if (t != null && (white || black)) {
                        n.getBoundsInScreen(rect)
                        ps.add(
                            P(
                                m.groupValues[1][0] - 'a', m.groupValues[2][0] - '1',
                                if (white) t.uppercaseChar() else t,
                                rect.exactCenterX(), rect.exactCenterY()
                            )
                        )
                    }
                }
            }
        }
        if (ps.size < 4) return null
        val fx = fit(ps.map { it.f to it.cx }) ?: return null
        val fy = fit(ps.map { it.r to it.cy }) ?: return null
        val ax = abs(fx.second)
        val ay = abs(fy.second)
        if (ax < 20f || ay < 20f || ax / ay > 1.3f || ay / ax > 1.3f) return null
        val b = CharArray(64) { '.' }
        for (p in ps) b[p.r * 8 + p.f] = p.c
        return Snap(b, Geo(fx.first, fy.first, fx.second, fy.second))
    }

    /** Regressao linear y = a + s*x; devolve (a, s). */
    private fun fit(v: List<Pair<Int, Float>>): Pair<Float, Float>? {
        val n = v.size
        val mx = v.sumOf { it.first.toDouble() } / n
        val my = v.sumOf { it.second.toDouble() } / n
        var sxx = 0.0
        var sxy = 0.0
        for ((x, y) in v) { sxx += (x - mx) * (x - mx); sxy += (x - mx) * (y - my) }
        if (sxx < 1e-6) return null
        val s = sxy / sxx
        return Pair((my - s * mx).toFloat(), s.toFloat())
    }

    /** Menor no quadrado com largura >= 70% da tela: candidato a area do tabuleiro. */
    fun boardRect(root: AccessibilityNodeInfo, screenW: Int): Rect? {
        var best: Rect? = null
        val r = Rect()
        walk(root) { n ->
            n.getBoundsInScreen(r)
            val w = r.width()
            val h = r.height()
            if (w >= screenW * 0.7f && w <= screenW + 4 && abs(w - h) <= w * 0.03f) {
                val cur = best
                if (cur == null || w < cur.width()) best = Rect(r)
            }
        }
        return best
    }

    /** Clica no primeiro elemento cujo texto contem uma das palavras (ex.: escolha de promocao). */
    fun clickWord(root: AccessibilityNodeInfo, words: Set<String>): Boolean {
        var hit: AccessibilityNodeInfo? = null
        walk(root) { n ->
            if (hit == null) {
                val toks = desc(n).split(splitRe)
                if (toks.any { it in words }) hit = n
            }
        }
        var x: AccessibilityNodeInfo? = hit
        while (x != null) {
            if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            x = x.parent
        }
        return false
    }
}
