package com.example.myapplication

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.example.myapplication.ui.theme.KahootPalette
import kotlinx.coroutines.delay
import kotlin.math.sin

enum class GameState { IDLE, LOADING, ACTIVE, ERROR }

private const val GAME_URL = "http://10.0.2.2:8787/host/"
private const val MIN_LOADING_DURATION_MS = 5000L

@Composable
fun GameWebViewScreen(
    modifier: Modifier = Modifier,
    url: String = GAME_URL,
    gameTitle: String = "Game",
    onWebViewCreated: (WebView) -> Unit,
    onBackToHome: () -> Unit
) {
    var gameState by remember { mutableStateOf(GameState.LOADING) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Page finished loading, but we still enforce a minimum splash duration below
    var pageReady by remember { mutableStateOf(false) }
    var minTimeElapsed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(loadAttempt) {
        minTimeElapsed = false
        delay(MIN_LOADING_DURATION_MS)
        minTimeElapsed = true
    }

    LaunchedEffect(pageReady, minTimeElapsed) {
        if (pageReady && minTimeElapsed && gameState != GameState.ERROR) {
            gameState = GameState.ACTIVE
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // WebView container with animated opacity
        val opacity by animateFloatAsState(
            targetValue = if (gameState == GameState.ACTIVE) 1f else 0f,
            animationSpec = tween(600, easing = LinearOutSlowInEasing),
            label = "WebViewOpacity"
        )

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (gameState != GameState.ERROR) {
                                pageReady = false
                                gameState = GameState.LOADING
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (gameState != GameState.ERROR) {
                                pageReady = true
                                view?.post {
                                    view.requestFocus()
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            gameState = GameState.ERROR
                        }
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        textZoom = 100
                        try {
                            val method = javaClass.getMethod("setSpatialNavigationEnabled", Boolean::class.javaPrimitiveType)
                            method.invoke(this, true)
                        } catch (e: java.lang.Exception) {
                            e.printStackTrace()
                        }
                    }
                    loadUrl(url)
                    webViewInstance = this
                    onWebViewCreated(this)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = opacity }
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        webViewInstance?.requestFocus()
                    }
                },
            update = { webView ->
                webView.requestFocus()
            }
        )

        // Request D-pad focus on WebView once page is fully loaded
        LaunchedEffect(gameState) {
            if (gameState == GameState.ACTIVE) {
                focusRequester.requestFocus()
            }
        }

        // Overlay layer based on state
        Crossfade(
            targetState = gameState,
            animationSpec = tween(300),
            label = "OverlayTransition"
        ) { state ->
            when (state) {
                GameState.LOADING -> {
                    GameLoadingScreen(gameTitle = gameTitle)
                }
                GameState.ERROR -> {
                    GameErrorScreen(
                        onRetry = {
                            pageReady = false
                            gameState = GameState.LOADING
                            loadAttempt++
                            webViewInstance?.reload()
                        },
                        onBack = onBackToHome
                    )
                }
                else -> {
                    // Active state shows nothing on top of webview
                    Box(modifier = Modifier.size(0.dp))
                }
            }
        }
    }
}

@Composable
fun GameLoadingScreen(gameTitle: String) {
    val transition = rememberInfiniteTransition(label = "KetchappAnimations")

    // Bouncing title float animation
    val titleYOffset by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TitleFloat"
    )

    // Pulse glow animation
    val pulseGlow by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    // Continuous progress percentage animation (0 -> 100 over MIN_LOADING_DURATION_MS)
    var progressPercent by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (progressPercent < 100) {
            val elapsed = System.currentTimeMillis() - startTime
            val fraction = (elapsed.toFloat() / MIN_LOADING_DURATION_MS).coerceIn(0f, 1f)
            progressPercent = (fraction * 100).toInt()
            delay(33) // ~30 fps update
        }
    }

    // Cycling TV Remote Control Tips
    val tips = remember {
        listOf(
            "TIP: Use D-Pad arrows on TV Remote to navigate",
            "TIP: Press OK / Enter to confirm your selection",
            "TIP: Press BACK button anytime to return to Home"
        )
    }
    var currentTipIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            currentTipIndex = (currentTipIndex + 1) % tips.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0826),
                        Color(0xFF1E0E3D),
                        Color(0xFF12082D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Floating geometry particle canvas (Ketchapp aesthetic signature)
        FloatingParticlesCanvas()

        // 2. Main Loading UI Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Sleek Studio Badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HYPER-CASUAL ENGINE • TV EDITION",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Game Title with floating bounce animation & gradient text effect
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.offset(y = titleYOffset.dp)
            ) {
                // Subtle glow behind text
                Text(
                    text = gameTitle.uppercase(),
                    color = Color(0xFF00E5FF).copy(alpha = pulseGlow * 0.35f),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
                )
                Text(
                    text = gameTitle.uppercase(),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Percentage counter + Ketchapp Progress Bar
            KetchappProgressBar(
                progressFraction = (progressPercent / 100f).coerceIn(0f, 1f),
                progressPercent = progressPercent,
                modifier = Modifier.width(360.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Cycling Tips Banner
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tips[currentTipIndex],
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Ketchapp-style progress bar with percentage indicator,
 * multi-stop neon gradient fill, and a light flare head.
 */
@Composable
fun KetchappProgressBar(
    progressFraction: Float,
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Percentage counter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOADING GAME...",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "$progressPercent%",
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress track container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            val filledWidth = maxWidth * progressFraction

            if (progressFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .width(filledWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF7C4DFF), // Purple
                                    Color(0xFF00E5FF), // Cyan
                                    Color(0xFFFF4081), // Pink
                                    Color(0xFFFFD54F)  // Yellow
                                )
                            )
                        )
                )

                // Light flare at the leading edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (filledWidth - 8.dp).coerceAtLeast(0.dp))
                        .size(12.dp)
                        .shadow(8.dp, CircleShape, spotColor = Color(0xFF00E5FF))
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}

/**
 * Animated background canvas rendering floating Ketchapp-like geometric shapes
 * (squares, rotated diamonds, circles, dots) drifting smoothly.
 */
@Composable
fun FloatingParticlesCanvas() {
    val transition = rememberInfiniteTransition(label = "ParticlesAnim")
    val animTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 12 particle seeds with fixed initial positions and drift math
        val particleData = listOf(
            Triple(0.12f, 0.25f, 18f),
            Triple(0.28f, 0.70f, 24f),
            Triple(0.45f, 0.18f, 14f),
            Triple(0.62f, 0.82f, 20f),
            Triple(0.82f, 0.35f, 16f),
            Triple(0.90f, 0.75f, 22f),
            Triple(0.20f, 0.45f, 12f),
            Triple(0.55f, 0.50f, 26f),
            Triple(0.75f, 0.15f, 15f),
            Triple(0.35f, 0.88f, 18f),
            Triple(0.08f, 0.80f, 16f),
            Triple(0.88f, 0.52f, 20f)
        )

        val colors = listOf(
            Color(0xFF00E5FF).copy(alpha = 0.22f),
            Color(0xFFFF4081).copy(alpha = 0.20f),
            Color(0xFF7C4DFF).copy(alpha = 0.25f),
            Color(0xFFFFD54F).copy(alpha = 0.22f)
        )

        particleData.forEachIndexed { i, p ->
            val baseX = p.first * w
            val baseY = p.second * h
            val pSize = p.third

            // Drift offset
            val offsetY = (sin((animTime * 0.05f + i * 1.5f).toDouble()).toFloat()) * 30f
            val offsetX = (sin((animTime * 0.03f + i * 0.8f).toDouble()).toFloat()) * 20f

            val center = Offset(baseX + offsetX, baseY + offsetY)
            val color = colors[i % colors.size]

            when (i % 3) {
                0 -> {
                    // Draw Circle
                    drawCircle(color = color, radius = pSize, center = center)
                }
                1 -> {
                    // Draw Rotated Square / Diamond
                    rotate(degrees = animTime + i * 30f, pivot = center) {
                        drawRect(
                            color = color,
                            topLeft = Offset(center.x - pSize, center.y - pSize),
                            size = Size(pSize * 2, pSize * 2)
                        )
                    }
                }
                2 -> {
                    // Draw Triangle
                    rotate(degrees = -animTime * 0.5f + i * 20f, pivot = center) {
                        val path = Path().apply {
                            moveTo(center.x, center.y - pSize)
                            lineTo(center.x + pSize, center.y + pSize)
                            lineTo(center.x - pSize, center.y + pSize)
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                }
            }
        }
    }
}

/**
 * Clean, modern error screen translated to English.
 */
@Composable
fun GameErrorScreen(
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val interactionSourceRetry = remember { MutableInteractionSource() }
    val isRetryFocused by interactionSourceRetry.collectIsFocusedAsState()

    val interactionSourceBack = remember { MutableInteractionSource() }
    val isBackFocused by interactionSourceBack.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(KahootPalette.PurpleDark, KahootPalette.Purple)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(420.dp)
                .background(KahootPalette.Cream, RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Text(
                text = "⚠",
                color = KahootPalette.Red,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Connection Failed",
                color = KahootPalette.TextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Unable to connect to the game server.\nPlease verify that the game server is active.",
                color = KahootPalette.TextDark.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Retry button
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .weight(1f)
                        .background(
                            if (isRetryFocused) KahootPalette.Yellow else KahootPalette.TextDark.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = interactionSourceRetry,
                            indication = null,
                            onClick = onRetry
                        )
                        .focusable(interactionSource = interactionSourceRetry),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = KahootPalette.TextDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry",
                            color = KahootPalette.TextDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Close button
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .weight(1f)
                        .background(
                            if (isBackFocused) KahootPalette.Purple else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .border(BorderStroke(1.5.dp, KahootPalette.Purple), RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSourceBack,
                            indication = null,
                            onClick = onBack
                        )
                        .focusable(interactionSource = interactionSourceBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = if (isBackFocused) Color.White else KahootPalette.Purple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
