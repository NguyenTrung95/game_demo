package com.example.myapplication

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.KahootPalette
import com.example.myapplication.ui.theme.MyApplicationTheme

sealed class ScreenTab(val title: String) {
    object Home : ScreenTab("Home")
    class GameWebView(val url: String, val gameTitle: String) : ScreenTab("Play Game")
}

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val activeTabState = mutableStateOf<ScreenTab>(ScreenTab.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    activeTabState = activeTabState,
                    onWebViewCreated = { wv -> webView = wv }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val currentWebView = webView
        if (currentWebView != null &&
            activeTabState.value is ScreenTab.GameWebView &&
            event.action == KeyEvent.ACTION_DOWN
        ) {
            val jsKeyName = jsKeyNameForDpadKeyCode(event.keyCode)
            if (jsKeyName != null) {
                dispatchDpadKeyToWebPage(currentWebView, jsKeyName)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

// WebView.dispatchKeyEvent() không dịch tin cậy KeyEvent Android sang phím mũi tên cho JS của
// trang host (game-duckrace) — trang này tự quản lý focus qua window.addEventListener('keydown').
// Nên bắn thẳng 1 KeyboardEvent giả vào DOM thay vì trông chờ WebView tự chuyển tiếp.
private fun jsKeyNameForDpadKeyCode(keyCode: Int): String? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
    KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
    KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "Enter"
    else -> null
}

private fun dispatchDpadKeyToWebPage(webView: WebView, key: String) {
    // Sự kiện keydown giả không được browser tự click nút đang focus (isTrusted = false),
    // nên với phím Enter/OK phải gọi .click() luôn trên activeElement.
    val clickOnEnter = if (key == "Enter") "document.activeElement && document.activeElement.click();" else ""
    webView.evaluateJavascript(
        "document.activeElement && document.activeElement.dispatchEvent(" +
            "new KeyboardEvent('keydown', { key: '$key', bubbles: true })); $clickOnEnter",
        null
    )
}

@Composable
fun MainAppScreen(
    activeTabState: MutableState<ScreenTab>,
    onWebViewCreated: (WebView) -> Unit
) {
    var activeTab by activeTabState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KahootPalette.PurpleDark)
    ) {
        if (activeTab is ScreenTab.GameWebView) {
            val gameWebTab = activeTab as ScreenTab.GameWebView
            BackHandler {
                activeTab = ScreenTab.Home
            }
            GameWebViewScreen(
                url = gameWebTab.url,
                gameTitle = gameWebTab.gameTitle,
                onWebViewCreated = onWebViewCreated,
                onBackToHome = { activeTab = ScreenTab.Home }
            )
        } else {
            // Full screen layout instead of Sidebar
            Box(modifier = Modifier.fillMaxSize()) {
                when (activeTab) {
                    ScreenTab.Home -> HomeScreen(
                        onGameSelected = { url, title ->
                            activeTab = ScreenTab.GameWebView(url, title)
                        }
                    )
                    else -> PlaceholderScreen(tab = activeTab)
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(tab: ScreenTab) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KahootPalette.PurpleDark)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tab.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "This screen is currently under development.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}