package br.sistemasuper.chess

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AutoService : AccessibilityService() {

    companion object {
        @Volatile var running = false
        @Volatile var status = "Serviço desligado"
    }

    private lateinit var wm: WindowManager
    private lateinit var ht: HandlerThread
    private lateinit var h: Handler
    private val ui = Handler(Looper.getMainLooper())
    private val shotExec = Executors.newSingleThreadExecutor()

    private var panel: View? = null
    private var btn: TextView? = null

    @Volatile private var on = false
    @Volatile private var gen = 0
    @Volatile private var delayMs = 1500
    @Volatile private var elo = 1500

    private var engine: Engine? = null
    private val img = ImgReader()

    // Estado da partida (so usado na thread de trabalho)
    private var lastKey: String? = null
    private var movedFrom: String? = null
    private var movedTo: String? = null
    private var movedAt = 0L
    private var attempts = 0
    private var lastMove = ""
    private var afterMine: CharArray? = null
    private val rights = mutableSetOf('K', 'Q', 'k', 'q')

    override fun onServiceConnected() {
        running = true
        status = "OFF"
        delayMs = Prefs.get(this, "delay", 1500)
        elo = Prefs.get(this, "elo", 1500)
        ht = HandlerThread("chess").also { it.start() }
        h = Handler(ht.looper)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onInterrupt() { setOn(false) }

    override fun onDestroy() {
        setOn(false)
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        engine?.close()
        if (::ht.isInitialized) ht.quitSafely()
        shotExec.shutdown()
        running = false
        status = "Serviço desligado"
        super.onDestroy()
    }

    // ---------------------------------------------------------------- liga/desliga

    private fun setOn(v: Boolean) {
        if (on == v) return
        on = v
        gen++
        h.removeCallbacksAndMessages(null)
        if (v) {
            resetGame()
            status = "Iniciando"
            val g = gen
            h.post { tick(g) }
        } else {
            engine?.stop() // interrompe a busca na hora
            status = "OFF"
        }
        ui.post {
            btn?.text = if (v) "ON" else "OFF"
            btn?.background = pill(if (v) 0xFF16A34A.toInt() else 0xFFB91C1C.toInt(), 10)
        }
    }

    private fun resetGame() {
        lastKey = null; movedFrom = null; movedTo = null; movedAt = 0L
        attempts = 0; lastMove = ""; afterMine = null
        rights.clear(); rights.addAll(listOf('K', 'Q', 'k', 'q'))
    }

    private fun tick(g: Int) {
        if (!on || g != gen) return
        var next = 800L
        try {
            next = step()
        } catch (t: Throwable) {
            status = "Erro: ${t.javaClass.simpleName}"
            next = 1500L
        }
        if (on && g == gen) h.postDelayed({ tick(g) }, next)
    }

    /** Espera em passos curtos; devolve false se foi desligado no meio. */
    private fun sleepI(ms: Long): Boolean {
        var left = ms
        while (left > 0) {
            if (!on) return false
            val s = minOf(left, 25L)
            Thread.sleep(s)
            left -= s
        }
        return on
    }

    // ---------------------------------------------------------------- um passo do jogo

    private fun step(): Long {
        val snap = readBoard() ?: return 900
        val b = snap.b
        val key = String(b)
        val white = snap.geo.whiteBottom

        if (key == Chess.START && lastKey != Chess.START) {
            resetGame()
            if (!white) movedTo = key // pretas: espera o adversario abrir
        }
        Chess.updateRights(b, rights)
        val stable = key == lastKey
        lastKey = key

        if (key == movedTo) { status = "Aguardando adversário"; return 700 }

        val mv: String
        var retry = false
        if (key == movedFrom) {
            // o lance nao foi aceito pelo jogo: tenta de novo com outro tipo de gesto
            if (System.currentTimeMillis() - movedAt < 2500) return 500
            if (attempts >= 4) { status = "Jogada não registrada"; return 1500 }
            mv = lastMove
            retry = true
        } else {
            val t = movedTo
            if (t != null && Chess.diff(key, t) > 4) { status = "Posição incoerente"; return 700 }
            if (!stable) return 400
            status = "Calculando"
            mv = think(key, b, white) ?: return 1200
            if (!on) return 0
            if (!sleepI(delayMs.toLong())) return 0 // atraso configurado
        }

        val ok = tapMove(mv, snap.geo, if (retry) attempts else 0)
        if (!on) return 0
        attempts = if (retry) attempts + 1 else 1
        movedAt = System.currentTimeMillis()
        if (!retry) {
            val n = Chess.apply(b, mv)
            movedFrom = key
            movedTo = String(n)
            afterMine = n
            lastMove = mv
        }
        if (ok && mv.length == 5 && sleepI(500)) {
            rootInActiveWindow?.let { AxReader.clickWord(it, AxReader.wordsFor(mv[4])) }
        }
        status = if (ok) "Jogou $mv" else "Falha ao tocar"
        return 900
    }

    private fun think(key: String, b: CharArray, white: Boolean): String? {
        val e = engine ?: Engine(this).also { engine = it }
        val fen = Chess.fen(b, white, Chess.rightsStr(rights), Chess.ep(afterMine, b, white))
        val mv = e.best(fen, elo, 400)
        if (!on) return null
        if (mv == null) { status = "Motor indisponível"; return null }
        if (mv == "(none)") { movedTo = key; status = "Fim de jogo"; return null }
        val p = b[Chess.sq(mv.substring(0, 2))]
        if (p == '.' || p.isUpperCase() != white) { status = "Leitura inconsistente"; return null }
        return mv
    }

    // ---------------------------------------------------------------- leitura do tabuleiro

    private fun readBoard(): Snap? {
        val root = rootInActiveWindow
        if (root == null) { status = "Sem janela ativa"; return null }
        if (root.packageName == packageName) { status = "Abra o jogo de xadrez"; return null }
        AxReader.scan(root)?.let { return it }
        if (Build.VERSION.SDK_INT >= 30) {
            val rect = AxReader.boardRect(root, resources.displayMetrics.widthPixels)
            if (rect == null) { status = "Tabuleiro não encontrado"; return null }
            val bmp = shot()
            if (bmp == null) { status = "Sem captura"; return null }
            val s = img.read(bmp, rect)
            bmp.recycle()
            if (s == null) status = "Calibrando: inicie uma partida (posição inicial)"
            return s
        }
        status = "Jogo não expõe o tabuleiro (Android 10 não tem captura)"
        return null
    }

    /** Captura em memoria pelo proprio servico de acessibilidade; nunca vai para o disco. */
    private fun shot(): Bitmap? {
        var out: Bitmap? = null
        val latch = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, shotExec, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    out = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    hb.close()
                } catch (_: Throwable) {
                }
                latch.countDown()
            }

            override fun onFailure(code: Int) { latch.countDown() }
        })
        latch.await(2, TimeUnit.SECONDS)
        return out
    }

    // ---------------------------------------------------------------- toques

    /** mode par = toca origem e destino; mode impar = arrasta. */
    private fun tapMove(mv: String, g: Geo, mode: Int): Boolean {
        val f = Chess.sq(mv.substring(0, 2))
        val t = Chess.sq(mv.substring(2, 4))
        val fx = g.x(f % 8); val fy = g.y(f / 8)
        val tx = g.x(t % 8); val ty = g.y(t / 8)
        if (mode % 2 == 1) {
            val p = Path().apply { moveTo(fx, fy); lineTo(tx, ty) }
            return gesture(p, 260)
        }
        if (!gesture(Path().apply { moveTo(fx, fy) }, 50)) return false
        if (!sleepI(180)) return false
        return gesture(Path().apply { moveTo(tx, ty) }, 50)
    }

    private fun gesture(p: Path, dur: Long): Boolean {
        var ok = false
        val latch = CountDownLatch(1)
        val d = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build()
        val accepted = dispatchGesture(d, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(g: GestureDescription?) { latch.countDown() }
        }, null)
        if (!accepted) return false
        latch.await(2, TimeUnit.SECONDS)
        return ok
    }

    // ---------------------------------------------------------------- menu flutuante

    private fun pill(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = r * resources.displayMetrics.density
    }

    private inner class Sb(val change: (Int) -> Unit, val done: (Int) -> Unit) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) = change(p)
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) = done(s.progress)
    }

    private fun showPanel() {
        val ctx = ContextThemeWrapper(this, R.style.PanelTheme)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(8))
            background = pill(0xE6141C26.toInt(), 14)
        }
        val lp = WindowManager.LayoutParams(
            dp(190), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Settings.canDrawOverlays(this)) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.get(this@AutoService, "px", dp(8))
            y = Prefs.get(this@AutoService, "py", dp(120))
        }

        // alca de arrastar
        val grip = FrameLayout(ctx).apply {
            addView(View(ctx).apply { background = pill(0x66FFFFFF, 3) }, FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER))
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt()
                        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
                    }
                    MotionEvent.ACTION_UP -> {
                        Prefs.set(this@AutoService, "px", lp.x); Prefs.set(this@AutoService, "py", lp.y)
                    }
                }
                true
            }
        }
        root.addView(grip, LinearLayout.LayoutParams(-1, dp(18)))

        val b = TextView(ctx).apply {
            text = "OFF"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = pill(0xFFB91C1C.toInt(), 10)
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { setOn(!on) }
        }
        btn = b
        root.addView(b, LinearLayout.LayoutParams(-1, -2))

        fun label() = TextView(ctx).apply { setTextColor(0xFFCBD5E1.toInt()); textSize = 11f; setPadding(0, dp(6), 0, 0) }

        val dLbl = label().apply { text = "Atraso: $delayMs ms" }
        val dBar = SeekBar(ctx).apply {
            max = 50
            progress = delayMs / 100
            setOnSeekBarChangeListener(Sb(
                { p -> delayMs = p * 100; dLbl.text = "Atraso: $delayMs ms" },
                { Prefs.set(this@AutoService, "delay", delayMs) }
            ))
        }
        // Elo 600..3190 em passos de 10; o fim da barra = forca maxima
        val eLbl = label()
        fun eloText() { eLbl.text = if (elo >= 3190) "Elo: Máx" else "Elo: $elo" }
        eloText()
        val eBar = SeekBar(ctx).apply {
            max = 259
            progress = ((elo - 600) / 10).coerceIn(0, 259)
            setOnSeekBarChangeListener(Sb(
                { p -> elo = 600 + p * 10; eloText() },
                { Prefs.set(this@AutoService, "elo", elo) }
            ))
        }
        root.addView(dLbl); root.addView(dBar)
        root.addView(eLbl); root.addView(eBar)

        panel = root
        try { wm.addView(root, lp) } catch (e: Exception) { status = "Falha ao criar menu" }
    }
}
