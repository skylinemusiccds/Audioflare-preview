package com.universe.audioflare.data.model.home

import com.universe.audioflare.data.model.explore.mood.Mood
import com.universe.audioflare.data.model.home.chart.Chart
import com.universe.audioflare.utils.Resource


data class HomeResponse(
    val homeItem: Resource<ArrayList<HomeItem>>,
    val exploreMood: Resource<Mood>,
    val exploreChart: Resource<Chart>
)