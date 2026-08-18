package com.tct.bot.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.tct.bot.R

class WebViewFragment : Fragment(R.layout.fragment_webview) {

    private lateinit var webView: WebView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: View
    private lateinit var layoutWaitingServer: View
    
    private var isRetrying = false
    private var isMainPageError = false
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    companion object {
        fun newInstance(url: String, title: String) = WebViewFragment().apply {
            arguments = Bundle().apply {
                putString("url", url)
                putString("title", title)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.webview)
        toolbar = view.findViewById(R.id.toolbar)
        progressBar = view.findViewById(R.id.progress_bar)
        layoutWaitingServer = view.findViewById(R.id.layout_waiting_server)

        val targetUrl = arguments?.getString("url") ?: "https://t-ct.org"
        val targetTitle = arguments?.getString("title") ?: "Browser"

        toolbar.title = targetTitle
        
        toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
        })

        setupWebView()
        loadTarget(targetUrl)
    }

    private fun loadTarget(url: String) {
        if (url.contains("youtube.com/embed")) {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                    <style>
                        body { margin: 0; padding: 0; background-color: #000; display: flex; justify-content: center; align-items: center; height: 100vh; }
                        iframe { width: 100%; height: 100%; border: none; }
                    </style>
                </head>
                <body>
                    <iframe src="$url" allow="autoplay; fullscreen"></iframe>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL("https://t-ct.org", html, "text/html", "UTF-8", null)
        } else {
            if (url.contains("127.0.0.1") || url.contains("localhost")) {
                layoutWaitingServer.visibility = View.VISIBLE
                webView.visibility = View.GONE
            }
            webView.loadUrl(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false 
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                isMainPageError = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                if (!isMainPageError) {
                    isRetrying = false
                    layoutWaitingServer.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    isMainPageError = true
                    val failingUrl = request.url.toString()
                    
                    if (failingUrl.contains("127.0.0.1") || failingUrl.contains("localhost")) {
                        isRetrying = true
                        layoutWaitingServer.visibility = View.VISIBLE
                        webView.visibility = View.GONE
                        
                        retryRunnable?.let { handler.removeCallbacks(it) }
                        retryRunnable = Runnable { if (isRetrying) webView.reload() }
                        handler.postDelayed(retryRunnable!!, 3000)
                    }
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        retryRunnable?.let { handler.removeCallbacks(it) }
        webView.destroy()
    }
}
