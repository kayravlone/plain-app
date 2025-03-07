package com.ismartcoding.plain.data.preference

import android.content.Context
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ismartcoding.lib.helpers.CryptoHelper
import com.ismartcoding.lib.helpers.JsonHelper.jsonEncode
import com.ismartcoding.lib.helpers.StringHelper
import com.ismartcoding.plain.TempData
import com.ismartcoding.plain.data.enums.DarkTheme
import com.ismartcoding.plain.data.enums.Language
import com.ismartcoding.plain.data.enums.PasswordType
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.audio.DPlaylistAudio
import com.ismartcoding.plain.features.audio.MediaPlayMode
import com.ismartcoding.plain.features.device.DeviceSortBy
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.features.video.DVideo
import kotlinx.serialization.json.Json
import java.util.Locale

object PasswordPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("password")
}

object PasswordTypePreference : BasePreference<Int>() {
    override val default = PasswordType.RANDOM.value
    override val key = intPreferencesKey("password_type")

    suspend fun putAsync(
        context: Context,
        value: PasswordType,
    ) {
        putAsync(context, value.value)
    }

    fun getValue(preferences: Preferences): PasswordType {
        return PasswordType.parse(get(preferences))
    }

    suspend fun getValueAsync(context: Context): PasswordType {
        return PasswordType.parse(getAsync(context))
    }
}

object AuthTwoFactorPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("auth_two_factor")
}

object AuthDevTokenPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("auth_dev_token")
}

object UrlTokenPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("url_token")

    suspend fun ensureValueAsync(
        context: Context,
        preferences: Preferences,
    ) {
        TempData.urlToken = get(preferences)
        if (TempData.urlToken.isEmpty()) {
            TempData.urlToken = CryptoHelper.generateAESKey()
            putAsync(context, TempData.urlToken)
        }
    }

    suspend fun resetAsync(context: Context) {
        TempData.urlToken = CryptoHelper.generateAESKey()
        putAsync(context, TempData.urlToken)
    }
}

object ApiPermissionsPreference : BasePreference<Set<String>>() {
    override val default = setOf<String>()
    override val key = stringSetPreferencesKey("api_permissions")

    suspend fun putAsync(
        context: Context,
        permission: Permission,
        enable: Boolean,
    ) {
        val permissions = getAsync(context).toMutableSet()
        if (enable) {
            permissions.add(permission.name)
        } else {
            permissions.remove(permission.name)
        }
        putAsync(context, permissions)
    }
}

object HttpPortPreference : BasePreference<Int>() {
    override val default = 8080
    override val key = intPreferencesKey("http_port")
}

object HttpsPortPreference : BasePreference<Int>() {
    override val default = 8443
    override val key = intPreferencesKey("https_port")
}

object DarkThemePreference : BasePreference<Int>() {
    override val default = DarkTheme.UseDeviceTheme.value
    override val key = intPreferencesKey("dark_theme")

    suspend fun putAsync(
        context: Context,
        value: DarkTheme,
    ) {
        putAsync(context, value.value)
        setDarkMode(value)
    }

    fun setDarkMode(theme: DarkTheme) {
        when (theme) {
            DarkTheme.ON -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            DarkTheme.OFF -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
}

object CustomPrimaryColorPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("custom_primary_color")
}

object AmoledDarkThemePreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("amoled_dark_theme")
}

object ThemeIndexPreference : BasePreference<Int>() {
    override val default = 5
    override val key = intPreferencesKey("theme_index")
}

object KeepScreenOnPreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("keep_screen_on")
}

object SystemScreenTimeoutPreference : BasePreference<Int>() {
    override val default = 0
    override val key = intPreferencesKey("system_screen_timeout")
}

object LanguagePreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("locale")

    suspend fun getLocaleAsync(context: Context): Locale? {
        return getLocale(getAsync(context))
    }

    fun getLocale(preferences: Preferences): Locale? {
        return getLocale(get(preferences))
    }

    private fun getLocale(value: String): Locale? {
        if (value.isEmpty()) {
            return null
        }

        val s = value.split("-")
        return if (s.size > 1) {
            Locale(s[0], s[1])
        } else {
            Locale(value)
        }
    }

    suspend fun putAsync(
        context: Context,
        locale: Locale?,
    ) {
        var value = ""
        if (locale != null) {
            value = locale.language
            if (locale.country.isNotEmpty()) {
                value += "-${locale.country}"
            }
        }
        putAsync(context, value)
        Language.setLocale(context, locale ?: LocaleList.getDefault().get(0))
    }
}

object WebPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("web")

    override suspend fun putAsync(
        context: Context,
        value: Boolean,
    ) {
        TempData.webEnabled = value
        super.putAsync(context, value)
    }
}

object ExchangeRatePreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("exchange")

    fun getConfig(preferences: Preferences): ExchangeConfig {
        val str = get(preferences)
        if (str.isEmpty()) {
            return ExchangeConfig()
        }
        return Json.decodeFromString(str)
    }

    suspend fun putAsync(
        context: Context,
        value: ExchangeConfig,
    ) {
        putAsync(context, jsonEncode(value))
    }
}

object ClientIdPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("client_id")

    suspend fun ensureValueAsync(
        context: Context,
        preferences: Preferences,
    ) {
        TempData.clientId = get(preferences)
        if (TempData.clientId.isEmpty()) {
            TempData.clientId = StringHelper.shortUUID()
            putAsync(context, TempData.clientId)
        }
    }
}

object KeyStorePasswordPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("key_store_password")

    suspend fun ensureValueAsync(
        context: Context,
        preferences: Preferences,
    ) {
        TempData.keyStorePassword = get(preferences)
        if (TempData.keyStorePassword.isEmpty()) {
            TempData.keyStorePassword = StringHelper.shortUUID()
            putAsync(context, TempData.keyStorePassword)
        }
    }
}

object DeviceSortByPreference : BasePreference<Int>() {
    override val default = DeviceSortBy.LAST_ACTIVE.ordinal
    override val key = intPreferencesKey("device_sort_by")

    suspend fun putAsync(
        context: Context,
        value: DeviceSortBy,
    ) {
        putAsync(context, value.ordinal)
    }

    suspend fun getValueAsync(context: Context): DeviceSortBy {
        val value = getAsync(context)
        return DeviceSortBy.values().find { it.ordinal == value } ?: DeviceSortBy.LAST_ACTIVE
    }
}

object AudioPlayModePreference : BasePreference<Int>() {
    override val default = MediaPlayMode.REPEAT.ordinal
    override val key = intPreferencesKey("audio_play_mode")

    suspend fun putAsync(
        context: Context,
        value: MediaPlayMode,
    ) {
        putAsync(context, value.ordinal)
    }

    suspend fun getValueAsync(context: Context): MediaPlayMode {
        val value = getAsync(context)
        return MediaPlayMode.values().find { it.ordinal == value } ?: MediaPlayMode.REPEAT
    }
}

object AudioSortByPreference : BasePreference<Int>() {
    override val default = FileSortBy.DATE_DESC.ordinal
    override val key = intPreferencesKey("audio_sort_by")

    suspend fun putAsync(
        context: Context,
        value: FileSortBy,
    ) {
        putAsync(context, value.ordinal)
    }

    suspend fun getValueAsync(context: Context): FileSortBy {
        val value = getAsync(context)
        return FileSortBy.values().find { it.ordinal == value } ?: FileSortBy.DATE_DESC
    }
}

object VideoSortByPreference : BasePreference<Int>() {
    override val default = FileSortBy.DATE_DESC.ordinal
    override val key = intPreferencesKey("video_sort_by")

    suspend fun putAsync(
        context: Context,
        value: FileSortBy,
    ) {
        putAsync(context, value.ordinal)
    }

    suspend fun getValueAsync(context: Context): FileSortBy {
        val value = getAsync(context)
        return FileSortBy.values().find { it.ordinal == value } ?: FileSortBy.DATE_DESC
    }
}

object ImageSortByPreference : BasePreference<Int>() {
    override val default = FileSortBy.DATE_DESC.ordinal
    override val key = intPreferencesKey("image_sort_by")

    suspend fun putAsync(
        context: Context,
        value: FileSortBy,
    ) {
        putAsync(context, value.ordinal)
    }

    suspend fun getValueAsync(context: Context): FileSortBy {
        val value = getAsync(context)
        return FileSortBy.values().find { it.ordinal == value } ?: FileSortBy.DATE_DESC
    }
}

object FileSortByPreference : BasePreference<Int>() {
    override val default = FileSortBy.NAME_ASC.ordinal
    override val key = intPreferencesKey("file_sort_by")

    suspend fun putAsync(
        context: Context,
        value: FileSortBy,
    ) {
        putAsync(context, value.ordinal)
    }

    suspend fun getValueAsync(context: Context): FileSortBy {
        val value = getAsync(context)
        return FileSortBy.values().find { it.ordinal == value } ?: FileSortBy.NAME_ASC
    }
}

object ShowHiddenFilesPreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("show_hidden_files")
}

object ChatGPTApiKeyPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("chat_gpt_api_key")
}

object NoteEditModePreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("note_edit_mode")
}

object FeedAutoRefreshPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("feed_auto_refresh")
}

object FeedAutoRefreshIntervalPreference : BasePreference<Int>() {
    override val default = 7200
    override val key = intPreferencesKey("feed_auto_refresh_interval")
}

object FeedAutoRefreshOnlyWifiPreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("feed_auto_refresh_only_wifi")
}

object EditorAccessoryLevelPreference : BasePreference<Int>() {
    override val default = 0
    override val key = intPreferencesKey("editor_accessory_level")
}

object EditorWrapContentPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("editor_wrap_content")
}

object EditorShowLineNumbersPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("editor_show_line_numbers")
}

object EditorSyntaxHighlightPreference : BasePreference<Boolean>() {
    override val default = true
    override val key = booleanPreferencesKey("editor_syntax_highlight")
}

object AudioSleepTimerMinutesPreference : BasePreference<Int>() {
    override val default = 30
    override val key = intPreferencesKey("audio_sleep_timer_minutes")
}

object AudioSleepTimerFinishLastPreference : BasePreference<Boolean>() {
    override val default = false
    override val key = booleanPreferencesKey("audio_sleep_timer_finish_last")
}

object ScanHistoryPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("scan_history")

    suspend fun getValueAsync(context: Context): List<String> {
        val str = getAsync(context)
        if (str.isEmpty()) {
            return listOf()
        }
        return Json.decodeFromString(str)
    }

    suspend fun putAsync(
        context: Context,
        value: List<String>,
    ) {
        putAsync(context, jsonEncode(value))
    }
}

object AudioPlaylistPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("audio_playlist")

    suspend fun getValueAsync(context: Context): List<DPlaylistAudio> {
        val str = getAsync(context)
        if (str.isEmpty()) {
            return listOf()
        }
        return Json.decodeFromString(str)
    }

    suspend fun putAsync(
        context: Context,
        value: List<DPlaylistAudio>,
    ) {
        putAsync(context, jsonEncode(value))
    }

    suspend fun deleteAsync(
        context: Context,
        paths: Set<String>,
    ) {
        putAsync(
            context,
            getValueAsync(context).toMutableList().apply {
                removeIf { paths.contains(it.path) }
            },
        )
    }

    suspend fun addAsync(
        context: Context,
        audios: List<DPlaylistAudio>,
    ) {
        val items = getValueAsync(context).toMutableList()
        items.removeIf { i -> audios.any { it.path == i.path } }
        items.addAll(audios)
        putAsync(context, items)
    }
}

object AudioPlayingPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("audio_playing")

    suspend fun getValueAsync(context: Context): DPlaylistAudio? {
        val str = getAsync(context)
        if (str.isEmpty()) {
            return null
        }
        return Json.decodeFromString(str)
    }

    suspend fun putAsync(
        context: Context,
        value: DPlaylistAudio?,
    ) {
        putAsync(context, if (value == null) "" else jsonEncode(value))
    }
}

object VideoPlaylistPreference : BasePreference<String>() {
    override val default = ""
    override val key = stringPreferencesKey("video_playlist")

    suspend fun getValueAsync(context: Context): List<DVideo> {
        val str = getAsync(context)
        if (str.isEmpty()) {
            return listOf()
        }
        return Json.decodeFromString(str)
    }

    suspend fun putAsync(
        context: Context,
        value: List<DVideo>,
    ) {
        putAsync(context, jsonEncode(value))
    }

    suspend fun deleteAsync(
        context: Context,
        paths: Set<String>,
    ) {
        putAsync(
            context,
            getValueAsync(context).toMutableList().apply {
                removeIf { paths.contains(it.path) }
            },
        )
    }

    suspend fun addAsync(
        context: Context,
        videos: List<DVideo>,
    ) {
        val items = getValueAsync(context).toMutableList()
        items.removeIf { i -> videos.any { it.path == i.path } }
        items.addAll(videos)
        putAsync(context, items)
    }
}
