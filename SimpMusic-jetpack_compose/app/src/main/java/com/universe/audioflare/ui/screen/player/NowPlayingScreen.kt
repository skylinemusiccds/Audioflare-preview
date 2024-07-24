@file:OptIn(ExperimentalMaterial3Api::class)

package com.universe.audioflare.ui.screen.player

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.universe.audioflare.R
import com.universe.audioflare.common.DownloadState
import com.universe.audioflare.data.db.entities.PairSongLocalPlaylist
import com.universe.audioflare.extension.GradientAngle
import com.universe.audioflare.extension.GradientOffset
import com.universe.audioflare.extension.formatDuration
import com.universe.audioflare.extension.getBrushListColorFromPalette
import com.universe.audioflare.extension.getScreenSizeInfo
import com.universe.audioflare.extension.navigateSafe
import com.universe.audioflare.extension.parseTimestampToMilliseconds
import com.universe.audioflare.extension.removeConflicts
import com.universe.audioflare.service.RepeatState
import com.universe.audioflare.service.test.download.MusicDownloadService
import com.universe.audioflare.ui.component.CenterLoadingBox
import com.universe.audioflare.ui.component.DescriptionView
import com.universe.audioflare.ui.component.FullscreenLyricsSheet
import com.universe.audioflare.ui.component.HeartCheckBox
import com.universe.audioflare.ui.component.LyricsView
import com.universe.audioflare.ui.component.MediaPlayerView
import com.universe.audioflare.ui.component.NowPlayingBottomSheet
import com.universe.audioflare.ui.theme.AppTheme
import com.universe.audioflare.ui.theme.md_theme_dark_background
import com.universe.audioflare.ui.theme.overlay
import com.universe.audioflare.ui.theme.seed
import com.universe.audioflare.ui.theme.typo
import com.universe.audioflare.viewModel.LyricsProvider
import com.universe.audioflare.viewModel.SharedViewModel
import com.universe.audioflare.viewModel.UIEvent
import com.skydoves.landscapist.animation.crossfade.CrossfadePlugin
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.palette.rememberPaletteState
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@ExperimentalMaterial3Api
@Composable
fun NowPlayingScreen(
    sharedViewModel: SharedViewModel,
    navController: NavController
) {
    val screenInfo = getScreenSizeInfo()

    val TAG = "NowPlayingScreen"
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current

    //ViewModel State
    val controllerState by sharedViewModel.controllerState.collectAsState()
    val screenDataState by sharedViewModel.nowPlayingScreenData.collectAsState()
    val timelineState by sharedViewModel.timeline.collectAsState()
    val listLiked by sharedViewModel.listYouTubeLiked.collectAsState(initial = arrayListOf())

    val songEntity = sharedViewModel.simpleMediaServiceHandler?.nowPlayingState?.mapNotNull { it.songEntity }?.collectAsState(initial = null)

    val shouldShowVideo by sharedViewModel.getVideo.collectAsState()

    LaunchedEffect(key1 = timelineState) {
        Log.w(TAG, "Loading: ${timelineState.loading}")
    }

    //State
    val mainScrollState = rememberScrollState()

    var showHideMiddleLayout by rememberSaveable {
        mutableStateOf(true)
    }

    var showSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showFullscreenLyrics by rememberSaveable {
        mutableStateOf(false)
    }

    //Palette state
    var palette by rememberPaletteState(null)
    val startColor = remember {
        Animatable(md_theme_dark_background)
    }
    val endColor = remember {
        Animatable(md_theme_dark_background)
    }
    val gradientOffset by remember {
        mutableStateOf(GradientOffset(GradientAngle.CW135))
    }

    LaunchedEffect(key1 = palette) {
        palette?.let {
            val colorList = getBrushListColorFromPalette(it, context)
            startColor.animateTo(colorList[0])
            endColor.animateTo(colorList[1])
        }
    }

    //Height
    var topAppBarHeightDp by rememberSaveable {
        mutableIntStateOf(0)
    }
    var middleLayoutHeightDp by rememberSaveable {
        mutableIntStateOf(0)
    }
    var infoLayoutHeightDp by rememberSaveable {
        mutableIntStateOf(0)
    }
    var middleLayoutPaddingDp by rememberSaveable {
        mutableIntStateOf(0)
    }
    val minimumPaddingDp by rememberSaveable {
        mutableIntStateOf(
            30
        )
    }
    LaunchedEffect(
        topAppBarHeightDp, screenInfo, infoLayoutHeightDp, minimumPaddingDp
    ) {
        if (topAppBarHeightDp > 0 && middleLayoutHeightDp > 0 && infoLayoutHeightDp > 0 && screenInfo.hDP > 0) {
            val result = (screenInfo.hDP - topAppBarHeightDp - middleLayoutHeightDp - infoLayoutHeightDp - minimumPaddingDp) / 2
            middleLayoutPaddingDp = if (result > minimumPaddingDp) {
                result
            } else {
                minimumPaddingDp
            }
        }
    }

    var sliderValue by rememberSaveable {
        mutableFloatStateOf(0f)
    }
    LaunchedEffect(key1 = timelineState) {
        sliderValue = if (timelineState.total > 0L) {
            timelineState.current.toFloat() * 100 / timelineState.total.toFloat()
        } else {
            0f
        }
    }
    LaunchedEffect(key1 = screenDataState) {
        showHideMiddleLayout = screenDataState.canvasData == null
    }

    //Show ControlLayout Or Show Artist Badge
    var showHideControlLayout by rememberSaveable {
        mutableStateOf(true)
    }
    val controlLayoutAlpha: Float by animateFloatAsState(
        targetValue = if (showHideControlLayout) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            easing = LinearEasing,
        ), label = "ControlLayoutAlpha"
    )

    var showHideJob by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(key1 = showHideJob) {
        if (!showHideJob) {
            delay(5000)
            if (mainScrollState.value == 0){ showHideControlLayout = false }
            showHideJob = true
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            screenDataState
        }.distinctUntilChangedBy {
            it.canvasData?.url
        }.collectLatest {
            if (it.canvasData != null && mainScrollState.value == 0) {
                showHideJob = false
            } else {
                showHideJob = true
                showHideControlLayout = true
            }
        }
    }


    LaunchedEffect(key1 = showHideControlLayout) {
        if (showHideControlLayout && screenDataState.canvasData != null && mainScrollState.value == 0) {
            showHideJob = false
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { mainScrollState.value }
            .distinctUntilChanged()
            .collect {
                if (it > 0 && !showHideControlLayout && screenDataState.canvasData != null) {
                    showHideJob = true
                    showHideControlLayout = true
                } else if (showHideControlLayout && it == 0 && screenDataState.canvasData != null) {
                    showHideJob = false
                }
            }
    }

    //Fullscreen overlay
    var showHideFullscreenOverlay by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = showHideFullscreenOverlay) {
        if (showHideFullscreenOverlay) {
            delay(3000)
            showHideFullscreenOverlay = false
        }
    }

    if (showSheet && songEntity != null) {
        NowPlayingBottomSheet(
            isBottomSheetVisible = showSheet,
            onDismiss = {
                showSheet = false
            },
            navController = navController,
            sharedViewModel = sharedViewModel,
            songEntity = songEntity,
            onToggleLike = { sharedViewModel.onUIEvent(UIEvent.ToggleLike) },
            getLocalPlaylist = { sharedViewModel.getAllLocalPlaylist() },
            listLocalPlaylist = sharedViewModel.localPlaylist.collectAsState(),
            onDownload = {
                songEntity.value?.let { song ->
                    sharedViewModel.updateDownloadState(
                        song.videoId,
                        DownloadState.STATE_PREPARING,
                    )
                    val downloadRequest =
                        DownloadRequest
                            .Builder(
                                song.videoId,
                                song.videoId.toUri(),
                            ).setData(song.title.toByteArray())
                            .setCustomCacheKey(song.videoId)
                            .build()
                    DownloadService.sendAddDownload(
                        context,
                        MusicDownloadService::class.java,
                        downloadRequest,
                        false,
                    )
                }
            },
            onSleepTimer = {
                sharedViewModel.setSleepTimer(it)
            },
            onMainLyricsProvider = { provider ->
                sharedViewModel.setLyricsProvider(provider)
            },
            onAddToLocalPlaylist = { playlist ->
                val song = songEntity.value ?: return@NowPlayingBottomSheet
                val tempTrack = ArrayList<String>()
                if (playlist.tracks != null) {
                    tempTrack.addAll(playlist.tracks)
                }
                if (!tempTrack.contains(
                        song.videoId,
                    ) &&
                    playlist.syncedWithYouTubePlaylist == 1 &&
                    playlist.youtubePlaylistId != null
                ) {
                    sharedViewModel.addToYouTubePlaylist(
                        playlist.id,
                        playlist.youtubePlaylistId,
                        song.videoId,
                    )
                }
                if (!tempTrack.contains(song.videoId)) {
                    sharedViewModel.insertPairSongLocalPlaylist(
                        PairSongLocalPlaylist(
                            playlistId = playlist.id,
                            songId = song.videoId,
                            position = playlist.tracks?.size ?: 0,
                            inPlaylist = LocalDateTime.now(),
                        ),
                    )
                    tempTrack.add(song.videoId)
                }
                sharedViewModel.updateLocalPlaylistTracks(
                    tempTrack.removeConflicts(),
                    playlist.id,
                )
            }
        )
    }

    if (showFullscreenLyrics) {
        FullscreenLyricsSheet(
            sharedViewModel = sharedViewModel,
            color = startColor.value,
            navController = navController
        ) {
            showFullscreenLyrics = false
        }
    }

    Column(
        Modifier
            .verticalScroll(
                mainScrollState
            )
            .then(
                if (showHideMiddleLayout) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                startColor.value, endColor
                                    .value
                            ),
                            start = gradientOffset.start,
                            end = gradientOffset.end
                        )
                    )
                } else {
                    Modifier.background(md_theme_dark_background)
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            //Canvas Layout
            //TODO: Canvas Layout
            Box(
                modifier = Modifier
                    .height(screenInfo.hDP.dp)
                    .fillMaxWidth()
                    .alpha(
                        if (!showHideMiddleLayout) 1f else 0f
                    )
            ) {
                //Canvas Layout
                //TODO: Canvas Layout
                Crossfade(targetState = screenDataState.canvasData?.isVideo) { isVideo ->
                    if (isVideo == true) {
                        screenDataState.canvasData?.url?.let {
                            MediaPlayerView(
                                url = it, modifier = Modifier
                                    .fillMaxHeight()
                                    .wrapContentWidth(unbounded = true, align = Alignment.CenterHorizontally)
                            )
                        }
                    } else if (isVideo == false) {
                        CoilImage(
                            imageModel = {
                                screenDataState.canvasData?.url
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Crossfade(
                    targetState = (screenDataState.canvasData != null && showHideControlLayout),
                    modifier = Modifier
                        .fillMaxSize()
                        .align(
                            Alignment.BottomCenter
                        )
                ) {
                    if (it) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.8f to overlay,
                                            1f to Color.Black
                                        ),
                                    )
                                )
                        )
                    }

                }
            }

            TopAppBar (
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned {
                        topAppBarHeightDp = with(localDensity) { it.size.height.toDp().value.toInt() }
                    },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = Color.Transparent
                ),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.now_playing_upper),
                            style = typo.bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = screenDataState.playlistName,
                            style = typo.labelMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.CenterVertically)
                                .basicMarquee(animationMode = MarqueeAnimationMode.Immediately)
                                .focusable()
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_keyboard_arrow_down_24),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSheet = true
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_more_vert_24),
                            contentDescription = "",
                            tint = Color.White
                        )
                    }
                },
            )
            Column {
                Spacer(modifier = Modifier.height(
                    topAppBarHeightDp.dp
                )
                )
                Box {
                    Column(
                        Modifier
                            .fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier
                            .animateContentSize()
                            .height(
                                middleLayoutPaddingDp.dp
                            )
                            .fillMaxWidth()
                        )

                        //Middle Layout
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .onGloballyPositioned {
                                middleLayoutHeightDp = with(localDensity) { it.size.height.toDp().value.toInt() }
                            }
                            .alpha(
                                if (showHideMiddleLayout) 1f else 0f
                            )
                            .aspectRatio(1f)) {

                            //IS SONG => Show Artwork
                            CoilImage(
                                imageModel =
                                {
                                    screenDataState.thumbnailURL?.toUri()
                                },
                                previewPlaceholder = painterResource(id = R.drawable.holder),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth()
                                    .aspectRatio(
                                        if (!screenDataState.isVideo) 1f else 16f / 9
                                    )
                                    .clip(
                                        RoundedCornerShape(8.dp)
                                    )
                                    .alpha(
                                        if (!screenDataState.isVideo || !shouldShowVideo) 1f else 0f
                                    ),
                                loading = {
                                    CenterLoadingBox(modifier = Modifier.fillMaxSize())
                                },
                                component = rememberImageComponent {
                                    +CrossfadePlugin(duration = 200)
                                    +PalettePlugin(
                                        paletteLoadedListener = {
                                            palette = it
                                        },
                                        useCache = true,
                                    )
                                    +PlaceholderPlugin.Loading(painterResource(id = R.drawable.holder))
                                    +PlaceholderPlugin.Failure(painterResource(id = R.drawable.holder))
                                }
                            )

                            //IS VIDEO => Show Video
                            androidx.compose.animation.AnimatedVisibility(
                                visible = screenDataState.isVideo && shouldShowVideo,
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9)
                                        .clip(
                                            RoundedCornerShape(8.dp)
                                        )
                                        .background(
                                            md_theme_dark_background
                                        )
                                ) {
                                    //Player
                                    //TODO: Player
                                    Box(Modifier.fillMaxSize()) {
                                        sharedViewModel.simpleMediaServiceHandler?.player?.let {
                                            MediaPlayerView(player = it, modifier = Modifier.align(Alignment.Center))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                onClick = { showHideFullscreenOverlay = !showHideFullscreenOverlay },
                                                indication = null,
                                                interactionSource = remember {
                                                    MutableInteractionSource()
                                                }
                                            )
                                    ) {
                                        Crossfade(
                                            targetState = showHideFullscreenOverlay,
                                        ) {
                                            if (it) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colorStops = arrayOf(
                                                                    0.03f to Color.DarkGray,
                                                                    0.3f to overlay,
                                                                    0.8f to Color.Transparent,
                                                                )
                                                            )
                                                        )
                                                )
                                                {
                                                    IconButton(onClick = {
                                                        navController.navigateSafe(
                                                            R.id.action_global_fullscreenFragment
                                                        )
                                                    }, Modifier.align(Alignment.TopEnd)) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.baseline_fullscreen_24),
                                                            contentDescription = "",
                                                            tint = Color.White
                                                        )
                                                    }
                                                    Row(
                                                        Modifier
                                                            .align(Alignment.Center)
                                                            .fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceEvenly
                                                    ) {
                                                        FilledTonalIconButton(
                                                            colors = IconButtonDefaults.iconButtonColors().copy(
                                                                containerColor = Color.Transparent
                                                            ),
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .aspectRatio(1f)
                                                                .clip(
                                                                    CircleShape
                                                                ),
                                                            onClick = {
                                                                sharedViewModel.onUIEvent(UIEvent.Backward)
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Replay5,
                                                                tint = Color.White,
                                                                contentDescription = "",
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .alpha(0.8f)
                                                            )
                                                        }
                                                        FilledTonalIconButton(
                                                            colors = IconButtonDefaults.iconButtonColors().copy(
                                                                containerColor = Color.Transparent
                                                            ),
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .aspectRatio(1f)
                                                                .clip(
                                                                    CircleShape
                                                                ),
                                                            onClick = {
                                                                sharedViewModel.onUIEvent(UIEvent.Forward)
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Forward5,
                                                                tint = Color.White,
                                                                contentDescription = "",
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .alpha(0.8f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier
                            .animateContentSize()
                            .height(
                                middleLayoutPaddingDp.dp
                            )
                            .fillMaxWidth()
                        )

                        //Info Layout
                        Box {
                            Column(
                                Modifier
                                    .alpha(controlLayoutAlpha)
                                    .onGloballyPositioned {
                                        infoLayoutHeightDp = with(localDensity) { it.size.height.toDp().value.toInt() }
                                    }
                            )
                            {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 40.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = screenDataState.nowPlayingTitle,
                                            style = typo.headlineMedium,
                                            maxLines = 1,
                                            color = Color.White,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(align = Alignment.CenterVertically)
                                                .basicMarquee(animationMode = MarqueeAnimationMode.Immediately)
                                                .focusable()
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = screenDataState.artistName,
                                            style = typo.bodyMedium,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(align = Alignment.CenterVertically)
                                                .basicMarquee(animationMode = MarqueeAnimationMode.Immediately)
                                                .focusable()
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(10.dp))
                                    //TODO: Like Button
                                    HeartCheckBox(checked = controllerState.isLiked, size = 32) {
                                        sharedViewModel.onUIEvent(UIEvent.ToggleLike)
                                    }
                                }
                                //Real Slider
                                Box(
                                    Modifier
                                        .padding(
                                            top = 15.dp
                                        )
                                        .padding(horizontal = 40.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Crossfade(timelineState.loading) {
                                            if (it) {
                                                LinearProgressIndicator(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(5.dp)
                                                        .padding(
                                                            horizontal = 10.dp
                                                        )
                                                        .clip(
                                                            RoundedCornerShape(8.dp)
                                                        ),
                                                    color = Color.Gray,
                                                    trackColor = Color.DarkGray,
                                                )
                                            } else {
                                                LinearProgressIndicator(
                                                    progress = { timelineState.bufferedPercent.toFloat() / 100 },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(5.dp)
                                                        .padding(
                                                            horizontal = 10.dp
                                                        )
                                                        .clip(
                                                            RoundedCornerShape(8.dp)
                                                        ),
                                                    color = Color.Gray,
                                                    trackColor = Color.DarkGray,
                                                )
                                            }
                                        }
                                    }
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                        Slider(
                                            value = sliderValue,
                                            onValueChange = {
                                                sharedViewModel.onUIEvent(
                                                    UIEvent.UpdateProgress(it)
                                                )
                                            },
                                            valueRange = 0f..100f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(
                                                    Alignment.TopCenter
                                                ),
                                            colors = SliderDefaults.colors().copy(
                                                thumbColor = Color.White,
                                                activeTrackColor = Color.White,
                                                inactiveTrackColor = Color.Transparent
                                            ),
                                            thumb = {
                                                SliderDefaults.Thumb(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .padding(6.dp),
                                                    thumbSize = DpSize(8.dp, 8.dp),
                                                    interactionSource = remember {
                                                        MutableInteractionSource()
                                                    },
                                                    colors = SliderDefaults.colors().copy(
                                                        thumbColor = Color.White,
                                                        activeTrackColor = Color.White,
                                                        inactiveTrackColor = Color.Transparent
                                                    ),
                                                    enabled = true
                                                )
                                            },
                                        )
                                    }
                                }
                                //Time Layout
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 40.dp)
                                ) {
                                    Text(
                                        text = if (timelineState.current >= 0L) formatDuration(timelineState.current) else stringResource(id = R.string.na_na),
                                        style = typo.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Left
                                    )
                                    Text(
                                        text = if (timelineState.total >= 0L) formatDuration(timelineState.total) else stringResource(id = R.string.na_na),
                                        style = typo.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Right
                                    )
                                }

                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                )
                                //Control Button Layout
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                        .padding(horizontal = 40.dp)
                                ) {
                                    FilledTonalIconButton(
                                        colors = IconButtonDefaults.iconButtonColors().copy(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .aspectRatio(1f)
                                            .clip(
                                                CircleShape
                                            ),
                                        onClick = {
                                            sharedViewModel.onUIEvent(UIEvent.Shuffle)
                                        }
                                    ) {
                                        Crossfade(targetState = controllerState.isShuffle, label = "Shuffle Button") { isShuffle ->
                                            if (isShuffle) {
                                                Icon(
                                                    imageVector = Icons.Filled.Shuffle, tint = Color.White, contentDescription = "",
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Shuffle, tint = seed, contentDescription = "",
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                    FilledTonalIconButton(
                                        colors = IconButtonDefaults.iconButtonColors().copy(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .aspectRatio(1f)
                                            .clip(
                                                CircleShape
                                            ),
                                        onClick = {
                                            if (controllerState.isPreviousAvailable) {
                                                sharedViewModel.onUIEvent(UIEvent.Previous)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipPrevious,
                                            tint = if (controllerState.isPreviousAvailable) Color.White else Color.Gray,
                                            contentDescription = "",
                                            modifier = Modifier.size(52.dp),
                                        )
                                    }
                                    FilledTonalIconButton(
                                        colors = IconButtonDefaults.iconButtonColors().copy(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .size(96.dp)
                                            .aspectRatio(1f)
                                            .clip(
                                                CircleShape
                                            ),
                                        onClick = {
                                            sharedViewModel.onUIEvent(UIEvent.PlayPause)
                                        }
                                    ) {
                                        Crossfade(targetState = controllerState.isPlaying) { isPlaying ->
                                            if (!isPlaying) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayCircle,
                                                    tint = Color.White,
                                                    contentDescription = "",
                                                    modifier = Modifier.size(72.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.PauseCircle,
                                                    tint = Color.White,
                                                    contentDescription = "",
                                                    modifier = Modifier.size(72.dp)
                                                )
                                            }
                                        }
                                    }
                                    FilledTonalIconButton(
                                        colors = IconButtonDefaults.iconButtonColors().copy(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .aspectRatio(1f)
                                            .clip(
                                                CircleShape
                                            ),
                                        onClick = {
                                            if (controllerState.isNextAvailable) {
                                                sharedViewModel.onUIEvent(UIEvent.Next)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            tint = if (controllerState.isNextAvailable) Color.White else Color.Gray,
                                            contentDescription = "",
                                            modifier = Modifier.size(52.dp)
                                        )
                                    }
                                    FilledTonalIconButton(
                                        colors = IconButtonDefaults.iconButtonColors().copy(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .aspectRatio(1f)
                                            .clip(
                                                CircleShape
                                            ),
                                        onClick = {
                                            sharedViewModel.onUIEvent(UIEvent.Repeat)
                                        }
                                    ) {
                                        Crossfade(targetState = controllerState.repeatState) { rs ->
                                            when (rs) {
                                                is RepeatState.None -> {
                                                    Icon(
                                                        imageVector = Icons.Filled.Repeat, tint = Color.White, contentDescription = "",
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }

                                                RepeatState.All -> {
                                                    Icon(
                                                        imageVector = Icons.Filled.Repeat, tint = seed, contentDescription = "",
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }

                                                RepeatState.One -> {
                                                    Icon(
                                                        imageVector = Icons.Filled.RepeatOne, tint = seed, contentDescription = "",
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                //List Bottom Buttons
                                //24.dp
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 40.dp),
                                ) {
                                    IconButton(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .aspectRatio(1f)
                                            .align(Alignment.CenterStart)
                                            .clip(
                                                CircleShape
                                            ),
                                        onClick = {
                                            navController.navigateSafe(
                                                R.id.action_global_infoFragment
                                            )
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Info, tint = Color.White, contentDescription = "")
                                    }
                                    Row(
                                        Modifier.align(Alignment.CenterEnd)
                                    ) {
                                        Crossfade(
                                            targetState = !listLiked.isNullOrEmpty() && listLiked?.contains(screenDataState.songInfoData?.videoId) == true
                                        ) {
                                            if (it){
                                                IconButton(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .aspectRatio(1f)
                                                        .clip(
                                                            CircleShape
                                                        ),
                                                    onClick = {
                                                        sharedViewModel.addToYouTubeLiked()
                                                    }
                                                ) {
                                                    Icon(imageVector = Icons.Filled.Done, tint = Color.White, contentDescription = "")
                                                }
                                            }
                                            else {
                                                IconButton(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .aspectRatio(1f)
                                                        .clip(
                                                            CircleShape
                                                        ),
                                                    onClick = {
                                                        sharedViewModel.addToYouTubeLiked()
                                                    }
                                                ) {
                                                    Icon(imageVector = Icons.Filled.Add, tint = Color.White, contentDescription = "")
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.size(8.dp))
                                        IconButton(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .aspectRatio(1f)
                                                .clip(
                                                    CircleShape
                                                ),
                                            onClick = {
                                                navController.navigateSafe(
                                                    R.id.action_global_queueFragment
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                                                tint = Color.White,
                                                contentDescription = ""
                                            )
                                        }
                                    }
                                }
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = !showHideControlLayout) {
                                Box(
                                    modifier = Modifier
                                        .height(
                                            infoLayoutHeightDp.dp
                                        )
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 20.dp,
                                            horizontal = 40.dp
                                        )
                                        .clickable(
                                            onClick = {
                                                if (mainScrollState.value == 0) {
                                                    showHideJob = true
                                                    showHideControlLayout = !showHideControlLayout
                                                }
                                            },
                                            indication = null,
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            }
                                        ),
                                    contentAlignment = Alignment.BottomStart,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CoilImage(
                                            imageModel = {
                                                screenDataState.songInfoData?.authorThumbnail?.toUri()
                                            },
                                            previewPlaceholder = painterResource(id = R.drawable.holder),
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(
                                                    CircleShape
                                                ),
                                            component = rememberImageComponent {
                                                +CrossfadePlugin(duration = 200)
                                                +PlaceholderPlugin.Loading(painterResource(id = R.drawable.holder))
                                                +PlaceholderPlugin.Failure(painterResource(id = R.drawable.holder))
                                            }
                                        )
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Text(
                                            text = screenDataState.songInfoData?.author ?: "",
                                            style = typo.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                    //Touch Area
                    androidx.compose.animation.AnimatedVisibility(visible = screenDataState.canvasData != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    (middleLayoutPaddingDp * 2 + middleLayoutHeightDp).dp
                                )
                                .clickable(
                                    onClick = {
                                        if (mainScrollState.value == 0) {
                                            showHideJob = true
                                            showHideControlLayout = !showHideControlLayout
                                        }
                                    },
                                    indication = null,
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                )
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 40.dp)) {
                    //Lyrics Layout
                    AnimatedVisibility(
                        visible = screenDataState.lyricsData != null,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        ElevatedCard(
                            onClick = {},
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.elevatedCardColors().copy(
                                containerColor = startColor.value
                            )
                        ) {
                            Column(modifier = Modifier.padding(15.dp)) {
                                Spacer(modifier = Modifier.height(5.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(id = R.string.lyrics),
                                        style = typo.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                        TextButton(
                                            onClick = {
                                                showFullscreenLyrics = true
                                            }, contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .height(20.dp)
                                                .width(40.dp)
                                        ) {
                                            Text(text = stringResource(id = R.string.show))
                                        }
                                    }
                                }
                                //TODO:Lyrics
                                //Lyrics Layout
                                Spacer(modifier = Modifier.height(18.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                ) {
                                    screenDataState.lyricsData?.let {
                                        LyricsView(lyricsData = it, timeLine = sharedViewModel.timeline,
                                            onLineClick = { f ->
                                                sharedViewModel.onUIEvent(UIEvent.UpdateProgress(f))
                                            }
                                        )
                                    }
                                }

                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = when (screenDataState.lyricsData?.lyrics?.syncType) {
                                            "LINE_SYNCED" -> stringResource(id = R.string.line_synced)
                                            else -> stringResource(id = R.string.unsynced)
                                        },
                                        style = typo.bodySmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                    )
                                    Text(
                                        text = when (screenDataState.lyricsData?.lyricsProvider) {
                                            LyricsProvider.MUSIXMATCH -> stringResource(id = R.string.lyrics_provider)
                                            LyricsProvider.YOUTUBE -> stringResource(id = R.string.lyrics_provider_youtube)
                                            LyricsProvider.SPOTIFY -> stringResource(id = R.string.spotify_lyrics_provider)
                                            LyricsProvider.OFFLINE -> stringResource(id = R.string.offline_mode)
                                            else -> {
                                                ""
                                            }
                                        },
                                        style = typo.bodySmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AnimatedVisibility(visible = screenDataState.songInfoData != null) {
                        ElevatedCard(
                            onClick = {
                                val song = songEntity?.value
                                if (song != null && song.artistId?.firstOrNull() != null) {
                                    navController.navigateSafe(
                                        R.id.action_global_artistFragment,
                                        bundleOf(
                                            "channelId" to song.artistId.firstOrNull()
                                        )
                                    )
                                }
                                else {
                                    navController.navigateSafe(
                                        R.id.action_global_artistFragment,
                                        bundleOf(
                                            "channelId" to screenDataState.songInfoData?.authorId
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.elevatedCardColors().copy(
                                containerColor = startColor.value
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            ) {
                                CoilImage(
                                    imageModel = {
                                        screenDataState.songInfoData?.authorThumbnail?.toUri()
                                    },
                                    previewPlaceholder = painterResource(id = R.drawable.holder_video),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.8f)
                                        .clip(
                                            RoundedCornerShape(8.dp)
                                        ),
                                    component = rememberImageComponent {
                                        +CrossfadePlugin(duration = 200)
                                        +PlaceholderPlugin.Loading(painterResource(id = R.drawable.holder_video))
                                        +PlaceholderPlugin.Failure(painterResource(id = R.drawable.holder_video))
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(15.dp)
                                        .fillMaxSize()
                                ) {
                                    Column(Modifier.align(Alignment.TopStart)) {
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = stringResource(id = R.string.artists),
                                            style = typo.labelMedium,
                                            color = Color.White,
                                        )
                                    }
                                    Column(Modifier.align(Alignment.BottomStart)) {
                                        Text(
                                            text = screenDataState.songInfoData?.author ?: "",
                                            style = typo.labelMedium,
                                            color = Color.White,
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = screenDataState.songInfoData?.subscribers ?: "",
                                            style = typo.bodySmall,
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AnimatedVisibility(visible = screenDataState.songInfoData != null) {
                        ElevatedCard(
                            onClick = {},
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.elevatedCardColors().copy(
                                containerColor = startColor.value
                            )
                        ) {
                            Column(
                                Modifier
                                    .padding(15.dp)
                                    .fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = stringResource(id = R.string.published_at, screenDataState.songInfoData?.uploadDate ?: ""),
                                    style = typo.labelSmall,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(id = R.string.view_count, String.format(java.util.Locale.getDefault(), "%,d", screenDataState.songInfoData?.viewCount)),
                                    style = typo.labelMedium,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(
                                        id = R.string.like_and_dislike,
                                        screenDataState.songInfoData?.like ?: 0,
                                        screenDataState.songInfoData?.dislike ?: 0
                                    ),
                                    style = typo.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(id = R.string.description),
                                    style = typo.labelSmall,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                DescriptionView(
                                    text = screenDataState.songInfoData?.description ?: "",
                                    onTimeClicked = { raw ->
                                        val timestamp = parseTimestampToMilliseconds(raw)
                                        if (timestamp != 0.0 && timestamp < timelineState.total) {
                                            sharedViewModel.onUIEvent(
                                                UIEvent.UpdateProgress(
                                                    ((timestamp * 100) / timelineState.total).toFloat(),
                                                ),
                                            )
                                        }
                                    },
                                    onURLClicked = { url ->
                                        uriHandler.openUri(
                                            url
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Spacer(modifier = Modifier.height(
                        with(localDensity) { WindowInsets.systemBars.getBottom(localDensity).toDp() }
                    ))
                }
            }
        }
    }
}

@UnstableApi
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=1080px,height=5000px,dpi=440")
@Composable
fun NowPlayingScreenPreview() {
    AppTheme {
//        NowPlayingScreen()
    }
}