package com.deniscerri.ytdl.ee

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.receiver.ShareActivity
import com.deniscerri.ytdl.ui.welcome.WelcomeActivity
import java.net.URLEncoder

/** Browser front-end; all supported media downloads use YTDLnis's original yt-dlp engine. */
class SocialBrowserActivity : Activity() {
    private val background = Color.rgb(12, 17, 27)
    private val panel = Color.rgb(27, 36, 51)
    private val accent = Color.rgb(34, 187, 165)
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var webView: WebView
    private lateinit var home: ScrollView
    private lateinit var container: LinearLayout

    private val sites = listOf(
        Triple("▶ YouTube", "https://m.youtube.com/", "#C92D39"),
        Triple("♪ TikTok", "https://www.tiktok.com/", "#293346"),
        Triple("◎ Instagram", "https://www.instagram.com/", "#A84988"),
        Triple("f Facebook", "https://m.facebook.com/", "#2B5DB5"),
        Triple("𝕏 X", "https://x.com/", "#303744"),
        Triple("@ Threads", "https://www.threads.net/", "#303744"),
        Triple("▣ Reddit", "https://www.reddit.com/", "#C95A2F"),
        Triple("♫ SoundCloud", "https://soundcloud.com/", "#B96821"),
        Triple("▶ Vimeo", "https://vimeo.com/", "#187C9D"),
        Triple("▶ Dailymotion", "https://www.dailymotion.com/", "#305DA4"),
        Triple("▤ Pinterest", "https://www.pinterest.com/", "#A9384C"),
        Triple("◉ Twitch", "https://www.twitch.tv/", "#6842AF")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preserve the mandatory upstream runtime/permission setup on first launch.
        if (WelcomeActivity.isNeeded(PreferenceManager.getDefaultSharedPreferences(this))) {
            val welcomeIntent = Intent(this, WelcomeActivity::class.java)
                .putExtra(
                    WelcomeActivity.EXTRA_NEXT_INTENT,
                    Intent(this, SocialBrowserActivity::class.java)
                )
            startActivity(welcomeIntent)
            finish()
            return
        }
        window.statusBarColor = background
        window.navigationBarColor = background
        buildScreen()
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            if (webView.url != null) {
                showWeb()
                address.setText(webView.url)
            } else showHome()
        } else showHome()
    }

    private fun dp(size: Int) = (size * resources.displayMetrics.density + 0.5f).toInt()

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun text(value: String, size: Float = 15f): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        minHeight = 0
        minWidth = 0
        setPadding(dp(3), 0, dp(3), 0)
        setTextColor(Color.WHITE)
        setBackgroundColor(panel)
        setOnClickListener { action() }
    }

    private fun buildScreen() {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        setContentView(container)
        val titleRow = row().apply { setPadding(dp(12), dp(7), dp(12), dp(5)) }
        titleRow.addView(text("ZZ Media", 21f).apply {
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(button("محرك التحميل") { openEngine() })
        container.addView(titleRow)

        val urlRow = row().apply { setPadding(dp(10), 0, dp(10), dp(5)) }
        address = EditText(this).apply {
            hint = "رابط الفيديو أو اسم الموقع"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            textSize = 14f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(9), dp(5), dp(9), dp(5))
            setBackgroundColor(panel)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setOnEditorActionListener { _, _, _ -> navigateInput(); true }
        }
        urlRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        urlRow.addView(button("اذهب") { navigateInput() })
        container.addView(urlRow)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(accent)
        }
        container.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        home = createHome()
        container.addView(home, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        webView = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(background)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    progress.progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (uri.scheme == "http" || uri.scheme == "https") return false
                    if (uri.scheme == "intent" || uri.scheme == "file" || uri.scheme == "javascript") return true
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                        true
                    } catch (_: Exception) { true }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null) address.setText(url)
                    progress.visibility = View.GONE
                }
            }
        }
        container.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val nav = row().apply {
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(panel)
        }
        val items: List<Pair<String, () -> Unit>> = listOf(
            "⌂ الرئيسية" to { showHome() },
            "↩ رجوع" to { if (webView.visibility == View.VISIBLE && webView.canGoBack()) webView.goBack() else showHome() },
            "⟳ تحديث" to { if (webView.visibility == View.VISIBLE) webView.reload() },
            "⬇ تنزيل" to { downloadCurrent() },
            "☷ التنزيلات" to { openEngine("Downloads") }
        )
        items.forEach { (label, callback) ->
            nav.addView(button(label, callback), LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        container.addView(nav)
    }

    private fun createHome(): ScrollView = ScrollView(this).apply {
        fillViewport = true
        val content = LinearLayout(this@SocialBrowserActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(text("مواقع التواصل والفيديو", 22f).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(text("افتح الفيديو أو المنشور، ثم اضغط تنزيل.", 14f).apply {
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(6), 0, dp(12))
        })
        val columns = if (resources.configuration.smallestScreenWidthDp >= 600) 4 else 3
        val grid = GridLayout(this@SocialBrowserActivity).apply {
            columnCount = columns
            useDefaultMargins = true
        }
        sites.forEachIndexed { index, site ->
            val tile = button(site.first) { navigate(site.second) }.apply {
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                setBackgroundColor(Color.parseColor(site.third))
            }
            grid.addView(tile, GridLayout.LayoutParams(
                GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)
            ).apply {
                width = 0
                height = dp(88)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        content.addView(grid)
        content.addView(text("التحميل يعتمد على دعم yt-dlp للموقع. لا يتم تجاوز DRM أو القيود التقنية.", 14f).apply {
            setTextColor(Color.LTGRAY)
            setPadding(dp(2), dp(16), dp(2), dp(8))
        })
        addView(content)
    }

    private fun showHome() {
        home.visibility = View.VISIBLE
        webView.visibility = View.GONE
        progress.visibility = View.GONE
        address.setText("")
    }

    private fun showWeb() {
        home.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun navigateInput() {
        val input = address.text.toString().trim()
        if (input.isBlank()) return
        val url = when {
            input.startsWith("https://", true) || input.startsWith("http://", true) -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
        }
        navigate(url)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
        address.clearFocus()
    }

    private fun navigate(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") {
            Toast.makeText(this, "روابط HTTP وHTTPS فقط", Toast.LENGTH_SHORT).show()
            return
        }
        address.setText(url)
        showWeb()
        webView.loadUrl(url)
    }

    private fun downloadCurrent() {
        val url = webView.url
        if (webView.visibility != View.VISIBLE || url.isNullOrBlank()) {
            Toast.makeText(this, "افتح رابط فيديو أو منشور أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        // Hand off the selected page URL to YTDLnis's full format/quality/queue UI.
        startActivity(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setClass(this@SocialBrowserActivity, ShareActivity::class.java)
            putExtra(Intent.EXTRA_TEXT, url)
        })
    }

    private fun openEngine(destination: String? = null) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (destination != null) putExtra("destination", destination)
        })
    }

    @Deprecated("Compatibility with the existing project minSdk 24")
    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE) {
            if (webView.canGoBack()) webView.goBack() else showHome()
        } else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
