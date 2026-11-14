package br.sistemasuper.yoru

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.FileOutputStream

/** Tela de personalizacao. Tambem e o app que aparece na lista, para abrir e desinstalar com seguranca. */
class SettingsActivity : Activity() {

    private lateinit var box: LinearLayout
    private val reqWallpaper = 1
    private val reqFont = 2

    private val pal get() = Themes.current()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        scroll.addView(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)
        build()
    }

    private fun build() {
        val p = pal
        val bg = p.skyTop or (0xFF shl 24)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        (box.parent as View).setBackgroundColor(bg)
        box.removeAllViews()
        box.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 20), Ui.dpi(this, 18), Ui.dpi(this, 40))

        text("Yoru Launcher", 26f, p.text, true)
        text("Personalizacao. As mudancas aparecem na tela inicial.", 13f, p.textDim, false)

        title("Como usar")
        row("Definir como tela inicial", "Abre a escolha do Android. Voce pode voltar ao launcher antigo a qualquer momento nas mesmas configuracoes.") {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        row("Desinstalar este launcher", "Abre as informacoes do app para remover. O Android volta ao launcher anterior sozinho.") {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }

        title("Tema e fundo")
        row("Tema de cores", pal.name) {
            pick("Tema de cores", Themes.all.map { it.name }, Prefs.theme) {
                Prefs.theme = it
                build()
            }
        }
        row("Efeito animado", Ui.effectNames[Prefs.effect.coerceIn(0, 4)]) {
            pick("Efeito animado", Ui.effectNames, Prefs.effect) {
                Prefs.effect = it
                build()
            }
        }
        row("Animacao", Ui.animNames[Prefs.animMode.coerceIn(0, 2)] + "  (Economia e Parada gastam menos bateria)") {
            pick("Animacao", Ui.animNames, Prefs.animMode) {
                Prefs.animMode = it
                build()
            }
        }
        slider("Escurecer fundo", 0, 70, Prefs.dim, "%") { Prefs.dim = it }

        title("Wallpaper animado (GIF, video ou imagem)")
        row("Escolher da galeria", "Aceita GIF, MP4/WebM e imagens") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            startActivityForResult(i, reqWallpaper)
        }
        val bundled = Media.bundled(this)
        if (bundled.isNotEmpty()) {
            row("Wallpapers que vieram no app", "${bundled.size} disponiveis") {
                pick("Wallpapers", bundled, -1) { idx ->
                    val ok = Media.fromAsset(this, bundled[idx])
                    toast(if (ok) "Wallpaper aplicado" else "Nao foi possivel aplicar")
                    build()
                }
            }
        }
        row("Voltar para a cena desenhada", if (Prefs.wallpaperMode == 1) "Usando seu wallpaper agora" else "Ja esta em uso") {
            Media.clear(this)
            Prefs.wallpaperMode = 0
            build()
        }

        title("Relogio")
        toggle("Formato 24 horas", Prefs.clock24) { Prefs.clock24 = it }
        slider("Tamanho do relogio", 40, 110, Prefs.clockSize, "") { Prefs.clockSize = it }
        row("Frase abaixo do relogio", Prefs.greeting.ifEmpty { "Automatica (bom dia, boa noite...)" }) {
            val et = EditText(this)
            et.setText(Prefs.greeting)
            et.hint = "Deixe vazio para automatica"
            AlertDialog.Builder(this).setTitle("Frase").setView(et)
                .setPositiveButton("Salvar") { _, _ ->
                    Prefs.greeting = et.text.toString()
                    build()
                }.setNegativeButton("Cancelar", null).show()
        }

        title("Icones e lista de apps")
        row("Formato dos icones", Ui.shapeNames[Prefs.iconShape.coerceIn(0, 3)]) {
            pick("Formato dos icones", Ui.shapeNames, Prefs.iconShape) {
                Prefs.iconShape = it
                build()
            }
        }
        slider("Tamanho dos icones", 40, 76, Prefs.iconSize, "dp") { Prefs.iconSize = it }
        slider("Colunas na lista", 3, 6, Prefs.columns, "") { Prefs.columns = it }
        toggle("Mostrar nomes dos apps", Prefs.labels) { Prefs.labels = it }
        slider("Transparencia do dock", 10, 100, Prefs.glass, "%") { Prefs.glass = it }
        row("Mostrar apps ocultos", "${Prefs.list(Prefs.hidden).size} oculto(s)") {
            Prefs.hidden = ""
            build()
        }

        title("Fonte")
        row("Fonte", Ui.fontNames[Prefs.font.coerceIn(0, 5)]) {
            pick("Fonte", Ui.fontNames.subList(0, 5) + "Escolher arquivo (.ttf / .otf)", Prefs.font) {
                if (it == 5) {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    i.type = "*/*"
                    startActivityForResult(i, reqFont)
                } else {
                    Prefs.font = it
                    build()
                }
            }
        }

        title("Outros")
        row("Configuracoes do Android", "Abre as configuracoes normais do celular") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        row("Restaurar padrao", "Apaga todas as suas escolhas neste launcher") {
            Prefs.reset()
            Media.clear(this)
            Ui.customFontFile(this).delete()
            Ui.invalidateFont()
            build()
        }
    }

    // ------------------------------------------------------------ componentes

    private fun text(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = size
        tv.setTextColor(color)
        tv.typeface = if (bold) Typeface.create(Ui.typeface(this), Typeface.BOLD) else Ui.typeface(this)
        box.addView(tv)
        return tv
    }

    private fun title(s: String) {
        val tv = text(s.uppercase(), 12f, pal.accent, true)
        tv.letterSpacing = 0.12f
        (tv.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dpi(this, 26)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.rounded(Ui.alpha(pal.panel, 235), Ui.dp(this, 18f), Ui.alpha(pal.accent, 50), Ui.dpi(this, 1))
        c.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 13), Ui.dpi(this, 16), Ui.dpi(this, 13))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Ui.dpi(this, 8)
        box.addView(c, lp)
        return c
    }

    private fun label(parent: LinearLayout, main: String, sub: String?) {
        val a = TextView(this)
        a.text = main
        a.textSize = 15f
        a.setTextColor(pal.text)
        a.typeface = Ui.typeface(this)
        parent.addView(a)
        if (!sub.isNullOrEmpty()) {
            val b = TextView(this)
            b.text = sub
            b.textSize = 12f
            b.setTextColor(pal.textDim)
            b.typeface = Ui.typeface(this)
            parent.addView(b)
        }
    }

    private fun row(main: String, sub: String?, onClick: () -> Unit) {
        val c = card()
        label(c, main, sub)
        c.setOnClickListener { onClick() }
    }

    private fun toggle(main: String, value: Boolean, set: (Boolean) -> Unit) {
        val c = card()
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        val tv = TextView(this)
        tv.text = main
        tv.textSize = 15f
        tv.setTextColor(pal.text)
        tv.typeface = Ui.typeface(this)
        c.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, v -> set(v) }
        c.addView(sw)
    }

    private fun slider(main: String, min: Int, max: Int, value: Int, unit: String, set: (Int) -> Unit) {
        val c = card()
        val head = TextView(this)
        head.textSize = 15f
        head.setTextColor(pal.text)
        head.typeface = Ui.typeface(this)
        head.text = "$main: ${value.coerceIn(min, max)}$unit"
        c.addView(head)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value.coerceIn(min, max) - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                head.text = "$main: ${p + min}$unit"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                set((s?.progress ?: 0) + min)
            }
        })
        c.addView(sb)
    }

    private fun pick(title: String, names: List<String>, current: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(names.toTypedArray(), current) { d, which ->
                d.dismiss()
                onPick(which)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // -------------------------------------------------------- arquivos escolhidos

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == reqWallpaper) {
            toast(if (Media.fromUri(this, uri)) "Wallpaper aplicado" else "Nao foi possivel usar esse arquivo")
        } else if (requestCode == reqFont) {
            try {
                val dest = Ui.customFontFile(this)
                contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }
                Typeface.createFromFile(dest)
                Prefs.font = 5
                Ui.invalidateFont()
            } catch (e: Exception) {
                Ui.customFontFile(this).delete()
                toast("Esse arquivo nao e uma fonte valida")
            }
        }
        build()
    }

    override fun onResume() {
        super.onResume()
        Ui.invalidateFont()
    }
}
