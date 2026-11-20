package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PersonaEntity
import com.example.ui.state.LocalizedCopy
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

@Composable
fun StreamingModelBubble(
    persona: PersonaEntity?,
    streamingText: String?,
    isGeneratingImage: Boolean,
    thinkingLabel: String,
    generatingImageLabel: String,
    copy: LocalizedCopy,
    modifier: Modifier = Modifier
) {
    val accentColor = PersonaVisuals.accentColorFor(persona?.accentIndex ?: 0)
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val transition = rememberInfiniteTransition(label = "ai_typing_transition")

    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val shimmerProgress by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val isWaitingForTokens = !isGeneratingImage && streamingText.isNullOrBlank()
    val isActivelyStreaming = !isGeneratingImage && !streamingText.isNullOrBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("streaming_model_bubble"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(34.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.34f * pulseAlpha),
                                Color.Transparent
                            )
                        ),
                        radius = size.maxDimension * 0.78f
                    )
                }
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = pulseAlpha),
                            secondaryColor.copy(alpha = 0.7f * pulseAlpha)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_dost_avatar),
                contentDescription = stringResource(R.string.cd_dost_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = persona?.name ?: "Dost AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )

                if (isActivelyStreaming) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier.testTag("streaming_typing_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            TypingDotsWave(
                                wavePhase = wavePhase,
                                primaryColor = accentColor,
                                secondaryColor = secondaryColor,
                                dotSize = 5.dp,
                                dotSpacing = 3.dp,
                                bounceHeightDp = 2.5f
                            )
                            Text(
                                text = thinkingLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                border = if (isWaitingForTokens) {
                    BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.45f * pulseAlpha),
                                secondaryColor.copy(alpha = 0.25f)
                            )
                        )
                    )
                } else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    isGeneratingImage -> {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                                .testTag("ai_generating_image_indicator"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = accentColor
                            )
                            Text(
                                text = generatingImageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    isWaitingForTokens -> {
                        AiTypingBubbleContent(
                            thinkingLabel = thinkingLabel,
                            accentColor = accentColor,
                            secondaryColor = secondaryColor,
                            pulseAlpha = pulseAlpha,
                            wavePhase = wavePhase,
                            shimmerProgress = shimmerProgress
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MarkdownMessageContent(
                                markdown = streamingText.orEmpty(),
                                textColor = MaterialTheme.colorScheme.onSurface,
                                copyCodeLabel = copy.copyCode,
                                copiedLabel = copy.copied
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.28f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .alpha(pulseAlpha)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(accentColor, Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTypingBubbleContent(
    thinkingLabel: String,
    accentColor: Color,
    secondaryColor: Color,
    pulseAlpha: Float,
    wavePhase: Float,
    shimmerProgress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics {
                contentDescription = thinkingLabel
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("ai_typing_indicator"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer {
                            val scale = 0.9f + 0.15f * pulseAlpha
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }

            TypingDotsWave(
                wavePhase = wavePhase,
                primaryColor = accentColor,
                secondaryColor = secondaryColor,
                dotSize = 8.dp,
                dotSpacing = 5.dp,
                bounceHeightDp = 4.5f
            )

            Text(
                text = thinkingLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("ai_typing_label")
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ShimmerSkeletonLine(
                widthFraction = 0.88f,
                shimmerProgress = shimmerProgress,
                accentColor = accentColor
            )
            ShimmerSkeletonLine(
                widthFraction = 0.56f,
                shimmerProgress = shimmerProgress,
                accentColor = secondaryColor
            )
        }
    }
}

@Composable
private fun TypingDotsWave(
    wavePhase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    dotSize: Dp,
    dotSpacing: Dp,
    bounceHeightDp: Float
) {
    val dotOffsets = listOf(0f, 0.95f, 1.9f)
    val dotColors = listOf(
        primaryColor,
        MaterialTheme.colorScheme.primary,
        secondaryColor
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing)
    ) {
        dotOffsets.forEachIndexed { index, offset ->
            val wave = max(0f, sin(wavePhase - offset))
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = -wave * bounceHeightDp * density
                        val scale = 0.78f + 0.34f * wave
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.42f + 0.58f * wave
                    }
                    .clip(CircleShape)
                    .background(dotColors[index])
            )
        }
    }
}

@Composable
private fun ShimmerSkeletonLine(
    widthFraction: Float,
    shimmerProgress: Float,
    accentColor: Color
) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val highlightColor = accentColor.copy(alpha = 0.28f)

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .drawBehind {
                val width = size.width
                val band = width * 0.45f
                val startX = shimmerProgress * width - band
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(baseColor, highlightColor, baseColor),
                        start = Offset(startX, 0f),
                        end = Offset(startX + band, size.height)
                    )
                )
            }
    )
}
