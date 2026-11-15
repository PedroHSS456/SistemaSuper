package br.sistemasuper.chess

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Tela minima: so atalhos para as duas permissoes e o estado atual. */
class MainActivity : Activity() {
    private lateinit var info: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val ov = Settings.canDrawOverlays(this@MainActivity)
            info.text = buildString {
                append(if (ov) "✔ Sobreposição permitida\n" else "✘ Sobreposição não permitida\n")
                append(if (AutoService.running) "✔ Serviço de acessibilidade ativo\n" else "✘ Serviço de acessibilidade desligado\n")
                append(if (Build.VERSION.SDK_INT >= 30) "✔ Leitura por imagem disponível (Android 11+)\n" else "• Leitura por imagem indisponível (Android 10)\n")
                append("\nEstado: ").append(AutoService.status)
            }
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val d = resources.displayMetrics.density
        val p = (20 * d).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p * 2, p, p)
            setBackgroundColor(0xFF0F1720.toInt())
        }
        fun txt(t: String, sz: Float, c: Int) = TextView(this).apply { text = t; textSize = sz; setTextColor(c); setPadding(0, 0, 0, p / 2) }
        col.addView(txt("Chess Auto Player", 22f, 0xFFE2E8F0.toInt()))
        col.addView(txt("1. Permita a sobreposição\n2. Ative o serviço de acessibilidade\n3. Abra o jogo, deixe a posição inicial na tela e toque ON no menu flutuante", 14f, 0xFFCBD5E1.toInt()))
        col.addView(Button(this).apply {
            text = "Permitir sobreposição"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        col.addView(Button(this).apply {
            text = "Ativar serviço de acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        info = txt("", 14f, 0xFF94A3B8.toInt()).apply { setPadding(0, p, 0, 0) }
        col.addView(info)
        setContentView(col)
    }

    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(refresh) }
}
