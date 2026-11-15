package br.sistemasuper.chess

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Stockfish 17.1 embutido no APK (libstockfish.so na pasta nativa do app), falado via UCI.
 * Elo 1320..3189: UCI_LimitStrength + UCI_Elo do proprio Stockfish.
 * Elo >= 3190: forca maxima.
 * Elo < 1320: o Stockfish nao baixa disso; aproximamos escolhendo entre os melhores lances
 * (MultiPV) com chance crescente de escolher um lance pior.
 */
class Engine(private val ctx: Context) {
    private var proc: Process? = null
    private var wr: OutputStreamWriter? = null
    private val q = LinkedBlockingQueue<String>()
    private val lock = Any()
    @Volatile private var searching = false

    @Synchronized
    fun ensure(): Boolean {
        if (proc?.isAlive == true) return true
        val f = File(ctx.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!f.exists()) return false
        q.clear()
        val p = ProcessBuilder(f.absolutePath).redirectErrorStream(true).start()
        proc = p
        wr = OutputStreamWriter(p.outputStream)
        val rd = BufferedReader(InputStreamReader(p.inputStream))
        Thread {
            try {
                while (true) q.put(rd.readLine() ?: break)
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
        send("uci")
        if (!waitFor("uciok", 8000)) return false
        send("setoption name Threads value 1")
        send("setoption name Hash value 16")
        send("isready")
        return waitFor("readyok", 8000)
    }

    private fun send(s: String) {
        synchronized(lock) {
            try { wr?.write(s + "\n"); wr?.flush() } catch (_: Exception) {}
        }
    }

    private fun waitFor(token: String, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val l = q.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (l.startsWith(token)) return true
        }
        return false
    }

    /** Interrompe a busca em andamento (botao OFF). */
    fun stop() { if (searching) send("stop") }

    fun close() {
        send("quit")
        proc?.destroy()
        proc = null
    }

    fun best(fen: String, elo: Int, ms: Int): String? {
        if (!ensure()) return null
        q.clear()
        val low = elo < 1320
        send("setoption name UCI_LimitStrength value ${elo in 1320..3189}")
        if (elo in 1320..3189) send("setoption name UCI_Elo value $elo")
        send("setoption name MultiPV value ${if (low) 8 else 1}")
        send("position fen $fen")
        searching = true
        send(if (low) "go depth 7" else "go movetime $ms")

        val lines = HashMap<Int, Pair<Int, String>>() // multipv -> (profundidade, lance)
        var best: String? = null
        val end = System.currentTimeMillis() + ms + 8000
        while (System.currentTimeMillis() < end) {
            val l = q.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (l.startsWith("bestmove")) { best = l.split(' ').getOrNull(1); break }
            if (low && l.startsWith("info") && l.contains(" multipv ") && l.contains(" pv ")) {
                val t = l.split(' ')
                val d = t.getOrNull(t.indexOf("depth") + 1)?.toIntOrNull() ?: continue
                val k = t.getOrNull(t.indexOf("multipv") + 1)?.toIntOrNull() ?: continue
                val m = t.getOrNull(t.indexOf("pv") + 1) ?: continue
                val old = lines[k]
                if (old == null || d >= old.first) lines[k] = d to m
            }
        }
        searching = false
        if (best == null) { send("stop"); return null }
        if (low && best != "(none)" && lines.isNotEmpty()) {
            val moves = lines.toSortedMap().values.map { it.second }
            val p = (0.3 + (1320 - elo) / 720.0 * 0.55).coerceIn(0.3, 0.85)
            var k = 0
            while (k < moves.size - 1 && Random.nextDouble() < p) k++
            return moves[k]
        }
        return best
    }
}
