package br.sistemasuper.yoru

import android.app.Activity
import android.app.ActivityOptions
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
        val tlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = Ui.dpi(this, 56)
        homeLayer.addView(top, tlp)

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

        homeLayer.setOnLongClickListener {
            showHomeMenu()
            true
        }
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
            apps = list
            if (Prefs.dock.isEmpty()) {
                Prefs.dock = AppRepo.defaultDock(this, list).joinToString("|")
            }
            fillDock()
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
        val items = listOf("Personalizar", "Trocar de tema", "Configuracoes do Android")
        val acts = listOf<() -> Unit>(
            { startActivity(Intent(this, SettingsActivity::class.java)) },
            {
                Prefs.theme = (Prefs.theme + 1) % Themes.all.size
                wall.apply()
                applyLook()
                fillDock()
                if (drawerOpen) fillDrawer()
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
        if (intent.action == Intent.ACTION_MAIN && drawerOpen) closeDrawer()
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
            fillDock()
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
