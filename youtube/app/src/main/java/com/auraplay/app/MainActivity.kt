package com.auraplay.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.auraplay.app.data.AppPreferences
import com.auraplay.app.databinding.ActivityMainBinding
import com.auraplay.app.service.MediaNotificationManager
import com.auraplay.app.service.MediaPlaybackData
import com.auraplay.app.service.MediaPlaybackService
import com.auraplay.app.service.PlaybackStateHolder
import com.auraplay.app.ui.SettingsBottomSheetDialog
import com.auraplay.app.webview.AuraWebChromeClient
import com.auraplay.app.webview.AuraWebViewClient
import com.auraplay.app.webview.WebMediaBridge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AppPreferences
    private lateinit var webChromeClient: AuraWebChromeClient
    private lateinit var mediaBridge: WebMediaBridge

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted for Android 13 Spotify-style media notifications
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        checkNotificationPermission()
        setupMediaBridgeCallbacks()
        setupWebView()
        setupTopBarControls()
        setupMiniPlayerDock()
        setupBackNavigation()
        observePlaybackState()

        // Load the default startup URL as specified by the user
        val targetUrl = preferences.defaultUrl
        binding.webView.loadUrl(targetUrl)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = false
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        // Use modern mobile User-Agent optimized for YouTube mobile and HTML5 media
        val defaultUserAgent = webSettings.userAgentString
        webSettings.userAgentString = "$defaultUserAgent AuraPlay/1.0 (Android 13 Go Edition)"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        // WebChromeClient for HTML5 video fullscreen and progress bar
        webChromeClient = AuraWebChromeClient(
            progressBar = binding.pageProgressBar,
            webViewContainer = binding.webView,
            fullscreenContainer = binding.fullscreenContainer,
            onFullscreenChanged = { isFullscreen ->
                binding.topBar.visibility = if (isFullscreen) View.GONE else View.VISIBLE
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (isFullscreen) {
                    controller.systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        )
        binding.webView.webChromeClient = webChromeClient

        // WebViewClient for Ad-Blocking and URL loading
        val webViewClient = AuraWebViewClient(
            context = this,
            preferences = preferences,
            onPageFinishedListener = { url, _ ->
                updateDomainIndicator(url)
            }
        )
        binding.webView.webViewClient = webViewClient

        // JavaScript Bridge for HTML5 Media & Ad Events
        mediaBridge = WebMediaBridge(
            onPlaybackUpdate = {
                val data = PlaybackStateHolder.playbackState.value
                if (data.isPlaying && preferences.isBackgroundAudioEnabled) {
                    startMediaService()
                }
            }
        )
        binding.webView.addJavascriptInterface(mediaBridge, WebMediaBridge.JS_INTERFACE_NAME)
    }

    private fun setupMediaBridgeCallbacks() {
        PlaybackStateHolder.onPlayRequested = {
            runOnUiThread {
                WebMediaBridge.sendPlay(binding.webView)
            }
        }
        PlaybackStateHolder.onPauseRequested = {
            runOnUiThread {
                WebMediaBridge.sendPause(binding.webView)
            }
        }
        PlaybackStateHolder.onSeekForwardRequested = {
            runOnUiThread {
                WebMediaBridge.sendSeek(binding.webView, 10)
            }
        }
        PlaybackStateHolder.onSeekBackwardRequested = {
            runOnUiThread {
                WebMediaBridge.sendSeek(binding.webView, -10)
            }
        }
        PlaybackStateHolder.onStopRequested = {
            runOnUiThread {
                WebMediaBridge.sendPause(binding.webView)
                binding.dockMiniPlayer.visibility = View.GONE
            }
        }
    }

    private fun setupTopBarControls() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            }
        }

        binding.btnReload.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnHome.setOnClickListener {
            binding.webView.loadUrl(preferences.defaultUrl)
        }

        // Required Settings Icon
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialog = SettingsBottomSheetDialog.newInstance(preferences) { newUrl, shouldReload ->
            if (shouldReload) {
                binding.webView.loadUrl(newUrl)
            }
            updateDomainIndicator(binding.webView.url ?: newUrl)
        }
        dialog.show(supportFragmentManager, SettingsBottomSheetDialog.TAG)
    }

    private fun setupMiniPlayerDock() {
        binding.btnMiniPlayPause.setOnClickListener {
            val isCurrentlyPlaying = PlaybackStateHolder.playbackState.value.isPlaying
            if (isCurrentlyPlaying) {
                WebMediaBridge.sendPause(binding.webView)
            } else {
                WebMediaBridge.sendPlay(binding.webView)
            }
        }

        binding.btnMiniPrev.setOnClickListener {
            WebMediaBridge.sendSeek(binding.webView, -10)
        }

        binding.btnMiniNext.setOnClickListener {
            WebMediaBridge.sendSeek(binding.webView, 10)
        }

        binding.btnMiniClose.setOnClickListener {
            WebMediaBridge.sendPause(binding.webView)
            stopMediaService()
            binding.dockMiniPlayer.visibility = View.GONE
        }
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            PlaybackStateHolder.playbackState.collectLatest { data ->
                updateUiWithPlaybackData(data)
            }
        }
    }

    private fun updateUiWithPlaybackData(data: MediaPlaybackData) {
        // Update Mini Player Dock
        if (data.hasActiveMedia) {
            binding.dockMiniPlayer.visibility = View.VISIBLE
            binding.tvMiniTitle.text = data.title
            binding.tvMiniArtist.text = data.artist

            if (data.isPlaying) {
                binding.btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
            } else {
                binding.btnMiniPlayPause.setImageResource(R.drawable.ic_play)
            }

            if (data.artworkBitmap != null) {
                binding.imgMiniThumb.setImageBitmap(data.artworkBitmap)
            } else {
                binding.imgMiniThumb.setImageResource(R.drawable.ic_music_note)
            }

            binding.tvLowDataBadge.visibility = if (data.isLowDataMode) View.VISIBLE else View.GONE
        }

        // Update Shield Count
        if (data.adBlockedCount > 0) {
            binding.tvBlockedCount.text = "${data.adBlockedCount} Ads Strip"
            binding.tvBlockedCount.visibility = View.VISIBLE
        } else {
            binding.tvBlockedCount.text = "Shield Active"
            binding.tvBlockedCount.visibility = View.VISIBLE
        }
    }

    private fun updateDomainIndicator(url: String) {
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            binding.tvCurrentDomain.text = host.removePrefix("www.").removePrefix("m.")
        } catch (e: Exception) {
            binding.tvCurrentDomain.text = url
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webChromeClient.isCustomViewShowing()) {
                    webChromeClient.onHideCustomView()
                } else if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun startMediaService() {
        if (!preferences.isBackgroundAudioEnabled) return
        val serviceIntent = Intent(this, MediaPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopMediaService() {
        val serviceIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = MediaNotificationManager.ACTION_STOP
        }
        startService(serviceIntent)
    }

    override fun onStop() {
        super.onStop()
        val data = PlaybackStateHolder.playbackState.value

        // When moving to the background:
        // If background audio is enabled and media is playing, keep audio streaming
        // and switch to low-data audio mode to minimize data consumption.
        if (preferences.isBackgroundAudioEnabled && data.isPlaying) {
            if (preferences.isDataSaverEnabled) {
                // Drop stream to 144p and suspend visual rendering
                WebMediaBridge.sendLowDataMode(binding.webView, true)
                PlaybackStateHolder.setLowDataMode(true)
            }
            // Ensure foreground service is running
            startMediaService()
            // Do NOT call binding.webView.onPause() so HTML5 audio continues playing!
        } else {
            binding.webView.onPause()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.webView.onResume()

        // When returning to the foreground, restore standard video quality and visuals
        WebMediaBridge.sendLowDataMode(binding.webView, false)
        PlaybackStateHolder.setLowDataMode(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }
}
