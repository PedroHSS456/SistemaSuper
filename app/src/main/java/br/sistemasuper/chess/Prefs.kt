package br.sistemasuper.chess

import android.content.Context

/** Preferencias internas do app (somem na desinstalacao). */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun set(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
}
