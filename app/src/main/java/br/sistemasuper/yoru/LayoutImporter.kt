package br.sistemasuper.yoru

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Tenta ler (somente leitura) a tela inicial do launcher atual e copiar apps, pastas e dock.
 * Nada e escrito no outro launcher. Se ele nao permitir a leitura, devolve uma mensagem clara.
 */
object LayoutImporter {

    class Result(val ok: Boolean, val message: String)

    private class Row(
        val id: Int, val title: String, val intent: String, val container: Int,
        val screen: Int, val x: Int, val y: Int, val type: Int
    )

    fun run(ctx: Context, apps: List<AppInfo>): Result {
        val pm = ctx.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val candidates = pm.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo.packageName }
            .filter { it != ctx.packageName }
            .distinct()
        if (candidates.isEmpty()) return Result(false, "Nao encontrei outro launcher no celular.")

        var denied: String? = null
        for (pkg in candidates) {
            for (auth in authorities(pm, pkg)) {
                val rows: List<Row>? = try {
                    read(ctx, auth)
                } catch (e: SecurityException) {
                    denied = pkg
                    null
                } catch (e: Exception) {
                    null
                }
                if (rows != null && rows.isNotEmpty()) return apply(rows, apps)
            }
        }
        return if (denied != null) {
            Result(false, "O launcher atual protege a tela inicial e nao deixa outros apps lerem. Use \"Escolher apps da tela inicial\".")
        } else {
            Result(false, "Nao consegui ler a tela inicial do launcher atual. Use \"Escolher apps da tela inicial\".")
        }
    }

    private fun authorities(pm: PackageManager, pkg: String): List<String> {
        val out = LinkedHashSet<String>()
        try {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS)
            info.providers?.forEach { p ->
                p.authority?.split(";")?.forEach { a ->
                    if (a.endsWith(".settings") || a.contains("launcher")) out.add(a)
                }
            }
        } catch (e: Exception) {
        }
        out.add("$pkg.settings")
        out.add("com.android.launcher3.settings")
        out.add("com.android.launcher2.settings")
        return out.toList()
    }

    private fun read(ctx: Context, auth: String): List<Row> {
        val list = ArrayList<Row>()
        val cur = ctx.contentResolver.query(Uri.parse("content://$auth/favorites"), null, null, null, null)
            ?: return list
        cur.use {
            val iId = it.getColumnIndex("_id")
            val iTitle = it.getColumnIndex("title")
            val iIntent = it.getColumnIndex("intent")
            val iCont = it.getColumnIndex("container")
            val iScr = it.getColumnIndex("screen")
            val iX = it.getColumnIndex("cellX")
            val iY = it.getColumnIndex("cellY")
            val iType = it.getColumnIndex("itemType")
            val iProf = it.getColumnIndex("profileId")
            if (iId < 0 || iCont < 0 || iX < 0 || iY < 0 || iType < 0) return list
            while (it.moveToNext()) {
                if (iProf >= 0 && it.getInt(iProf) != 0) continue
                list.add(
                    Row(
                        it.getInt(iId),
                        if (iTitle >= 0) (it.getString(iTitle) ?: "") else "",
                        if (iIntent >= 0) (it.getString(iIntent) ?: "") else "",
                        it.getInt(iCont),
                        if (iScr >= 0) it.getInt(iScr) else 0,
                        it.getInt(iX), it.getInt(iY), it.getInt(iType)
                    )
                )
            }
        }
        return list
    }

    private fun apply(rows: List<Row>, apps: List<AppInfo>): Result {
        val byKey = apps.associateBy { it.key }
        val byPkg = apps.groupBy { it.pkg }

        fun keyOf(intent: String): String? {
            if (intent.isEmpty()) return null
            val comp = try {
                Intent.parseUri(intent, 0).component
            } catch (e: Exception) {
                null
            } ?: return null
            val k = comp.flattenToString()
            if (byKey.containsKey(k)) return k
            return byPkg[comp.packageName]?.firstOrNull()?.key
        }

        val desk = rows.filter { it.container == -100 }
        val screens = desk.map { it.screen }.distinct().sorted()

        val dockKeys = rows.filter { it.container == -101 && (it.type == 0 || it.type == 1) }
            .sortedWith(compareBy({ it.screen }, { it.x }))
            .mapNotNull { keyOf(it.intent) }
            .distinct()
            .take(5)

        val items = ArrayList<HomeItem>()
        var folders = 0
        var count = 0
        for (r in desk) {
            val page = screens.indexOf(r.screen)
            if (page < 0 || page > 9) continue
            if (r.type == 0 || r.type == 1) {
                val k = keyOf(r.intent)
                if (k != null) {
                    items.add(HomeItem(page, r.x, r.y, k, "", ArrayList()))
                    count++
                }
            } else if (r.type == 2) {
                val ks = rows.filter { it.container == r.id }
                    .sortedWith(compareBy({ it.y }, { it.x }))
                    .mapNotNull { keyOf(it.intent) }
                    .distinct()
                    .toMutableList()
                if (ks.isNotEmpty()) {
                    items.add(HomeItem(page, r.x, r.y, null, r.title.ifEmpty { "Pasta" }, ks))
                    folders++
                    count += ks.size
                }
            }
        }
        if (items.isEmpty() && dockKeys.isEmpty()) {
            return Result(false, "A tela inicial do launcher atual esta vazia ou so tem widgets.")
        }

        Prefs.homeCols = ((items.maxOfOrNull { it.x } ?: 3) + 1).coerceIn(3, 6)
        Prefs.homeRows = ((items.maxOfOrNull { it.y } ?: 4) + 1).coerceIn(4, 8)
        HomeModel.items.clear()
        HomeModel.items.addAll(items)
        HomeModel.pages = maxOf(1, screens.size)
        HomeModel.normalize()
        if (dockKeys.isNotEmpty()) Prefs.dock = dockKeys.joinToString("|")
        return Result(true, "Importado: $count app(s) e $folders pasta(s) em ${HomeModel.pages} pagina(s).")
    }
}
