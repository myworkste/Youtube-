package com.auraplay.app.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultUrl: String
        get() = prefs.getString(KEY_DEFAULT_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_DEFAULT_URL, value.trim()).apply()

    var isAdBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADBLOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADBLOCK_ENABLED, value).apply()

    var isBackgroundAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_AUDIO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_AUDIO_ENABLED, value).apply()

    var isDataSaverEnabled: Boolean
        get() = prefs.getBoolean(KEY_DATA_SAVER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DATA_SAVER_ENABLED, value).apply()

    var lastVisitedUrl: String
        get() = prefs.getString(KEY_LAST_VISITED_URL, defaultUrl) ?: defaultUrl
        set(value) = prefs.edit().putString(KEY_LAST_VISITED_URL, value).apply()

    companion object {
        private const val PREFS_NAME = "auraplay_prefs"
        private const val KEY_DEFAULT_URL = "key_default_url"
        private const val KEY_ADBLOCK_ENABLED = "key_adblock_enabled"
        private const val KEY_BG_AUDIO_ENABLED = "key_bg_audio_enabled"
        private const val KEY_DATA_SAVER_ENABLED = "key_data_saver_enabled"
        private const val KEY_LAST_VISITED_URL = "key_last_visited_url"

        const val DEFAULT_URL = "https://m.youtube.com"
        const val PRESET_YT_MOBILE = "https://m.youtube.com"
        const val PRESET_YT_MUSIC = "https://music.youtube.com"
        const val PRESET_SOUNDCLOUD = "https://soundcloud.com"
        const val PRESET_TWITCH = "https://m.twitch.tv"
    }
}
