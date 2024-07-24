package com.universe.audioflare.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.EMPTY
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import coil.ImageLoader
import coil.request.ImageRequest
import com.universe.audioflare.R
import com.universe.audioflare.common.ASC
import com.universe.audioflare.common.DESC
import com.universe.audioflare.common.LOCAL_PLAYLIST_ID
import com.universe.audioflare.common.LOCAL_PLAYLIST_ID_SAVED_QUEUE
import com.universe.audioflare.common.MEDIA_CUSTOM_COMMAND
import com.universe.audioflare.data.dataStore.DataStoreManager
import com.universe.audioflare.data.db.entities.SongEntity
import com.universe.audioflare.data.model.browse.album.Track
import com.universe.audioflare.data.model.searchResult.songs.Artist
import com.universe.audioflare.data.repository.MainRepository
import com.universe.audioflare.extension.connectArtists
import com.universe.audioflare.extension.getScreenSize
import com.universe.audioflare.extension.isVideo
import com.universe.audioflare.extension.toArrayListTrack
import com.universe.audioflare.extension.toListName
import com.universe.audioflare.extension.toMediaItem
import com.universe.audioflare.extension.toSongEntity
import com.universe.audioflare.extension.toTrack
import com.universe.audioflare.service.test.source.MergingMediaSourceFactory
import com.universe.audioflare.ui.widget.BasicWidget
import com.universe.audioflare.utils.Resource
import com.universe.audioflare.viewModel.FilterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

@UnstableApi
class SimpleMediaServiceHandler(
    val player: ExoPlayer,
    private val mediaSession: MediaSession,
    mediaSessionCallback: SimpleMediaSessionCallback,
    private val dataStoreManager: DataStoreManager,
    private val mainRepository: MainRepository,
    var coroutineScope: CoroutineScope,
    private val context: Context,
) : Player.Listener {
    private val TAG = "SimpleMediaServiceHandler"

    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var volumeNormalizationJob: Job? = null

    private var sleepTimerJob: Job? = null

    private var downloadImageForWidgetJob: Job? = null

    private val basicWidget = BasicWidget.instance

    private val _simpleMediaState = MutableStateFlow<SimpleMediaState>(SimpleMediaState.Initial)
    val simpleMediaState = _simpleMediaState.asStateFlow()

    private var _nowPlaying = MutableStateFlow(player.currentMediaItem)
    val nowPlaying = _nowPlaying.asStateFlow()

    private var _queueData = MutableStateFlow<QueueData?>(null)
    val queueData = _queueData.asStateFlow()

    private var _controlState = MutableStateFlow(ControlState(
        isPlaying = player.isPlaying,
        isShuffle = player.shuffleModeEnabled,
        repeatState = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatState.One
            Player.REPEAT_MODE_ALL -> RepeatState.All
            Player.REPEAT_MODE_OFF -> RepeatState.None
            else -> {
                RepeatState.None
            }
        },
        isLiked = false,
        isNextAvailable = player.hasNextMediaItem(),
        isPreviousAvailable = player.hasPreviousMediaItem(),

    ))
    val controlState = _controlState.asStateFlow()

    private var _stateFlow = MutableStateFlow<StateSource>(StateSource.STATE_CREATED)
    val stateFlow = _stateFlow.asStateFlow()
    private var _currentSongIndex = MutableStateFlow<Int>(player.currentMediaItemIndex)
    val currentSongIndex = _currentSongIndex.asSharedFlow()

    private var _nowPlayingState = MutableStateFlow(NowPlayingTrackState.initial())
    val nowPlayingState = _nowPlayingState.asStateFlow()

    private var _sleepTimerState = MutableStateFlow<SleepTimerState>(SleepTimerState(false, 0))
    val sleepTimerState = _sleepTimerState.asStateFlow()

    private var skipSilent = false

    private var normalizeVolume = false

    private var job: Job? = null

    private var updateNotificationJob: Job? = null

    private var toggleLikeJob: Job? = null

    private var loadJob: Job? = null

    private var songEntityJob: Job? = null

    init {
        player.addListener(this)
        job = Job()
        sleepTimerJob = Job()
        volumeNormalizationJob = Job()
        updateNotificationJob = Job()
        toggleLikeJob = Job()
        loadJob = Job()
        songEntityJob = Job()
        downloadImageForWidgetJob = Job()
        skipSilent = runBlocking { dataStoreManager.skipSilent.first() == DataStoreManager.TRUE }
        normalizeVolume =
            runBlocking { dataStoreManager.normalizeVolume.first() == DataStoreManager.TRUE }
        if (runBlocking { dataStoreManager.saveStateOfPlayback.first() } == DataStoreManager.TRUE) {
            Log.d("CHECK INIT", "TRUE")
            val shuffleKey = runBlocking { dataStoreManager.shuffleKey.first() }
            val repeatKey = runBlocking { dataStoreManager.repeatKey.first() }
            Log.d("CHECK INIT", "Shuffle: $shuffleKey")
            Log.d("CHECK INIT", "Repeat: $repeatKey")
            player.shuffleModeEnabled = shuffleKey == DataStoreManager.TRUE
            player.repeatMode =
                when (repeatKey) {
                    DataStoreManager.REPEAT_ONE -> Player.REPEAT_MODE_ONE
                    DataStoreManager.REPEAT_ALL -> Player.REPEAT_MODE_ALL
                    DataStoreManager.REPEAT_MODE_OFF -> Player.REPEAT_MODE_OFF
                    else -> {
                        Player.REPEAT_MODE_OFF
                    }
                }
        }
        _nowPlaying.value = player.currentMediaItem
        mediaSessionCallback.apply {
            toggleLike = ::toggleLike
        }
        mayBeRestoreQueue()
    }
    private var getDataOfNowPlayingTrackStateJob: Job? = null
    private fun getDataOfNowPlayingState(mediaItem: MediaItem) {
        val videoId = if (mediaItem.isVideo()) {
            mediaItem.mediaId.removePrefix(MergingMediaSourceFactory.isVideo)
        } else {
            mediaItem.mediaId
        }
        val track = queueData.value?.listTracks?.find { it.videoId == videoId }
        _nowPlayingState.update {
            it.copy(
                mediaItem = mediaItem,
                track = track,
            )
        }
        updateWidget(mediaItem)
        getDataOfNowPlayingTrackStateJob?.cancel()
        getDataOfNowPlayingTrackStateJob = coroutineScope.launch {
            Log.w(TAG, "getDataOfNowPlayingState: $videoId")
            mainRepository.getSongById(videoId).cancellable().singleOrNull().let { songEntity ->
                if (songEntity != null) {
                    _controlState.update { it.copy(isLiked = songEntity.liked) }
                    mainRepository.updateSongInLibrary(LocalDateTime.now(), songEntity.videoId)
                    mainRepository.updateListenCount(songEntity.videoId)
                } else {
                    _controlState.update { it.copy(isLiked = false) }
                    mainRepository.insertSong(
                        track?.toSongEntity() ?: mediaItem.toSongEntity()!!
                    )
                }
                Log.w(TAG, "getDataOfNowPlayingState: $songEntity")
                Log.w(TAG, "getDataOfNowPlayingState: $track")
                _nowPlayingState.update {
                    it.copy(
                        songEntity = songEntity ?: track?.toSongEntity() ?: mediaItem.toSongEntity()
                    )
                }
                Log.w(TAG, "getDataOfNowPlayingState: ${nowPlayingState.value}")
            }
            songEntityJob?.cancel()
            songEntityJob = coroutineScope.launch {
                mainRepository.getSongAsFlow(videoId).cancellable().filterNotNull().collectLatest { songEntity ->
                    _nowPlayingState.update {
                        it.copy(
                            songEntity = songEntity
                        )
                    }
                    _controlState.update {
                        it.copy(
                            isLiked = songEntity.liked
                        )
                    }
                }
            }
        }
    }

    private fun toggleLike() {
        Log.w(TAG, "toggleLike: ${nowPlayingState.value.mediaItem.mediaId}")
        toggleLikeJob?.cancel()
        toggleLikeJob =
            coroutineScope.launch {
                var id = (player.currentMediaItem?.mediaId ?: "" )
                if (id.contains("Video")) {
                    id = id.removePrefix("Video")
                }
                mainRepository.updateLikeStatus(
                    id,
                    if (!(controlState.first().isLiked)) 1 else 0,
                )
                delay(200)
                updateNotification()
            }
    }

    fun like(liked: Boolean) {
        _controlState.value = _controlState.value.copy(isLiked = liked)
        updateNotification()
    }

    // Set sleep timer
    fun sleepStart(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob =
            coroutineScope.launch(Dispatchers.Main) {
                _sleepTimerState.update {
                    it.copy(isDone = false, timeRemaining = minutes)
                }
                var count = minutes
                while (count > 0) {
                    delay(60 * 1000L)
                    count--
                    _sleepTimerState.update {
                        it.copy(isDone = false, timeRemaining = count)
                    }
                }
                player.pause()
                _sleepTimerState.update {
                    it.copy(isDone = true, timeRemaining = 0)
                }
            }
    }

    fun sleepStop() {
        sleepTimerJob?.cancel()
        _sleepTimerState.value = SleepTimerState(false, 0)
    }

    private fun updateNextPreviousTrackAvailability() {
        _controlState.value = _controlState.value.copy(
            isNextAvailable = player.hasNextMediaItem(),
            isPreviousAvailable = player.hasPreviousMediaItem(),
        )
    }

    private fun getMediaItemWithIndex(index: Int): MediaItem {
        return player.getMediaItemAt(index)
    }

    fun removeMediaItem(position: Int) {
        player.removeMediaItem(position)
        val temp = _queueData.value?.listTracks
        temp?.removeAt(position)
        _queueData.value = _queueData.value?.copy(
            listTracks = temp ?: arrayListOf(),
        )
        _currentSongIndex.value = player.currentMediaItemIndex
    }

    fun addMediaItem(
        mediaItem: MediaItem,
        playWhenReady: Boolean = true,
    ) {
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun addMediaItemNotSet(mediaItem: MediaItem) {
        player.addMediaItem(mediaItem)
        if (player.mediaItemCount == 1) {
            player.prepare()
            player.playWhenReady = true
        }
        updateNextPreviousTrackAvailability()
    }

    fun addMediaItemNotSet(
        mediaItem: MediaItem,
        index: Int,
    ) {
        player.addMediaItem(index, mediaItem)
        if (player.mediaItemCount == 1) {
            player.prepare()
            player.playWhenReady = true
        }
        updateNextPreviousTrackAvailability()
    }

    fun clearMediaItems() {
        player.clearMediaItems()
    }

    fun addMediaItemList(mediaItemList: List<MediaItem>) {
        for (mediaItem in mediaItemList) {
            addMediaItemNotSet(mediaItem)
        }
        Log.d("Media Item List", "addMediaItemList: ${player.mediaItemCount}")
    }

    fun playMediaItemInMediaSource(index: Int) {
        player.seekTo(index, 0)
        player.prepare()
        player.playWhenReady = true
    }

    fun moveMediaItem(
        fromIndex: Int,
        newIndex: Int,
    ) {
        player.moveMediaItem(fromIndex, newIndex)
        _currentSongIndex.value = player.currentMediaItemIndex
    }

    suspend fun swap(
        from: Int,
        to: Int,
    ) {
        if (from < to) {
            for (i in from until to) {
                moveItemDown(i)
            }
        } else {
            for (i in from downTo to + 1) {
                moveItemUp(i)
            }
        }
    }

    suspend fun onPlayerEvent(playerEvent: PlayerEvent) {
        when (playerEvent) {
            PlayerEvent.Backward -> player.seekBack()
            PlayerEvent.Forward -> player.seekForward()
            PlayerEvent.PlayPause -> {
                if (player.isPlaying) {
                    player.pause()
                    stopProgressUpdate()
                } else {
                    player.play()
                    startProgressUpdate()
                }
            }

            PlayerEvent.Next -> player.seekToNext()
            PlayerEvent.Previous -> player.seekToPrevious()
            PlayerEvent.Stop -> {
                stopProgressUpdate()
                player.stop()
                _nowPlayingState.value = NowPlayingTrackState.initial()
            }

            is PlayerEvent.UpdateProgress -> player.seekTo((player.duration * playerEvent.newProgress / 100).toLong())
            PlayerEvent.Shuffle -> {
                if (player.shuffleModeEnabled) {
                    player.shuffleModeEnabled = false
                    _controlState.value = _controlState.value.copy(isShuffle = false)
                } else {
                    player.shuffleModeEnabled = true
                    _controlState.value = _controlState.value.copy(isShuffle = true)
                }
            }

            PlayerEvent.Repeat -> {
                when (player.repeatMode) {
                    ExoPlayer.REPEAT_MODE_OFF -> {
                        player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
                        _controlState.value = _controlState.value.copy(repeatState = RepeatState.All)
                    }

                    ExoPlayer.REPEAT_MODE_ONE -> {
                        player.repeatMode = ExoPlayer.REPEAT_MODE_OFF
                        _controlState.value = _controlState.value.copy(repeatState = RepeatState.None)
                    }

                    ExoPlayer.REPEAT_MODE_ALL -> {
                        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
                        _controlState.value = _controlState.value.copy(repeatState = RepeatState.One)
                    }

                    else -> {
                        when (controlState.first().repeatState) {
                            RepeatState.None -> {
                                player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
                                _controlState.value = _controlState.value.copy(repeatState = RepeatState.All)
                            }

                            RepeatState.One -> {
                                player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
                                _controlState.value = _controlState.value.copy(repeatState = RepeatState.All)
                            }

                            RepeatState.All -> {
                                player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
                                _controlState.value = _controlState.value.copy(repeatState = RepeatState.One)
                            }
                        }
                    }
                }
            }

            PlayerEvent.ToggleLike -> {
                toggleLike()
            }
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        val shouldBePlaying = !(player.playbackState == Player.STATE_ENDED || !player.playWhenReady)
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            if (shouldBePlaying) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        Log.d("Tracks", "onTracksChanged: ${tracks.groups.size}")
        super.onTracksChanged(tracks)
    }

    override fun onPlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_TIMEOUT -> {
                Log.e("Player Error", "onPlayerError: ${error.message}")
                Toast.makeText(
                    context,
                    context.getString(R.string.time_out_check_internet_connection_or_change_piped_instance_in_settings),
                    Toast.LENGTH_LONG,
                ).show()
                player.pause()
            }

            else -> {
                Log.e("Player Error", "onPlayerError: ${error.message}")
                Toast.makeText(
                    context,
                    context.getString(R.string.time_out_check_internet_connection_or_change_piped_instance_in_settings),
                    Toast.LENGTH_LONG,
                ).show()
                player.pause()
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Log.w("Smooth Switching Transition", "Current Position: ${player.currentPosition}")
        mayBeNormalizeVolume()
        Log.w("REASON", "onMediaItemTransition: $reason")
        Log.d("Media Item Transition", "Media Item: ${mediaItem?.mediaMetadata?.title}")
        if (mediaItem?.mediaId != _nowPlaying.value?.mediaId ) {
            _nowPlaying.value = mediaItem
        }
        if (mediaItem?.mediaId != nowPlayingState.value.mediaItem.mediaId) {
            Log.w(TAG, "onMediaItemTransition: ${mediaItem?.mediaId}")
            if (mediaItem != null) {
                getDataOfNowPlayingState(mediaItem)
            }
            else {
                _nowPlayingState.update { NowPlayingTrackState.initial() }
            }
        }
        _queueData.value?.listTracks?.let { list ->
            if (list.size > 3 && list.size - player.currentMediaItemIndex < 3
                && list.size - player.currentMediaItemIndex >= 0
                && _stateFlow.value == StateSource.STATE_INITIALIZED
                ) {
                Log.d("Check loadMore", "loadMore")
                loadMore()
            }
        }
        updateNextPreviousTrackAvailability()
        updateNotification()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                _simpleMediaState.value = SimpleMediaState.Initial
                Log.d(TAG, "onPlaybackStateChanged: Idle")
            }
            Player.STATE_ENDED -> {
                _simpleMediaState.value = SimpleMediaState.Ended
                Log.d(TAG, "onPlaybackStateChanged: Ended")
            }
            Player.STATE_BUFFERING -> {
                _simpleMediaState.value = SimpleMediaState.Buffering(player.currentPosition)
                Log.d(TAG, "onPlaybackStateChanged: Buffering")
            }
            Player.STATE_READY -> {
                Log.d(TAG, "onPlaybackStateChanged: Ready")
                _simpleMediaState.value = SimpleMediaState.Ready(player.duration)

            }
            else -> {
                Log.d(TAG, "onPlaybackStateChanged: $playbackState")
                _simpleMediaState.value =
                    SimpleMediaState.Buffering(player.currentPosition)
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        when (shuffleModeEnabled) {
            true -> {
                _controlState.value = _controlState.value.copy(isShuffle = true)
            }

            false -> {
                _controlState.value = _controlState.value.copy(isShuffle = false)
            }
        }
        updateNextPreviousTrackAvailability()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        when (repeatMode) {
            ExoPlayer.REPEAT_MODE_OFF -> _controlState.value = _controlState.value.copy(repeatState = RepeatState.None)
            ExoPlayer.REPEAT_MODE_ONE -> _controlState.value = _controlState.value.copy(repeatState = RepeatState.One)
            ExoPlayer.REPEAT_MODE_ALL -> _controlState.value = _controlState.value.copy(repeatState = RepeatState.All)
        }
        updateNextPreviousTrackAvailability()
        updateNotification()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _controlState.value = _controlState.value.copy(isPlaying = isPlaying)
        basicWidget.updatePlayingState(
            context,
            isPlaying,
        )
        if (isPlaying) {
            coroutineScope.launch(Dispatchers.Main) {
                startProgressUpdate()
            }
        } else {
            stopProgressUpdate()
            mayBeSaveRecentSong()
        }
        updateNextPreviousTrackAvailability()
    }

    private suspend fun startProgressUpdate() =
        job.run {
            while (true) {
                delay(100)
                _simpleMediaState.value = SimpleMediaState.Progress(player.currentPosition)
            }
        }

    private suspend fun startBufferedUpdate() =
        job.run {
            while (true) {
                delay(500)
                _simpleMediaState.value =
                    SimpleMediaState.Loading(player.bufferedPercentage, player.duration)
            }
        }

    fun loadMore() {
        // Separate local and remote data
        // Local Add Prefix to PlaylistID to differentiate between local and remote
        // Local: LC-PlaylistID
        val playlistId = _queueData.value?.playlistId ?: return
        Log.w("Check loadMore", playlistId.toString())
        val continuation = _queueData.value?.continuation
        Log.w("Check loadMore", continuation.toString())
        if (continuation != null) {
            if (playlistId.startsWith(LOCAL_PLAYLIST_ID)) {
                coroutineScope.launch {
                    _stateFlow.value = StateSource.STATE_INITIALIZING
                    val longId = playlistId.replace(LOCAL_PLAYLIST_ID, "").toLong()
                    Log.w("Check loadMore", longId.toString())
                    val filter = if (continuation.startsWith(ASC)) FilterState.OlderFirst else FilterState.NewerFirst
                    val offset =
                        if (filter == FilterState.OlderFirst) {
                            continuation.removePrefix(
                                ASC,
                            ).toInt()
                        } else {
                            continuation.removePrefix(DESC).toInt()
                        }
                    val total = mainRepository.getLocalPlaylist(longId).firstOrNull()?.tracks?.size ?: 0
                    mainRepository.getPlaylistPairSongByOffset(
                        longId,
                        offset,
                        filter,
                        total
                    ).singleOrNull()?.let { pair ->
                        Log.w("Check loadMore response", pair.size.toString())
                        mainRepository.getSongsByListVideoId(pair.map { it.songId }).single().let { songs ->
                            if (songs.isNotEmpty()) {
                                delay(300)
                                loadMoreCatalog(songs.toArrayListTrack())
//                                Queue.setContinuation(
//                                    playlistId,
//                                    if (filter == FilterState.OlderFirst) ASC + (offset + 1) else DESC + (offset + 1).toString(),
//                                )
                                _queueData.value = _queueData.value?.copy(
                                    continuation = if (filter == FilterState.OlderFirst) ASC + (offset + 1) else DESC + (offset + 1).toString(),
                                )
                            }
//                            loadingMore.value = false
                        }
                    }
                }
            } else {
                coroutineScope.launch {
                    _stateFlow.value = StateSource.STATE_INITIALIZING
                    Log.w("Check loadMore continuation", continuation.toString())
                    mainRepository.getContinueTrack(playlistId, continuation)
                        .singleOrNull().let { response ->
                            val list = response?.first
                            if (list != null) {
                                Log.w("Check loadMore response", response.toString())
                                loadMoreCatalog(list)
                            }
                            _queueData.value = _queueData.value?.copy(
                                continuation = response?.second,
                            )
//                            loadingMore.value = false
                        }
                }
            }
        }
    }

    fun getRelated(videoId: String) {
//            Queue.clear()
        coroutineScope.launch {
            mainRepository.getRelatedData(videoId).collect { response ->
                when (response) {
                    is Resource.Success -> {
                        loadMoreCatalog(response.data?.first ?: arrayListOf())
                        _queueData.value = _queueData.value?.copy(
                            continuation = response.data?.second,
                        )
                    }
                    is Resource.Error -> {
                        Log.d("Check Related", "getRelated: ${response.message}")
                        _queueData.value = _queueData.value?.copy(
                            continuation = null,
                        )
                    }
                }
            }
        }
    }

    fun setQueueData(
        queueData: QueueData
    ) {
        _queueData.value = queueData
    }

    fun mediaListSize(): Int {
        return player.mediaItemCount
    }

    fun getCurrentMediaItem(): MediaItem? {
        return player.currentMediaItem
    }

    private fun stopProgressUpdate() {
        job?.cancel()
    }

    private fun stopBufferedUpdate() {
        job?.cancel()
        _simpleMediaState.value =
            SimpleMediaState.Loading(player.bufferedPercentage, player.duration)
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        super.onIsLoadingChanged(isLoading)
        _simpleMediaState.value =
            SimpleMediaState.Loading(player.bufferedPercentage, player.duration)
        if (isLoading) {
            coroutineScope.launch(Dispatchers.Main) {
                startBufferedUpdate()
            }
        } else {
            stopBufferedUpdate()
        }
    }

    private fun mayBeNormalizeVolume() {
        runBlocking {
            normalizeVolume = dataStoreManager.normalizeVolume.first() == DataStoreManager.TRUE
        }
        if (!normalizeVolume) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            player.volume = 1f
            return
        }

        if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        }

        player.currentMediaItem?.mediaId?.let { songId ->
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob =
                coroutineScope.launch(Dispatchers.Main) {
                    mainRepository.getNewFormat(songId).cancellable().first().let { format ->
                        if (format != null) {
                            try {
                                loudnessEnhancer?.setTargetGain(
                                    -((format.loudnessDb ?: 0f) * 100).toInt() + 500,
                                )
                                Log.w(
                                    "Loudness",
                                    "mayBeNormalizeVolume: ${loudnessEnhancer?.targetGain}",
                                )
                                loudnessEnhancer?.enabled = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
        }
    }

    private fun maybeSkipSilent() {
        skipSilent = runBlocking { dataStoreManager.skipSilent.first() } == DataStoreManager.TRUE
        player.skipSilenceEnabled = skipSilent
    }

    private fun mayBeRestoreQueue() {
        coroutineScope.launch {
            if (dataStoreManager.saveRecentSongAndQueue.first() == DataStoreManager.TRUE) {
                val currentPlayingTrack = mainRepository.getSongById(dataStoreManager.recentMediaId.first()).singleOrNull()?.toTrack()
                if (currentPlayingTrack != null) {
                    val queue = mainRepository.getSavedQueue().singleOrNull()
                    setQueueData(
                        QueueData(
                            listTracks = queue?.firstOrNull()?.listTrack?.toCollection(arrayListOf()) ?: arrayListOf(currentPlayingTrack),
                            firstPlayedTrack = currentPlayingTrack,
                            playlistId = LOCAL_PLAYLIST_ID_SAVED_QUEUE,
                            playlistName = dataStoreManager.playlistFromSaved.first(),
                            playlistType = PlaylistType.PLAYLIST,
                            continuation = null
                        )
                    )
                    var index = queue?.firstOrNull()?.listTrack?.map { it.videoId }?.indexOf(
                        currentPlayingTrack.videoId
                    )
                    if (index == null || index == -1) index = 0
                    addMediaItem(currentPlayingTrack.toMediaItem(), playWhenReady = false)
                    seekTo(dataStoreManager.recentPosition.first())
                    loadPlaylistOrAlbum(index = index)
                }
            }
        }
    }

    private fun updateWidget(nowPlaying: MediaItem) {
        basicWidget.performUpdate(
            context,
            this,
            null,
        )
        downloadImageForWidgetJob?.cancel()
        downloadImageForWidgetJob =
            coroutineScope.launch {
                val p = getScreenSize(context)
                val widgetImageSize = p.x.coerceAtMost(p.y)
                val imageRequest =
                    ImageRequest.Builder(context)
                        .data(nowPlaying.mediaMetadata.artworkUri)
                        .size(widgetImageSize)
                        .placeholder(R.drawable.holder_video)
                        .target(
                            onSuccess = { drawable ->
                                basicWidget.updateImage(
                                    context,
                                    drawable.toBitmap(
                                        widgetImageSize,
                                        widgetImageSize,
                                    ),
                                )
                            },
                            onStart = { holder ->
                                if (holder != null) {
                                    basicWidget.updateImage(
                                        context,
                                        holder.toBitmap(
                                            widgetImageSize,
                                            widgetImageSize,
                                        ),
                                    )
                                }
                            },
                            onError = {
                                AppCompatResources.getDrawable(
                                    context,
                                    R.drawable.holder_video,
                                )
                                    ?.let { it1 ->
                                        basicWidget.updateImage(
                                            context,
                                            it1.toBitmap(
                                                widgetImageSize,
                                                widgetImageSize,
                                            ),
                                        )
                                    }
                            },
                        ).build()
                ImageLoader(context).execute(imageRequest)
            }
    }


    fun mayBeSaveRecentSong() {
        coroutineScope.launch {
            if (dataStoreManager.saveRecentSongAndQueue.first() == DataStoreManager.TRUE) {
                dataStoreManager.saveRecentSong(
                    player.currentMediaItem?.mediaId ?: "",
                    player.contentPosition,
                )
                dataStoreManager.setPlaylistFromSaved(queueData.value?.playlistName ?: "")
                Log.d("Check saved", player.currentMediaItem?.mediaMetadata?.title.toString())
                val temp: ArrayList<Track> = ArrayList()
                temp.clear()
                temp.addAll(_queueData.value?.listTracks ?: arrayListOf())
                Log.w("Check recover queue", temp.toString())
                mainRepository.recoverQueue(temp)
            }
        }
    }

    fun mayBeSavePlaybackState() {
        if (runBlocking { dataStoreManager.saveStateOfPlayback.first() } == DataStoreManager.TRUE) {
            runBlocking {
                dataStoreManager.recoverShuffleAndRepeatKey(
                    player.shuffleModeEnabled,
                    player.repeatMode,
                )
            }
        }
    }

    fun editSkipSilent(skip: Boolean) {
        skipSilent = skip
        maybeSkipSilent()
    }

    fun editNormalizeVolume(normalize: Boolean) {
        normalizeVolume = normalize
    }

    fun seekTo(position: String) {
        player.seekTo(position.toLong())
        Log.d("Check seek", "seekTo: ${player.currentPosition}")
    }

    fun skipSegment(position: Long) {
        if (position in 0..player.duration) {
            player.seekTo(position)
        } else if (position > player.duration) {
            player.seekToNext()
        }
    }

    private fun sendOpenEqualizerIntent() {
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun sendCloseEqualizerIntent() {
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
            },
        )
    }

    fun release() {
        player.stop()
        player.playWhenReady = false
        player.removeListener(this)
        sendCloseEqualizerIntent()
        if (job?.isActive == true) {
            job?.cancel()
            job = null
        }
        if (sleepTimerJob?.isActive == true) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
        }
        if (volumeNormalizationJob?.isActive == true) {
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob = null
        }
        if (toggleLikeJob?.isActive == true) {
            toggleLikeJob?.cancel()
            toggleLikeJob = null
        }
        if (updateNotificationJob?.isActive == true) {
            updateNotificationJob?.cancel()
            updateNotificationJob = null
        }
        if (loadJob?.isActive == true) {
            loadJob?.cancel()
            loadJob = null
        }
        Log.w("Service", "Check job: ${job?.isActive}")
        Log.w("Service", "scope is active: ${coroutineScope.isActive}")
    }

    @SuppressLint("PrivateResource")
    private fun updateNotification() {
        updateNotificationJob?.cancel()
        updateNotificationJob =
            coroutineScope.launch {
                var id = (player.currentMediaItem?.mediaId ?: "" )
                if (id.contains("Video")) {
                    id = id.removePrefix("Video")
                }
                val liked =
                    mainRepository.getSongById(id)
                        .first()?.liked
                if (liked != null) {
                    _controlState.value = _controlState.value.copy(isLiked = liked)
                }
                mediaSession.setCustomLayout(
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName(
                                if (liked == true) {
                                    context.getString(R.string.liked)
                                } else {
                                    context.getString(
                                        R.string.like,
                                    )
                                },
                            )
                            .setIconResId(if (liked == true) R.drawable.baseline_favorite_24 else R.drawable.baseline_favorite_border_24)
                            .setSessionCommand(SessionCommand(MEDIA_CUSTOM_COMMAND.LIKE, Bundle()))
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName(
                                when (player.repeatMode) {
                                    Player.REPEAT_MODE_ONE -> context.getString(R.string.repeat_one)

                                    Player.REPEAT_MODE_ALL -> context.getString(R.string.repeat_all)

                                    else -> context.getString(R.string.repeat_off)
                                 },
                            )
                            .setSessionCommand(
                                SessionCommand(
                                    MEDIA_CUSTOM_COMMAND.REPEAT,
                                    Bundle(),
                                ),
                            )
                            .setIconResId(
                                when (player.repeatMode) {
                                    Player.REPEAT_MODE_ONE -> R.drawable.baseline_repeat_one_24
                                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                    else -> R.drawable.baseline_repeat_24_enable
                                },
                            )
                            .build(),
                    ),
                )
            }
    }

    fun getPlayerDuration(): Long {
        return player.duration
    }

    fun getProgress(): Long {
        return player.currentPosition
    }

//    fun changeAddedState() {
//        added.value = false
//    }
//
//    fun addFirstMetadata(it: Track) {
//        added.value = true
//        catalogMetadata.add(0, it)
//        Log.d("MusicSource", "addFirstMetadata: ${it.title}, ${catalogMetadata.size}")
//    }

    @UnstableApi
    suspend fun moveItemUp(position: Int) {
        moveMediaItem(position, position - 1)
        queueData.first()?.listTracks?.let { list ->
            val temp = list[position]
            list[position] = list[position - 1]
            list[position - 1] = temp
            _queueData.value = queueData.first()?.copy(
                listTracks = list,
            )
        }
        _currentSongIndex.value = player.currentMediaItemIndex
    }

    @UnstableApi
    suspend fun moveItemDown(position: Int) {
        moveMediaItem(position, position + 1)
        queueData.first()?.listTracks?.let { list ->
            val temp = list[position]
            list[position] = list[position + 1]
            list[position + 1] = temp
            _queueData.value = queueData.first()?.copy(
                listTracks = list,
            )
        }
        _currentSongIndex.value = player.currentMediaItemIndex
    }

    @UnstableApi
    fun addFirstMediaItemToIndex(
        mediaItem: MediaItem?,
        index: Int,
    ) {
        if (mediaItem != null) {
            Log.d("MusicSource", "addFirstMediaItem: ${mediaItem.mediaId}")
            moveMediaItem(0, index)
        }
    }

    fun reset() {
        _currentSongIndex.value = 0
        _stateFlow.value = StateSource.STATE_CREATED
    }

    @UnstableApi
    suspend fun load(
        downloaded: Int = 0,
        index: Int? = null,
    ) {
        updateCatalog(downloaded, index).let {
            if (index != 0 && index != null) {
                moveMediaItem(0, index)
            }
            _stateFlow.value = StateSource.STATE_INITIALIZED
        }
    }

    suspend fun loadMoreCatalog(listTrack: ArrayList<Track>) {
        Log.d("Queue", listTrack.map { it.title }.toString())
        _stateFlow.value = StateSource.STATE_INITIALIZING
        val catalogMetadata: ArrayList<Track> = arrayListOf()
        for (i in 0 until listTrack.size) {
            val track = listTrack[i]
            var thumbUrl =
                track.thumbnails?.last()?.url
                    ?: "http://i.ytimg.com/vi/${track.videoId}/maxresdefault.jpg"
            if (thumbUrl.contains("w120")) {
                thumbUrl = Regex("([wh])120").replace(thumbUrl, "$1544")
            }
            val artistName: String = track.artists.toListName().connectArtists()
            val isSong = (track.thumbnails?.last()?.height != 0 && track.thumbnails?.last()?.height == track.thumbnails?.last()?.width
                && track.thumbnails?.last()?.height != null) && (track.thumbnails.lastOrNull()?.url?.contains("hq720") == false
                && track.thumbnails.lastOrNull()?.url?.contains("maxresdefault") == false)
            if (track.artists.isNullOrEmpty()) {
                mainRepository.getSongInfo(track.videoId).singleOrNull()
                    .let { songInfo ->
                        if (songInfo != null) {
                            catalogMetadata.add(
                                track.copy(
                                    artists =
                                        listOf(
                                            Artist(
                                                songInfo.authorId,
                                                songInfo.author ?: "",
                                            ),
                                        ),
                                ),
                            )
                            addMediaItemNotSet(
                                MediaItem.Builder().setUri(track.videoId)
                                    .setMediaId(track.videoId)
                                    .setCustomCacheKey(track.videoId)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(track.title)
                                            .setArtist(songInfo.author)
                                            .setArtworkUri(thumbUrl.toUri())
                                            .setAlbumTitle(track.album?.name)
                                            .setDescription(if (isSong) "Song" else "Video")
                                            .build(),
                                    )
                                    .build(),
                            )
                        } else {
                            val mediaItem =
                                MediaItem.Builder()
                                    .setMediaId(track.videoId)
                                    .setUri(track.videoId)
                                    .setCustomCacheKey(track.videoId)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setArtworkUri(thumbUrl.toUri())
                                            .setAlbumTitle(track.album?.name)
                                            .setTitle(track.title)
                                            .setArtist("Various Artists")
                                            .setDescription(if (isSong) "Song" else "Video")
                                            .build(),
                                    )
                                    .build()
                            addMediaItemNotSet(mediaItem)
                            catalogMetadata.add(
                                track.copy(
                                    artists = listOf(Artist("", "Various Artists")),
                                ),
                            )
                        }
                    }
            } else {
                addMediaItemNotSet(
                    MediaItem.Builder().setUri(track.videoId)
                        .setMediaId(track.videoId)
                        .setCustomCacheKey(track.videoId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(artistName)
                                .setArtworkUri(thumbUrl.toUri())
                                .setAlbumTitle(track.album?.name)
                                .setDescription(if (isSong) "Song" else "Video")
                                .build(),
                        )
                        .build(),
                )
                catalogMetadata.add(track)
            }
            Log.d(
                "MusicSource",
                "updateCatalog: ${track.title}, ${catalogMetadata.size}",
            )
            Log.d("MusicSource", "updateCatalog: ${track.title}")
        }
        _queueData.value = _queueData.value?.addTrackList(catalogMetadata)
        _stateFlow.value = StateSource.STATE_INITIALIZED
    }

    @UnstableApi
    suspend fun updateCatalog(downloaded: Int = 0, index: Int? = null): Boolean {
        _stateFlow.value = StateSource.STATE_INITIALIZING
        val tempQueue: ArrayList<Track> = arrayListOf()
        tempQueue.addAll(_queueData.value?.listTracks ?: arrayListOf())
        val catalogMetadata: ArrayList<Track> = arrayListOf()
        Log.w("SimpleMediaServiceHandler", "Catalog size: ${tempQueue.size}")
        Log.w("SimpleMediaServiceHandler", "Skip index: $index")
        for (i in 0 until tempQueue.size) {
            if (i == index) {
                continue
            }
            val track = tempQueue[i]
            var thumbUrl =
                track.thumbnails?.last()?.url
                    ?: "http://i.ytimg.com/vi/${track.videoId}/maxresdefault.jpg"
            if (thumbUrl.contains("w120")) {
                thumbUrl = Regex("([wh])120").replace(thumbUrl, "$1544")
            }
            val isSong = (track.thumbnails?.last()?.height != 0 && track.thumbnails?.last()?.height == track.thumbnails?.last()?.width
                && track.thumbnails?.last()?.height != null) && (track.thumbnails.lastOrNull()?.url?.contains("hq720") == false
                && track.thumbnails.lastOrNull()?.url?.contains("maxresdefault") == false)
            if (downloaded == 1) {
                if (track.artists.isNullOrEmpty()) {
                    mainRepository.getSongInfo(track.videoId).singleOrNull().let { songInfo ->
                            if (songInfo != null) {
                                val mediaItem =
                                    MediaItem.Builder()
                                        .setMediaId(track.videoId)
                                        .setUri(track.videoId)
                                        .setCustomCacheKey(track.videoId)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setArtworkUri(thumbUrl.toUri())
                                                .setAlbumTitle(track.album?.name)
                                                .setTitle(track.title)
                                                .setArtist(songInfo.author)
                                                .setDescription(if (isSong) "Song" else "Video")
                                                .build(),
                                        )
                                        .build()
                                addMediaItemNotSet(mediaItem)
                                catalogMetadata.add(
                                    track.copy(
                                        artists =
                                            listOf(
                                                Artist(
                                                    songInfo.authorId,
                                                    songInfo.author ?: "",
                                                ),
                                            ),
                                    ),
                                )
                            } else {
                                val mediaItem =
                                    MediaItem.Builder()
                                        .setMediaId(track.videoId)
                                        .setUri(track.videoId)
                                        .setCustomCacheKey(track.videoId)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setArtworkUri(thumbUrl.toUri())
                                                .setAlbumTitle(track.album?.name)
                                                .setTitle(track.title)
                                                .setArtist("Various Artists")
                                                .setDescription(if (isSong) "Song" else "Video")
                                                .build(),
                                        )
                                        .build()
                                addMediaItemNotSet(mediaItem)
                                catalogMetadata.add(
                                    track.copy(
                                        artists = listOf(Artist("", "Various Artists")),
                                    ),
                                )
                            }
                        }
                } else {
                    val mediaItem =
                        MediaItem.Builder()
                            .setMediaId(track.videoId)
                            .setUri(track.videoId)
                            .setCustomCacheKey(track.videoId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setArtworkUri(thumbUrl.toUri())
                                    .setAlbumTitle(track.album?.name)
                                    .setTitle(track.title)
                                    .setArtist(track.artists.toListName().connectArtists())
                                    .setDescription(if (isSong) "Song" else "Video")
                                    .build(),
                            )
                            .build()
                    addMediaItemNotSet(mediaItem)
                    catalogMetadata.add(track)
                }
                Log.d("MusicSource", "updateCatalog: ${track.title}, ${catalogMetadata.size}")
            } else {
                val artistName: String = track.artists.toListName().connectArtists()
                if (track.artists.isNullOrEmpty()) {
                    mainRepository.getSongInfo(track.videoId).cancellable().first()
                        .let { songInfo ->
                            if (songInfo != null) {
                                catalogMetadata.add(
                                    track.copy(
                                        artists =
                                            listOf(
                                                Artist(
                                                    songInfo.authorId,
                                                    songInfo.author ?: "",
                                                ),
                                            ),
                                    ),
                                )
                                addMediaItemNotSet(
                                    MediaItem.Builder().setUri(track.videoId)
                                        .setMediaId(track.videoId)
                                        .setCustomCacheKey(track.videoId)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(track.title)
                                                .setArtist(songInfo.author)
                                                .setArtworkUri(thumbUrl.toUri())
                                                .setDescription(if (isSong) "Song" else "Video")
                                                .setAlbumTitle(track.album?.name)
                                                .build(),
                                        )
                                        .build(),
                                )
                            } else {
                                val mediaItem =
                                    MediaItem.Builder()
                                        .setMediaId(track.videoId)
                                        .setUri(track.videoId)
                                        .setCustomCacheKey(track.videoId)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setArtworkUri(thumbUrl.toUri())
                                                .setAlbumTitle(track.album?.name)
                                                .setTitle(track.title)
                                                .setDescription(if (isSong) "Song" else "Video")
                                                .setArtist("Various Artists")
                                                .build(),
                                        )
                                        .build()
                                addMediaItemNotSet(mediaItem)
                                catalogMetadata.add(
                                    track.copy(
                                        artists = listOf(Artist("", "Various Artists")),
                                    ),
                                )
                            }
                        }
                } else {
                    addMediaItemNotSet(
                        MediaItem.Builder().setUri(track.videoId)
                            .setMediaId(track.videoId)
                            .setCustomCacheKey(track.videoId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(artistName)
                                    .setArtworkUri(thumbUrl.toUri())
                                    .setAlbumTitle(track.album?.name)
                                    .setDescription(if (isSong) "Song" else "Video")
                                    .build(),
                            )
                            .build(),
                    )
                    catalogMetadata.add(track)
                }
                Log.d(
                    "MusicSource",
                    "updateCatalog: ${track.title}, ${catalogMetadata.size}",
                )
                Log.d("MusicSource", "updateCatalog: ${track.title}")
            }
        }
        if (index != null) {
            catalogMetadata.add(index, tempQueue[index])
        }
        Log.w("SimpleMediaServiceHandler", "current queue: ${player.mediaItemCount}")
        Log.d("SimpleMediaServiceHandler", "updateCatalog: ${catalogMetadata.size}")
        _queueData.value = _queueData.value?.copy(
            listTracks = catalogMetadata,
        )
        return true
    }

    fun addQueueToPlayer() {
        loadJob?.cancel()
        loadJob =
            coroutineScope.launch {
                load()
            }
    }

    fun loadPlaylistOrAlbum(index: Int? = null) {
        loadJob?.cancel()
        loadJob =
            coroutineScope.launch {
                load(index = index)
            }
    }

    fun setCurrentSongIndex(index: Int) {
        _currentSongIndex.value = index
    }

    fun updateSubtitle(url: String) {
        val index = player.currentMediaItemIndex
        val mediaItem = player.currentMediaItem
        Log.w("Subtitle", "updateSubtitle: $url")
        val subtitle =
            SubtitleConfiguration.Builder(url.plus("&fmt=ttml").toUri())
                .setId(mediaItem?.mediaId)
                .setMimeType(MimeTypes.APPLICATION_TTML)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        val new = mediaItem?.buildUpon()?.setSubtitleConfigurations(listOf(subtitle))?.build()
        if (new != null) {
            player.replaceMediaItem(
                index,
                new,
            )
            println("update subtitle" + player.currentMediaItem?.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri.toString())
        }
    }

    suspend fun playNext(track: Track) {
        _stateFlow.value = StateSource.STATE_INITIALIZING
        val catalogMetadata: ArrayList<Track> = queueData.first()?.listTracks ?: arrayListOf()
        var thumbUrl =
            track.thumbnails?.last()?.url
                ?: "http://i.ytimg.com/vi/${track.videoId}/maxresdefault.jpg"
        if (thumbUrl.contains("w120")) {
            thumbUrl = Regex("([wh])120").replace(thumbUrl, "$1544")
        }
        val artistName: String = track.artists.toListName().connectArtists()
        val isSong = (track.thumbnails?.last()?.height != 0 && track.thumbnails?.last()?.height == track.thumbnails?.last()?.width
            && track.thumbnails?.last()?.height != null) && (track.thumbnails.lastOrNull()?.url?.contains("hq720") == false
            && track.thumbnails.lastOrNull()?.url?.contains("maxresdefault") == false)
        if ((player.currentMediaItemIndex + 1 in 0..(queueData.first()?.listTracks?.size ?: 0))) {
            if (track.artists.isNullOrEmpty()) {
                mainRepository.getSongInfo(track.videoId).cancellable().first().let { songInfo ->
                    if (songInfo != null) {
                        catalogMetadata.add(
                            player.currentMediaItemIndex + 1,
                            track.copy(
                                artists =
                                    listOf(
                                        Artist(
                                            songInfo.authorId,
                                            songInfo.author ?: "",
                                        ),
                                    ),
                            ),
                        )
                        addMediaItemNotSet(
                            MediaItem.Builder().setUri(track.videoId)
                                .setMediaId(track.videoId)
                                .setCustomCacheKey(track.videoId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(track.title)
                                        .setArtist(songInfo.author)
                                        .setArtworkUri(thumbUrl.toUri())
                                        .setAlbumTitle(track.album?.name)
                                        .setDescription(if (isSong) "Song" else "Video")
                                        .build(),
                                )
                                .build(),
                            player.currentMediaItemIndex + 1,
                        )
                    } else {
                        val mediaItem =
                            MediaItem.Builder()
                                .setMediaId(track.videoId)
                                .setUri(track.videoId)
                                .setCustomCacheKey(track.videoId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setArtworkUri(thumbUrl.toUri())
                                        .setAlbumTitle(track.album?.name)
                                        .setTitle(track.title)
                                        .setArtist("Various Artists")
                                        .setDescription(if (isSong) "Song" else "Video")
                                        .build(),
                                )
                                .build()
                        addMediaItemNotSet(mediaItem, player.currentMediaItemIndex + 1)
                        catalogMetadata.add(
                            player.currentMediaItemIndex + 1,
                            track.copy(
                                artists = listOf(Artist("", "Various Artists")),
                            ),
                        )
                    }
                }
            } else {
                addMediaItemNotSet(
                    MediaItem.Builder().setUri(track.videoId)
                        .setMediaId(track.videoId)
                        .setCustomCacheKey(track.videoId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(artistName)
                                .setArtworkUri(thumbUrl.toUri())
                                .setAlbumTitle(track.album?.name)
                                .setDescription(if (isSong) "Song" else "Video")
                                .build(),
                        )
                        .build(),
                    player.currentMediaItemIndex + 1,
                )
                catalogMetadata.add(player.currentMediaItemIndex + 1, track)
            }
            Log.d(
                "MusicSource",
                "updateCatalog: ${track.title}, ${catalogMetadata.size}",
            )
            Log.d("MusicSource", "updateCatalog: ${track.title}")
            _queueData.value = queueData.first()?.copy(
                listTracks = catalogMetadata,
            )
        }
        _stateFlow.value = StateSource.STATE_INITIALIZED
    }
}

sealed class RepeatState {
    data object None : RepeatState()

    data object All : RepeatState()

    data object One : RepeatState()
}

sealed class PlayerEvent {
    data object PlayPause : PlayerEvent()

    data object Backward : PlayerEvent()

    data object Forward : PlayerEvent()

    data object Stop : PlayerEvent()

    data object Next : PlayerEvent()

    data object Previous : PlayerEvent()

    data object Shuffle : PlayerEvent()

    data object Repeat : PlayerEvent()

    data class UpdateProgress(val newProgress: Float) : PlayerEvent()

    data object ToggleLike : PlayerEvent()
}

sealed class SimpleMediaState {
    data object Initial : SimpleMediaState()

    data object Ended : SimpleMediaState()

    data class Ready(val duration: Long) : SimpleMediaState()

    data class Loading(val bufferedPercentage: Int, val duration: Long) : SimpleMediaState()

    data class Progress(val progress: Long) : SimpleMediaState()

    data class Buffering(val position: Long) : SimpleMediaState()
}

data class NowPlayingTrackState(
    val mediaItem: MediaItem,
    val track: Track?,
    val songEntity: SongEntity?
) {
    fun isNotEmpty(): Boolean {
        return this != initial()
    }

    companion object {
        fun initial(): NowPlayingTrackState {
            return NowPlayingTrackState(
                mediaItem = EMPTY, track = null, songEntity = null
            )
        }
    }
}

enum class StateSource {
    STATE_CREATED,
    STATE_INITIALIZING,
    STATE_INITIALIZED,
    STATE_ERROR,
}

data class ControlState(
    val isPlaying: Boolean,
    val isShuffle: Boolean,
    val repeatState: RepeatState,
    val isLiked: Boolean,
    val isNextAvailable: Boolean,
    val isPreviousAvailable: Boolean
)

data class QueueData(
    val listTracks: ArrayList<Track> = arrayListOf(),
    val firstPlayedTrack: Track? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val playlistType: PlaylistType? = null,
    val continuation: String? = null
) {
    fun addTrackList(tracks: Collection<Track>): QueueData {
        val temp = listTracks
        temp.addAll(tracks)
        return this.copy(
            listTracks = temp
        )
    }

    fun removeFirstTrackForPlaylistAndAlbum(): QueueData {
        val temp = listTracks
        temp.removeAt(0)
        return this.copy(
            listTracks = temp
        )
    }
    fun removeTrackWithIndex(index: Int): QueueData {
        val temp = listTracks
        temp.removeAt(index)
        return this.copy(
            listTracks = temp
        )
    }
    fun setContinuation(continuation: String): QueueData {
        return this.copy(
            continuation = continuation
        )
    }
    fun isLocalPlaylist(): Boolean {
        return playlistType == PlaylistType.LOCAL_PLAYLIST
    }
    fun isRadio(): Boolean {
        return playlistType == PlaylistType.RADIO
    }
    fun isPlaylist(): Boolean {
        return playlistType == PlaylistType.PLAYLIST
    }
}

/**
 * @param isDone whether the timer is done to make a notification
 * @param timeRemaining the time remaining in minutes
 */

data class SleepTimerState(
    val isDone : Boolean,
    val timeRemaining: Int,
)

enum class PlaylistType {
    PLAYLIST,
    LOCAL_PLAYLIST,
    RADIO,
}