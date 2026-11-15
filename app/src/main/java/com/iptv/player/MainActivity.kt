package com.iptv.player

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var panel: View
    private lateinit var btnCountry: Button
    private lateinit var btnGroup: Button
    private lateinit var search: EditText
    private lateinit var count: TextView
    private lateinit var status: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: ChannelAdapter

    private var all: List<Channel> = emptyList()
    private var shown: List<Channel> = emptyList()
    private var country: String? = null
    private var group: String? = null
    private var query: String = ""
    private var fullscreen = false
    private var currentIndex = -1
    private var triedProgressive = false

    private val arabCodes = listOf(
        "sa", "eg", "ae", "qa", "kw", "bh", "om", "jo", "lb", "sy", "iq",
        "ye", "ps", "ma", "dz", "tn", "ly", "sd", "mr", "so", "dj", "km"
    )

    private val groupNames = mapOf(
        "General" to "عام", "News" to "أخبار", "Sports" to "رياضة",
        "Movies" to "أفلام", "Series" to "مسلسلات", "Kids" to "أطفال",
        "Religious" to "ديني", "Music" to "موسيقى", "Entertainment" to "ترفيه",
        "Documentary" to "وثائقي", "Education" to "تعليم", "Comedy" to "كوميديا",
        "Culture" to "ثقافة", "Lifestyle" to "نمط حياة", "Shop" to "تسوق",
        "Business" to "أعمال", "Animation" to "رسوم متحركة", "Classic" to "كلاسيكي",
        "Outdoor" to "طبيعة", "Travel" to "سفر", "Family" to "عائلة",
        "Cooking" to "طبخ", "Auto" to "سيارات", "Legislative" to "برلمان",
        "Undefined" to "غير مصنف"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.playerView)
        panel = findViewById(R.id.panel)
        btnCountry = findViewById(R.id.btnCountry)
        btnGroup = findViewById(R.id.btnGroup)
        search = findViewById(R.id.search)
        count = findViewById(R.id.count)
        status = findViewById(R.id.status)
        list = findViewById(R.id.list)

        adapter = ChannelAdapter { pos ->
            play(pos)
            setFullscreen(true)
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    status.visibility = View.GONE
                } else if (state == Player.STATE_BUFFERING) {
                    showStatus("جاري التحميل...")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!triedProgressive && currentIndex in shown.indices) {
                    triedProgressive = true
                    startPlayback(shown[currentIndex], false)
                } else {
                    showStatus("تعذّر تشغيل هذه القناة")
                }
            }
        })

        btnCountry.setOnClickListener { showCountryDialog() }
        btnGroup.setOnClickListener { showGroupDialog() }
        search.doAfterTextChanged {
            query = it?.toString() ?: ""
            applyFilter()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) {
                    setFullscreen(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        count.text = "جاري تحميل القنوات..."
        Thread {
            val channels = try {
                BufferedReader(InputStreamReader(assets.open("iptv.m3u"), "UTF-8")).use {
                    M3uParser.parse(it)
                }
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                all = channels
                applyFilter()
            }
        }.start()
    }

    private fun countryName(code: String): String {
        if (code.isEmpty()) return "غير محدد"
        val c = if (code == "uk") "GB" else code.uppercase()
        val n = Locale("", c).getDisplayCountry(Locale("ar"))
        return if (n.isNullOrBlank()) code.uppercase() else n
    }

    private fun groupName(g: String): String = groupNames[g] ?: g

    private fun applyFilter() {
        val q = query.trim().lowercase()
        shown = all.filter {
            (country == null || it.country == country) &&
                (group == null || it.group == group) &&
                (q.isEmpty() || it.name.lowercase().contains(q))
        }
        currentIndex = shown.indexOfFirst { it.url == adapter.playingUrl }
        adapter.submit(shown)
        count.text = "${shown.size} قناة"
        btnCountry.text = "الدولة: " + (country?.let { countryName(it) } ?: "الكل")
        btnGroup.text = "التصنيف: " + (group?.let { groupName(it) } ?: "الكل")
    }

    private fun showCountryDialog() {
        val counts = all.groupingBy { it.country }.eachCount()
        val codes = counts.keys.sortedWith(
            compareBy<String>({ if (it in arabCodes) 0 else 1 }, { -(counts[it] ?: 0) })
        )
        val labels = ArrayList<String>()
        labels.add("الكل (${all.size})")
        codes.forEach { labels.add("${countryName(it)} (${counts[it]})") }
        AlertDialog.Builder(this)
            .setTitle("اختر الدولة")
            .setItems(labels.toTypedArray()) { _, which ->
                country = if (which == 0) null else codes[which - 1]
                group = null
                applyFilter()
            }
            .show()
    }

    private fun showGroupDialog() {
        val base = all.filter { country == null || it.country == country }
        val counts = base.groupingBy { it.group }.eachCount()
        val groups = counts.keys.sortedByDescending { counts[it] ?: 0 }
        val labels = ArrayList<String>()
        labels.add("الكل (${base.size})")
        groups.forEach { labels.add("${groupName(it)} (${counts[it]})") }
        AlertDialog.Builder(this)
            .setTitle("اختر التصنيف")
            .setItems(labels.toTypedArray()) { _, which ->
                group = if (which == 0) null else groups[which - 1]
                applyFilter()
            }
            .show()
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun play(index: Int) {
        if (index !in shown.indices) return
        currentIndex = index
        triedProgressive = false
        val ch = shown[index]
        adapter.playingUrl = ch.url
        adapter.notifyDataSetChanged()
        showStatus(ch.name)
        startPlayback(ch, true)
    }

    private fun mimeFor(url: String): String? {
        val lower = url.lowercase()
        val path = lower.substringBefore('?')
        return when {
            lower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".ts") || path.endsWith(".mp4") || path.endsWith(".mkv") ||
                path.endsWith(".mp3") || path.endsWith(".aac") -> null
            else -> MimeTypes.APPLICATION_M3U8
        }
    }

    private fun startPlayback(ch: Channel, useHint: Boolean) {
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent(
                ch.userAgent
                    ?: "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
            )
        val referer = ch.referer
        if (referer != null) {
            factory.setDefaultRequestProperties(mapOf("Referer" to referer))
        }
        val itemBuilder = MediaItem.Builder().setUri(ch.url)
        if (useHint) {
            val mime = mimeFor(ch.url)
            if (mime != null) itemBuilder.setMimeType(mime)
        }
        val source = DefaultMediaSourceFactory(factory).createMediaSource(itemBuilder.build())
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        panel.visibility = if (on) View.GONE else View.VISIBLE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            list.scrollToPosition(maxOf(currentIndex, 0))
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (fullscreen && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    play(currentIndex - 1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    play(currentIndex + 1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (currentIndex in shown.indices) showStatus(shown[currentIndex].name)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        if (currentIndex >= 0) player.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
