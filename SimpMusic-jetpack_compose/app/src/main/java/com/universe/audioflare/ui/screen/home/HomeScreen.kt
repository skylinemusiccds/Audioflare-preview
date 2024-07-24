package com.universe.audioflare.ui.screen.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.universe.audioflare.R
import com.universe.audioflare.common.CHART_SUPPORTED_COUNTRY
import com.universe.audioflare.common.Config
import com.universe.audioflare.data.model.browse.album.Track
import com.universe.audioflare.data.model.explore.mood.Mood
import com.universe.audioflare.data.model.home.HomeItem
import com.universe.audioflare.data.model.home.chart.Chart
import com.universe.audioflare.data.model.home.chart.toTrack
import com.universe.audioflare.extension.navigateSafe
import com.universe.audioflare.extension.toTrack
import com.universe.audioflare.service.PlaylistType
import com.universe.audioflare.service.QueueData
import com.universe.audioflare.ui.component.CenterLoadingBox
import com.universe.audioflare.ui.component.DropdownButton
import com.universe.audioflare.ui.component.EndOfPage
import com.universe.audioflare.ui.component.HomeItem
import com.universe.audioflare.ui.component.HomeShimmer
import com.universe.audioflare.ui.component.ItemArtistChart
import com.universe.audioflare.ui.component.ItemTrackChart
import com.universe.audioflare.ui.component.ItemVideoChart
import com.universe.audioflare.ui.component.MoodMomentAndGenreHomeItem
import com.universe.audioflare.ui.component.QuickPicksItem
import com.universe.audioflare.ui.component.RippleIconButton
import com.universe.audioflare.ui.theme.typo
import com.universe.audioflare.viewModel.HomeViewModel
import com.universe.audioflare.viewModel.SharedViewModel
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.crossfade.CrossfadePlugin
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    sharedViewModel: SharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    navController: NavController,
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    val accountInfo by viewModel.accountInfo.collectAsState()
    val homeData by viewModel.homeItemList.collectAsState()
    val newRelease by viewModel.newRelease.collectAsState()
    val chart by viewModel.chart.collectAsState()
    val moodMomentAndGenre by viewModel.exploreMoodItem.collectAsState()
    val chartLoading by viewModel.loadingChart.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val accountShow by rememberSaveable {
        mutableStateOf(homeData.find { it.subtitle == accountInfo?.first } != null)
    }
    val regionChart by viewModel.regionCodeChart.collectAsState()
    val homeRefresh by sharedViewModel.homeRefresh.collectAsState()
    val pullToRefreshState =
        rememberPullToRefreshState(
            20.dp,
        )
    val scaleFraction =
        if (pullToRefreshState.isRefreshing) {
            1f
        } else {
            LinearOutSlowInEasing.transform(pullToRefreshState.progress).coerceIn(0f, 1f)
        }
    if (pullToRefreshState.isRefreshing) {
        viewModel.getHomeItemList()
        if (!loading) {
            pullToRefreshState.endRefresh()
            sharedViewModel.homeRefreshDone()
        }
    }
    LaunchedEffect(key1 = homeRefresh) {
        Log.w("HomeScreen", "homeRefresh: $homeRefresh")
        if (homeRefresh) {
            Log.w(
                "HomeScreen",
                "scrollState.firstVisibleItemIndex: ${scrollState.firstVisibleItemIndex}",
            )
            if (scrollState.firstVisibleItemIndex == 1) {
                Log.w(
                    "HomeScreen",
                    "scrollState.canScrollBackward: ${scrollState.canScrollBackward}",
                )
                pullToRefreshState.startRefresh()
            } else {
                Log.w(
                    "HomeScreen",
                    "scrollState.canScrollBackward: ${scrollState.canScrollBackward}",
                )
                launch { scrollState.scrollToItem(0, 0) }
                sharedViewModel.homeRefreshDone()
            }
        }
    }
    Column {
        HomeTopAppBar(navController)
        Box(
            modifier =
            Modifier
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            PullToRefreshContainer(
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 15.dp)
                    .graphicsLayer(scaleX = scaleFraction, scaleY = scaleFraction),
                state = pullToRefreshState,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Crossfade(targetState = loading, label = "Home Shimmer") { loading ->
                if (!loading) {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 15.dp),
                        state = scrollState,
                    ) {
                        item {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = accountInfo != null && accountShow,
                            ) {
                                AccountLayout(
                                    accountName = accountInfo?.first ?: "",
                                    url = accountInfo?.second ?: "",
                                )
                            }
                        }
                        item {
                            androidx.compose.animation.AnimatedVisibility(
                                visible =
                                    homeData.find {
                                        it.title ==
                                            context.getString(
                                                R.string.quick_picks,
                                            )
                                    } != null,
                            ) {
                                QuickPicks(
                                    homeItem =
                                        homeData.find {
                                            it.title ==
                                                context.getString(
                                                    R.string.quick_picks,
                                                )
                                        } ?: return@AnimatedVisibility,
                                    sharedViewModel = sharedViewModel,
                                )
                            }
                        }
                        items(homeData) {
                            if (it.title != context.getString(R.string.quick_picks)) {
                                HomeItem(
                                    homeViewModel = viewModel,
                                    sharedViewModel = sharedViewModel,
                                    data = it,
                                    navController = navController,
                                )
                            }
                        }
                        items(newRelease) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = newRelease.isNotEmpty(),
                            ) {
                                HomeItem(
                                    homeViewModel = viewModel,
                                    sharedViewModel = sharedViewModel,
                                    data = it,
                                    navController = navController,
                                )
                            }
                        }
                        item {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = moodMomentAndGenre != null,
                            ) {
                                moodMomentAndGenre?.let {
                                    MoodMomentAndGenre(
                                        mood = it,
                                        navController = navController,
                                    )
                                }
                            }
                        }
                        item {
                            Crossfade(targetState = chart == null && !chartLoading) { noData ->
                                if (!noData) {
                                    Column(
                                        Modifier
                                            .padding(vertical = 10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        ChartTitle()
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Crossfade(targetState = regionChart) {
                                            Log.w("HomeScreen", "regionChart: $it")
                                            if (it != null) {
                                                DropdownButton(
                                                    items = CHART_SUPPORTED_COUNTRY.itemsData.toList(),
                                                    defaultSelected =
                                                    CHART_SUPPORTED_COUNTRY.itemsData.getOrNull(
                                                        CHART_SUPPORTED_COUNTRY.items.indexOf(it),
                                                    )
                                                        ?: CHART_SUPPORTED_COUNTRY.itemsData[1],
                                                ) {
                                                    viewModel.exploreChart(
                                                        CHART_SUPPORTED_COUNTRY.items[
                                                            CHART_SUPPORTED_COUNTRY.itemsData.indexOf(
                                                                it,
                                                            ),
                                                        ],
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Crossfade(
                                            targetState = chartLoading,
                                            label = "Chart",
                                        ) { loading ->
                                            if (!loading) {
                                                chart?.let {
                                                    ChartData(
                                                        chart = it,
                                                        sharedViewModel = sharedViewModel,
                                                        navController = navController,
                                                        context = context,
                                                    )
                                                }
                                            } else {
                                                CenterLoadingBox(
                                                    modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(400.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            EndOfPage()
                        }
                    }
                } else {
                    HomeShimmer()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(navController: NavController) {
    val hour =
        remember {
            val date = Calendar.getInstance().time
            val formatter = SimpleDateFormat("HH")
            formatter.format(date).toInt()
        }
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = typo.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text =
                        when (hour) {
                            in 6..12 -> {
                                stringResource(R.string.good_morning)
                            }

                            in 13..17 -> {
                                stringResource(R.string.good_afternoon)
                            }

                            in 18..23 -> {
                                stringResource(R.string.good_evening)
                            }

                            else -> {
                                stringResource(R.string.good_night)
                            }
                        },
                    style = typo.bodySmall,
                )
            }
        },
        actions = {
            RippleIconButton(resId = R.drawable.outline_notifications_24) {
                navController.navigateSafe(R.id.action_global_notificationFragment)
            }
            RippleIconButton(resId = R.drawable.baseline_history_24) {
                navController.navigateSafe(
                    R.id.action_bottom_navigation_item_home_to_recentlySongsFragment,
                )
            }
            RippleIconButton(resId = R.drawable.baseline_settings_24) {
                navController.navigateSafe(
                    R.id.action_bottom_navigation_item_home_to_settingsFragment,
                )
            }
        },
    )
}

@Composable
fun AccountLayout(
    accountName: String,
    url: String,
) {
    Column {
        Text(
            text = stringResource(id = R.string.welcome_back),
            style = typo.bodyMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        ) {
            CoilImage(
                imageModel = { url },
                imageOptions =
                    ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                    ),
                previewPlaceholder = painterResource(id = R.drawable.holder),
                component =
                    rememberImageComponent {
                        CrossfadePlugin(
                            duration = 550,
                        )
                    },
                modifier =
                Modifier
                    .size(40.dp)
                    .clip(
                        CircleShape,
                    ),
            )
            Text(
                text = accountName,
                style = typo.headlineMedium,
                color = Color.White,
                modifier =
                    Modifier
                        .padding(start = 8.dp),
            )
        }
    }
}

@UnstableApi
@ExperimentalFoundationApi
@Composable
fun QuickPicks(
    homeItem: HomeItem,
    sharedViewModel: SharedViewModel,
) {
    val lazyListState = rememberLazyGridState()
    val snapperFlingBehavior = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState))
    val density = LocalDensity.current
    var widthDp by remember {
        mutableStateOf(0.dp)
    }
    Column(
        Modifier
            .padding(vertical = 8.dp)
            .onGloballyPositioned { coordinates ->
                with(density) {
                    widthDp = (coordinates.size.width).toDp()
                }
            },
    ) {
        Text(
            text = stringResource(id = R.string.let_s_start_with_a_radio),
            style = typo.bodyMedium,
        )
        Text(
            text = stringResource(id = R.string.quick_picks),
            style = typo.headlineMedium,
            maxLines = 1,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            modifier = Modifier.height(280.dp),
            state = lazyListState,
            flingBehavior = snapperFlingBehavior,
        ) {
            items(homeItem.contents) {
                if (it != null) {
                    QuickPicksItem(onClick = {
                        val firstQueue: Track = it.toTrack()
                        sharedViewModel.simpleMediaServiceHandler?.setQueueData(
                            QueueData(
                                listTracks = arrayListOf(firstQueue),
                                firstPlayedTrack = firstQueue,
                                playlistId = "RDAMVM${it.videoId}",
                                playlistName = "\"${it.title}\" Radio",
                                playlistType = PlaylistType.RADIO,
                                continuation = null
                            )
                        )
                        sharedViewModel.loadMediaItemFromTrack(
                            firstQueue,
                            from = "\"${it.title}\" Radio",
                            type = Config.SONG_CLICK,
                        )
                    },
                        data = it,
                        widthDp = widthDp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoodMomentAndGenre(
    mood: Mood,
    navController: NavController,
) {

    val lazyListState1 = rememberLazyGridState()
    val snapperFlingBehavior1 = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState1))

    val lazyListState2 = rememberLazyGridState()
    val snapperFlingBehavior2 = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState2))

    Column(
        Modifier
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.let_s_pick_a_playlist_for_you),
            style = typo.bodyMedium,
        )
        Text(
            text = stringResource(id = R.string.moods_amp_moment),
            style = typo.headlineMedium,
            maxLines = 1,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3),
            modifier = Modifier.height(210.dp),
            state = lazyListState1,
            flingBehavior = snapperFlingBehavior1,
        ) {
            items(mood.moodsMoments) {
                MoodMomentAndGenreHomeItem(title = it.title) {
                    navController.navigateSafe(
                        R.id.action_global_moodFragment,
                        Bundle().apply {
                            putString("params", it.params)
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(id = R.string.genre),
            style = typo.headlineMedium,
            maxLines = 1,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3), modifier = Modifier.height(210.dp),
            state = lazyListState2,
            flingBehavior = snapperFlingBehavior2,
        ) {
            items(mood.genres) {
                MoodMomentAndGenreHomeItem(title = it.title) {
                    navController.navigateSafe(
                        R.id.action_global_moodFragment,
                        Bundle().apply {
                            putString("params", it.params)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ChartTitle() {
    Column {
        Text(
            text = stringResource(id = R.string.what_is_best_choice_today),
            style = typo.bodyMedium,
        )
        Text(
            text = stringResource(id = R.string.chart),
            style = typo.headlineMedium,
            maxLines = 1,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun ChartData(
    chart: Chart,
    sharedViewModel: SharedViewModel,
    navController: NavController,
    context: Context,
) {
    var gridWidthDp by remember {
        mutableStateOf(0.dp)
    }
    val density = LocalDensity.current

    val lazyListState = rememberLazyListState()
    val snapperFlingBehavior = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyListState = lazyListState))

    val lazyListState1 = rememberLazyGridState()
    val snapperFlingBehavior1 = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState1))

    val lazyListState2 = rememberLazyGridState()
    val snapperFlingBehavior2 = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState2))

    val lazyListState3 = rememberLazyGridState()
    val snapperFlingBehavior3 = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState3))

    Column(
        Modifier.onGloballyPositioned { coordinates ->
            with(density) {
                gridWidthDp = (coordinates.size.width).toDp()
            }
        }
    ) {
        AnimatedVisibility(
            visible = !chart.songs.isNullOrEmpty(),
            enter = fadeIn(animationSpec = tween(2000)),
            exit = fadeOut(animationSpec = tween(2000)),
        ) {
            Column {
                Text(
                    text = stringResource(id = R.string.top_tracks),
                    style = typo.headlineMedium,
                    maxLines = 1,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                )
                if (!chart.songs.isNullOrEmpty()) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(3),
                        modifier = Modifier.height(210.dp),
                        state = lazyListState1,
                        flingBehavior = snapperFlingBehavior1,
                    ) {
                        items(chart.songs.size) {
                            val data = chart.songs[it]
                            ItemTrackChart(onClick = {
                                sharedViewModel.simpleMediaServiceHandler?.setQueueData(
                                    QueueData(
                                        listTracks = arrayListOf(data),
                                        firstPlayedTrack = data,
                                        playlistName = "\"${data.title}\" ${context.getString(R.string.in_charts)}",
                                        playlistType = PlaylistType.RADIO,
                                        playlistId = "RDAMVM${data.videoId}",
                                        continuation = null
                                    )
                                )
                                sharedViewModel.loadMediaItemFromTrack(
                                    data,
                                    from = "\"${data.title}\" ${context.getString(R.string.in_charts)}",
                                    type = Config.VIDEO_CLICK,
                                )
                            }, data = data, position = it + 1, widthDp = gridWidthDp)
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(id = R.string.top_videos),
            style = typo.headlineMedium,
            maxLines = 1,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
        LazyRow(
            state = lazyListState,
            flingBehavior = snapperFlingBehavior
        ) {
            items(chart.videos.items.size) {
                val data = chart.videos.items[it]
                ItemVideoChart(
                    onClick = {
                        val firstQueue: Track = data.toTrack()
                        sharedViewModel.simpleMediaServiceHandler?.setQueueData(
                            QueueData(
                                listTracks = arrayListOf(firstQueue),
                                firstPlayedTrack = firstQueue,
                                playlistName = "\"${data.title}\" ${context.getString(R.string.in_charts)}",
                                playlistType = PlaylistType.RADIO,
                                playlistId = "RDAMVM${data.videoId}",
                                continuation = null
                            )
                        )
                        sharedViewModel.loadMediaItemFromTrack(
                            firstQueue,
                            from = "\"${data.title}\" ${context.getString(R.string.in_charts)}",
                            type = Config.VIDEO_CLICK,
                        )
                    },
                    data = data,
                    position = it + 1,
                )
            }
        }
        Text(
            text = stringResource(id = R.string.top_artists),
            style = typo.headlineMedium,
            maxLines = 1,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3), modifier = Modifier.height(240.dp),
            state = lazyListState2,
            flingBehavior = snapperFlingBehavior2,
        ) {
            items(chart.artists.itemArtists.size) {
                val data = chart.artists.itemArtists[it]
                ItemArtistChart(onClick = {
                    val args = Bundle()
                    args.putString("channelId", data.browseId)
                    navController.navigateSafe(R.id.action_global_artistFragment, args)
                }, data = data, context = context, widthDp = gridWidthDp)
            }
        }
        AnimatedVisibility(visible = !chart.trending.isNullOrEmpty()) {
            Column {
                Text(
                    text = stringResource(id = R.string.trending),
                    style = typo.headlineMedium,
                    maxLines = 1,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                )
                if (!chart.trending.isNullOrEmpty()) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(3),
                        modifier = Modifier.height(210.dp),
                        state = lazyListState3,
                        flingBehavior = snapperFlingBehavior3,
                    ) {
                        items(chart.trending.size) {
                            val data = chart.trending[it]
                            ItemTrackChart(onClick = {
                                sharedViewModel.simpleMediaServiceHandler?.setQueueData(
                                    QueueData(
                                        listTracks = arrayListOf(data),
                                        firstPlayedTrack = data,
                                        playlistName = "\"${data.title}\" ${context.getString(R.string.in_charts)}",
                                        playlistType = PlaylistType.RADIO,
                                        playlistId = "RDAMVM${data.videoId}",
                                        continuation = null
                                    )
                                )
                                sharedViewModel.loadMediaItemFromTrack(
                                    data,
                                    from = "\"${data.title}\" ${context.getString(R.string.in_charts)}",
                                    type = Config.VIDEO_CLICK,
                                )
                            }, data = data, position = null, widthDp = gridWidthDp)
                        }
                    }
                }
            }
        }
    }
}