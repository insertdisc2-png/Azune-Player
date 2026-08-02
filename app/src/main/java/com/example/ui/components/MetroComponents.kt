package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.TextUnit
import com.example.data.model.CoverArtCache
import kotlin.math.sin

// Local Font configuration for Inter loaded offline from resources
val InterFontFamily = FontFamily.SansSerif

private var safeInterFontFamily: FontFamily? = null

fun getSafeInterFontFamily(context: android.content.Context): FontFamily {
    return FontFamily.SansSerif
}

private fun isValidTtfHeader(file: java.io.File): Boolean {
    if (!file.exists() || file.length() < 4) return false
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            if (read == 4) {
                val isTtf = header[0] == 0.toByte() && header[1] == 1.toByte() && header[2] == 0.toByte() && header[3] == 0.toByte()
                val isOtto = header[0] == 'O'.toByte() && header[1] == 'T'.toByte() && header[2] == 'T'.toByte() && header[3] == 'O'.toByte()
                val isTrue = header[0] == 't'.toByte() && header[1] == 'r'.toByte() && header[2] == 'u'.toByte() && header[3] == 'e'.toByte()
                isTtf || isOtto || isTrue
            } else {
                false
            }
        }
    } catch (e: Exception) {
        false
    }
}

// Inside MetroComponents.kt
private val fontFamilyCache = java.util.concurrent.ConcurrentHashMap<String, FontFamily>()

// Font helper translating look types to standard compose typography engines
@Composable
fun getMetroFontFamily(fontName: String): FontFamily {
    val normName = fontName.trim()
    val context = androidx.compose.ui.platform.LocalContext.current
    val interFamily = getSafeInterFontFamily(context)

    if (normName.equals("system", ignoreCase = true) || 
        normName.equals("Segoe", ignoreCase = true) || 
        normName.isEmpty()
    ) {
        return interFamily
    }

    // Return cached FontFamily immediately to avoid any main-thread disk I/O on recomposition
    fontFamilyCache[normName]?.let { return it }

    val loadedFont = try {
        val fontsDir = java.io.File(context.filesDir, "fonts")
        if (fontsDir.exists()) {
            val fontFile = java.io.File(fontsDir, "$normName.ttf")
            if (fontFile.exists() && fontFile.length() > 0 && isValidTtfHeader(fontFile)) {
                val tf = android.graphics.Typeface.createFromFile(fontFile)
                if (tf != null) {
                    FontFamily(tf)
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Throwable) {
        null
    }

    val finalFontFamily = loadedFont ?: when (normName) {
        "Monospace" -> FontFamily.Monospace
        "Slab" -> FontFamily.Serif
        "Aesthetic" -> FontFamily.Cursive
        else -> interFamily
    }

    fontFamilyCache[normName] = finalFontFamily
    return finalFontFamily
}

@Composable
fun getThemeAccentColor(hex: String): Color {
    val systemPrimary = MaterialTheme.colorScheme.primary
    return remember(hex, systemPrimary) {
        if (hex.equals("system", ignoreCase = true)) {
            systemPrimary
        } else {
            try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (e: Exception) {
                systemPrimary
            }
        }
    }
}

// Accent Colors List representing official Windows Phone / Metro palettes
val METRO_PALETTES = listOf(
    "system" to "System Accent",
    "#0078D7" to "Cobalt",
    "#E81123" to "Crimson",
    "#107C41" to "Xbox Green",
    "#F0A30A" to "Mango",
    "#A200FF" to "Purple",
    "#00ABA9" to "Teal",
    "#D13438" to "Lumia Red",
    "#D83B01" to "Orange",
    "#00A2E8" to "Azure"
)

// Main Background drawing engine with custom Windows retro blueprint grids
@Composable
fun MetroBackgroundContainer(
    settings: com.example.data.database.UserSettingsEntity? = null,
    transparency: Float,
    bgStyle: String,
    modifier: Modifier = Modifier,
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resolvedTheme = when (themeMode) {
        "light" -> "light"
        "dark" -> "dark"
        "amoled" -> "amoled"
        else -> if (isSystemDark) "dark" else "light" // Follow System Option
    }
    val baseBgColor = when (resolvedTheme) {
        "light" -> Color(0xFFF4F5F8)
        "amoled" -> Color.Black
        else -> Color(0xFF0C0D11) // Premium elegant charcoal gray dark mode (not 100% black)
    }
    val lineAccent = if (resolvedTheme == "light") Color.Black else Color.White
    val sweepColors = if (resolvedTheme == "light") {
        listOf(Color(0x0D000000), Color(0x00000000))
    } else {
        listOf(Color(0x122D2D2D), Color(0x00000000))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor) // Dynamic deep theme
    ) {
        // App Custom background image overlay, opacity and blur
        if (settings != null && bgStyle == "upload" && settings.appBackgroundImage.isNotEmpty()) {
            val bgImg = settings.appBackgroundImage
            if (bgImg == "seattle" || bgImg == "redmond" || bgImg == "neon" || bgImg == "space") {
                val gradientBrush = when (bgImg) {
                    "seattle" -> Brush.verticalGradient(listOf(Color(0xFF1F2F3D), Color(0xFF0F1419)))
                    "redmond" -> Brush.radialGradient(listOf(Color(0xFF0C2540), Color(0xFF020914)))
                    "neon" -> Brush.linearGradient(listOf(Color(0xFF38003C), Color(0xFF050515)))
                    else -> Brush.verticalGradient(listOf(Color(0xFF0B001A), Color(0xFF030303))) // space
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush)
                        .alpha(settings.appBackgroundOpacity)
                )
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                val modelVal = remember(bgImg) {
                    val file = java.io.File(bgImg)
                    if (file.exists()) file.absolutePath else bgImg
                }
                val painter = rememberAsyncImagePainter(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(modelVal)
                        .crossfade(true)
                        .build()
                )
                val painterState = painter.state
                if (painterState is coil.compose.AsyncImagePainter.State.Error) {
                    android.util.Log.e("MetroComponents", "Background image load failed: ${painterState.result.throwable.message} for path: $modelVal")
                } else if (painterState is coil.compose.AsyncImagePainter.State.Success) {
                    android.util.Log.i("MetroComponents", "Background image loaded successfully! Size: ${painterState.result.drawable.intrinsicWidth}x${painterState.result.drawable.intrinsicHeight}")
                }
                
                val blurModifier = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    Modifier.blur(settings.appBackgroundBlur.dp)
                } else {
                    Modifier
                }
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurModifier)
                )
                // Premium Dimming/Wash-out protection overlay over the custom photo for pristine typography readability
                val overlayColor = if (resolvedTheme == "light") Color.White else Color.Black
                val overlayAlpha = (1f - settings.appBackgroundOpacity).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayColor.copy(alpha = overlayAlpha))
                )
            }
        }

        // Draw elegant mathematical mesh grid background typical of WP SDK design layouts
        if (bgStyle != "solid") {
            Canvas(modifier = Modifier.fillMaxSize().alpha(if (resolvedTheme == "light") 0.04f else 0.08f)) {
                val width = size.width
                val height = size.height

                when (bgStyle) {
                    "grid" -> {
                        // "grid" (Tech Gridlines) with coordinates crosshairs
                        val gp = 64.dp.toPx()
                        var x = 0f
                        while (x < width) {
                            drawLine(
                                color = lineAccent,
                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                end = androidx.compose.ui.geometry.Offset(x, height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            x += gp
                        }
                        var y = 0f
                        while (y < height) {
                            drawLine(
                                color = lineAccent,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(width, y),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            y += gp
                        }
                        // Draw crosshair ticks at major intersections
                        var cx = 0f
                        var cCol = 0
                        while (cx < width) {
                            var cy = 0f
                            var cRow = 0
                            while (cy < height) {
                                if ((cCol + cRow) % 3 == 0) {
                                    val sizeVal = 6.dp.toPx()
                                    drawLine(
                                        color = lineAccent,
                                        start = androidx.compose.ui.geometry.Offset(cx - sizeVal, cy),
                                        end = androidx.compose.ui.geometry.Offset(cx + sizeVal, cy),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        color = lineAccent,
                                        start = androidx.compose.ui.geometry.Offset(cx, cy - sizeVal),
                                        end = androidx.compose.ui.geometry.Offset(cx, cy + sizeVal),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                cy += gp
                                cRow++
                            }
                            cx += gp
                            cCol++
                        }
                    }
                    "retro-tiles" -> {
                        // "retro-tiles" (Architect Blueprints with double rulers and angle guides)
                        val bp = 48.dp.toPx()
                        var x = 0f
                        while (x < width) {
                            drawLine(
                                color = lineAccent,
                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                end = androidx.compose.ui.geometry.Offset(x, height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            x += bp
                        }
                        var y = 0f
                        while (y < height) {
                            drawLine(
                                color = lineAccent,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(width, y),
                                strokeWidth = 0.5.dp.toPx()
                            )
                            y += bp
                        }
                        // Blueprint outer rulers ticks
                        val tickLen = 8.dp.toPx()
                        var rx = 0f
                        while (rx < width) {
                            drawLine(
                                color = lineAccent,
                                start = androidx.compose.ui.geometry.Offset(rx, 0f),
                                end = androidx.compose.ui.geometry.Offset(rx, tickLen),
                                strokeWidth = 1.dp.toPx()
                            )
                            rx += 16.dp.toPx()
                        }
                        var ry = 0f
                        while (ry < height) {
                            drawLine(
                                color = lineAccent,
                                start = androidx.compose.ui.geometry.Offset(0f, ry),
                                end = androidx.compose.ui.geometry.Offset(tickLen, ry),
                                strokeWidth = 1.dp.toPx()
                            )
                            ry += 16.dp.toPx()
                        }
                        // Diagonal projection lines
                        drawLine(
                            color = lineAccent,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(width, width),
                            strokeWidth = 0.5.dp.toPx()
                        )
                        drawLine(
                            color = lineAccent,
                            start = androidx.compose.ui.geometry.Offset(0f, height),
                            end = androidx.compose.ui.geometry.Offset(width, height - width),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }
                    "constellation" -> {
                        // "constellation" (Geometrical Star constellations)
                        val stars = listOf(
                            androidx.compose.ui.geometry.Offset(0.15f * width, 0.12f * height),
                            androidx.compose.ui.geometry.Offset(0.28f * width, 0.22f * height),
                            androidx.compose.ui.geometry.Offset(0.42f * width, 0.18f * height),
                            androidx.compose.ui.geometry.Offset(0.55f * width, 0.28f * height),
                            androidx.compose.ui.geometry.Offset(0.72f * width, 0.20f * height),
                            androidx.compose.ui.geometry.Offset(0.85f * width, 0.15f * height),
                            androidx.compose.ui.geometry.Offset(0.12f * width, 0.45f * height),
                            androidx.compose.ui.geometry.Offset(0.30f * width, 0.52f * height),
                            androidx.compose.ui.geometry.Offset(0.50f * width, 0.48f * height),
                            androidx.compose.ui.geometry.Offset(0.68f * width, 0.58f * height),
                            androidx.compose.ui.geometry.Offset(0.88f * width, 0.50f * height),
                            androidx.compose.ui.geometry.Offset(0.20f * width, 0.78f * height),
                            androidx.compose.ui.geometry.Offset(0.38f * width, 0.82f * height),
                            androidx.compose.ui.geometry.Offset(0.58f * width, 0.72f * height),
                            androidx.compose.ui.geometry.Offset(0.76f * width, 0.85f * height),
                            androidx.compose.ui.geometry.Offset(0.85f * width, 0.78f * height)
                        )
                        val connections = listOf(
                            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5),
                            Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10),
                            Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15),
                            Pair(1, 7), Pair(3, 8), Pair(4, 9), Pair(7, 12), Pair(9, 13)
                        )
                        connections.forEach { (a, b) ->
                            if (a < stars.size && b < stars.size) {
                                drawLine(
                                    color = lineAccent,
                                    start = stars[a],
                                    end = stars[b],
                                    strokeWidth = 0.5.dp.toPx()
                                )
                            }
                        }
                        stars.forEach { pos ->
                            drawCircle(color = lineAccent, radius = 3.dp.toPx(), center = pos)
                            drawCircle(color = lineAccent.copy(alpha = 0.3f), radius = 7.dp.toPx(), center = pos)
                        }
                    }
                    "circuit" -> {
                        // "circuit" (Futuristic microprocessor tracing)
                        val p1 = Path().apply {
                            moveTo(0.12f * width, 0.05f * height)
                            lineTo(0.12f * width, 0.20f * height)
                            lineTo(0.24f * width, 0.32f * height)
                            lineTo(0.24f * width, 0.50f * height)
                        }
                        drawPath(path = p1, color = lineAccent, style = Stroke(width = 1f.dp.toPx()))
                        drawCircle(color = lineAccent, radius = 3.5f.dp.toPx(), center = androidx.compose.ui.geometry.Offset(0.24f * width, 0.50f * height))

                        val p2 = Path().apply {
                            moveTo(0.88f * width, 0.10f * height)
                            lineTo(0.88f * width, 0.30f * height)
                            lineTo(0.72f * width, 0.46f * height)
                            lineTo(0.55f * width, 0.46f * height)
                        }
                        drawPath(path = p2, color = lineAccent, style = Stroke(width = 1f.dp.toPx()))
                        drawCircle(color = lineAccent, radius = 3.5f.dp.toPx(), center = androidx.compose.ui.geometry.Offset(0.55f * width, 0.46f * height))

                        val p3 = Path().apply {
                            moveTo(0.15f * width, 0.85f * height)
                            lineTo(0.35f * width, 0.85f * height)
                            lineTo(0.48f * width, 0.72f * height)
                            lineTo(0.65f * width, 0.72f * height)
                        }
                        drawPath(path = p3, color = lineAccent, style = Stroke(width = 1f.dp.toPx()))
                        drawCircle(color = lineAccent, radius = 3.5f.dp.toPx(), center = androidx.compose.ui.geometry.Offset(0.65f * width, 0.72f * height))

                        val p4 = Path().apply {
                            moveTo(0.80f * width, 0.65f * height)
                            lineTo(0.80f * width, 0.80f * height)
                            lineTo(0.72f * width, 0.88f * height)
                        }
                        drawPath(path = p4, color = lineAccent, style = Stroke(width = 1f.dp.toPx()))
                        drawCircle(color = lineAccent, radius = 3.5f.dp.toPx(), center = androidx.compose.ui.geometry.Offset(0.72f * width, 0.88f * height))
                    }
                    "mesh" -> {
                        // "mesh" (Mathematical Math wave lines visualization)
                        val waves = 7
                        val points = 16
                        for (w in 0 until waves) {
                            val path = Path()
                            val baseH = height * (0.15f + 0.7f * (w.toFloat() / waves))
                            for (p in 0..points) {
                                val t = p.toFloat() / points
                                val px = t * width
                                val phase = t * Math.PI * 3.0 + w * 0.9
                                val py = baseH + kotlin.math.sin(phase).toFloat() * 16.dp.toPx()
                                if (p == 0) {
                                    path.moveTo(px, py)
                                } else {
                                    path.lineTo(px, py)
                                }
                            }
                            drawPath(path = path, color = lineAccent, style = Stroke(width = 0.75f.dp.toPx()))
                        }
                    }
                }
            }
        }

        // Draw elegant orbital background sweep to break solid flatness
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = sweepColors,
                        radius = 2000f
                    )
                )
        )

        content()
    }
}

// Standard rectangular Windows Phone glass/solid Action Tile
@Composable
fun MetroTile(
    title: String,
    icon: ImageVector,
    accentHex: String,
    transparency: Float,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    sizeType: Int = 1, // 1: Square, 2: Wide, 3: Tall
    liveContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val accentColor = getThemeAccentColor(accentHex)
    val tileBgColor = accentColor.copy(alpha = (1f - transparency).coerceAtLeast(0.1f))
    
    val isBgLight = MaterialTheme.colorScheme.surface.let { (it.red + it.green + it.blue) / 3f > 0.5f }
    val onTileColor = if (isBgLight && transparency > 0.45f) Color.Black else Color.White
    val onTileSecondaryColor = if (isBgLight && transparency > 0.45f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f)
    val tileBorderColor = if (isBgLight && transparency > 0.45f) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)

    Card(
        modifier = modifier
            .padding(4.dp)
            .then(
                when (sizeType) {
                    2 -> Modifier.aspectRatio(2f)
                    3 -> Modifier.aspectRatio(0.48f)
                    else -> Modifier.aspectRatio(1f)
                }
            )
            .clickable(onClick = onClick)
            .border(2.dp, tileBorderColor),
        colors = CardDefaults.cardColors(containerColor = tileBgColor),
        shape = androidx.compose.ui.graphics.RectangleShape // Metro standard 90-degree vector box
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val iconAlignment = when (sizeType) {
                3 -> Alignment.TopStart
                else -> Alignment.Center
            }
            val iconSize = when (sizeType) {
                3 -> 36.dp
                2 -> 40.dp
                else -> 32.dp
            }

            if (sizeType == 2 && liveContent != null) {
                liveContent()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .size(iconSize)
                        .align(iconAlignment),
                    tint = onTileColor
                )
            }
            
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = onTileSecondaryColor,
                            fontWeight = FontWeight.Light,
                            fontSize = 11.sp
                        )
                    )
                }
                Text(
                    text = title.lowercase(),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = onTileColor,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        }
    }
}

// Flat text input complying with Metro style specifications
@Composable
fun MetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val isBgLight = MaterialTheme.colorScheme.surface.let { (it.red + it.green + it.blue) / 3f > 0.5f }
    val textColor = if (isBgLight) Color.Black else Color.White
    val textSecondaryColor = if (isBgLight) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f)
    val borderColor = if (isBgLight) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.3f)
    val focusedBorderColor = if (isBgLight) Color.Black else Color.White
    val fieldBg = if (isBgLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(fieldBg),
        placeholder = { Text(placeholder.lowercase(), color = textSecondaryColor, fontSize = 14.sp) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = textSecondaryColor) } },
        trailingIcon = onClear?.let {
            if (value.isNotEmpty()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "clear", tint = textColor)
                    }
                }
            } else null
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = borderColor,
            focusedContainerColor = fieldBg,
            unfocusedContainerColor = fieldBg
        ),
        shape = androidx.compose.ui.graphics.RectangleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

// Metro flat button selection options
@Composable
fun MetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentHex: String = "#0078D7",
    isOutlined: Boolean = false
) {
    val themeColor = getThemeAccentColor(accentHex)
    
    Button(
        onClick = onClick,
        modifier = modifier
            .border(
                width = 2.dp,
                color = if (isOutlined) Color.White else Color.Transparent,
                shape = androidx.compose.ui.graphics.RectangleShape
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOutlined) Color.Transparent else themeColor,
            contentColor = Color.White
        ),
        shape = androidx.compose.ui.graphics.RectangleShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = text.lowercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.sp
            )
        )
    }
}

// Live Equalizer visualizer effect
@Composable
fun MetroVisualizer(
    accentHex: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    positionMs: Long = 0L,
    trackId: String = ""
) {
    // Waveform completely removed per user request - render nothing
    Spacer(modifier = modifier)
}

// Standard Windows Phone playlist item card
@Composable
fun PlaylistTrackItem(
    track: Track,
    accentHex: String,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    fontFamily: String = "Inter",
    onAddToPlaylist: (() -> Unit)? = null,
    isLightMode: Boolean = false,
    enableCoverArt: Boolean = true,
    coverArtResolution: String = "low",
    cornerStyle: String = "sharp",
    showDivider: Boolean = false,
    dividerColor: Color = Color.Transparent
) {
    val themeColor = getThemeAccentColor(accentHex)
    val isBgLight = isLightMode
    val textColor = if (isBgLight) Color.Black else Color.White
    val textSecondaryColor = if (isBgLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    val circleBorderColor = if (isBgLight) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f)
    val activeTrackBg = if (isBgLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f)
    
    val coverShape = when (cornerStyle) {
        "circle" -> CircleShape
        "rounded" -> androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        else -> androidx.compose.ui.graphics.RectangleShape
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCurrent) activeTrackBg else Color.Transparent)
                .clickable(onClick = onPlay)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flat numbered circle frame or playing indicator
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(themeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                if (enableCoverArt) {
                    TrackCoverImage(
                        track = track,
                        resolution = coverArtResolution,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(coverShape)
                            .border(1.dp, circleBorderColor, coverShape),
                        fallbackSymbol = "♬",
                        symbolFontSize = 18.sp,
                        themeAccentColor = themeColor,
                        isLight = isBgLight
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, circleBorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♬",
                            color = textSecondaryColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Titles and Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    color = if (isCurrent) themeColor else textColor,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = getMetroFontFamily(fontFamily),
                    fontSize = 15.sp,
                    maxLines = 1,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = track.artist,
                    color = textSecondaryColor,
                    fontWeight = FontWeight.Light,
                    fontFamily = getMetroFontFamily(fontFamily),
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            // Action buttons
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "favorite",
                    tint = if (isFavorite) themeColor else textSecondaryColor
                )
            }

            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "add to playlist",
                        tint = textSecondaryColor
                    )
                }
            }
        }
        if (showDivider) {
            Divider(color = dividerColor)
        }
    }
}

@Composable
fun TrackCoverImage(
    track: Track,
    resolution: String,
    modifier: Modifier = Modifier,
    fallbackSymbol: String = "♬",
    symbolFontSize: TextUnit = 115.sp,
    themeAccentColor: Color = Color.White,
    isLight: Boolean = false,
    contentDescription: String? = null
) {
    var coverBitmap by remember(track.id, resolution) { 
        mutableStateOf(CoverArtCache.getInMemory(track.id, resolution)) 
    }
    
    val isKnownEmpty = remember(track.id, resolution) {
        CoverArtCache.isKnownNoCover(track.id, resolution)
    }
    
    LaunchedEffect(track.id, resolution) {
        if (coverBitmap == null && !isKnownEmpty && !track.isSynth && track.path.isNotEmpty()) {
            kotlinx.coroutines.delay(250) // Increased debounce to save intensive operations during fast scroll
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                CoverArtCache.get(track.id, track.path, resolution)
            }
            if (bitmap != null) {
                coverBitmap = bitmap
            }
        }
    }

    val imageBitmap = remember(coverBitmap) {
        coverBitmap?.asImageBitmap()
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(if (isLight) Color(0xFFEAF4FC) else Color(0xFF0F0F0F)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackSymbol,
                color = themeAccentColor,
                fontSize = symbolFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

