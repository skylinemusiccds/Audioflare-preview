package com.universe.audioflare.viewModel

import android.app.Application
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.universe.kotlinytmusicscraper.YouTube
import com.universe.kotlinytmusicscraper.models.YouTubeLocale
import com.universe.kotlinytmusicscraper.models.response.spotify.CanvasResponse
import com.universe.kotlinytmusicscraper.models.audioflare.GithubResponse
import com.universe.kotlinytmusicscraper.models.sponsorblock.SkipSegments
import com.universe.kotlinytmusicscraper.models.youtube.YouTubeInitialPage
import com.universe.audioflare.R
import com.universe.audioflare.common.Config
import com.universe.audioflare.common.Config.ALBUM_CLICK
import com.universe.audioflare.common.Config.PLAYLIST_CLICK
import com.universe.audioflare.common.Config.RECOVER_TRACK_QUEUE
import com.universe.audioflare.common.Config.SHARE
import com.universe.audioflare.common.Config.SONG_CLICK
import com.universe.audioflare.common.Config.VIDEO_CLICK
import com.universe.audioflare.common.DownloadState
import com.universe.audioflare.common.SELECTED_LANGUAGE
import com.universe.audioflare.common.STATUS_DONE
import com.universe.audioflare.data.dataStore.DataStoreManager
import com.universe.audioflare.data.dataStore.DataStoreManager.Settings.TRUE
import com.universe.audioflare.data.db.entities.AlbumEntity
import com.universe.audioflare.data.db.entities.LocalPlaylistEntity
import com.universe.audioflare.data.db.entities.LyricsEntity
import com.universe.audioflare.data.db.entities.NewFormatEntity
import com.universe.audioflare.data.db.entities.PairSongLocalPlaylist
import com.universe.audioflare.data.db.entities.PlaylistEntity
import com.universe.audioflare.data.db.entities.SongEntity
import com.universe.audioflare.data.db.entities.SongInfoEntity
import com.universe.audioflare.data.model.browse.album.Track
import com.universe.audioflare.data.model.metadata.Line
import com.universe.audioflare.data.model.metadata.Lyrics
import com.universe.audioflare.data.repository.MainRepository
import com.universe.audioflare.di.DownloadCache
import com.universe.audioflare.extension.isSong
import com.universe.audioflare.extension.isVideo
import com.universe.audioflare.extension.toListName
import com.universe.audioflare.extension.toLyrics
import com.universe.audioflare.extension.toLyricsEntity
import com.universe.audioflare.extension.toMediaItem
import com.universe.audioflare.extension.toSongEntity
import com.universe.audioflare.extension.toTrack
import com.universe.audioflare.service.ControlState
import com.universe.audioflare.service.NowPlayingTrackState
import com.universe.audioflare.service.PlayerEvent
import com.universe.audioflare.service.RepeatState
import com.universe.audioflare.service.SimpleMediaServiceHandler
import com.universe.audioflare.service.SimpleMediaState
import com.universe.audioflare.service.SleepTimerState
import com.universe.audioflare.service.test.download.DownloadUtils
import com.universe.audioflare.service.test.notification.NotifyWork
import com.universe.audioflare.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
@UnstableApi
class SharedViewModel
@Inject
constructor(
    private var dataStoreManager: DataStoreManager,
    @DownloadCache private val downloadedCache: SimpleCache,
    private val mainRepository: MainRepository,
    private val application: Application,
) : AndroidViewModel(application) {
    var isFirstLiked: Boolean = false
    var isFirstMiniplayer: Boolean = false
    var isFirstSuggestions: Boolean = false
    var showOrHideMiniplayer: MutableSharedFlow<Boolean> = MutableSharedFlow()

    private val TAG = "SharedViewModel"

    @Inject
    lateinit var downloadUtils: DownloadUtils

    private var restoreLastPlayedTrackDone: Boolean = false

    var simpleMediaServiceHandler: SimpleMediaServiceHandler? = null

    private var _songDB: MutableLiveData<SongEntity?> = MutableLiveData()
    val songDB: LiveData<SongEntity?> = _songDB
    private var _liked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val liked: SharedFlow<Boolean> = _liked.asSharedFlow()

    private var _downloadList: MutableStateFlow<ArrayList<SongEntity>?> = MutableStateFlow(null)
    val downloadList: SharedFlow<ArrayList<SongEntity>?> = _downloadList.asSharedFlow()

    private val context
        get() = getApplication<Application>()

    val isServiceRunning = MutableLiveData<Boolean>(false)

    var videoId = MutableLiveData<String>()

    var gradientDrawable: MutableLiveData<GradientDrawable> = MutableLiveData()

    var isPlaying = MutableStateFlow<Boolean>(false)

    var _lyrics = MutableStateFlow<Resource<Lyrics>?>(null)

    //    val lyrics: LiveData<Resource<Lyrics>> = _lyrics
    private var lyricsFormat: MutableLiveData<ArrayList<Line>> = MutableLiveData()
    var lyricsFull = MutableLiveData<String>()

    // SponsorBlock
    private var _skipSegments: MutableStateFlow<List<SkipSegments>?> = MutableStateFlow(null)
    val skipSegments: StateFlow<List<SkipSegments>?> = _skipSegments

    private var _sleepTimerState = MutableStateFlow(SleepTimerState(false, 0))
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState

    private var watchTimeList: ArrayList<Float> = arrayListOf()

    private var regionCode: String? = null
    private var language: String? = null
    private var quality: String? = null

    private var _format: MutableStateFlow<NewFormatEntity?> = MutableStateFlow(null)
    val format: SharedFlow<NewFormatEntity?> = _format.asSharedFlow()

    private var _songInfo: MutableStateFlow<SongInfoEntity?> = MutableStateFlow(null)
    val songInfo: SharedFlow<SongInfoEntity?> = _songInfo.asSharedFlow()

    private var _canvas: MutableStateFlow<CanvasResponse?> = MutableStateFlow(null)
    val canvas: StateFlow<CanvasResponse?> = _canvas

    private var canvasJob: Job? = null

    val intent: MutableStateFlow<Intent?> = MutableStateFlow(null)

    private var jobWatchtime: Job? = null

    private var getFormatFlowJob: Job? = null

    private var getLyricsJob: Job? = null

    var playlistId: MutableStateFlow<String?> = MutableStateFlow(null)

    private var _listYouTubeLiked: MutableStateFlow<ArrayList<String>?> = MutableStateFlow(null)
    val listYouTubeLiked: SharedFlow<ArrayList<String>?> = _listYouTubeLiked.asSharedFlow()

    var isFullScreen: Boolean = false
    var isSubtitle: Boolean = true

    private var _nowPlayingState = MutableStateFlow<NowPlayingTrackState?>(null)
    val nowPlayingState: StateFlow<NowPlayingTrackState?> = _nowPlayingState

    private var _controllerState = MutableStateFlow<ControlState>(
        ControlState(
            isPlaying = false, isShuffle = false, repeatState = RepeatState.None, isLiked = false, isNextAvailable = false, isPreviousAvailable = false
        )
    )
    val controllerState: StateFlow<ControlState> = _controllerState
    private val _getVideo: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val getVideo: StateFlow<Boolean> = _getVideo

    private var _timeline = MutableStateFlow<TimeLine>(
        TimeLine(
            current = -1L,
            total = -1L,
            bufferedPercent = 0,
            loading = true
        )
    )
    val timeline: StateFlow<TimeLine> = _timeline

    private var _nowPlayingScreenData = MutableStateFlow<NowPlayingScreenData>(
        NowPlayingScreenData.initial(),
    )
    val nowPlayingScreenData: StateFlow<NowPlayingScreenData> = _nowPlayingScreenData

    init {
        viewModelScope.launch {
            val timeLineJob = launch {
                combine(timeline.distinctUntilChangedBy {
                    it.total
                }, nowPlayingState.distinctUntilChangedBy {
                    it?.songEntity?.videoId
                }) {
                    timeline, nowPlayingState ->
                    Pair(timeline, nowPlayingState)
                }
                    .collectLatest {
                        val nowPlaying = it.second
                        val timeline = it.first
                        if (timeline.total > 0 && nowPlaying?.songEntity != null) {
                            if (nowPlaying.mediaItem.isSong()) {
                                Log.w(TAG, "Duration is ${timeline.total}")
                                Log.w(TAG, "MediaId is ${nowPlaying.mediaItem.mediaId}")
                                getCanvas(nowPlaying.mediaItem.mediaId, (timeline.total / 1000).toInt())
                            }
                            nowPlaying.songEntity.let { song ->
                                Log.w(TAG, "Get lyrics from format")
                                getLyricsFromFormat(song, (timeline.total / 1000).toInt())
                            }
                        }
                    }
            }

            val downloadedJob =
                launch {
                    mainRepository.getDownloadedSongsAsFlow(offset = 0).distinctUntilChanged()
                        .collectLatest {
                            _downloadList.value = it?.toCollection(ArrayList())
                        }
                }

            val format =
                launch {
                    format.distinctUntilChanged().collectLatest { formatTemp ->
                        if (dataStoreManager.sendBackToGoogle.first() == TRUE) {
                            if (formatTemp != null) {
                                println("format in viewModel: $formatTemp")
                                Log.d(TAG, "Collect format ${formatTemp.videoId}")
                                Log.w(TAG, "Format expire at ${formatTemp.expiredTime}")
                                Log.i(TAG, "AtrUrl ${formatTemp.playbackTrackingAtrUrl}")
                                initPlayback(
                                    formatTemp.playbackTrackingVideostatsPlaybackUrl,
                                    formatTemp.playbackTrackingAtrUrl,
                                    formatTemp.playbackTrackingVideostatsWatchtimeUrl,
                                    formatTemp.cpn,
                                )
                            }
                        }
                    }
                }
            val checkGetVideoJob = launch {
                dataStoreManager.watchVideoInsteadOfPlayingAudio.collectLatest {
                    Log.w(TAG, "GetVideo is $it")
                    if (it == TRUE) {
                        _getVideo.value = true
                    } else {
                        _getVideo.value = false
                    }
                }
            }
            timeLineJob.join()
            downloadedJob.join()
            format.join()
            checkGetVideoJob.join()
        }
    }

    fun setHandler(handler: SimpleMediaServiceHandler) {
        simpleMediaServiceHandler = handler
        runBlocking {
            dataStoreManager.getString("miniplayer_guide").first().let {
                isFirstMiniplayer = it != STATUS_DONE
            }
            dataStoreManager.getString("suggest_guide").first().let {
                isFirstSuggestions = it != STATUS_DONE
            }
            dataStoreManager.getString("liked_guide").first().let {
                isFirstLiked = it != STATUS_DONE
            }
        }
        viewModelScope.launch {
            handler.nowPlayingState.distinctUntilChangedBy {
                it.songEntity?.videoId
            }.collectLatest { state ->
                Log.w(TAG, "NowPlayingState is $state")
                _nowPlayingState.value = state
                _nowPlayingScreenData.value = NowPlayingScreenData(
                    nowPlayingTitle = state.track?.title ?: "",
                    artistName = state.track?.artists?.toListName()?.firstOrNull() ?: "",
                    isVideo = false,
                    thumbnailURL = null,
                    canvasData = null,
                    lyricsData = null,
                    songInfoData = null,
                    playlistName = simpleMediaServiceHandler?.queueData?.value?.playlistName ?: ""
                )
                _liked.value = state.songEntity?.liked ?: false
                _format.value = null
                _songInfo.value = null
                _canvas.value = null
                canvasJob?.cancel()
                state.mediaItem.let { now ->
                    getSongInfo(now.mediaId)
                    getSkipSegments(now.mediaId)
                    getFormat(now.mediaId)
                    _nowPlayingScreenData.update {
                        it.copy(
                            thumbnailURL = now.mediaMetadata.artworkUri.toString(),
                            isVideo = now.isVideo(),
                        )
                    }
                }
            }
        }
//        initJob =
        viewModelScope.launch {
            val job1 =
                launch {
                    handler.simpleMediaState.collect { mediaState ->
                        when (mediaState) {
                            is SimpleMediaState.Buffering -> {
                                _timeline.update {
                                    it.copy(
                                        loading = true
                                    )
                                }
                            }

                            SimpleMediaState.Initial -> {
                                _timeline.update { it.copy(loading = true) }
                            }
                            SimpleMediaState.Ended -> {
                                _timeline.update {
                                    it.copy(
                                        current = -1L,
                                        total = -1L,
                                        bufferedPercent = 0,
                                        loading = true
                                    )
                                }
                            }

                            is SimpleMediaState.Progress -> {
                                if (mediaState.progress >= 0L) {
                                    _timeline.update {
                                        it.copy(
                                            current = mediaState.progress
                                        )
                                    }
                                }
                                else {
                                    _timeline.update {
                                        it.copy(
                                            loading = true
                                        )
                                    }
                                }
                            }

                            is SimpleMediaState.Loading -> {
                                _timeline.update {
                                    it.copy(
                                        bufferedPercent = mediaState.bufferedPercentage,
                                        total = mediaState.duration
                                    )
                                }
                            }

                            is SimpleMediaState.Ready -> {
                                _timeline.update {
                                    it.copy(
                                        current = simpleMediaServiceHandler!!.getProgress(),
                                        loading = false,
                                        total = mediaState.duration
                                    )
                                }
                            }
                        }
                    }
                }
//                val job2 =
//                    launch {
//                        handler.nowPlaying.collectLatest { nowPlaying ->
//                            Log.w("MainActivity", "nowPlaying collect $nowPlaying")
//                            nowPlaying?.let { now ->
//                                _format.value = null
//                                _songInfo.value = null
//                                _canvas.value = null
//                                canvasJob?.cancel()
//                                getSongInfo(now.mediaId)
//                                getSkipSegments(now.mediaId)
//                                basicWidget.performUpdate(
//                                    context,
//                                    simpleMediaServiceHandler!!,
//                                    null,
//                                )
//                                downloadImageForWidgetJob?.cancel()
//                                downloadImageForWidgetJob =
//                                    viewModelScope.launch {
//                                        val p = getScreenSize(context)
//                                        val widgetImageSize = p.x.coerceAtMost(p.y)
//                                        val imageRequest =
//                                            ImageRequest.Builder(context)
//                                                .data(nowPlaying.mediaMetadata.artworkUri)
//                                                .size(widgetImageSize)
//                                                .placeholder(R.drawable.holder_video)
//                                                .target(
//                                                    onSuccess = { drawable ->
//                                                        basicWidget.updateImage(
//                                                            context,
//                                                            drawable.toBitmap(
//                                                                widgetImageSize,
//                                                                widgetImageSize,
//                                                            ),
//                                                        )
//                                                    },
//                                                    onStart = { holder ->
//                                                        if (holder != null) {
//                                                            basicWidget.updateImage(
//                                                                context,
//                                                                holder.toBitmap(
//                                                                    widgetImageSize,
//                                                                    widgetImageSize,
//                                                                ),
//                                                            )
//                                                        }
//                                                    },
//                                                    onError = {
//                                                        AppCompatResources.getDrawable(
//                                                            context,
//                                                            R.drawable.holder_video,
//                                                        )
//                                                            ?.let { it1 ->
//                                                                basicWidget.updateImage(
//                                                                    context,
//                                                                    it1.toBitmap(
//                                                                        widgetImageSize,
//                                                                        widgetImageSize,
//                                                                    ),
//                                                                )
//                                                            }
//                                                    },
//                                                ).build()
//                                        ImageLoader(context).execute(imageRequest)
//                                    }
//                            }
//                            if (nowPlaying != null) {
//                                transformEmit(nowPlaying)
//                                val tempSong =
//                                    simpleMediaServiceHandler!!.queueData.first()?.listTracks?.getOrNull(
//                                        getCurrentMediaItemIndex(),
//                                    )
//                                if (tempSong != null) {
//                                    Log.d("Check tempSong", tempSong.toString())
//                                    mainRepository.insertSong(
//                                        tempSong.toSongEntity(),
//                                    ).first()
//                                        .let { id ->
//                                            Log.d("Check insertSong", id.toString())
//                                        }
//                                    mainRepository.getSongById(tempSong.videoId)
//                                        .collectLatest { songEntity ->
//                                            _songDB.value = songEntity
//                                            if (songEntity != null) {
//                                                Log.w(
//                                                    "Check like",
//                                                    "SharedViewModel nowPlaying collect ${songEntity.liked}",
//                                                )
//                                                _liked.value = songEntity.liked
//                                            }
//                                        }
//                                    mainRepository.updateSongInLibrary(
//                                        LocalDateTime.now(),
//                                        tempSong.videoId,
//                                    )
//                                    mainRepository.updateListenCount(tempSong.videoId)
//                                    tempSong.durationSeconds?.let {
//                                        mainRepository.updateDurationSeconds(
//                                            it,
//                                            tempSong.videoId,
//                                        )
//                                    }
//                                    videoId.postValue(tempSong.videoId)
//                                    transformEmit(nowPlaying)
//                                }
//                                val index = getCurrentMediaItemIndex() + 1
//                                Log.w("Check index", index.toString())
//                                val size = simpleMediaServiceHandler!!.queueData.first()?.listTracks?.size ?: 0
//                                Log.w("Check size", size.toString())
//                                Log.w("Check loadingMore", loadingMore.toString())
//                            }
//                        }
//                    }
            val controllerJob = launch {
                Log.w(TAG, "ControllerJob is running")
                handler.controlState.collectLatest {
                    Log.w(TAG, "ControlState is $it")
                    _controllerState.value = it
                }
            }
            val sleepTimerJob = launch {
                handler.sleepTimerState.collectLatest {
                    _sleepTimerState.value = it
                }
            }
            val playlistNameJob = launch {
                handler.queueData.collectLatest {
                    _nowPlayingScreenData.update {
                        it.copy(playlistName = it.playlistName)
                    }
                }
            }
//                    val getDurationJob = launch {
//                        combine(nowPlayingState, mediaState){ nowPlayingState, mediaState ->
//                            Pair(nowPlayingState, mediaState)
//                        }.collectLatest {
//                            val nowPlaying = it.first
//                            val media = it.second
//                            if (media is SimpleMediaState.Ready && nowPlaying?.mediaItem != null) {
//                                if (nowPlaying.mediaItem.isSong()) {
//                                    Log.w(TAG, "Duration is ${media.duration}")
//                                    getCanvas(nowPlaying.mediaItem.mediaId, media.duration.toInt())
//                                }
//                                getLyricsFromFormat(nowPlaying.mediaItem.mediaId, media.duration.toInt())
//                            }
//                        }
//                    }
//                    getDurationJob.join()
//            val job3 =
//                launch {
//                    handler.controlState.collectLatest { controlState ->
//                        _shuffleModeEnabled.value = controlState.isShuffle
//                        _repeatMode.value = controlState.repeatState
//                        isPlaying.value = controlState.isPlaying
//                    }
//                }
//                    val job10 =
//                        launch {
//                            nowPlayingMediaItem.collectLatest { now ->
//                                Log.d(
//                                    "Now Playing",
//                                    now?.mediaId + now?.mediaMetadata?.title
//                                )
//                            }
//                        }
            job1.join()
            controllerJob.join()
            sleepTimerJob.join()
            playlistNameJob.join()
//                job2.join()
//            job3.join()
//            nowPlayingJob.join()
//                    job10.join()
        }
        if (runBlocking { dataStoreManager.loggedIn.first() } == TRUE) {
            getYouTubeLiked()
        }
    }


    private fun getYouTubeLiked() {
        viewModelScope.launch {
            mainRepository.getPlaylistData("VLLM").collect { response ->
                val list = response.data?.tracks?.map { it.videoId }?.toCollection(ArrayList())
                _listYouTubeLiked.value = list
            }
        }
    }

    private fun getCanvas(
        videoId: String,
        duration: Int,
    ) {
        Log.w("Start getCanvas", "$videoId $duration")
//        canvasJob?.cancel()
        viewModelScope.launch {
            mainRepository.getCanvas(videoId, duration).cancellable().collect { response ->
                _canvas.value = response
                Log.w(TAG, "Canvas is $response")
                if (nowPlayingState.value?.mediaItem?.mediaId == videoId) {
                    _nowPlayingScreenData.update {
                        it.copy(
                            canvasData = response?.canvases?.firstOrNull()?.canvas_url?.let { canvasUrl ->
                                NowPlayingScreenData.CanvasData(
                                    isVideo = canvasUrl.contains(".mp4"),
                                    url = canvasUrl
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun initPlayback(
        playback: String?,
        atr: String?,
        watchTime: String?,
        cpn: String?,
    ) {
        jobWatchtime?.cancel()
        viewModelScope.launch {
            if (playback != null && atr != null && watchTime != null && cpn != null) {
                watchTimeList.clear()
                mainRepository.initPlayback(playback, atr, watchTime, cpn, playlistId.value)
                    .collect {
                        if (it.first == 204) {
                            Log.d("Check initPlayback", "Success")
                            watchTimeList.add(0f)
                            watchTimeList.add(5.54f)
                            watchTimeList.add(it.second)
                            updateWatchTime()
                        }
                    }
            }
        }
    }

    private fun updateWatchTime() {
        viewModelScope.launch {
            jobWatchtime =
                launch {
                    timeline.collect { timeline ->
                        val value = timeline.current
                        if (value > 0 && watchTimeList.isNotEmpty()) {
                            val second = (value / 1000).toFloat()
                            if (second in watchTimeList.last()..watchTimeList.last() + 1.2f) {
                                val watchTimeUrl =
                                    _format.value?.playbackTrackingVideostatsWatchtimeUrl
                                val cpn = _format.value?.cpn
                                if (second + 20.23f < (timeline.total / 1000).toFloat()) {
                                    watchTimeList.add(second + 20.23f)
                                    if (watchTimeUrl != null && cpn != null) {
                                        mainRepository.updateWatchTime(
                                            watchTimeUrl,
                                            watchTimeList,
                                            cpn,
                                            playlistId.value,
                                        ).collect { response ->
                                            if (response == 204) {
                                                Log.d("Check updateWatchTime", "Success")
                                            }
                                        }
                                    }
                                } else {
                                    watchTimeList.clear()
                                    if (watchTimeUrl != null && cpn != null) {
                                        mainRepository.updateWatchTimeFull(
                                            watchTimeUrl,
                                            cpn,
                                            playlistId.value,
                                        ).collect { response ->
                                            if (response == 204) {
                                                Log.d("Check updateWatchTimeFull", "Success")
                                            }
                                        }
                                    }
                                }
                                Log.w("Check updateWatchTime", watchTimeList.toString())
                            }
                        }
                    }
                }
            jobWatchtime?.join()
        }
    }

    fun getString(key: String): String? {
        return runBlocking { dataStoreManager.getString(key).first() }
    }

    fun putString(
        key: String,
        value: String,
    ) {
        runBlocking { dataStoreManager.putString(key, value) }
    }

    fun setSleepTimer(minutes: Int) {
        simpleMediaServiceHandler?.sleepStart(minutes)
    }

    fun stopSleepTimer() {
        simpleMediaServiceHandler?.sleepStop()
    }

    private var _downloadState: MutableStateFlow<Download?> = MutableStateFlow(null)
    var downloadState: StateFlow<Download?> = _downloadState.asStateFlow()

    fun getDownloadStateFromService(videoId: String) {
        viewModelScope.launch {
            downloadState = downloadUtils.getDownload(videoId).stateIn(viewModelScope)
            downloadState.collect { down ->
                if (down != null) {
                    when (down.state) {
                        Download.STATE_COMPLETED -> {
                            mainRepository.getSongById(videoId).collect { song ->
                                if (song?.downloadState != DownloadState.STATE_DOWNLOADED) {
                                    mainRepository.updateDownloadState(
                                        videoId,
                                        DownloadState.STATE_DOWNLOADED,
                                    )
                                }
                            }
                            Log.d("Check Downloaded", "Downloaded")
                        }

                        Download.STATE_FAILED -> {
                            mainRepository.getSongById(videoId).collect { song ->
                                if (song?.downloadState != DownloadState.STATE_NOT_DOWNLOADED) {
                                    mainRepository.updateDownloadState(
                                        videoId,
                                        DownloadState.STATE_NOT_DOWNLOADED,
                                    )
                                }
                            }
                            Log.d("Check Downloaded", "Failed")
                        }

                        Download.STATE_DOWNLOADING -> {
                            mainRepository.getSongById(videoId).collect { song ->
                                if (song?.downloadState != DownloadState.STATE_DOWNLOADING) {
                                    mainRepository.updateDownloadState(
                                        videoId,
                                        DownloadState.STATE_DOWNLOADING,
                                    )
                                }
                            }
                            Log.d("Check Downloaded", "Downloading ${down.percentDownloaded}")
                        }

                        else -> {
                            Log.d("Check Downloaded", "${down.state}")
                        }
                    }
                }
            }
        }
    }

    fun checkIsRestoring() {
        viewModelScope.launch {
            mainRepository.getDownloadedSongs().first().let { songs ->
                songs?.forEach { song ->
                    if (!downloadedCache.keys.contains(song.videoId)) {
                        mainRepository.updateDownloadState(
                            song.videoId,
                            DownloadState.STATE_NOT_DOWNLOADED,
                        )
                    }
                }
            }
            mainRepository.getAllDownloadedPlaylist().first().let { list ->
                for (data in list) {
                    when (data) {
                        is AlbumEntity -> {
                            if (data.tracks.isNullOrEmpty() || (
                                    !downloadedCache.keys.containsAll(
                                        data.tracks,
                                    )
                                )
                            ) {
                                mainRepository.updateAlbumDownloadState(
                                    data.browseId,
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                )
                            }
                        }

                        is PlaylistEntity -> {
                            if (data.tracks.isNullOrEmpty() || (
                                    !downloadedCache.keys.containsAll(
                                        data.tracks,
                                    )
                                )
                            ) {
                                mainRepository.updatePlaylistDownloadState(
                                    data.id,
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                )
                            }
                        }

                        is LocalPlaylistEntity -> {
                            if (data.tracks.isNullOrEmpty() || (
                                    !downloadedCache.keys.containsAll(
                                        data.tracks,
                                    )
                                )
                            ) {
                                mainRepository.updateLocalPlaylistDownloadState(
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                    data.id,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun insertLyrics(lyrics: LyricsEntity) {
        viewModelScope.launch {
            mainRepository.insertLyrics(lyrics)
        }
    }

    private fun getSkipSegments(videoId: String) {
        resetSkipSegments()
        viewModelScope.launch {
            mainRepository.getSkipSegments(videoId).collect { segments ->
                if (segments != null) {
                    Log.w("Check segments $videoId", segments.toString())
                    _skipSegments.value = segments
                } else {
                    _skipSegments.value = null
                }
            }
        }
    }

    private fun resetSkipSegments() {
        _skipSegments.value = null
    }

    private fun getSavedLyrics(track: Track) {
        viewModelScope.launch {
            resetLyrics()
            mainRepository.getSavedLyrics(track.videoId).cancellable().collect { lyrics ->
                if (lyrics != null) {
                    val lyricsData = lyrics.toLyrics()
                    Log.d(TAG, "Saved Lyrics $lyricsData")
                    updateLyrics(
                        track.videoId,
                        lyricsData,
                        false,
                        LyricsProvider.OFFLINE
                    )
                } else {
                    resetLyrics()
                    mainRepository.getLyricsData(track.artists.toListName().firstOrNull() ?: "", track.title, track.durationSeconds)
                        .cancellable().collect { response ->
                            when (_lyrics.value) {
                                is Resource.Success -> {
                                    _lyrics.value?.data?.let {
                                        updateLyrics(
                                            track.videoId,
                                            it,
                                            false,
                                            LyricsProvider.MUSIXMATCH
                                        )
                                        insertLyrics(
                                            it.toLyricsEntity(track.videoId),
                                        )
                                        if (dataStoreManager.enableTranslateLyric.first() == TRUE) {
                                            mainRepository.getTranslateLyrics(response.first).cancellable()
                                                .collect { translate ->
                                                    if (translate != null) {
                                                        updateLyrics(
                                                            track.videoId,
                                                            translate.toLyrics(
                                                                it
                                                            ),
                                                            true,
                                                        )
                                                    }
                                                }
                                        }
                                    }
                                }

                                else -> {
                                    Log.d("Check lyrics", "Loading")
                                    updateLyrics(
                                        track.videoId,
                                        null,
                                        false
                                    )
                                }
                            }
                        }
                }
            }
        }
    }

    fun getCurrentMediaItemIndex(): Int {
        return runBlocking { simpleMediaServiceHandler?.currentSongIndex?.first() } ?: 0
    }

    @UnstableApi
    fun playMediaItemInMediaSource(index: Int) {
        simpleMediaServiceHandler?.playMediaItemInMediaSource(index)
    }

    @UnstableApi
    fun loadMediaItemFromTrack(
        track: Track,
        type: String,
        index: Int? = null,
        from: String
    ) {
        quality = runBlocking { dataStoreManager.quality.first() }
        viewModelScope.launch {
            simpleMediaServiceHandler?.clearMediaItems()
            mainRepository.insertSong(track.toSongEntity()).first().let {
                println("insertSong: $it")
                mainRepository.getSongById(track.videoId)
                    .collect { songEntity ->
                        _songDB.value = songEntity
                        if (songEntity != null) {
                            Log.w("Check like", "loadMediaItemFromTrack ${songEntity.liked}")
                            _liked.value = songEntity.liked
                        }
                    }
            }
            track.durationSeconds?.let {
                mainRepository.updateDurationSeconds(
                    it,
                    track.videoId,
                )
            }
            withContext(Dispatchers.Main) {
                simpleMediaServiceHandler?.addMediaItem(track.toMediaItem(), playWhenReady = type != RECOVER_TRACK_QUEUE)
            }

            when (type) {
                SONG_CLICK -> {
                    simpleMediaServiceHandler?.getRelated(track.videoId)
                }

                VIDEO_CLICK -> {
                    simpleMediaServiceHandler?.getRelated(track.videoId)
                }

                SHARE -> {
                    simpleMediaServiceHandler?.getRelated(track.videoId)
                }

                PLAYLIST_CLICK -> {
                    if (index == null) {
//                                        fetchSourceFromQueue(downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = 0)
                    } else {
//                                        fetchSourceFromQueue(index!!, downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = index)
                    }
                }

                ALBUM_CLICK -> {
                    if (index == null) {
//                                        fetchSourceFromQueue(downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = 0)
                    } else {
//                                        fetchSourceFromQueue(index!!, downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = index)
                    }
                }
            }
        }
    }

    @UnstableApi
    fun onUIEvent(uiEvent: UIEvent) =
        viewModelScope.launch {
            when (uiEvent) {
                UIEvent.Backward ->
                    simpleMediaServiceHandler?.onPlayerEvent(
                        PlayerEvent.Backward,
                    )

                UIEvent.Forward -> simpleMediaServiceHandler?.onPlayerEvent(PlayerEvent.Forward)
                UIEvent.PlayPause ->
                    simpleMediaServiceHandler?.onPlayerEvent(
                        PlayerEvent.PlayPause,
                    )

                UIEvent.Next -> simpleMediaServiceHandler?.onPlayerEvent(PlayerEvent.Next)
                UIEvent.Previous ->
                    simpleMediaServiceHandler?.onPlayerEvent(
                        PlayerEvent.Previous,
                    )

                UIEvent.Stop -> simpleMediaServiceHandler?.onPlayerEvent(PlayerEvent.Stop)
                is UIEvent.UpdateProgress -> {
                    simpleMediaServiceHandler?.onPlayerEvent(
                        PlayerEvent.UpdateProgress(
                            uiEvent.newProgress,
                        ),
                    )
                }

                UIEvent.Repeat -> simpleMediaServiceHandler?.onPlayerEvent(PlayerEvent.Repeat)
                UIEvent.Shuffle -> simpleMediaServiceHandler?.onPlayerEvent(PlayerEvent.Shuffle)
                UIEvent.ToggleLike -> simpleMediaServiceHandler?.onPlayerEvent(
                    PlayerEvent.ToggleLike
                )
            }
        }

    private var _listLocalPlaylist: MutableStateFlow<List<LocalPlaylistEntity>> =
        MutableStateFlow(listOf())
    val localPlaylist: StateFlow<List<LocalPlaylistEntity>> = _listLocalPlaylist

    fun getAllLocalPlaylist() {
        viewModelScope.launch {
            mainRepository.getAllLocalPlaylists().collect { values ->
                _listLocalPlaylist.emit(values)
            }
        }
    }

    fun updateLocalPlaylistTracks(
        list: List<String>,
        id: Long,
    ) {
        viewModelScope.launch {
            mainRepository.getSongsByListVideoId(list).collect { values ->
                var count = 0
                values.forEach { song ->
                    if (song.downloadState == DownloadState.STATE_DOWNLOADED) {
                        count++
                    }
                }
                mainRepository.updateLocalPlaylistTracks(list, id)
                Toast.makeText(
                    getApplication(),
                    application.getString(R.string.added_to_playlist),
                    Toast.LENGTH_SHORT,
                ).show()
                if (count == values.size) {
                    mainRepository.updateLocalPlaylistDownloadState(
                        DownloadState.STATE_DOWNLOADED,
                        id,
                    )
                } else {
                    mainRepository.updateLocalPlaylistDownloadState(
                        DownloadState.STATE_NOT_DOWNLOADED,
                        id,
                    )
                }
            }
        }
    }

    fun getLyricsSyncState(): Config.SyncState {
        return when (_lyrics.value?.data?.syncType) {
            null -> Config.SyncState.NOT_FOUND
            "LINE_SYNCED" -> Config.SyncState.LINE_SYNCED
            "UNSYNCED" -> Config.SyncState.UNSYNCED
            else -> Config.SyncState.NOT_FOUND
        }
    }

    fun getActiveLyrics(current: Long): Int? {
        val lyricsFormat = _lyrics.value?.data?.lines
        lyricsFormat?.indices?.forEach { i ->
            val sentence = lyricsFormat[i]
            val startTimeMs = sentence.startTimeMs.toLong()

            // estimate the end time of the current sentence based on the start time of the next sentence
            val endTimeMs =
                if (i < lyricsFormat.size - 1) {
                    lyricsFormat[i + 1].startTimeMs.toLong()
                } else {
                    // if this is the last sentence, set the end time to be some default value (e.g., 1 minute after the start time)
                    startTimeMs + 60000
                }
            if (current in startTimeMs..endTimeMs) {
                return i
            }
        }
        if (!lyricsFormat.isNullOrEmpty() && (
                current in (
                    0..(
                        lyricsFormat.getOrNull(0)?.startTimeMs
                            ?: "0"
                    ).toLong()
                )
            )
        ) {
            return -1
        }
        return null
    }

    @UnstableApi
    override fun onCleared() {
        runBlocking {
            jobWatchtime?.cancel()
//            simpleMediaServiceHandler?.onPlayerEvent(PlayerEvent.Stop)
        }
        simpleMediaServiceHandler = null
        Log.w("Check onCleared", "onCleared")
    }

    private fun resetLyrics() {
        _lyrics.value = (Resource.Error<Lyrics>("reset"))
        lyricsFormat.postValue(arrayListOf())
        lyricsFull.postValue("")
    }

    fun updateDownloadState(
        videoId: String,
        state: Int,
    ) {
        viewModelScope.launch {
            mainRepository.getSongById(videoId).collect { songEntity ->
                _songDB.value = songEntity
                if (songEntity != null) {
                    Log.w(
                        "Check like",
                        "SharedViewModel updateDownloadState ${songEntity.liked}",
                    )
                    _liked.value = songEntity.liked
                }
            }
            mainRepository.updateDownloadState(videoId, state)
        }
    }

    fun changeAllDownloadingToError() {
        viewModelScope.launch {
            mainRepository.getDownloadingSongs().collect { songs ->
                songs?.forEach { song ->
                    mainRepository.updateDownloadState(
                        song.videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
        }
    }

    private val _songFull: MutableLiveData<YouTubeInitialPage?> = MutableLiveData()
    var songFull: LiveData<YouTubeInitialPage?> = _songFull

    fun getSongFull(videoId: String) {
        viewModelScope.launch {
            mainRepository.getFullMetadata(videoId).collect {
                _songFull.postValue(it)
            }
        }
    }

//    val _artistId: MutableLiveData<Resource<ChannelId>> = MutableLiveData()
//    var artistId: LiveData<Resource<ChannelId>> = _artistId
//    fun convertNameToId(artistId: String) {
//        viewModelScope.launch {
//            mainRepository.convertNameToId(artistId).collect {
//                _artistId.postValue(it)
//            }
//        }
//    }

    fun getLocation() {
        regionCode = runBlocking { dataStoreManager.location.first() }
        quality = runBlocking { dataStoreManager.quality.first() }
        language = runBlocking { dataStoreManager.getString(SELECTED_LANGUAGE).first() }
        YouTube.locale = YouTubeLocale(gl = regionCode ?: "US", hl = language?.substring(0..1) ?: "en")
    }

    fun checkAllDownloadingSongs() {
        viewModelScope.launch {
            mainRepository.getDownloadingSongs().collect { songs ->
                songs?.forEach { song ->
                    mainRepository.updateDownloadState(
                        song.videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
            mainRepository.getPreparingSongs().collect { songs ->
                songs.forEach { song ->
                    mainRepository.updateDownloadState(
                        song.videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
        }
    }

    fun checkAuth() {
        viewModelScope.launch {
            dataStoreManager.cookie.first().let { cookie ->
                if (cookie != "") {
                    YouTube.cookie = cookie
                    Log.d("Cookie", "Cookie is not empty")
                } else {
                    Log.e("Cookie", "Cookie is empty")
                }
            }
            dataStoreManager.musixmatchCookie.first().let { cookie ->
                if (cookie != "") {
                    YouTube.musixMatchCookie = cookie
                    Log.d("Musixmatch", "Cookie is not empty")
                } else {
                    Log.e("Musixmatch", "Cookie is empty")
                }
            }
        }
    }

    private fun getFormat(mediaId: String?) {
        getFormatFlowJob?.cancel()
        getFormatFlowJob = viewModelScope.launch {
            if (mediaId != null) {
                mainRepository.getFormatFlow(mediaId).cancellable().collectLatest { f ->
                    Log.w(TAG, "Get format for "+ mediaId.toString() + ": " +f.toString())
                    if (f != null) {
                        _format.emit(f)
                    } else {
                        _format.emit(null)
                    }
                }
            }
        }
    }

    private var songInfoJob: Job? = null
    fun getSongInfo(mediaId: String?) {
        songInfoJob?.cancel()
        songInfoJob = viewModelScope.launch {
            if (mediaId != null) {
                mainRepository.getSongInfo(mediaId).collect { song ->
                    _songInfo.value = song
                    _nowPlayingScreenData.update {
                        it.copy(
                            songInfoData = song
                        )
                    }
                }
            }
        }
    }

    private var _githubResponse = MutableLiveData<GithubResponse?>()
    val githubResponse: LiveData<GithubResponse?> = _githubResponse

    fun checkForUpdate() {
        viewModelScope.launch {
            mainRepository.checkForUpdate().collect { response ->
                dataStoreManager.putString(
                    "CheckForUpdateAt",
                    System.currentTimeMillis().toString(),
                )
                _githubResponse.postValue(response)
            }
        }
    }

    fun skipSegment(position: Long) {
        simpleMediaServiceHandler?.skipSegment(position)
    }

    fun sponsorBlockEnabled() = runBlocking { dataStoreManager.sponsorBlockEnabled.first() }

    fun sponsorBlockCategories() = runBlocking { dataStoreManager.getSponsorBlockCategories() }

    fun stopPlayer() {
        isPlaying.value = false
        onUIEvent(UIEvent.Stop)
    }

    fun addToYouTubePlaylist(
        localPlaylistId: Long,
        youtubePlaylistId: String,
        videoId: String,
    ) {
        viewModelScope.launch {
            mainRepository.updateLocalPlaylistYouTubePlaylistSyncState(
                localPlaylistId,
                LocalPlaylistEntity.YouTubeSyncState.Syncing,
            )
            mainRepository.addYouTubePlaylistItem(youtubePlaylistId, videoId).collect { response ->
                if (response == "STATUS_SUCCEEDED") {
                    mainRepository.updateLocalPlaylistYouTubePlaylistSyncState(
                        localPlaylistId,
                        LocalPlaylistEntity.YouTubeSyncState.Synced,
                    )
                    Toast.makeText(
                        getApplication(),
                        application.getString(R.string.added_to_youtube_playlist),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    mainRepository.updateLocalPlaylistYouTubePlaylistSyncState(
                        localPlaylistId,
                        LocalPlaylistEntity.YouTubeSyncState.NotSynced,
                    )
                    Toast.makeText(
                        getApplication(),
                        application.getString(R.string.error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun addQueueToPlayer() {
        simpleMediaServiceHandler?.addQueueToPlayer()
    }

    private fun loadPlaylistOrAlbum(index: Int? = null) {
        simpleMediaServiceHandler?.loadPlaylistOrAlbum(index)
    }

    private fun updateLyrics(
        videoId: String, lyrics: Lyrics?, isTranslatedLyrics: Boolean, lyricsProvider: LyricsProvider = LyricsProvider.MUSIXMATCH
    ) {
        if (_nowPlayingState.value?.songEntity?.videoId == videoId) {
            when (isTranslatedLyrics) {
                true -> {
                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData = it.lyricsData?.copy(
                                translatedLyrics = lyrics
                            )
                        )
                    }
                }
                false -> {
                    if (lyrics != null) {
                        _nowPlayingScreenData.update {
                            it.copy(
                                lyricsData = NowPlayingScreenData.LyricsData(
                                    lyrics = lyrics,
                                    lyricsProvider = lyricsProvider
                                )
                            )
                        }
                    }
                    else {
                        _nowPlayingScreenData.update {
                            it.copy(
                                lyricsData = null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getLyricsFromFormat(
        song: SongEntity,
        duration: Int,
    ) {
        viewModelScope.launch {
            val videoId = song.videoId
            Log.w(TAG, "Get Lyrics From Format for $videoId")
            if (dataStoreManager.lyricsProvider.first() == DataStoreManager.MUSIXMATCH) {
                    val artist =
                        if (song.artistName?.firstOrNull() != null && song.artistName.firstOrNull()
                                ?.contains("Various Artists") == false
                        ) {
                            song.artistName.firstOrNull()
                        } else {
                            simpleMediaServiceHandler?.nowPlaying?.first()?.mediaMetadata?.artist
                                ?: ""
                        }
                    mainRepository.getLyricsData(
                        (artist ?: "").toString(),
                        song.title,
                        duration,
                    ).cancellable().collect { response ->
                        Log.w(TAG, response.second.data.toString())

                        when (response.second) {
                            is Resource.Success -> {
                                if (response.second.data != null) {
                                    Log.d(TAG, "Get Lyrics Data Success")
                                    updateLyrics(
                                        videoId,
                                        response.second.data,
                                        false,
                                        LyricsProvider.MUSIXMATCH
                                    )
                                    insertLyrics(
                                        response.second.data!!.toLyricsEntity(
                                            videoId,
                                        ),
                                    )
                                    if (dataStoreManager.enableTranslateLyric.first() == TRUE) {
                                        mainRepository.getTranslateLyrics(
                                            response.first,
                                        ).cancellable()
                                            .collect { translate ->
                                                if (translate != null) {
                                                    Log.d(TAG, "Get Translate Lyrics Success")
                                                    updateLyrics(
                                                        videoId,
                                                        translate.toLyrics(
                                                            response.second.data!!,
                                                        ),
                                                        true
                                                    )
                                                }
                                            }
                                    }
                                } else if (dataStoreManager.spotifyLyrics.first() == TRUE) {
                                    getSpotifyLyrics(
                                        song.toTrack().copy(
                                            durationSeconds = duration,
                                        ),
                                        "${song.title} $artist",
                                        duration,
                                    )
                                }
                            }

                            is Resource.Error -> {
                                Log.w(TAG, "Get Lyrics Data Error")
                                if (_lyrics.value?.message != "reset") {
                                    if (dataStoreManager.spotifyLyrics.first() == TRUE) {
                                        getSpotifyLyrics(
                                            song.toTrack().copy(
                                                durationSeconds = duration,
                                            ),
                                            "${song.title} $artist",
                                            duration,
                                        )
                                    } else {
                                        getSavedLyrics(
                                            song.toTrack().copy(
                                                durationSeconds = duration,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                }
            } else if (dataStoreManager.lyricsProvider.first() == DataStoreManager.YOUTUBE) {

                mainRepository.getYouTubeCaption(videoId).cancellable().collect { response ->
                    _lyrics.value = response
                    when (response) {
                        is Resource.Success -> {
                            if (response.data != null) {
                                insertLyrics(response.data.toLyricsEntity(videoId))
                                updateLyrics(
                                    videoId,
                                    response.data,
                                    false,
                                    LyricsProvider.YOUTUBE
                                )
                            } else if (dataStoreManager.spotifyLyrics.first() == TRUE) {
                                getSpotifyLyrics(
                                    song.toTrack().copy(
                                        durationSeconds = duration,
                                    ),
                                    "${song.title} ${song.artistName?.firstOrNull() ?: simpleMediaServiceHandler?.nowPlaying?.first()?.mediaMetadata?.artist ?: ""}",
                                    duration,
                                )
                            }
                        }

                        is Resource.Error -> {
                            if (_lyrics.value?.message != "reset") {
                                if (dataStoreManager.spotifyLyrics.first() == TRUE) {
                                    getSpotifyLyrics(
                                        song.toTrack().copy(
                                            durationSeconds = duration,
                                        ),
                                        "${song.title} ${song.artistName?.firstOrNull() ?: simpleMediaServiceHandler?.nowPlaying?.first()?.mediaMetadata?.artist ?: ""}",
                                        duration,
                                    )
                                } else {
                                    getSavedLyrics(
                                        song.toTrack().copy(
                                            durationSeconds = duration,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    private fun getSpotifyLyrics(
        track: Track,
        query: String,
        duration: Int? = null,
    ) {
        viewModelScope.launch {
            Log.d("Check SpotifyLyrics", "SpotifyLyrics $query")
            mainRepository.getSpotifyLyrics(query, duration).cancellable().collect { response ->
                Log.d("Check SpotifyLyrics", response.toString())
                _lyrics.value = response
                when (response) {
                    is Resource.Success -> {
                        if (response.data != null) {
                            insertLyrics(response.data.toLyricsEntity(query))
                            updateLyrics(
                                track.videoId,
                                response.data,
                                false,
                                LyricsProvider.SPOTIFY
                            )
                        }
                    }

                    is Resource.Error -> {
                        if (_lyrics.value?.message != "reset") {
                            getSavedLyrics(
                                track,
                            )
                        }
                    }
                }
            }
        }
    }

    fun getLyricsProvider(): String {
        return runBlocking { dataStoreManager.lyricsProvider.first() }
    }

    fun setLyricsProvider(provider: String) {
        viewModelScope.launch {
            dataStoreManager.setLyricsProvider(provider)
            delay(500)
            nowPlayingState.value?.songEntity?.let {
                getLyricsFromFormat(it, timeline.value.total.toInt()/1000)
            }
        }
    }

    fun updateInLibrary(videoId: String) {
        viewModelScope.launch {
            mainRepository.updateSongInLibrary(LocalDateTime.now(), videoId)
        }
    }

    fun insertPairSongLocalPlaylist(pairSongLocalPlaylist: PairSongLocalPlaylist) {
        viewModelScope.launch {
            mainRepository.insertPairSongLocalPlaylist(pairSongLocalPlaylist)
        }
    }

    private var _recreateActivity: MutableLiveData<Boolean> = MutableLiveData()
    val recreateActivity: LiveData<Boolean> = _recreateActivity

    fun activityRecreate() {
        _recreateActivity.value = true
    }

    fun activityRecreateDone() {
        _recreateActivity.value = false
    }

    fun addToQueue(track: Track) {
        viewModelScope.launch {
            simpleMediaServiceHandler?.loadMoreCatalog(arrayListOf(track))
            Toast.makeText(
                context,
                context.getString(R.string.added_to_queue),
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    }

    fun addListToQueue(list: ArrayList<Track>) {
        viewModelScope.launch {
            simpleMediaServiceHandler?.loadMoreCatalog(list)
            Toast.makeText(
                context,
                context.getString(R.string.added_to_queue),
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    }

    fun playNext(song: Track) {
        viewModelScope.launch {
            simpleMediaServiceHandler?.playNext(song)
            Toast.makeText(context, context.getString(R.string.play_next), Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun logInToYouTube() = dataStoreManager.loggedIn

    fun addToYouTubeLiked() {
        viewModelScope.launch {
            val videoId = simpleMediaServiceHandler?.nowPlaying?.first()?.mediaId
            if (videoId != null) {
                val like = (listYouTubeLiked.first()?.contains(videoId) == true)
                if (!like) {
                    mainRepository.addToYouTubeLiked(
                        simpleMediaServiceHandler?.nowPlaying?.first()?.mediaId,
                    )
                        .collect { response ->
                            if (response == 200) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.added_to_youtube_liked),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                getYouTubeLiked()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                } else {
                    mainRepository.removeFromYouTubeLiked(
                        simpleMediaServiceHandler?.nowPlaying?.first()?.mediaId,
                    )
                        .collect {
                            if (it == 200) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.removed_from_youtube_liked),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                getYouTubeLiked()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                }
            }
        }
    }

    private var _homeRefresh: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val homeRefresh: StateFlow<Boolean> = _homeRefresh.asStateFlow()

    fun homeRefresh() {
        _homeRefresh.value = true
    }

    fun homeRefreshDone() {
        _homeRefresh.value = false
    }

    fun runWorker() {
        Log.w("Check Worker", "Worker")
        val request =
            PeriodicWorkRequestBuilder<NotifyWork>(
                12L,
                TimeUnit.HOURS,
            )
                .addTag("Worker Test")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            "Artist Worker",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

}

sealed class UIEvent {
    data object PlayPause : UIEvent()

    data object Backward : UIEvent()

    data object Forward : UIEvent()

    data object Next : UIEvent()

    data object Previous : UIEvent()

    data object Stop : UIEvent()

    data object Shuffle : UIEvent()

    data object Repeat : UIEvent()

    data class UpdateProgress(val newProgress: Float) : UIEvent()

    data object ToggleLike: UIEvent()
}

sealed class UIState {
    object Initial : UIState()

    object Ready : UIState()

    object Ended : UIState()
}

enum class LyricsProvider {
    MUSIXMATCH,
    YOUTUBE,
    SPOTIFY,
    OFFLINE,
}

data class TimeLine(
    val current: Long,
    val total: Long,
    val bufferedPercent: Int,
    val loading: Boolean = true,
)

data class NowPlayingScreenData(
    val playlistName: String,
    val nowPlayingTitle: String,
    val artistName: String,
    val isVideo: Boolean,
    val thumbnailURL: String?,
    val canvasData: CanvasData? = null,
    val lyricsData: LyricsData? = null,
    val songInfoData: SongInfoEntity? = null,
) {
    data class CanvasData(
        val isVideo: Boolean,
        val url : String
    )
    data class LyricsData(
        val lyrics: Lyrics,
        val translatedLyrics: Lyrics? = null,
        val lyricsProvider: LyricsProvider,
    )

    companion object {
        fun initial(): NowPlayingScreenData {
            return NowPlayingScreenData(
                nowPlayingTitle = "",
                artistName = "",
                isVideo = false,
                thumbnailURL = null,
                canvasData = null,
                lyricsData = null,
                songInfoData = null,
                playlistName = "",
            )
        }
    }
}