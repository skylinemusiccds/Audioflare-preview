package com.universe.audioflare.data.dataStore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.preferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.universe.audioflare.common.*
import com.universe.audioflare.common.Constants.Companion.FALSE
import com.universe.audioflare.common.Constants.Companion.REPEAT_ALL
import com.universe.audioflare.common.Constants.Companion.REPEAT_MODE_OFF
import com.universe.audioflare.common.Constants.Companion.REPEAT_ONE
import com.universe.audioflare.common.Constants.Companion.TRUE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val settingsDataStore = context.dataStore

    // Preferences keys
    private val COOKIE = stringPreferencesKey("cookie")
    private val LOGGED_IN = stringPreferencesKey("logged_in")
    private val LOCATION = stringPreferencesKey("location")
    private val QUALITY = stringPreferencesKey("quality")
    private val NORMALIZE_VOLUME = stringPreferencesKey("normalize_volume")
    private val SKIP_SILENT = stringPreferencesKey("skip_silent")
    private val SAVE_STATE_OF_PLAYBACK = stringPreferencesKey("save_state_of_playback")
    private val SAVE_RECENT_SONG = stringPreferencesKey("save_recent_song")
    private val RECENT_SONG_MEDIA_ID_KEY = stringPreferencesKey("recent_song_media_id")
    private val RECENT_SONG_POSITION_KEY = stringPreferencesKey("recent_song_position")
    private val SHUFFLE_KEY = stringPreferencesKey("shuffle_key")
    private val REPEAT_KEY = stringPreferencesKey("repeat_key")
    private val SEND_BACK_TO_GOOGLE = stringPreferencesKey("send_back_to_google")
    private val FROM_SAVED_PLAYLIST = stringPreferencesKey("from_saved_playlist")
    private val MUSIXMATCH_LOGGED_IN = stringPreferencesKey("musixmatch_logged_in")
    private val LYRICS_PROVIDER = stringPreferencesKey("lyrics_provider")
    private val TRANSLATION_LANGUAGE = stringPreferencesKey("translation_language")
    private val USE_TRANSLATION_LANGUAGE = stringPreferencesKey("use_translation_language")
    private val MUSIXMATCH_COOKIE = stringPreferencesKey("musixmatch_cookie")
    private val SPONSOR_BLOCK_ENABLED = stringPreferencesKey("sponsor_block_enabled")
    private val MAX_SONG_CACHE_SIZE = preferencesKey<Int>("maxSongCacheSize")
    private val WATCH_VIDEO_INSTEAD_OF_PLAYING_AUDIO =
        stringPreferencesKey("watch_video_instead_of_playing_audio")
    private val VIDEO_QUALITY = stringPreferencesKey("video_quality")
    private val SPDC_TOKEN_KEY = stringPreferencesKey("spdc_token")
    private val SPDC = stringPreferencesKey("spdc")
    private val SPOTIFY_LYRICS = stringPreferencesKey("spotify_lyrics")
    private val SPOTIFY_CANVAS = stringPreferencesKey("spotify_canvas")
    private val SPOTIFY_PERSONAL_TOKEN = stringPreferencesKey("spotify_personal_token")
    private val SPOTIFY_PERSONAL_TOKEN_EXPIRES = preferencesKey<Long>("spotify_personal_token_expires")
    private val SPOTIFY_CLIENT_TOKEN = stringPreferencesKey("spotify_client_token")
    private val HOME_LIMIT = preferencesKey<Int>("home_limit")
    private val CHART_KEY = stringPreferencesKey("chart_key")

    // Flows
    val location: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[LOCATION] ?: "IN"
    }

    val quality: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[QUALITY] ?: COMMON_QUALITY.items[0].toString()
    }

    val language: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[stringPreferencesKey(SELECTED_LANGUAGE)] ?: SUPPORTED_LANGUAGE.codes.first()
    }

    val loggedIn: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[LOGGED_IN] ?: FALSE
    }

    val cookie: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[COOKIE] ?: ""
    }

    val normalizeVolume: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[NORMALIZE_VOLUME] ?: FALSE
    }

    val skipSilent: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SKIP_SILENT] ?: FALSE
    }

    val saveStateOfPlayback: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SAVE_STATE_OF_PLAYBACK] ?: FALSE
    }

    val shuffleKey: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SHUFFLE_KEY] ?: FALSE
    }

    val repeatKey: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[REPEAT_KEY] ?: REPEAT_MODE_OFF
    }

    val saveRecentSongAndQueue: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SAVE_RECENT_SONG] ?: FALSE
    }

    val recentMediaId: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[RECENT_SONG_MEDIA_ID_KEY] ?: ""
    }

    val recentPosition: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[RECENT_SONG_POSITION_KEY] ?: "0"
    }

    val playlistFromSaved: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[FROM_SAVED_PLAYLIST] ?: ""
    }

    val sendBackToGoogle: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SEND_BACK_TO_GOOGLE] ?: TRUE
    }

    val sponsorBlockEnabled: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SPONSOR_BLOCK_ENABLED] ?: FALSE
    }

    val enableTranslateLyric: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[USE_TRANSLATION_LANGUAGE] ?: FALSE
    }

    val lyricsProvider: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[LYRICS_PROVIDER] ?: MUSIXMATCH
    }

    val musixmatchLoggedIn: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[MUSIXMATCH_LOGGED_IN] ?: FALSE
    }

    val translationLanguage: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[TRANSLATION_LANGUAGE] ?: if (language.first().length >= 2) {
            language.first().substring(0..1)
        } else {
            "en"
        }
    }

    val musixmatchCookie: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[MUSIXMATCH_COOKIE] ?: ""
    }

    val spdcToken: Flow<String?> = settingsDataStore.data.map { preferences ->
        preferences[SPDC_TOKEN_KEY]
    }

    val spdc: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SPDC] ?: ""
    }

    val spotifyLyrics: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SPOTIFY_LYRICS] ?: TRUE
    }

    val spotifyCanvas: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SPOTIFY_CANVAS] ?: TRUE
    }

    val spotifyPersonalToken: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SPOTIFY_PERSONAL_TOKEN] ?: ""
    }

    val spotifyPersonalTokenExpires: Flow<Long> = settingsDataStore.data.map { preferences ->
        preferences[SPOTIFY_PERSONAL_TOKEN_EXPIRES] ?: 0
    }

    val spotifyClientToken: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[SPOTIFY_CLIENT_TOKEN] ?: ""
    }

    val homeLimit: Flow<Int> = settingsDataStore.data.map { preferences ->
        preferences[HOME_LIMIT] ?: 5
    }

    val chartKey: Flow<String> = settingsDataStore.data.map { preferences ->
        preferences[CHART_KEY] ?: "IN"
    }

    suspend fun setLocation(location: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[LOCATION] = location
            }
        }
    }

    suspend fun setQuality(quality: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[QUALITY] = quality
            }
        }
    }

    suspend fun setCookie(cookie: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[COOKIE] = cookie
            }
        }
    }

    suspend fun setLoggedIn(logged: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[LOGGED_IN] = if (logged) TRUE else FALSE
            }
        }
    }

    suspend fun setNormalizeVolume(normalize: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[NORMALIZE_VOLUME] = if (normalize) TRUE else FALSE
            }
        }
    }

    suspend fun setSkipSilent(skip: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SKIP_SILENT] = if (skip) TRUE else FALSE
            }
        }
    }

    suspend fun setSaveStateOfPlayback(save: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SAVE_STATE_OF_PLAYBACK] = if (save) TRUE else FALSE
            }
        }
    }

    suspend fun setSaveRecentSong(save: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SAVE_RECENT_SONG] = if (save) TRUE else FALSE
            }
        }
    }

    suspend fun setRecentSongMediaId(mediaId: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[RECENT_SONG_MEDIA_ID_KEY] = mediaId
            }
        }
    }

    suspend fun setRecentSongPosition(position: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[RECENT_SONG_POSITION_KEY] = position
            }
        }
    }

    suspend fun setShuffleKey(shuffle: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SHUFFLE_KEY] = if (shuffle) TRUE else FALSE
            }
        }
    }

    suspend fun setRepeatKey(repeatMode: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[REPEAT_KEY] = repeatMode
            }
        }
    }

    suspend fun setSendBackToGoogle(send: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SEND_BACK_TO_GOOGLE] = if (send) TRUE else FALSE
            }
        }
    }

    suspend fun setFromSavedPlaylist(fromSaved: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[FROM_SAVED_PLAYLIST] = if (fromSaved) TRUE else FALSE
            }
        }
    }

    suspend fun setMusixmatchLoggedIn(loggedIn: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[MUSIXMATCH_LOGGED_IN] = if (loggedIn) TRUE else FALSE
            }
        }
    }

    suspend fun setLyricsProvider(provider: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[LYRICS_PROVIDER] = provider
            }
        }
    }

    suspend fun setTranslationLanguage(language: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[TRANSLATION_LANGUAGE] = language
            }
        }
    }

    suspend fun setUseTranslationLanguage(useTranslation: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[USE_TRANSLATION_LANGUAGE] = if (useTranslation) TRUE else FALSE
            }
        }
    }

    suspend fun setMusixmatchCookie(cookie: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[MUSIXMATCH_COOKIE] = cookie
            }
        }
    }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPONSOR_BLOCK_ENABLED] = if (enabled) TRUE else FALSE
            }
        }
    }

    suspend fun setSpdcToken(spdcToken: String?) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                if (spdcToken != null) {
                    settings[SPDC_TOKEN_KEY] = spdcToken
                } else {
                    settings.remove(SPDC_TOKEN_KEY)
                }
            }
        }
    }

    suspend fun setSpdc(spdc: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPDC] = spdc
            }
        }
    }

    suspend fun setSpotifyLyrics(spotifyLyrics: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPOTIFY_LYRICS] = if (spotifyLyrics) TRUE else FALSE
            }
        }
    }

    suspend fun setSpotifyCanvas(spotifyCanvas: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPOTIFY_CANVAS] = if (spotifyCanvas) TRUE else FALSE
            }
        }
    }

    suspend fun setSpotifyPersonalToken(token: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPOTIFY_PERSONAL_TOKEN] = token
            }
        }
    }

    suspend fun setSpotifyPersonalTokenExpires(expires: Long) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPOTIFY_PERSONAL_TOKEN_EXPIRES] = expires
            }
        }
    }

    suspend fun setSpotifyClientToken(token: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[SPOTIFY_CLIENT_TOKEN] = token
            }
        }
    }

    suspend fun setHomeLimit(homeLimit: Int) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[HOME_LIMIT] = homeLimit
            }
        }
    }

    suspend fun setChartKey(chartKey: String) {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings[CHART_KEY] = chartKey
            }
        }
    }

    suspend fun clearAllDataStore() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.clear()
            }
        }
    }

    suspend fun clearCookie() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.remove(COOKIE)
            }
        }
    }

    suspend fun clearSpdcToken() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.remove(SPDC_TOKEN_KEY)
            }
        }
    }

    suspend fun clearMusixmatchCookie() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.remove(MUSIXMATCH_COOKIE)
            }
        }
    }

    suspend fun clearSpotifyPersonalToken() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.remove(SPOTIFY_PERSONAL_TOKEN)
            }
        }
    }

    suspend fun clearSpotifyClientToken() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.remove(SPOTIFY_CLIENT_TOKEN)
            }
        }
    }

    suspend fun clearSpotifyToken() {
        withContext(Dispatchers.IO) {
            settingsDataStore.edit { settings ->
                settings.remove(SPOTIFY_PERSONAL_TOKEN)
                settings.remove(SPOTIFY_CLIENT_TOKEN)
                settings.remove(SPOTIFY_PERSONAL_TOKEN_EXPIRES)
            }
        }
    }
}
