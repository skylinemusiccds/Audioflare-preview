package com.universe.audioflare.ui.fragment.login

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
import com.universe.audioflare.databinding.FragmentLogInBinding
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
class LogInFragment : Fragment() {

    private var _binding: FragmentLogInBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<LogInViewModel>()
    private val settingsViewModel by activityViewModels<SettingsViewModel>()
    private val sharedViewModel by activityViewModels<SharedViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLogInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                    if (url == Config.YOUTUBE_MUSIC_MAIN_URL) {
                        CookieManager.getInstance().getCookie(url)?.let {
                            viewModel.saveCookie(it)
                        }
                        WebStorage.getInstance().deleteAllData()

                        // Clear all the cookies
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()

                        binding.webView.clearCache(true)
                        binding.webView.clearFormData()
                        binding.webView.clearHistory()
                        binding.webView.clearSslPreferences()
                        viewModel.status.observe(this@LogInFragment) {
                            if (it) {
                                settingsViewModel.addAccount()
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
            loadUrl(Config.LOG_IN_URL)
        }
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
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