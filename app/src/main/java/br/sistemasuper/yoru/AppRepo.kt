package br.sistemasuper.yoru

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

class AppInfo(val label: String, val pkg: String, val component: ComponentName) {
    val key: String = component.flattenToString()
    val searchKey: String = normalize(label)
}

fun normalize(s: String): String {
    val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return n.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}

object AppRepo {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Limite de 6 MB para os icones em memoria.
    private val cache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun loadApps(ctx: Context, done: (List<AppInfo>) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            val pm = app.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found = pm.queryIntentActivities(intent, 0)
            val coll = Collator.getInstance(Locale.getDefault())
            coll.strength = Collator.PRIMARY
            val apps = found
                .filter { it.activityInfo.packageName != app.packageName }
                .map {
                    AppInfo(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName,
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    )
                }
                .sortedWith(Comparator { a, b -> coll.compare(a.label, b.label) })
            main.post { done(apps) }
        }
    }

    private fun keyOf(app: AppInfo, sizePx: Int): String = app.key + "|" + Prefs.iconShape + "|" + sizePx

    fun cachedIcon(app: AppInfo, sizePx: Int): Bitmap? = cache.get(keyOf(app, sizePx))

    fun clearIcons() {
        cache.evictAll()
    }

    fun loadIcon(ctx: Context, app: AppInfo, sizePx: Int, cb: (Bitmap) -> Unit) {
        val appCtx = ctx.applicationContext
        val key = keyOf(app, sizePx)
        val shape = Prefs.iconShape
        io.execute {
            var bmp = cache.get(key)
            if (bmp == null) {
                val pm = appCtx.packageManager
                val d = try {
                    pm.getActivityIcon(app.component)
                } catch (e: Exception) {
                    pm.defaultActivityIcon
                }
                bmp = IconMaker.make(d, sizePx, shape)
                cache.put(key, bmp)
            }
            val out = bmp
            main.post { cb(out) }
        }
    }

    /** Procura um app para o dock padrao (telefone, mensagens, camera, navegador). */
    fun defaultDock(ctx: Context, apps: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val wanted = ArrayList<String>()
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")),
            Intent("android.media.action.IMAGE_CAPTURE"),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        )
        for (i in intents) {
            val r = pm.resolveActivity(i, 0) ?: continue
            val match = apps.firstOrNull { it.pkg == r.activityInfo.packageName }
            if (match != null && !wanted.contains(match.key)) wanted.add(match.key)
        }
        return wanted
    }
}
