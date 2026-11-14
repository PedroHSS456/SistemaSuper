package br.sistemasuper.yoru

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale

/**
 * Tela inicial. Nada roda em segundo plano: o relogio atualiza so pelo evento
 * de minuto do sistema, e a animacao so corre enquanto esta tela esta visivel.
 */
class LauncherActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var wall: WallpaperView
    private lateinit var homeLayer: FrameLayout
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var greetView: TextView
    private lateinit var dockBar: LinearLayout
    private lateinit var drawerLayer: LinearLayout
    private lateinit var drawerGrid: GridLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var search: EditText
    private lateinit var pager: Pager
    private lateinit var dots: TextView
    private lateinit var emptyHint: TextView

    private var apps: List<AppInfo> = emptyList()
    private var drawerOpen = false
    private var swipeStartY = 0f
    private var swipeStartX = 0f
    private var swiping = false
    private var timeReceiver: BroadcastReceiver? = null
    private var pkgReceiver: BroadcastReceiver? = null
    private var gridKey = ""

    private val pal get() = Themes.current()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        HomeModel.load()

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = FrameLayout(this)
        wall = WallpaperView(this)
        root.addView(wall, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        buildHome()
        buildDrawer()
        setContentView(root)

        // Funciona no Android 10 e no 11 em diante.
        root.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            homeLayer.setPadding(0, top, 0, bottom)
            drawerLayer.setPadding(0, top, 0, bottom)
            insets
        }

        wall.apply()
        applyLook()
        reloadApps()
    }

    // --------------------------------------------------------------- construcao

    private fun buildHome() {
        homeLayer = FrameLayout(this)
        root.addView(homeLayer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER_HORIZONTAL
        clockView = TextView(this)
        clockView.gravity = Gravity.CENTER
        clockView.includeFontPadding = false
        dateView = TextView(this)
        dateView.gravity = Gravity.CENTER
        greetView = TextView(this)
        greetView.gravity = Gravity.CENTER
        top.addView(clockView)
        top.addView(dateView)
        top.addView(greetView)
        top.setPadding(0, Ui.dpi(this, 56), 0, 0)
        pager = Pager(this)
        pager.onPage = { updateDots() }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val clp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        clp.bottomMargin = Ui.dpi(this, 150)
        homeLayer.addView(col, clp)

        dockBar = LinearLayout(this)
        dockBar.orientation = LinearLayout.HORIZONTAL
        dockBar.gravity = Gravity.CENTER
        val dlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dlp.gravity = Gravity.BOTTOM
        dlp.leftMargin = Ui.dpi(this, 20)
        dlp.rightMargin = Ui.dpi(this, 20)
        dlp.bottomMargin = Ui.dpi(this, 26)
        homeLayer.addView(dockBar, dlp)

        val hint = TextView(this)
        hint.text = "▲  deslize para cima"
        hint.textSize = 11f
        hint.gravity = Gravity.CENTER
        hint.tag = "hint"
        val hlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hlp.gravity = Gravity.BOTTOM
        hlp.bottomMargin = Ui.dpi(this, 6)
        homeLayer.addView(hint, hlp)

        dots = TextView(this)
        dots.gravity = Gravity.CENTER
        dots.textSize = 10f
        val dl = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dl.gravity = Gravity.BOTTOM
        dl.bottomMargin = Ui.dpi(this, 136)
        homeLayer.addView(dots, dl)

        emptyHint = TextView(this)
        emptyHint.text = "Segure a tela para importar do seu launcher\nou escolher os apps da tela inicial"
        emptyHint.gravity = Gravity.CENTER
        emptyHint.textSize = 13f
        emptyHint.visibility = View.GONE
        val elp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        elp.gravity = Gravity.CENTER
        homeLayer.addView(emptyHint, elp)
    }

    private fun buildDrawer() {
        drawerLayer = LinearLayout(this)
        drawerLayer.orientation = LinearLayout.VERTICAL
        drawerLayer.visibility = View.GONE
        drawerLayer.isClickable = true

        search = EditText(this)
        search.hint = "Buscar apps"
        search.setSingleLine(true)
        search.textSize = 16f
        search.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.setMargins(Ui.dpi(this, 20), Ui.dpi(this, 18), Ui.dpi(this, 20), Ui.dpi(this, 8))
        drawerLayer.addView(search, slp)

        drawerScroll = ScrollView(this)
        drawerScroll.overScrollMode = View.OVER_SCROLL_NEVER
        drawerScroll.isVerticalScrollBarEnabled = false
        drawerGrid = GridLayout(this)
        drawerScroll.addView(drawerGrid, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        drawerLayer.addView(drawerScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(drawerLayer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                fillDrawer()
            }
        })
        search.setOnEditorActionListener { v, _, _ ->
            val q = normalize(v.text.toString().trim())
            if (q.isNotEmpty()) {
                val first = visibleApps().firstOrNull { it.searchKey.contains(q) }
                if (first != null) launch(first)
            }
            true
        }
    }

    // ------------------------------------------------------------------ visual

    private fun applyLook() {
        val p = pal
        val face = Ui.typeface(this)

        clockView.typeface = face
        clockView.textSize = Prefs.clockSize.toFloat()
        clockView.setTextColor(p.text)
        clockView.setShadowLayer(Ui.dp(this, 14f), 0f, 0f, Ui.alpha(p.accent, 150))

        dateView.typeface = face
        dateView.textSize = 15f
        dateView.setTextColor(p.accent)
        greetView.typeface = face
        greetView.textSize = 13f
        greetView.setTextColor(p.textDim)
        dots.setTextColor(p.accent)
        emptyHint.typeface = face
        emptyHint.setTextColor(p.textDim)
        homeLayer.findViewWithTag<TextView>("hint")?.let {
            it.typeface = face
            it.setTextColor(Ui.alpha(p.textDim, 150))
        }

        dockBar.background = Ui.rounded(
            Ui.panelColor(p), Ui.dp(this, 30f), Ui.alpha(p.accent, 60), Ui.dpi(this, 1)
        )
        dockBar.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 12), Ui.dpi(this, 14), Ui.dpi(this, 12))

        drawerLayer.setBackgroundColor(Ui.alpha(p.skyTop, 238))
        search.typeface = face
        search.setTextColor(p.text)
        search.setHintTextColor(p.textDim)
        search.background = Ui.rounded(Ui.alpha(p.panel, 230), Ui.dp(this, 22f), Ui.alpha(p.accent, 70), Ui.dpi(this, 1))
        search.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 12), Ui.dpi(this, 18), Ui.dpi(this, 12))

        updateTime()
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val hh = if (Prefs.clock24) h else (if (h % 12 == 0) 12 else h % 12)
        clockView.text = String.format(Locale.US, "%02d:%02d", hh, m)

        val loc = Locale.getDefault()
        val days = android.icu.text.DateFormatSymbols.getInstance(loc).weekdays
        val months = android.icu.text.DateFormatSymbols.getInstance(loc).months
        val wd = days[now.get(Calendar.DAY_OF_WEEK)]
        dateView.text = "$wd, ${now.get(Calendar.DAY_OF_MONTH)} ${months[now.get(Calendar.MONTH)]}"

        val custom = Prefs.greeting.trim()
        greetView.text = if (custom.isNotEmpty()) custom else when (h) {
            in 5..11 -> "bom dia ☕"
            in 12..17 -> "boa tarde ☀"
            in 18..22 -> "boa noite ☾"
            else -> "hora de descansar ☆"
        }
    }

    // --------------------------------------------------------------------- apps

    private fun reloadApps() {
        AppRepo.loadApps(this) { list ->
            val appsChanged = list.size != apps.size || list.indices.any { list[it].key != apps[it].key }
            apps = list
            if (Prefs.dock.isEmpty()) {
                Prefs.dock = AppRepo.defaultDock(this, list).joinToString("|")
            }
            if (appsChanged || !homeBuilt) {
                homeBuilt = true
                HomeModel.clean(list.map { it.key }.toSet())
                fillDock()
                rebuildHome()
            }
            if (drawerOpen) fillDrawer()
        }
    }

    private fun visibleApps(): List<AppInfo> {
        val hidden = Prefs.list(Prefs.hidden).toSet()
        val q = normalize(search.text.toString().trim())
        return apps.filter { it.key !in hidden && (q.isEmpty() || it.searchKey.contains(q)) }
    }

    private fun appByKey(k: String): AppInfo? = apps.firstOrNull { it.key == k }

    private fun makeCell(app: AppInfo, size: Int, showLabel: Boolean): LinearLayout {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL

        val px = Ui.dpi(this, size)
        val iv = ImageView(this)
        cell.addView(iv, LinearLayout.LayoutParams(px, px))
        val cached = AppRepo.cachedIcon(app, px)
        if (cached != null) iv.setImageBitmap(cached) else AppRepo.loadIcon(this, app, px) { iv.setImageBitmap(it) }

        if (showLabel) {
            val tv = TextView(this)
            tv.text = app.label
            tv.textSize = 11f
            tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.gravity = Gravity.CENTER
            tv.typeface = Ui.typeface(this)
            tv.setTextColor(pal.text)
            tv.setShadowLayer(4f, 0f, 1f, Color.BLACK)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dpi(this, 5)
            cell.addView(tv, lp)
        }

        cell.setOnClickListener { launch(app) }
        cell.setOnLongClickListener {
            showAppMenu(app)
            true
        }
        return cell
    }

    private fun fillDock() {
        dockBar.removeAllViews()
        val keys = Prefs.list(Prefs.dock)
        val size = Prefs.iconSize
        for (k in keys) {
            val app = appByKey(k) ?: continue
            val cell = makeCell(app, size, false)
            dockBar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun fillDrawer() {
        val list = visibleApps()
        val cols = Prefs.columns.coerceIn(3, 6)
        drawerGrid.removeAllViews()
        drawerGrid.columnCount = cols
        val w = resources.displayMetrics.widthPixels
        val cellW = w / cols
        val pad = Ui.dpi(this, 14)
        for (app in list) {
            val cell = makeCell(app, Prefs.iconSize, Prefs.labels)
            cell.setPadding(Ui.dpi(this, 4), pad / 2, Ui.dpi(this, 4), pad / 2)
            val lp = GridLayout.LayoutParams()
            lp.width = cellW
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            drawerGrid.addView(cell, lp)
        }
        gridKey = Prefs.columns.toString() + "|" + Prefs.iconSize + "|" + Prefs.iconShape + "|" + Prefs.labels
    }

    private fun launch(app: AppInfo) {
        try {
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory(Intent.CATEGORY_LAUNCHER)
            i.component = app.component
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            startActivity(i, ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
            if (drawerOpen) closeDrawer(false)
        } catch (e: Exception) {
            Toast.makeText(this, "Nao foi possivel abrir " + app.label, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ menus

    private fun showAppMenu(app: AppInfo) {
        val inDock = Prefs.list(Prefs.dock).contains(app.key)
        val items = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()

        items.add("Informacoes do app")
        acts.add {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        if (inDock) {
            items.add("Tirar do dock")
            acts.add {
                Prefs.dock = Prefs.list(Prefs.dock).filter { it != app.key }.joinToString("|")
                fillDock()
            }
        } else {
            items.add("Colocar no dock")
            acts.add {
                val cur = Prefs.list(Prefs.dock)
                if (cur.size >= 5) {
                    Toast.makeText(this, "O dock aceita ate 5 apps", Toast.LENGTH_SHORT).show()
                } else {
                    Prefs.dock = (cur + app.key).joinToString("|")
                    fillDock()
                }
            }
        }
        items.add("Adicionar a tela inicial")
        acts.add {
            if (HomeModel.hasKey(app.key)) {
                Toast.makeText(this, "Ja esta na tela inicial", Toast.LENGTH_SHORT).show()
            } else if (HomeModel.addApp(app.key, pager.current)) {
                HomeModel.save()
                rebuildHome()
                Toast.makeText(this, "Adicionado a tela inicial", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "A tela inicial esta cheia", Toast.LENGTH_SHORT).show()
            }
        }
        items.add("Ocultar da lista")
        acts.add {
            Prefs.hidden = (Prefs.list(Prefs.hidden) + app.key).joinToString("|")
            fillDrawer()
            fillDock()
        }
        items.add("Desinstalar")
        acts.add {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        choose(app.label, items, acts)
    }

    private fun showHomeMenu() {
        val items = listOf(
            "Importar do launcher atual", "Escolher apps da tela inicial",
            "Personalizar", "Trocar de tema", "Configuracoes do Android"
        )
        val acts = listOf<() -> Unit>(
            { confirmImport() },
            { chooseHomeApps() },
            { startActivity(Intent(this, SettingsActivity::class.java)) },
            {
                Prefs.theme = (Prefs.theme + 1) % Themes.all.size
                wall.apply()
                applyLook()
                fillDock()
                if (drawerOpen) fillDrawer()
                rebuildHome()
                Toast.makeText(this, Themes.current().name, Toast.LENGTH_SHORT).show()
            },
            { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        )
        choose("Yoru", items, acts)
    }

    private fun choose(title: String, items: List<String>, acts: List<() -> Unit>) {
        val p = pal
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = Ui.rounded(p.panel or (0xFF shl 24), Ui.dp(this, 26f), Ui.alpha(p.accent, 90), Ui.dpi(this, 1))
        box.setPadding(Ui.dpi(this, 8), Ui.dpi(this, 14), Ui.dpi(this, 8), Ui.dpi(this, 10))

        val head = TextView(this)
        head.text = title
        head.textSize = 15f
        head.typeface = Typeface.create(Ui.typeface(this), Typeface.BOLD)
        head.setTextColor(p.accent)
        head.setPadding(Ui.dpi(this, 16), 0, Ui.dpi(this, 16), Ui.dpi(this, 8))
        box.addView(head)

        for (i in items.indices) {
            val row = TextView(this)
            row.text = items[i]
            row.textSize = 15f
            row.typeface = Ui.typeface(this)
            row.setTextColor(p.text)
            row.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 13), Ui.dpi(this, 16), Ui.dpi(this, 13))
            row.setOnClickListener {
                dialog.dismiss()
                acts[i]()
            }
            box.addView(row)
        }
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(Ui.dpi(this, 280), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    // ------------------------------------------------------------ tela inicial

    private val handler = Handler(Looper.getMainLooper())
    private var homeBuilt = false
    private var hoverDir = 0
    private var dragItem: HomeItem? = null
    private var dragGhost: ImageView? = null
    private var dragCell: View? = null
    private var dragCancelSent = false
    private var dragMoved = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private fun homeIconDp(): Int {
        val hDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val cell = (hDp - 364f) / Prefs.homeRows.coerceIn(4, 8)
        return minOf(Prefs.iconSize.toFloat(), cell - (if (Prefs.labels) 28f else 14f)).coerceAtLeast(30f).toInt()
    }

    private fun newPage(): GridPage {
        val page = GridPage(this, Prefs.homeCols.coerceIn(3, 6), Prefs.homeRows.coerceIn(4, 8))
        page.setOnLongClickListener {
            showHomeMenu()
            true
        }
        return page
    }

    private fun rebuildHome() {
        HomeModel.trim()
        val w = resources.displayMetrics.widthPixels
        pager.row.removeAllViews()
        for (p in 0 until HomeModel.pages) {
            val page = newPage()
            for (item in HomeModel.items) {
                if (item.page == p) page.addView(makeHomeCell(item))
            }
            pager.row.addView(page, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        pager.post { pager.goTo(pager.current.coerceIn(0, HomeModel.pages - 1), false) }
        updateDots()
        emptyHint.visibility = if (HomeModel.items.isEmpty() && apps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateDots() {
        val n = HomeModel.pages
        if (n <= 1) {
            dots.visibility = View.GONE
            return
        }
        dots.visibility = View.VISIBLE
        dots.text = (0 until n).joinToString("   ") { if (it == pager.current) "●" else "○" }
    }

    private fun makeHomeCell(item: HomeItem): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.tag = item
        val px = Ui.dpi(this, homeIconDp())
        val key = item.key
        val label: String
        if (key != null) {
            val app = appByKey(key) ?: return cell
            val iv = ImageView(this)
            cell.addView(iv, LinearLayout.LayoutParams(px, px))
            val cached = AppRepo.cachedIcon(app, px)
            if (cached != null) iv.setImageBitmap(cached) else AppRepo.loadIcon(this, app, px) { iv.setImageBitmap(it) }
            label = app.label
        } else {
            val list = item.apps.mapNotNull { appByKey(it) }
            cell.addView(FolderIcon(this, list, pal, px), LinearLayout.LayoutParams(px, px))
            label = item.name
        }
        if (Prefs.labels) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 11f
            tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.gravity = Gravity.CENTER
            tv.typeface = Ui.typeface(this)
            tv.setTextColor(pal.text)
            tv.setShadowLayer(4f, 0f, 1f, Color.BLACK)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dpi(this, 5)
            cell.addView(tv, lp)
        }
        cell.setOnClickListener {
            if (key != null) appByKey(key)?.let { launch(it) } else openFolder(item)
        }
        cell.setOnLongClickListener {
            startDrag(item, cell)
            true
        }
        return cell
    }

    // --------------------------------------------------------------- arrastar

    private fun startDrag(item: HomeItem, cell: View) {
        val bmp = Bitmap.createBitmap(maxOf(1, cell.width), maxOf(1, cell.height), Bitmap.Config.ARGB_8888)
        cell.draw(Canvas(bmp))
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.alpha = 0.92f
        iv.scaleX = 1.12f
        iv.scaleY = 1.12f
        root.addView(iv, FrameLayout.LayoutParams(bmp.width, bmp.height))
        iv.x = lastX - bmp.width / 2f
        iv.y = lastY - bmp.height / 2f
        cell.alpha = 0.25f
        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dragItem = item
        dragGhost = iv
        dragCell = cell
        dragCancelSent = false
        dragMoved = false
        dragStartX = lastX
        dragStartY = lastY
    }

    private fun handleDrag(ev: MotionEvent): Boolean {
        if (!dragCancelSent) {
            dragCancelSent = true
            val c = MotionEvent.obtain(ev)
            c.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(c)
            c.recycle()
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val g = dragGhost
                if (g != null) {
                    g.x = lastX - g.width / 2f
                    g.y = lastY - g.height / 2f
                }
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (Math.hypot((lastX - dragStartX).toDouble(), (lastY - dragStartY).toDouble()) > slop * 2) dragMoved = true
                val w = resources.displayMetrics.widthPixels
                val edge = Ui.dpi(this, 36)
                val dir = if (lastX < edge) -1 else if (lastX > w - edge) 1 else 0
                if (dir == 0) {
                    hoverDir = 0
                    handler.removeCallbacks(edgeRun)
                } else if (dir != hoverDir) {
                    hoverDir = dir
                    handler.removeCallbacks(edgeRun)
                    handler.postDelayed(edgeRun, 650)
                }
            }
            MotionEvent.ACTION_UP -> finishDrag(true)
            MotionEvent.ACTION_CANCEL -> finishDrag(false)
        }
        return true
    }

    /** Segurando na borda da tela, troca de pagina (e cria uma nova na ultima). */
    private val edgeRun = object : Runnable {
        override fun run() {
            val dir = hoverDir
            if (dragItem == null || dir == 0) return
            val t = pager.current + dir
            if (t < 0) return
            if (t >= HomeModel.pages) {
                if (HomeModel.pages >= 10) return
                HomeModel.pages++
                val w = resources.displayMetrics.widthPixels
                pager.row.addView(newPage(), LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT))
                updateDots()
            }
            pager.goTo(t, true)
            handler.postDelayed(this, 900)
        }
    }

    private fun finishDrag(drop: Boolean) {
        handler.removeCallbacks(edgeRun)
        hoverDir = 0
        val item = dragItem ?: return
        dragGhost?.let { root.removeView(it) }
        dragGhost = null
        dragItem = null
        dragCell?.alpha = 1f
        dragCell = null
        if (!drop) {
            rebuildHome()
            return
        }
        if (!dragMoved) {
            showItemMenu(item)
            return
        }
        val page = pager.row.getChildAt(pager.current) as? GridPage
        val cell = page?.cellAt(lastX, lastY)
        if (cell != null) {
            val tx = cell.first
            val ty = cell.second
            val p = pager.current
            val target = HomeModel.at(p, tx, ty)
            val ik = item.key
            if (target == null) {
                item.page = p
                item.x = tx
                item.y = ty
            } else if (target !== item && ik != null) {
                if (target.key != null) {
                    HomeModel.items.remove(item)
                    HomeModel.makeFolder(target, ik)
                } else {
                    target.apps.add(ik)
                    HomeModel.items.remove(item)
                }
            }
            HomeModel.save()
        }
        rebuildHome()
    }

    // ------------------------------------------------------- menus da tela inicial

    private fun openInfo(app: AppInfo) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun uninstall(app: AppInfo) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun showItemMenu(item: HomeItem) {
        val k = item.key
        if (k != null) {
            val app = appByKey(k) ?: return
            choose(
                app.label,
                listOf("Informacoes do app", "Remover da tela inicial", "Desinstalar"),
                listOf<() -> Unit>(
                    { openInfo(app) },
                    {
                        HomeModel.items.remove(item)
                        HomeModel.save()
                        rebuildHome()
                    },
                    { uninstall(app) }
                )
            )
        } else {
            choose(
                item.name,
                listOf("Renomear pasta", "Desfazer pasta"),
                listOf<() -> Unit>(
                    { renameFolder(item) },
                    {
                        HomeModel.items.remove(item)
                        for (key in item.apps) HomeModel.place(HomeItem(0, 0, 0, key, "", ArrayList()), item.page)
                        HomeModel.save()
                        rebuildHome()
                    }
                )
            )
        }
    }

    private fun renameFolder(item: HomeItem) {
        val et = EditText(this)
        et.setText(item.name)
        et.setSingleLine(true)
        AlertDialog.Builder(this).setTitle("Nome da pasta").setView(et)
            .setPositiveButton("Salvar") { _, _ ->
                item.name = et.text.toString().trim().ifEmpty { "Pasta" }
                HomeModel.save()
                rebuildHome()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun openFolder(item: HomeItem) {
        val p = pal
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = Ui.rounded(p.panel or (0xFF shl 24), Ui.dp(this, 26f), Ui.alpha(p.accent, 90), Ui.dpi(this, 1))
        box.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 14), Ui.dpi(this, 12), Ui.dpi(this, 14))

        val head = TextView(this)
        head.text = item.name + "  ✎"
        head.textSize = 16f
        head.typeface = Typeface.create(Ui.typeface(this), Typeface.BOLD)
        head.setTextColor(p.accent)
        head.setPadding(Ui.dpi(this, 8), 0, 0, Ui.dpi(this, 10))
        head.setOnClickListener {
            dialog.dismiss()
            renameFolder(item)
        }
        box.addView(head)

        val grid = GridLayout(this)
        grid.columnCount = 4
        for (k in item.apps) {
            val app = appByKey(k) ?: continue
            val cell = makeCell(app, 50, true)
            cell.setOnClickListener {
                dialog.dismiss()
                launch(app)
            }
            cell.setOnLongClickListener {
                dialog.dismiss()
                showFolderAppMenu(item, app)
                true
            }
            val lp = GridLayout.LayoutParams()
            lp.width = Ui.dpi(this, 74)
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            grid.addView(cell, lp)
        }
        val sv = ScrollView(this)
        sv.addView(grid)
        box.addView(sv)
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(Ui.dpi(this, 330), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun showFolderAppMenu(folder: HomeItem, app: AppInfo) {
        choose(
            app.label,
            listOf("Tirar da pasta", "Informacoes do app"),
            listOf<() -> Unit>(
                {
                    folder.apps.remove(app.key)
                    if (folder.apps.isEmpty()) HomeModel.items.remove(folder)
                    HomeModel.place(HomeItem(0, 0, 0, app.key, "", ArrayList()), folder.page)
                    HomeModel.save()
                    rebuildHome()
                },
                { openInfo(app) }
            )
        )
    }

    private fun confirmImport() {
        if (apps.isEmpty()) {
            Toast.makeText(this, "Aguarde a lista de apps carregar", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Importar tela inicial")
            .setMessage(
                "O Yoru vai apenas LER a tela inicial do seu launcher atual e copiar apps, pastas e dock para ca. " +
                    "Nada muda no launcher do Android. Isso substitui a tela inicial atual do Yoru."
            )
            .setPositiveButton("Importar") { _, _ ->
                val r = LayoutImporter.run(this, apps)
                Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                if (r.ok) {
                    fillDock()
                    rebuildHome()
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun chooseHomeApps() {
        if (apps.isEmpty()) return
        val list = apps
        val onHome = HashSet<String>()
        for (h in HomeModel.items) {
            h.key?.let { onHome.add(it) }
            onHome.addAll(h.apps)
        }
        val checked = BooleanArray(list.size) { list[it].key in onHome }
        AlertDialog.Builder(this).setTitle("Apps na tela inicial")
            .setMultiChoiceItems(list.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Salvar") { _, _ ->
                val want = list.indices.filter { checked[it] }.map { list[it].key }.toSet()
                HomeModel.removeKeys(onHome - want)
                for (a in list) {
                    if (a.key in want && a.key !in onHome) HomeModel.addApp(a.key, pager.current)
                }
                HomeModel.save()
                rebuildHome()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ------------------------------------------------------------------ gaveta

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        search.setText("")
        fillDrawer()
        drawerLayer.visibility = View.VISIBLE
        drawerLayer.alpha = 0f
        drawerLayer.translationY = resources.displayMetrics.heightPixels * 0.12f
        drawerLayer.animate().alpha(1f).translationY(0f).setDuration(220).start()
        wall.setActive(false)
    }

    private fun closeDrawer(animate: Boolean = true) {
        if (!drawerOpen) return
        drawerOpen = false
        hideKeyboard()
        search.clearFocus()
        if (animate) {
            drawerLayer.animate().alpha(0f).translationY(resources.displayMetrics.heightPixels * 0.12f)
                .setDuration(180).withEndAction { drawerLayer.visibility = View.GONE }.start()
        } else {
            drawerLayer.animate().cancel()
            drawerLayer.visibility = View.GONE
        }
        wall.setActive(true)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    // ------------------------------------------------------------------- gestos

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastX = ev.rawX
        lastY = ev.rawY
        if (dragItem != null) return handleDrag(ev)
        if (!drawerOpen) {
            val slop = ViewConfiguration.get(this).scaledTouchSlop
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartY = ev.rawY
                    swipeStartX = ev.rawX
                    swiping = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = swipeStartY - ev.rawY
                    val dx = Math.abs(ev.rawX - swipeStartX)
                    if (!swiping && dy > slop * 3 && dy > dx * 1.4f) swiping = true
                }
                MotionEvent.ACTION_UP -> {
                    if (swiping) {
                        swiping = false
                        openDrawer()
                        return true
                    }
                }
                MotionEvent.ACTION_CANCEL -> swiping = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
    }

    // ------------------------------------------------------------ ciclo de vida

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botao Home apertado: volta para a tela inicial, fechando a gaveta.
        if (intent.action == Intent.ACTION_MAIN) {
            if (drawerOpen) closeDrawer() else if (pager.current != 0) pager.goTo(0, true)
        }
    }

    override fun onStart() {
        super.onStart()
        val tr = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                updateTime()
            }
        }
        val f = IntentFilter()
        f.addAction(Intent.ACTION_TIME_TICK)
        f.addAction(Intent.ACTION_TIME_CHANGED)
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(tr, f)
        timeReceiver = tr

        val pr = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                reloadApps()
            }
        }
        val pf = IntentFilter()
        pf.addAction(Intent.ACTION_PACKAGE_ADDED)
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED)
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED)
        pf.addDataScheme("package")
        registerReceiver(pr, pf)
        pkgReceiver = pr
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.changed) {
            Prefs.changed = false
            Ui.invalidateFont()
            AppRepo.clearIcons()
            wall.apply()
            applyLook()
            HomeModel.load()
            HomeModel.normalize()
            fillDock()
            rebuildHome()
            if (drawerOpen) fillDrawer()
        }
        updateTime()
        wall.setActive(!drawerOpen)
        reloadApps()
    }

    override fun onPause() {
        wall.setActive(false)
        super.onPause()
    }

    override fun onStop() {
        timeReceiver?.let { unregisterReceiver(it) }
        pkgReceiver?.let { unregisterReceiver(it) }
        timeReceiver = null
        pkgReceiver = null
        super.onStop()
    }
}
