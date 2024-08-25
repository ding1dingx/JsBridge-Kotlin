package com.ding1ding.jsbridge.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.ding1ding.jsbridge.Callback
import com.ding1ding.jsbridge.ConsolePipe
import com.ding1ding.jsbridge.MessageHandler
import com.ding1ding.jsbridge.WebViewJavascriptBridge
import com.ding1ding.jsbridge.model.Person

class MainActivity :
  AppCompatActivity(),
  View.OnClickListener {

  private lateinit var webView: WebView
  private lateinit var bridge: WebViewJavascriptBridge

  private val webViewContainer: LinearLayout by lazy { findViewById(R.id.linearLayout) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setupWebView()
    setupBridge()
    setupClickListeners()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && event?.repeatCount == 0) {
      when {
        webView.canGoBack() -> webView.goBack()
        else -> supportFinishAfterTransition()
      }
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onResume() {
    super.onResume()
    webView.onResume()
    webView.resumeTimers()
  }

  override fun onPause() {
    webView.onPause()
    webView.pauseTimers()
    super.onPause()
  }

  override fun onDestroy() {
    // 01
    bridge.release()
    // 02
    releaseWebView()
    Log.d(TAG, "onDestroy")
    super.onDestroy()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    webView = WebView(this).apply {
      removeJavascriptInterface("searchBoxJavaBridge_")
      removeJavascriptInterface("accessibility")
      removeJavascriptInterface("accessibilityTraversal")

      WebView.setWebContentsDebuggingEnabled(true)

      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        0,
        1f,
      )

      settings.apply {
        javaScriptEnabled = true
        allowUniversalAccessFromFileURLs = true
      }
      webViewClient = createWebViewClient()
      loadUrl("file:///android_asset/index.html")
    }

    webViewContainer.addView(webView)
  }

  private fun setupBridge() {
    bridge = WebViewJavascriptBridge(this, webView).apply {
      consolePipe = object : ConsolePipe {
        override fun post(message: String) {
          Log.d("[console.log]", message)
        }
      }

      registerHandler("DeviceLoadJavascriptSuccess", createDeviceLoadHandler())
      registerHandler("ObjTest", createObjTestHandler())
    }
  }

  private fun setupClickListeners() {
    listOf(R.id.buttonSync, R.id.buttonAsync, R.id.objTest).forEach {
      findViewById<View>(it).setOnClickListener(this)
    }
  }

  private fun createWebViewClient() = object : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      Log.d(TAG, "onPageStarted")
      bridge.injectJavascript()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      Log.d(TAG, "onPageFinished")
    }
  }

  private fun createDeviceLoadHandler() = object : MessageHandler<Map<String, String>, Any> {
    override fun handle(parameter: Map<String, String>): Any {
      Log.d(TAG, "DeviceLoadJavascriptSuccess, $parameter")
      return mapOf("result" to "Android")
    }
  }

  private fun createObjTestHandler() = object : MessageHandler<Map<String, Any>, Map<String, Any>> {
    override fun handle(parameter: Map<String, Any>): Map<String, Any> {
      val name = parameter["name"] as? String ?: ""
      val age = (parameter["age"] as? Number)?.toInt() ?: 0
      return mapOf("name" to name, "age" to age)
    }
  }

  override fun onClick(v: View?) {
    when (v?.id) {
      R.id.buttonSync -> callJsHandler("GetToken")
      R.id.buttonAsync -> callJsHandler("AsyncCall")
      R.id.objTest -> bridge.callHandler(
        "TestJavascriptCallNative",
        mapOf("message" to "Hello from Android"),
        null,
      )
    }
  }

  private fun callJsHandler(handlerName: String) {
    bridge.callHandler(
      handlerName,
      Person("Wukong", 23),
      object : Callback<Any> {
        override fun onResult(result: Any) {
          Log.d(TAG, "$handlerName, $result")
        }
      },
    )
  }

  private fun releaseWebView() {
    webViewContainer.removeView(webView)
    webView.apply {
      stopLoading()
      loadUrl("about:blank")
      clearHistory()
      removeAllViews()
      webChromeClient = null
      // webViewClient = null
      settings.javaScriptEnabled = false
      destroy()
    }
  }

  companion object {
    private const val TAG = "MainActivity"
  }
}
