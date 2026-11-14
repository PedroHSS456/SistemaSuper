package br.sistemasuper.yoru

import android.content.Context
import android.content.SharedPreferences

/** Todas as configuracoes ficam aqui. Leitura barata, sem nada rodando em segundo plano. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("yoru", Context.MODE_PRIVATE)
        }
    }

    /** Fica true quando algo mudou; a tela inicial reaplica o visual ao voltar. */
    @Volatile
    var changed = false

    private fun getI(k: String, d: Int) = sp.getInt(k, d)
    private fun putI(k: String, v: Int) {
        sp.edit().putInt(k, v).apply()
        changed = true
    }

    private fun getB(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) {
        sp.edit().putBoolean(k, v).apply()
        changed = true
    }

    private fun getS(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) {
        sp.edit().putString(k, v).apply()
        changed = true
    }

    /** Indice da paleta de cores (ver Themes). */
    var theme: Int
        get() = getI("theme", 0)
        set(v) = putI("theme", v)

    /** Efeito de particulas: 0 chuva, 1 sakura, 2 vagalumes, 3 neve, 4 nenhum. */
    var effect: Int
        get() = getI("effect", 0)
        set(v) = putI("effect", v)

    /** 0 normal (30 fps), 1 economia (15 fps), 2 desligada. */
    var animMode: Int
        get() = getI("animMode", 0)
        set(v) = putI("animMode", v)

    /** Escurecimento do fundo, 0 a 70 por cento. */
    var dim: Int
        get() = getI("dim", 15)
        set(v) = putI("dim", v)

    /** Fundo: 0 cena desenhada, 1 arquivo (GIF ou video) escolhido pela pessoa. */
    var wallpaperMode: Int
        get() = getI("wpMode", 0)
        set(v) = putI("wpMode", v)

    /** Nome do arquivo copiado para a pasta interna do app. */
    var wallpaperFile: String
        get() = getS("wpFile", "")
        set(v) = putS("wpFile", v)

    /** 0 circulo, 1 squircle, 2 quadrado arredondado, 3 original do app. */
    var iconShape: Int
        get() = getI("iconShape", 1)
        set(v) = putI("iconShape", v)

    var iconSize: Int
        get() = getI("iconSize", 56)
        set(v) = putI("iconSize", v)

    var columns: Int
        get() = getI("columns", 4)
        set(v) = putI("columns", v)

    var labels: Boolean
        get() = getB("labels", true)
        set(v) = putB("labels", v)

    /** 0 fina, 1 suave, 2 serifada, 3 casual, 4 mono, 5 arquivo da pessoa. */
    var font: Int
        get() = getI("font", 1)
        set(v) = putI("font", v)

    var clock24: Boolean
        get() = getB("clock24", true)
        set(v) = putB("clock24", v)

    var clockSize: Int
        get() = getI("clockSize", 76)
        set(v) = putI("clockSize", v)

    /** Opacidade dos paineis de vidro (dock e menus), 0 a 100. */
    var glass: Int
        get() = getI("glass", 55)
        set(v) = putI("glass", v)

    /** Frase sob o relogio. Vazio = saudacao automatica. */
    var greeting: String
        get() = getS("greeting", "")
        set(v) = putS("greeting", v)

    /** Apps do dock, separados por "|" (ComponentName). */
    var dock: String
        get() = getS("dock", "")
        set(v) = putS("dock", v)

    /** Apps ocultos, separados por "|" (ComponentName). */
    var hidden: String
        get() = getS("hidden", "")
        set(v) = putS("hidden", v)

    var homeCols: Int
        get() = getI("hCols", 4)
        set(v) = putI("hCols", v)

    var homeRows: Int
        get() = getI("hRows", 5)
        set(v) = putI("hRows", v)

    /** Layout da tela inicial (JSON). Salvar aqui nao dispara reaplicar o visual. */
    var home: String
        get() = sp.getString("home", "") ?: ""
        set(v) {
            sp.edit().putString("home", v).apply()
        }

    fun list(raw: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split("|")

    fun reset() {
        sp.edit().clear().apply()
        changed = true
    }
}
