package br.sistemasuper.yoru

import org.json.JSONArray
import org.json.JSONObject

/** Um item da tela inicial: app (key != null) ou pasta (key == null). */
class HomeItem(
    var page: Int,
    var x: Int,
    var y: Int,
    var key: String?,
    var name: String,
    val apps: MutableList<String>
)

/** Layout da tela inicial. Salvo so quando muda, sem nada em segundo plano. */
object HomeModel {
    val items = ArrayList<HomeItem>()
    var pages = 1

    fun load() {
        items.clear()
        pages = 1
        val raw = Prefs.home
        if (raw.isEmpty()) return
        try {
            val o = JSONObject(raw)
            pages = o.optInt("pages", 1).coerceIn(1, 10)
            val a = o.getJSONArray("items")
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val apps = ArrayList<String>()
                val ja = j.optJSONArray("apps")
                if (ja != null) for (k in 0 until ja.length()) apps.add(ja.getString(k))
                val key = if (j.has("k")) j.getString("k") else null
                items.add(HomeItem(j.getInt("p"), j.getInt("x"), j.getInt("y"), key, j.optString("n", "Pasta"), apps))
            }
        } catch (e: Exception) {
            items.clear()
            pages = 1
        }
    }

    fun trim() {
        val last = items.maxOfOrNull { it.page } ?: 0
        pages = maxOf(1, minOf(10, last + 1))
    }

    fun save() {
        trim()
        val a = JSONArray()
        for (h in items) {
            val j = JSONObject()
            j.put("p", h.page).put("x", h.x).put("y", h.y)
            val k = h.key
            if (k != null) {
                j.put("k", k)
            } else {
                j.put("n", h.name)
                val ja = JSONArray()
                for (s in h.apps) ja.put(s)
                j.put("apps", ja)
            }
            a.put(j)
        }
        Prefs.home = JSONObject().put("pages", pages).put("items", a).toString()
    }

    fun at(page: Int, x: Int, y: Int): HomeItem? = items.firstOrNull { it.page == page && it.x == x && it.y == y }

    fun hasKey(key: String): Boolean = items.any { it.key == key || it.apps.contains(key) }

    /** Poe o item na primeira casa livre, a partir da pagina preferida. */
    fun place(item: HomeItem, preferPage: Int = 0): Boolean {
        val cols = Prefs.homeCols.coerceIn(3, 6)
        val rows = Prefs.homeRows.coerceIn(4, 8)
        for (p in preferPage.coerceIn(0, 9)..9) {
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    if (at(p, x, y) == null) {
                        item.page = p
                        item.x = x
                        item.y = y
                        if (!items.contains(item)) items.add(item)
                        pages = maxOf(pages, p + 1)
                        return true
                    }
                }
            }
        }
        return false
    }

    fun addApp(key: String, preferPage: Int = 0): Boolean {
        if (hasKey(key)) return false
        return place(HomeItem(0, 0, 0, key, "", ArrayList()), preferPage)
    }

    fun makeFolder(target: HomeItem, dragKey: String) {
        val tk = target.key ?: return
        items.remove(target)
        items.add(HomeItem(target.page, target.x, target.y, null, "Pasta", mutableListOf(tk, dragKey)))
    }

    fun removeKeys(keys: Set<String>) {
        val iter = items.iterator()
        while (iter.hasNext()) {
            val h = iter.next()
            val k = h.key
            if (k != null) {
                if (k in keys) iter.remove()
            } else {
                h.apps.removeAll(keys)
                if (h.apps.isEmpty()) iter.remove()
            }
        }
    }

    /** Tira da tela inicial os apps que foram desinstalados. */
    fun clean(valid: Set<String>) {
        if (valid.size < 5) return
        val all = HashSet<String>()
        for (h in items) {
            h.key?.let { all.add(it) }
            all.addAll(h.apps)
        }
        val gone = all - valid
        if (gone.isNotEmpty()) {
            removeKeys(gone)
            save()
        }
    }

    /** Se a grade mudou de tamanho, realoca o que ficou fora dela. */
    fun normalize() {
        val cols = Prefs.homeCols.coerceIn(3, 6)
        val rows = Prefs.homeRows.coerceIn(4, 8)
        val bad = items.filter { it.x >= cols || it.y >= rows }
        if (bad.isNotEmpty()) {
            items.removeAll(bad.toSet())
            for (b in bad) place(b, b.page)
        }
        save()
    }
}
