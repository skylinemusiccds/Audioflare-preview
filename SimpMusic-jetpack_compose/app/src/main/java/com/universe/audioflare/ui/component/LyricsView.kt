package com.universe.audioflare.ui.component

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.universe.audioflare.R
import com.universe.audioflare.extension.KeepScreenOn
import com.universe.audioflare.extension.animateScrollAndCentralizeItem
import com.universe.audioflare.extension.formatDuration
import com.universe.audioflare.extension.navigateSafe
import com.universe.audioflare.service.RepeatState
import com.universe.audioflare.ui.theme.seed
import com.universe.audioflare.ui.theme.typo
import com.universe.audioflare.viewModel.NowPlayingScreenData
import com.universe.audioflare.viewModel.SharedViewModel
import com.universe.audioflare.viewModel.TimeLine
import com.universe.audioflare.viewModel.UIEvent
import com.moriatsushi.insetsx.ExperimentalSoftwareKeyboardApi
import com.moriatsushi.insetsx.systemBars
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


@Composable
fun LyricsView(
    lyricsData: NowPlayingScreenData.LyricsData,
    timeLine: StateFlow<TimeLine>,
    onLineClick: (Float) -> Unit,
) {
    val TAG = "LyricsView"

    val textMeasurer = rememberTextMeasurer()
    val localDensity = LocalDensity.current

    var columnHeightDp by remember {
        mutableStateOf(0.dp)
    }
    var columnWidthDp by remember {
        mutableStateOf(0.dp)
    }
    var currentLineHeight by remember {
        mutableIntStateOf(0)
    }
    val listState = rememberLazyListState()
    val current by timeLine.collectAsState()
    var currentLineIndex by rememberSaveable {
        mutableIntStateOf(-1)
    }
    LaunchedEffect(key1 = current) {
        val lines = lyricsData.lyrics.lines
        if (current.current > 0L) {
            lines?.indices?.forEach { i ->
                val sentence = lines[i]
                val startTimeMs = sentence.startTimeMs.toLong()

                // estimate the end time of the current sentence based on the start time of the next sentence
                val endTimeMs =
                    if (i < lines.size - 1) {
                        lines[i + 1].startTimeMs.toLong()
                    } else {
                        // if this is the last sentence, set the end time to be some default value (e.g., 1 minute after the start time)
                        startTimeMs + 60000
                    }
                if (current.current in startTimeMs..endTimeMs) {
                    currentLineIndex = i
                }
            }
            if (!lines.isNullOrEmpty() && (
                    current.current in (
                        0..(
                            lines.getOrNull(0)?.startTimeMs
                                ?: "0"
                            ).toLong()
                        )
                    )
            ) {
                currentLineIndex = -1
            }
        }
        else {
            currentLineIndex = -1
        }
    }
    LaunchedEffect(key1 = currentLineIndex, key2 = currentLineHeight) {
        if (currentLineIndex > -1 && currentLineHeight > 0 && lyricsData.lyrics.syncType == "LINE_SYNCED") {
            val boxEnd = listState.layoutInfo.viewportEndOffset
            val boxStart = listState.layoutInfo.viewportStartOffset
            val viewPort = boxEnd - boxStart
            val offset = viewPort/2 - currentLineHeight/2
            Log.w(TAG, "Offset: $offset")
            listState.animateScrollAndCentralizeItem(
                index = currentLineIndex,
                this
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                columnHeightDp = with(localDensity) { coordinates.size.height.toDp() }
                columnWidthDp = with(localDensity) { coordinates.size.width.toDp() }
            }
            .fillMaxSize()
    ) {
        items(lyricsData.lyrics.lines?.size ?: 0) { index ->
            val line = lyricsData.lyrics.lines?.getOrNull(index)
            val translatedWords = lyricsData.translatedLyrics?.lines?.getOrNull(index)?.words
            line?.words?.let {
                LyricsLineItem(
                    originalWords = it, translatedWords = translatedWords, isBold = index <= currentLineIndex,
                    modifier = Modifier
                        .clickable {
                            onLineClick(line.startTimeMs.toFloat() * 100 / timeLine.value.total)
                        }
                        .onGloballyPositioned { c ->
                            currentLineHeight = c.size.height
                        }
                )
            }
        }
    }
}

@Composable
fun LyricsLineItem(
    originalWords: String,
    translatedWords: String?,
    isBold: Boolean,
    modifier: Modifier = Modifier
) {
    Crossfade(targetState = isBold) {
        if (it) {
            Column(
                modifier = modifier
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = originalWords, style = typo.headlineMedium, color = Color.White)
                if (translatedWords != null) {
                    Text(text = translatedWords, style = typo.bodyMedium, color = Color.Yellow)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
    if (!isBold) {
        Column(
            modifier = modifier
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = originalWords, style = typo.bodyLarge, color = Color.LightGray)
            if (translatedWords != null) {
                Text(text = translatedWords, style = typo.bodyMedium, color = Color(0xFF97971A))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalSoftwareKeyboardApi::class)
@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun FullscreenLyricsSheet(
    sharedViewModel: SharedViewModel,
    navController: NavController,
    color: Color = Color(0xFF242424),
    onDismiss: () -> Unit
) {
    val screenDataState by sharedViewModel.nowPlayingScreenData.collectAsState()
    val timelineState by sharedViewModel.timeline.collectAsState()
    val controllerState by sharedViewModel.controllerState.collectAsState()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val coroutineScope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val windowInsets = WindowInsets.systemBars

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

    if (screenDataState.lyricsData != null) {
        KeepScreenOn()
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        containerColor = color,
        contentColor = Color.Transparent,
        dragHandle = {},
        scrimColor = Color.Black.copy(alpha = .5f),
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        windowInsets = WindowInsets(0,0,0,0),
        shape = RectangleShape,
    ) {
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = RectangleShape,
            colors = CardDefaults.cardColors().copy(containerColor = color),
        ) {
            Column(
                modifier = Modifier.padding(
                    bottom = with(localDensity) {
                        windowInsets.getBottom(localDensity).toDp()
                    },
                    top = with(localDensity) {
                        windowInsets.getTop(localDensity).toDp()
                    }
                )
            ){
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
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
                                text = screenDataState.nowPlayingTitle,
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
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_keyboard_arrow_down_24),
                                contentDescription = "",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {}, modifier = Modifier.alpha(0f)) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_more_vert_24),
                                contentDescription = "",
                                tint = Color.White
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(20.dp))



                Crossfade(
                    targetState = screenDataState.lyricsData != null, modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .padding(horizontal = 50.dp)
                ) {
                    if (it) {
                        screenDataState.lyricsData?.let { lyrics ->
                            LyricsView(lyricsData = lyrics, timeLine = sharedViewModel.timeline) { f ->
                                sharedViewModel.onUIEvent(UIEvent.UpdateProgress(f))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(id = R.string.unavailable),
                            style = typo.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.fillMaxSize(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Box {
                    Column()
                    {
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
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}