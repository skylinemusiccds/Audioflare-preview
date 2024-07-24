package com.universe.audioflare.data.model.home

import com.universe.audioflare.data.model.explore.mood.Mood
import com.universe.audioflare.data.model.home.chart.Chart
import com.universe.audioflare.utils.Resource

data class HomeDataCombine(
    val home: Resource<ArrayList<HomeItem>>,
    val mood: Resource<Mood>,
    val chart: Resource<Chart>,
    val newRelease: Resource<ArrayList<HomeItem>>,
) {
}