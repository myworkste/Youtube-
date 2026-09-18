package com.auraplay.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import com.auraplay.app.R
import com.auraplay.app.data.AppPreferences
import com.auraplay.app.databinding.DialogSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheetDialog(
    private val preferences: AppPreferences,
    private val onSettingsSaved: (newUrl: String, shouldReload: Boolean) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate current values
        binding.etDefaultUrl.setText(preferences.defaultUrl)
        binding.switchAdBlock.isChecked = preferences.isAdBlockEnabled
        binding.switchBackgroundAudio.isChecked = preferences.isBackgroundAudioEnabled
        binding.switchDataSaver.isChecked = preferences.isDataSaverEnabled

        // Preset Chips
        binding.chipYtMobile.setOnClickListener {
            binding.etDefaultUrl.setText(AppPreferences.PRESET_YT_MOBILE)
        }
        binding.chipYtMusic.setOnClickListener {
            binding.etDefaultUrl.setText(AppPreferences.PRESET_YT_MUSIC)
        }
        binding.chipSoundCloud.setOnClickListener {
            binding.etDefaultUrl.setText(AppPreferences.PRESET_SOUNDCLOUD)
        }
        binding.chipTwitch.setOnClickListener {
            binding.etDefaultUrl.setText(AppPreferences.PRESET_TWITCH)
        }

        // Clear Browser Cache & Cookies
        binding.btnClearCache.setOnClickListener {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            Toast.makeText(requireContext(), R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
        }

        // Save & Apply Button
        binding.btnSaveSettings.setOnClickListener {
            var url = binding.etDefaultUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                url = AppPreferences.DEFAULT_URL
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }

            val urlChanged = url != preferences.defaultUrl
            preferences.defaultUrl = url
            preferences.isAdBlockEnabled = binding.switchAdBlock.isChecked
            preferences.isBackgroundAudioEnabled = binding.switchBackgroundAudio.isChecked
            preferences.isDataSaverEnabled = binding.switchDataSaver.isChecked

            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
            onSettingsSaved(url, urlChanged)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheetDialog"

        fun newInstance(
            preferences: AppPreferences,
            onSettingsSaved: (newUrl: String, shouldReload: Boolean) -> Unit
        ): SettingsBottomSheetDialog {
            return SettingsBottomSheetDialog(preferences, onSettingsSaved)
        }
    }
}
