package br.sistemasuper.chess

import kotlin.math.abs

/** Posicao de tela de cada casa: centro da casa (arquivo f, fila r) = (ox + sx*f, oy + sy*r). */
class Geo(val ox: Float, val oy: Float, val sx: Float, val sy: Float) {
    fun x(f: Int) = ox + sx * f
    fun y(r: Int) = oy + sy * r
    val whiteBottom get() = sy < 0

    companion object {
        fun fromRect(l: Int, t: Int, size: Int, whiteBottom: Boolean): Geo {
            val c = size / 8f
            return if (whiteBottom) Geo(l + c / 2, t + size - c / 2, c, -c)
            else Geo(l + size - c / 2, t + c / 2, -c, c)
        }
    }
}

/** Tabuleiro lido: indice = fila*8 + arquivo (fila 0 = fila 1). Maiuscula = brancas. */
class Snap(val b: CharArray, val geo: Geo)

object Chess {
    val START = "RNBQKBNR" + "PPPPPPPP" + ".".repeat(32) + "pppppppp" + "rnbqkbnr"

    fun sq(s: String) = (s[1] - '1') * 8 + (s[0] - 'a')

    fun fen(b: CharArray, white: Boolean, rights: String, ep: String): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var e = 0
            for (f in 0..7) {
                val c = b[r * 8 + f]
                if (c == '.') e++ else {
                    if (e > 0) { sb.append(e); e = 0 }
                    sb.append(c)
                }
            }
            if (e > 0) sb.append(e)
            if (r > 0) sb.append('/')
        }
        sb.append(if (white) " w " else " b ")
        sb.append(if (rights.isEmpty()) "-" else rights).append(' ')
        sb.append(if (ep.isEmpty()) "-" else ep).append(" 0 1")
        return sb.toString()
    }

    /** Aplica o lance (inclui roque, en passant e promocao) e devolve o novo tabuleiro. */
    fun apply(b: CharArray, mv: String): CharArray {
        val n = b.copyOf()
        val from = sq(mv.substring(0, 2))
        val to = sq(mv.substring(2, 4))
        var p = n[from]
        if (p == 'K' || p == 'k') {
            val rook = if (p == 'K') 'R' else 'r'
            if (to - from == 2) { n[from + 3] = '.'; n[from + 1] = rook }
            else if (from - to == 2) { n[from - 4] = '.'; n[from - 1] = rook }
        }
        if ((p == 'P' || p == 'p') && from % 8 != to % 8 && n[to] == '.') n[(from / 8) * 8 + to % 8] = '.'
        if (mv.length > 4) p = if (p.isUpperCase()) mv[4].uppercaseChar() else mv[4].lowercaseChar()
        n[to] = p
        n[from] = '.'
        return n
    }

    /** Direitos de roque: so podem ser perdidos, nunca recuperados durante a partida. */
    fun updateRights(b: CharArray, rights: MutableSet<Char>) {
        if (b[4] != 'K') { rights.remove('K'); rights.remove('Q') }
        if (b[7] != 'R') rights.remove('K')
        if (b[0] != 'R') rights.remove('Q')
        if (b[60] != 'k') { rights.remove('k'); rights.remove('q') }
        if (b[63] != 'r') rights.remove('k')
        if (b[56] != 'r') rights.remove('q')
    }

    fun rightsStr(r: Set<Char>) = "KQkq".filter { it in r }

    /** Casa de en passant se o adversario acabou de avancar um peao duas casas. */
    fun ep(prev: CharArray?, cur: CharArray, ourWhite: Boolean): String {
        if (prev == null) return ""
        for (f in 0..7) {
            if (ourWhite) {
                if (prev[48 + f] == 'p' && cur[48 + f] != 'p' && cur[32 + f] == 'p' && prev[32 + f] != 'p') return "${'a' + f}6"
            } else {
                if (prev[8 + f] == 'P' && cur[8 + f] != 'P' && cur[24 + f] == 'P' && prev[24 + f] != 'P') return "${'a' + f}3"
            }
        }
        return ""
    }

    fun diff(a: String, b: String): Int {
        var n = 0
        for (i in 0 until minOf(a.length, b.length)) if (a[i] != b[i]) n++
        return n + abs(a.length - b.length)
    }
}
