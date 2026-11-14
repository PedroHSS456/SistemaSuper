package br.sistemasuper.yoru

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

/** Copia GIF, imagem ou video escolhidos para a pasta interna do app. */
object Media {
    const val IMAGE = "wp_image"
    const val VIDEO = "wp_video"

    fun fromUri(ctx: Context, uri: Uri): Boolean {
        return try {
            val mime = ctx.contentResolver.getType(uri) ?: ""
            val name = if (mime.startsWith("video/")) VIDEO else IMAGE
            clear(ctx)
            val dest = File(ctx.filesDir, name)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return false
            Prefs.wallpaperFile = name
            Prefs.wallpaperMode = 1
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Wallpapers colocados em app/src/main/assets/wallpapers/ antes de compilar. */
    fun bundled(ctx: Context): List<String> {
        return try {
            (ctx.assets.list("wallpapers") ?: emptyArray())
                .filter {
                    val n = it.lowercase()
                    n.endsWith(".gif") || n.endsWith(".mp4") || n.endsWith(".webm") ||
                        n.endsWith(".webp") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                }
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fromAsset(ctx: Context, assetName: String): Boolean {
        return try {
            val n = assetName.lowercase()
            val name = if (n.endsWith(".mp4") || n.endsWith(".webm")) VIDEO else IMAGE
            clear(ctx)
            val dest = File(ctx.filesDir, name)
            ctx.assets.open("wallpapers/$assetName").use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
            Prefs.wallpaperFile = name
            Prefs.wallpaperMode = 1
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, IMAGE).delete()
        File(ctx.filesDir, VIDEO).delete()
        Prefs.wallpaperFile = ""
    }
}

/**
 * Fundo da tela inicial. Mostra a cena desenhada ou, se a pessoa escolheu,
 * um GIF, imagem ou video. Tudo para quando a tela inicial nao esta visivel.
 */
class WallpaperView(ctx: Context) : FrameLayout(ctx) {

    val scene = SceneView(ctx)
    private val dimView = View(ctx)
    private var image: ImageView? = null
    private var texture: TextureView? = null
    private var player: MediaPlayer? = null
    private var gif: AnimatedImageDrawable? = null
    private var active = false

    init {
        addView(scene, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(dimView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dimView.visibility = View.GONE
        setBackgroundColor(Color.BLACK)
    }

    fun apply() {
        releaseMedia()
        var mediaShown = false
        val name = Prefs.wallpaperFile
        val file = File(context.filesDir, name)
        if (Prefs.wallpaperMode == 1 && name.isNotEmpty() && file.exists()) {
            mediaShown = if (name == Media.VIDEO) showVideo(file) else showImage(file)
        }
        if (mediaShown) {
            scene.setRunning(false)
            scene.visibility = View.GONE
            val a = Prefs.dim.coerceIn(0, 70) * 255 / 100
            dimView.setBackgroundColor(Color.argb(a, 0, 0, 0))
            dimView.visibility = View.VISIBLE
        } else {
            scene.visibility = View.VISIBLE
            dimView.visibility = View.GONE
            scene.applyPrefs()
            scene.setRunning(active)
        }
    }

    private fun showImage(file: File): Boolean {
        return try {
            val sw = resources.displayMetrics.widthPixels
            val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val sample = maxOf(1, info.size.width / sw)
                decoder.setTargetSampleSize(sample)
            }
            val iv = ImageView(context)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(d)
            addView(iv, 1, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            image = iv
            if (d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                gif = d
                if (active) d.start()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showVideo(file: File): Boolean {
        return try {
            val tv = TextureView(context)
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    startPlayer(tv, st, file)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    fitVideo(tv)
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    stopPlayer()
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
            addView(tv, 1, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            texture = tv
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun startPlayer(tv: TextureView, st: SurfaceTexture, file: File) {
        stopPlayer()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setSurface(Surface(st))
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener { m ->
                fitVideo(tv)
                if (active) m.start()
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            player = null
        }
    }

    private fun stopPlayer() {
        val mp = player ?: return
        player = null
        try {
            mp.release()
        } catch (e: Exception) {
        }
    }

    /** Preenche a tela cortando as bordas (center crop), sem esticar o video. */
    private fun fitVideo(tv: TextureView) {
        val mp = player ?: return
        val vw = mp.videoWidth.toFloat()
        val vh = mp.videoHeight.toFloat()
        if (vw <= 0f || vh <= 0f || tv.width <= 0 || tv.height <= 0) return
        val viewAspect = tv.width.toFloat() / tv.height.toFloat()
        val videoAspect = vw / vh
        val m = Matrix()
        val cx = tv.width / 2f
        val cy = tv.height / 2f
        if (videoAspect >= viewAspect) {
            m.setScale(videoAspect / viewAspect, 1f, cx, cy)
        } else {
            m.setScale(1f, viewAspect / videoAspect, cx, cy)
        }
        tv.setTransform(m)
    }

    private fun releaseMedia() {
        stopPlayer()
        gif?.stop()
        gif = null
        image?.let { removeView(it) }
        image = null
        texture?.let {
            it.surfaceTextureListener = null
            removeView(it)
        }
        texture = null
    }

    /** Chamado pela tela inicial: true quando esta visivel, false quando sai de vista. */
    fun setActive(a: Boolean) {
        active = a
        if (scene.visibility == View.VISIBLE) scene.setRunning(a)
        val g = gif
        if (g != null) {
            if (a) g.start() else g.stop()
        }
        val mp = player
        if (mp != null) {
            try {
                if (a) mp.start() else if (mp.isPlaying) mp.pause()
            } catch (e: Exception) {
            }
        }
    }

    override fun onDetachedFromWindow() {
        stopPlayer()
        super.onDetachedFromWindow()
    }
}
