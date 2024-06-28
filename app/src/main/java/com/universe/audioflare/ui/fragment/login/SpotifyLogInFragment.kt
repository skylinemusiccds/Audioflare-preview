package com.universe.audioflare.ui.fragment.login

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.universe.audioflare.R
import com.universe.audioflare.common.Config
import com.universe.audioflare.databinding.FragmentSpotifyLogInBinding
import com.universe.audioflare.extension.isMyServiceRunning
import com.universe.audioflare.service.SimpleMediaService
import com.universe.audioflare.viewModel.LogInViewModel
import com.universe.audioflare.viewModel.SettingsViewModel
import com.universe.audioflare.viewModel.SharedViewModel
import com.universe.audioflare.data.DataStoreManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class SpotifyLogInFragment : Fragment() {

    private var _binding: FragmentSpotifyLogInBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<LogInViewModel>()
    private val settingsViewModel by activityViewModels<SettingsViewModel>()
    private val sharedViewModel by activityViewModels<SharedViewModel>()

    private lateinit var dataStoreManager: DataStoreManager

    private var spdcToken: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSpotifyLogInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dataStoreManager = DataStoreManager(requireContext())

        lifecycleScope.launch {
            spdcToken = dataStoreManager.spdcToken.first()
            if (!spdcToken.isNullOrEmpty()) {
                handleSpdcToken(spdcToken!!)
            } else {
                // For testing, use the provided static token
                val staticSpdcToken = "AQAuGFPAGxCeOHGuDKDgNfbRZuYMcZFyulOv_jUxeCo_Jg9sk-HU3pShaUPHlioQykt0b0ryncjUvO8x71K5e0w40pvXWvgFZvtuAprXf-ceVxAcxC2d8dEXVmTKnNnbjYfs5Anr6z1-MJT5WBeSRofzZ7X6asMM_nmsXps5N9u8tjJqEss46hPIyQA6RVt1ubjRdKQ6YBkci7BQMHuc9SuNCBDb"
                handleSpdcToken(staticSpdcToken)
            }
        }
    }

    private fun handleSpdcToken(spdcToken: String) {
        saveSpdcTokenLocally(spdcToken)

        viewModel.saveSpotifySpdc(spdcToken)
        settingsViewModel.setSpotifyLogIn(true)
        Toast.makeText(
            requireContext(),
            R.string.login_success,
            Toast.LENGTH_SHORT
        ).show()
        findNavController().popBackStack()
    }

    private fun saveSpdcTokenLocally(spdcToken: String) {
        lifecycleScope.launch {
            dataStoreManager.saveSpdcToken(spdcToken)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                @SuppressLint("FragmentLiveDataObserve")
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url == Config.SPOTIFY_ACCOUNT_URL) {
                        CookieManager.getInstance().getCookie(url)?.let { cookies ->
                            val spdcToken = extractSpdcTokenFromCookies(cookies)
                            if (!spdcToken.isNullOrEmpty()) {
                                handleSpdcToken(spdcToken)
                            }
                        }
                        WebStorage.getInstance().deleteAllData()

                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()

                        binding.webView.clearCache(true)
                        binding.webView.clearFormData()
                        binding.webView.clearHistory()
                        binding.webView.clearSslPreferences()
                        viewModel.spotifyStatus.observe(this@SpotifyLogInFragment) {
                            if (it) {
                                settingsViewModel.setSpotifyLogIn(true)
                                Toast.makeText(
                                    requireContext(),
                                    R.string.login_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
            }
            settings.javaScriptEnabled = true
            loadUrl(Config.SPOTIFY_LOG_IN_URL)
        }
    }

    private fun extractSpdcTokenFromCookies(cookies: String): String? {
        val cookieMap = cookies.split(";").associate {
            val (name, value) = it.split("=")
            name.trim() to value.trim()
        }
        return cookieMap["sp_dc"]
    }

    @UnstableApi
    override fun onDestroyView() {
        super.onDestroyView()
        val activity = requireActivity()
        val bottom = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation_view)
        bottom.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.bottom_to_top)
        bottom.visibility = View.VISIBLE
        val miniplayer = activity.findViewById<ComposeView>(R.id.miniplayer)
        if (requireActivity().isMyServiceRunning(SimpleMediaService::class.java)) {
            miniplayer.animation =
                AnimationUtils.loadAnimation(requireContext(), R.anim.bottom_to_top)
            if (runBlocking { sharedViewModel.simpleMediaServiceHandler?.nowPlaying?.first() != null }) {
                miniplayer.visibility = View.VISIBLE
            }
        }
    }
}



/*
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.universe.audioflare.R
import com.universe.audioflare.common.Config
import com.universe.audioflare.databinding.FragmentSpotifyLogInBinding
import com.universe.audioflare.extension.isMyServiceRunning
import com.universe.audioflare.service.SimpleMediaService
import com.universe.audioflare.viewModel.LogInViewModel
import com.universe.audioflare.viewModel.SettingsViewModel
import com.universe.audioflare.viewModel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class SpotifyLogInFragment : Fragment() {

    private var _binding: FragmentSpotifyLogInBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<LogInViewModel>()
    private val settingsViewModel by activityViewModels<SettingsViewModel>()
    private val sharedViewModel by activityViewModels<SharedViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSpotifyLogInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spdcToken = "AQAuGFPAGxCeOHGuDKDgNfbRZuYMcZFyulOv_jUxeCo_Jg9sk-HU3pShaUPHlioQykt0b0ryncjUvO8x71K5e0w40pvXWvgFZvtuAprXf-ceVxAcxC2d8dEXVmTKnNnbjYfs5Anr6z1-MJT5WBeSRofzZ7X6asMM_nmsXps5N9u8tjJqEss46hPIyQA6RVt1ubjRdKQ6YBkci7BQMHuc9SuNCBDb"
        viewModel.saveSpotifySpdc(spdcToken)
        settingsViewModel.setSpotifyLogIn(true)
        Toast.makeText(
            requireContext(),
            R.string.login_success,
            Toast.LENGTH_SHORT
        ).show()
        findNavController().popBackStack()
    }

    /*  This block is commented out to prevent loading the Spotify login URL
        binding.topAppBarLayout.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        val activity = requireActivity()
        val bottom = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation_view)
        val miniplayer = activity.findViewById<ComposeView>(R.id.miniplayer)
        bottom.visibility = View.GONE
        miniplayer.visibility = View.GONE
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                @SuppressLint("FragmentLiveDataObserve")
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url == Config.SPOTIFY_ACCOUNT_URL) {
                        CookieManager.getInstance().getCookie(url)?.let {
                            viewModel.saveSpotifySpdc(it)
                        }
                        WebStorage.getInstance().deleteAllData()

                        // Clear all the cookies
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()

                        binding.webView.clearCache(true)
                        binding.webView.clearFormData()
                        binding.webView.clearHistory()
                        binding.webView.clearSslPreferences()
                        viewModel.spotifyStatus.observe(this@SpotifyLogInFragment) {
                            if (it) {
                                settingsViewModel.setSpotifyLogIn(true)
                                Toast.makeText(
                                    requireContext(),
                                    R.string.login_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
            }
            settings.javaScriptEnabled = true
            loadUrl(Config.SPOTIFY_LOG_IN_URL)
        }

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    */ // End of commented block

    @UnstableApi
    override fun onDestroyView() {
        super.onDestroyView()
        val activity = requireActivity()
        val bottom = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation_view)
        bottom.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.bottom_to_top)
        bottom.visibility = View.VISIBLE
        val miniplayer = activity.findViewById<ComposeView>(R.id.miniplayer)
        if (requireActivity().isMyServiceRunning(SimpleMediaService::class.java)) {
            miniplayer.animation =
                AnimationUtils.loadAnimation(requireContext(), R.anim.bottom_to_top)
            if (runBlocking { sharedViewModel.simpleMediaServiceHandler?.nowPlaying?.first() != null }) {
                miniplayer.visibility = View.VISIBLE
            }
        }
    }*/
}
