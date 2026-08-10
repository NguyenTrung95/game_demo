package com.example.myapplication

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
 import androidx.compose.foundation.Image
 import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
 import androidx.compose.foundation.lazy.grid.GridCells
 import androidx.compose.foundation.lazy.grid.items
 import androidx.compose.ui.draw.shadow
 import androidx.compose.ui.draw.scale
 import androidx.compose.ui.res.painterResource
 import androidx.compose.ui.layout.ContentScale
 import androidx.compose.foundation.layout.BoxWithConstraints
 import com.example.myapplication.ui.theme.KahootAnswerShape
 import com.example.myapplication.ui.theme.KahootShapeIcon
 import com.example.myapplication.ui.theme.KahootPalette

val ColorBgMain = Color(0xFF13092A) // Main background color for TV
val ColorSidebarBg = Color(0xFF13092A)
val ColorCardBg = Color(0xFF1B142D)
val ColorSearchBg = Color(0xFF261D42)
val ColorPrimaryPurple = Color(0xFF8052D1)
val ColorPinBoxBg = Color(0xFF332A66)
val ColorBadgeRed = Color(0xFFE21B3C)

// --- MOCK DATA ---
data class KahootGame(
    val title: String,
    val questionsCount: String,
    val plays: String,
    val themeColor: Color,
    val bottomColor: Color,
    val imageContent: @Composable () -> Unit,
    val isNew: Boolean = false,
    val url: String = ""
)

data class TopicCategory(
    val title: String,
    val color: Color,
    val icon: ImageVector
)

val DISCOVER_GAMES = listOf(
    KahootGame("Cosmic Wonders", "10 Questions", "12.5K", Color(0xFF1C2B54), Color(0xFF15122B), { Image(painter = painterResource(id = R.drawable.card_1), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("World Geography", "15 Questions", "9.3K", Color(0xFF00685A), Color(0xFF0F1E29), { Image(painter = painterResource(id = R.drawable.card_2), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Natural Science", "12 Questions", "8.7K", Color(0xFF00569C), Color(0xFF101D33), { Image(painter = painterResource(id = R.drawable.card_3), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("World Literature", "20 Questions", "6.2K", Color(0xFF6B4527), Color(0xFF281F19), { Image(painter = painterResource(id = R.drawable.card_4), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Fun Math", "15 Questions", "7.1K", Color(0xFF701BB8), Color(0xFF1B142D), { Image(painter = painterResource(id = R.drawable.card_5), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("English Master", "12 Questions", "11.8K", Color(0xFF005963), Color(0xFF111928), { Image(painter = painterResource(id = R.drawable.card_6), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) })
)

val LATEST_GAMES = listOf(
    KahootGame("Cell Biology", "10 Questions", "2.1K", Color(0xFF00685A), Color(0xFF071B1B), { Image(painter = painterResource(id = R.drawable.latest_1), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }, isNew = true),
    KahootGame("Climate Change", "12 Questions", "1.8K", Color(0xFF1C2B54), Color(0xFF19180E), { Image(painter = painterResource(id = R.drawable.latest_2), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }, isNew = true),
    KahootGame("Tech 4.0", "15 Questions", "3.4K", Color(0xFF1E3A8A), Color(0xFF0F1522), { Image(painter = painterResource(id = R.drawable.latest_3), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }, isNew = true),
    KahootGame("Famous Figures", "10 Questions", "2.7K", Color(0xFF3B3B3B), Color(0xFF1E1C1B), { Image(painter = painterResource(id = R.drawable.latest_4), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }, isNew = true),
    KahootGame("Road Safety", "12 Questions", "1.6K", Color(0xFF6B4527), Color(0xFF1A202A), { Image(painter = painterResource(id = R.drawable.latest_5), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }, isNew = true),
    KahootGame("Ocean Quest", "15 Questions", "2.5K", Color(0xFF00569C), Color(0xFF0B1A24), { Image(painter = painterResource(id = R.drawable.latest_6), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }, isNew = true)
)

val JOIN_GAMES = listOf(
    KahootGame("Duck Race", "12.5K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_1), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Tug of War", "9.8K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_2), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Speed Trivia", "8.2K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_3), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Obstacle Run", "7.1K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_4), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Treasure Hunt", "6.3K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_5), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Tower Stacker", "5.6K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_6), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Fun Math", "5.2K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_7), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }),
    KahootGame("Memory Rush", "4.8K Playing", "", Color(0xFF191B33), Color(0xFF191B33), { Image(painter = painterResource(id = R.drawable.join_game_8), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) })
)

val TOPICS = listOf(
    TopicCategory("Education", Color(0xFF5D29C6), Icons.Default.School),
    TopicCategory("Science", Color(0xFF0D60B6), Icons.Default.Science),
    TopicCategory("History", Color(0xFFE99616), Icons.Default.AccountBalance),
    TopicCategory("Sports", Color(0xFF1F8D34), Icons.Default.SportsSoccer),
    TopicCategory("Music", Color(0xFFD61F6E), Icons.Default.MusicNote),
    TopicCategory("Gaming", Color(0xFF009688), Icons.Default.Gamepad)
)

data class MetroGame(
    val title: String,
    val iconRes: Int,         // @DrawableRes
    val color: Color,
    val tileGradients: List<Color> = listOf(color, color.copy(alpha = 0.8f)),
    val iconTint: Color = Color.White,
    val vectorIcon: ImageVector? = null,
    val keyType: String = "",
    val isActive: Boolean = false,
    val playerCount: String = "",
    val url: String = ""
)

val METRO_GAMES = listOf(
    // Row 1
    MetroGame("Duck Race",       R.drawable.ic_game_duck_real,   Color(0xFF1B5E20), tileGradients = listOf(Color(0xFF00E676), Color(0xFF00B0FF)), keyType = "real_image", isActive = true, playerCount = "12.5K Playing", url = "https://game-demo-production-4101.up.railway.app/host/"),
    MetroGame("Tug of War",         R.drawable.ic_game_sumo_real,   Color(0xFF0D47A1), tileGradients = listOf(Color(0xFFFF1744), Color(0xFF2979FF)), keyType = "real_image", isActive = true, playerCount = "9.8K Playing",  url = "https://game-demo-production-4101.up.railway.app/tug-of-war/host/"),
    MetroGame("Speed Trivia",      R.drawable.ic_game_trivia_real, Color(0xFF263238), tileGradients = listOf(Color(0xFFFFEA00), Color(0xFFFF6D00)), keyType = "real_image"),
    MetroGame("Obstacle Run", R.drawable.ic_game_rocket_real, Color(0xFF004D40), tileGradients = listOf(Color(0xFF00B0FF), Color(0xFF00E676)), keyType = "real_image"),
    // Row 2
    MetroGame("Treasure Hunt",        R.drawable.ic_game_treasure_real, Color(0xFF3E2723), tileGradients = listOf(Color(0xFFFFD700), Color(0xFFFF6D00)), keyType = "real_image"),
    MetroGame("Tower Stacker",     R.drawable.ic_game_blocks_real, Color(0xFF4A148C), tileGradients = listOf(Color(0xFFD500F9), Color(0xFF651FFF)), keyType = "real_image"),
    MetroGame("Math Dash",       R.drawable.ic_game_math_real, Color(0xFF1B5E20), tileGradients = listOf(Color(0xFF00E676), Color(0xFF1DE9B6)), keyType = "real_image"),
    MetroGame("Memory Rush",  R.drawable.ic_game_memory_real, Color(0xFF004D40), tileGradients = listOf(Color(0xFF00B0FF), Color(0xFF651FFF)), keyType = "real_image"),
    // Row 3
    MetroGame("Snowball Fight",          R.drawable.ic_game_target_real, Color(0xFF0D1B2A), tileGradients = listOf(Color(0xFF40C4FF), Color(0xFF0091EA)), keyType = "real_image"),
    MetroGame("Maze Escape",       R.drawable.ic_game_maze_real, Color(0xFF004D40), tileGradients = listOf(Color(0xFF00E5FF), Color(0xFF00838F)), keyType = "real_image"),
    MetroGame("Knowledge Arena", R.drawable.ic_game_podium_real, Color(0xFF4A0E0E), tileGradients = listOf(Color(0xFFFFD700), Color(0xFFD50000)), keyType = "real_image"),
    MetroGame("See More",           R.drawable.ic_game_more_real, Color(0xFF1A1A2E), tileGradients = listOf(Color(0xFF651FFF), Color(0xFF311B92)), keyType = "real_image")
)

// --- UI COMPONENTS ---
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onGameSelected: (url: String, title: String) -> Unit
) {
    var selectedMenu by remember { mutableStateOf("Home") }
    val sidebarFocusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // 1. Sidebar (Width matches target, slightly narrower)
        KahootSidebarV2(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            selectedMenu = selectedMenu,
            onMenuSelected = { selectedMenu = it },
            sidebarFocusRequester = sidebarFocusRequester
        )

        // 2. Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (selectedMenu == "Home") {
                JoinGameContentV2(onGameSelected = onGameSelected, sidebarFocusRequester = sidebarFocusRequester)
            } else {
                // Placeholder for other menus
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$selectedMenu content is coming soon...", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

// --- SHARED TV UX MODIFIER ---
@Composable
fun Modifier.tvFocusUX(scaleFactor: Float = 1.05f, cornerRadius: Float = 12f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) scaleFactor else 1.0f, label = "scale")
    val borderColor = if (isFocused) Color.White else Color.Transparent

    return this
        .zIndex(if (isFocused) 1f else 0f)
        .graphicsLayer { 
            scaleX = scale
            scaleY = scale
            clip = false 
        }
        .border(if (isFocused) 2.dp else 0.dp, borderColor, RoundedCornerShape(cornerRadius.dp))
        .focusable(interactionSource = interactionSource)
}

// --- SIDEBAR ---
@Composable
fun KahootSidebarV2(
    modifier: Modifier = Modifier, 
    selectedMenu: String, 
    onMenuSelected: (String) -> Unit,
    sidebarFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier
            .background(Color(0xFF191333)) // Darker blueish purple for metro sidebar
            .padding(vertical = 32.dp, horizontal = 20.dp)
    ) {
        // Logo with TV Icon Accent
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, bottom = 44.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8052D1), Color(0xFFE21B3C))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Kaopiz",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        }

        // Menu Items
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).focusGroup().graphicsLayer { clip = false }) {
            val menus = listOf("Home", "Discover", "Library", "Settings")
            val icons = listOf(
                Icons.Default.Home,
                Icons.Default.Search,
                Icons.Default.Group,
                Icons.Default.Settings
            )
            
            menus.forEachIndexed { index, title ->
                SidebarMenuItemV2(
                    title = title, 
                    icon = icons[index], 
                    isSelected = selectedMenu == title,
                    onClick = { onMenuSelected(title) },
                    onFocus = { onMenuSelected(title) },
                    modifier = if (title == selectedMenu && sidebarFocusRequester != null) Modifier.focusRequester(sidebarFocusRequester) else Modifier
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun SidebarMenuItemV2(title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, onFocus: () -> Unit = {}, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }
    
    val bgColor = if (isFocused) Color.White else Color.Transparent
    val contentColor = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(bgColor, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title, 
            color = contentColor, 
            fontSize = 15.sp, 
            fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}

// --- TOP BAR ---
@Composable
fun TopBarV2(modifier: Modifier = Modifier, onJoinClicked: () -> Unit = {}, sidebarFocusRequester: FocusRequester? = null) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Search Bar (Expands to fill available space)
        val searchInteractionSource = remember { MutableInteractionSource() }
        val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
        
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (sidebarFocusRequester != null) Modifier.focusProperties { left = sidebarFocusRequester } else Modifier)
                .background(ColorSearchBg, RoundedCornerShape(20.dp))
                .border(if (isSearchFocused) 2.dp else 0.dp, Color.White, RoundedCornerShape(20.dp))
                .clickable(interactionSource = searchInteractionSource, indication = null) {}
                .focusable(interactionSource = searchInteractionSource)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Search games, quizzes, topics...", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        
        Spacer(modifier = Modifier.width(24.dp))

        // Right Actions
        Row(verticalAlignment = Alignment.CenterVertically) {

            
            // Profile
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFF6B21A8), RoundedCornerShape(50)), // Target profile icon color
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Welcome!", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Player", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// --- HERO SECTION ---
@Composable
fun HeroSectionV2(modifier: Modifier = Modifier, sidebarFocusRequester: FocusRequester? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (sidebarFocusRequester != null) Modifier.focusProperties { left = sidebarFocusRequester } else Modifier)
            .background(Color(0xFF2A0C6F), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 40.dp, top = 32.dp, bottom = 32.dp, end = 16.dp)
                    .weight(1.3f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Play to Learn,\nLearn to Play!", 
                    color = Color.White, 
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black, 
                    lineHeight = 38.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Create, play and share\nawesome games!", 
                    color = Color.White.copy(alpha = 0.9f), 
                    fontSize = 15.sp, 
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                val btnInteraction = remember { MutableInteractionSource() }
                val btnFocused by btnInteraction.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .background(Color.White, RoundedCornerShape(22.dp))
                        .border(if (btnFocused) 3.dp else 0.dp, ColorPrimaryPurple, RoundedCornerShape(22.dp))
                        .clickable(interactionSource = btnInteraction, indication = null) {}
                        .focusable(interactionSource = btnInteraction)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explore Now", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Graphics Right Side (Image crop)
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hero_graphic),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Fading left edge gradient
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp) // Wider blend since the banner is larger
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF2A0C6F), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

// --- ROWS ---
@Composable
fun GameRowSection(title: String, games: List<KahootGame>, modifier: Modifier = Modifier, contentPadding: PaddingValues, onGameSelected: (String, String) -> Unit, sidebarFocusRequester: FocusRequester? = null) {
    Column(modifier = modifier.graphicsLayer { clip = false }) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false }.then(if (sidebarFocusRequester != null) Modifier.focusProperties { left = sidebarFocusRequester } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = contentPadding
        ) {
            itemsIndexed(games) { _, game ->
                SimpleGameCardV2(
                    game = game, 
                    modifier = Modifier.width(240.dp),
                    onClick = { onGameSelected(game.url, game.title) }
                )
            }
        }
    }
}

@Composable
fun SimpleGameCardV2(game: KahootGame, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Column(modifier = modifier) {
        // Thumbnail Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .tvFocusUX(1.08f, 10f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .focusable(interactionSource = interactionSource)
        ) {
            // Full background image
            game.imageContent()
            
            // Subtle dark overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                            startY = 100f
                        )
                    )
            )
            
            if (game.isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(ColorBadgeRed, RoundedCornerShape(bottomEnd = 6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            // Play Button indicator on focus
            androidx.compose.animation.AnimatedVisibility(
                visible = isFocused,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .border(2.dp, Color.White, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow, 
                        contentDescription = null, 
                        tint = Color.White, 
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Metadata below the card
        Text(
            text = game.title, 
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f), 
            fontSize = 14.sp, 
            fontWeight = FontWeight.Bold, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Group, 
                contentDescription = null, 
                tint = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Gray, 
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = game.questionsCount, 
                color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Gray, 
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▷", color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = game.plays, 
                    color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Gray, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TopicsRowSection(title: String, topics: List<TopicCategory>, modifier: Modifier = Modifier, contentPadding: PaddingValues, sidebarFocusRequester: FocusRequester? = null) {
    Column(modifier = modifier.graphicsLayer { clip = false }) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false }.then(if (sidebarFocusRequester != null) Modifier.focusProperties { left = sidebarFocusRequester } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = contentPadding
        ) {
            itemsIndexed(topics) { _, topic ->
                TopicCardV2(topic = topic, modifier = Modifier.width(180.dp).height(48.dp))
            }
        }
    }
}

@Composable
fun TopicCardV2(topic: TopicCategory, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Row(
        modifier = modifier
            .tvFocusUX(1.08f, 8f)
            .background(topic.color, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null) {}
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = topic.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(topic.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// --- JOIN GAME SCREEN ---
@Composable
fun JoinGameContentV2(modifier: Modifier = Modifier, onGameSelected: (String, String) -> Unit, sidebarFocusRequester: FocusRequester? = null) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (sidebarFocusRequester != null) Modifier.focusProperties { left = sidebarFocusRequester } else Modifier)
            .padding(top = 16.dp, bottom = 16.dp, start = 24.dp, end = 24.dp)
            .graphicsLayer { clip = false } // allow focus-scale to overflow without clipping
    ) {
        // List Title
        Text("Game Collection", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Use BoxWithConstraints so we can measure available height and make cards fill exactly 3 rows
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { clip = false } // propagate no-clip downwards
        ) {
            val gap = 4.dp
            // Each row height = (total height - 2 gaps) / 3 rows
            val cardHeight = (maxHeight - gap * 2) / 3
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false }, // critical: prevent grid from clipping scaled cards
                userScrollEnabled = false
            ) {
                items(METRO_GAMES.size) { idx ->
                    val game = METRO_GAMES[idx]
                    MetroGameCard(
                        game = game,
                        modifier = Modifier.height(cardHeight),
                        onClick = {
                            if (game.isActive && game.url.isNotEmpty()) {
                                onGameSelected(game.url, game.title)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenderGameIcon(game: MetroGame, isFocused: Boolean) {
    val tileShape = CircleShape
    val alphaModifier = if (game.isActive) 1.0f else 0.75f

    Box(
        modifier = Modifier
            .fillMaxHeight(0.76f)
            .aspectRatio(1f)
            .graphicsLayer { alpha = alphaModifier }
            .shadow(if (isFocused) 14.dp else 6.dp, tileShape, spotColor = game.tileGradients.first())
            .background(
                Brush.linearGradient(colors = game.tileGradients),
                tileShape
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.15f))
                ),
                tileShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (game.keyType == "real_image") {
            Image(
                painter = painterResource(id = game.iconRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else if (game.keyType == "more") {
            // Kahoot 4 answer shapes in 2x2 grid
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KahootShapeIcon(KahootAnswerShape.Triangle, KahootPalette.Red, 11.dp)
                    KahootShapeIcon(KahootAnswerShape.Diamond, KahootPalette.Blue, 11.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KahootShapeIcon(KahootAnswerShape.Circle, KahootPalette.Yellow, 11.dp)
                    KahootShapeIcon(KahootAnswerShape.Square, KahootPalette.Green, 11.dp)
                }
            }
        } else if (game.vectorIcon != null) {
            Icon(
                imageVector = game.vectorIcon,
                contentDescription = null,
                tint = game.iconTint,
                modifier = Modifier.fillMaxHeight(0.56f)
            )
        } else {
            Icon(
                painter = painterResource(id = game.iconRes),
                contentDescription = null,
                tint = game.iconTint,
                modifier = Modifier.fillMaxHeight(0.56f)
            )
        }
    }
}

@Composable
fun MetroGameCard(game: MetroGame, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val cardShape = RoundedCornerShape(16.dp)
    val cardGradient = if (game.isActive) {
        Brush.verticalGradient(
            colors = listOf(
                game.color,
                game.color.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                game.color.copy(alpha = 0.65f),
                game.color.copy(alpha = 0.50f)
            )
        )
    }
    
    val borderWidthDp = 3.5.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val borderPx = with(density) { borderWidthDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 10f else 0f)
            .clip(cardShape)
            .drawWithContent {
                drawContent()
                if (isFocused) {
                    val inset = borderPx / 2f
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width - borderPx,
                            height = size.height - borderPx
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderPx)
                    )
                }
            }
            .background(cardGradient)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
    ) {
        // Highlight layer on focus
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        // Lock / Active badge in top-right corner
        if (!game.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LOCKED",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Proportional column layout for Icon + Labels
        Column(modifier = Modifier.fillMaxSize()) {

            // Upper zone (58%): Centered icon inside elevated tile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f),
                contentAlignment = Alignment.Center
            ) {
                RenderGameIcon(game = game, isFocused = isFocused)
            }

            // Lower zone (42%): Title + Player stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .padding(start = 14.dp, end = 44.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = game.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    letterSpacing = 0.15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (game.isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        // Pulsing Live Dot Indicator
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF4ADE80), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = game.playerCount,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    }
                } else {
                    Text(
                        text = "Coming Soon",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Play Action Button – bottom right
        if (game.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.30f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
