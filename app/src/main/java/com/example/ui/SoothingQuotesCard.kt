package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.HomeSectionId
import com.example.data.local.HomeSectionPrefs
import com.example.ui.theme.LocalNephoPalette

@Immutable
data class SoothingQuote(
    val text: String,
    val attribution: String
)

object SoothingQuotesCatalog {
    val quotes: List<SoothingQuote> = listOf(
        SoothingQuote(
            text = "Nature does not hurry, yet everything is accomplished.",
            attribution = "Lao Tzu"
        ),
        SoothingQuote(
            text = "You have power over your mind—not outside events. Realize this, and you will find strength.",
            attribution = "Marcus Aurelius"
        ),
        SoothingQuote(
            text = "Peace comes from within. Do not seek it without.",
            attribution = "Buddhist Proverb"
        ),
        SoothingQuote(
            text = "The quieter you become, the more you are able to hear.",
            attribution = "Rumi"
        ),
        SoothingQuote(
            text = "Smile, breathe, and go slowly.",
            attribution = "Thich Nhat Hanh"
        ),
        SoothingQuote(
            text = "Calm mind brings inner strength and self-confidence.",
            attribution = "Dalai Lama"
        ),
        SoothingQuote(
            text = "Still waters run deep.",
            attribution = "Traditional Proverb"
        ),
        SoothingQuote(
            text = "Be like water making its way through cracks—adjust to the object, and you shall find a way around or through it.",
            attribution = "Bruce Lee"
        ),
        SoothingQuote(
            text = "Nothing can bring you peace but yourself.",
            attribution = "Ralph Waldo Emerson"
        ),
        SoothingQuote(
            text = "Adopt the pace of nature: her secret is patience.",
            attribution = "Ralph Waldo Emerson"
        ),
        SoothingQuote(
            text = "Almost everything will work again if you unplug it for a few minutes, including you.",
            attribution = "Anne Lamott"
        ),
        SoothingQuote(
            text = "Within you, there is a stillness and a sanctuary to which you can retreat at any time.",
            attribution = "Hermann Hesse"
        ),
        SoothingQuote(
            text = "A smooth sea never made a skilled sailor.",
            attribution = "English Proverb"
        ),
        SoothingQuote(
            text = "The bamboo that bends is stronger than the oak that resists.",
            attribution = "Japanese Proverb"
        ),
        SoothingQuote(
            text = "Muddy water is best cleared by leaving it alone.",
            attribution = "Alan Watts"
        ),
        SoothingQuote(
            text = "To the mind that is still, the whole universe surrenders.",
            attribution = "Lao Tzu"
        ),
        SoothingQuote(
            text = "Feelings come and go like clouds in a windy sky. Conscious breathing is my anchor.",
            attribution = "Thich Nhat Hanh"
        ),
        SoothingQuote(
            text = "He who lives in harmony with himself lives in harmony with the universe.",
            attribution = "Marcus Aurelius"
        ),
        SoothingQuote(
            text = "Patience is bitter, but its fruit is sweet.",
            attribution = "Aristotle"
        ),
        SoothingQuote(
            text = "Even the darkest night will end and the sun will rise.",
            attribution = "Victor Hugo"
        ),
        SoothingQuote(
            text = "Fall seven times, stand up eight.",
            attribution = "Japanese Proverb"
        ),
        SoothingQuote(
            text = "Do not dwell in the past, do not dream of the future; concentrate the mind on the present moment.",
            attribution = "Zen Proverb"
        ),
        SoothingQuote(
            text = "When you realize nothing is lacking, the whole world belongs to you.",
            attribution = "Lao Tzu"
        ),
        SoothingQuote(
            text = "Let go, or be dragged.",
            attribution = "Zen Proverb"
        ),
        SoothingQuote(
            text = "No snowflake ever falls in the wrong place.",
            attribution = "Zen Proverb"
        ),
        SoothingQuote(
            text = "We suffer more often in imagination than in reality.",
            attribution = "Seneca"
        ),
        SoothingQuote(
            text = "If you want to go fast, go alone. If you want to go far, go together.",
            attribution = "African Proverb"
        ),
        SoothingQuote(
            text = "Keep your face always toward the sunshine—and shadows will fall behind you.",
            attribution = "Walt Whitman"
        ),
        SoothingQuote(
            text = "Happiness is not something ready made. It comes from your own actions.",
            attribution = "Dalai Lama"
        ),
        SoothingQuote(
            text = "In the midst of winter, I found there was, within me, an invincible summer.",
            attribution = "Albert Camus"
        ),
        SoothingQuote(
            text = "Every storm runs out of rain.",
            attribution = "Maya Angelou"
        ),
        SoothingQuote(
            text = "When we are no longer able to change a situation, we are challenged to change ourselves.",
            attribution = "Viktor Frankl"
        ),
        SoothingQuote(
            text = "Tension is who you think you should be. Relaxation is who you are.",
            attribution = "Chinese Proverb"
        ),
        SoothingQuote(
            text = "Out beyond ideas of wrongdoing and rightdoing, there is a field. I’ll meet you there.",
            attribution = "Rumi"
        ),
        SoothingQuote(
            text = "A journey of a thousand miles begins with a single step.",
            attribution = "Lao Tzu"
        ),
        SoothingQuote(
            text = "May the road rise up to meet you. May the wind be always at your back.",
            attribution = "Irish Proverb"
        )
    )

    fun initialIndex(seedMillis: Long = System.currentTimeMillis()): Int {
        if (quotes.isEmpty()) return 0
        // Rotate gently by minute/session so reopening Nepho surfaces a fresh quote
        val bucket = (seedMillis / 60_000L).toInt()
        return Math.floorMod(bucket, quotes.size)
    }

    fun nextIndex(currentIndex: Int): Int {
        if (quotes.size <= 1) return 0
        return (currentIndex + 1) % quotes.size
    }
}

/**
 * Returns true when all currently visible home sections are minimized.
 */
fun areAllVisibleSectionsMinimized(homeSections: HomeSectionPrefs): Boolean {
    val visibleSections = HomeSectionId.entries.filter { homeSections.isVisible(it) }
    return visibleSections.isNotEmpty() && visibleSections.all { homeSections.isMinimized(it) }
}

/**
 * Serene quote & proverb card framed with Nepho's calming breathing glow and border light sweep.
 * Appears whenever all visible home sections are minimized or the Inbox is empty.
 */
@Composable
fun SoothingQuotesCard(
    modifier: Modifier = Modifier,
    initialQuoteIndex: Int = SoothingQuotesCatalog.initialIndex()
) {
    val palette = LocalNephoPalette.current
    val lightingPhase = LocalCalmLightingPhase.current
    val quotes = SoothingQuotesCatalog.quotes
    var quoteIndex by rememberSaveable {
        mutableIntStateOf(initialQuoteIndex.coerceIn(0, (quotes.size - 1).coerceAtLeast(0)))
    }
    val currentQuote = quotes[quoteIndex]

    val accentTint = lerp(palette.calmBlue, palette.calmBlueBright, lightingPhase.breath * 0.65f)
    val surfaceColor = lerp(
        palette.surface,
        palette.calmBlueSurface,
        0.30f + lightingPhase.breath * 0.25f
    )
    val cardShape = RoundedCornerShape(20.dp)

    val cycleQuote = {
        quoteIndex = SoothingQuotesCatalog.nextIndex(quoteIndex)
    }

    Surface(
        shape = cardShape,
        color = surfaceColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .calmingLitBorder(
                accentColor = palette.calmBlue,
                cornerRadius = 20.dp,
                strokeWidth = 1.4.dp,
                intensity = CalmBorderIntensity.ACTIVE
            )
            .clickable(onClick = cycleQuote)
            .testTag("soothing_quotes_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (size.width <= 0f || size.height <= 0f) return@drawBehind
                    val sweepCenterX = (-0.25f + lightingPhase.sweep * 1.5f) * size.width
                    val halfSpan = size.width * 0.48f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.35f to palette.calmBlue.copy(alpha = 0.06f + lightingPhase.breath * 0.06f),
                                0.50f to palette.calmBlueBright.copy(alpha = 0.11f + lightingPhase.breath * 0.07f),
                                0.65f to palette.calmBlue.copy(alpha = 0.06f + lightingPhase.breath * 0.06f),
                                1.0f to Color.Transparent
                            ),
                            startX = sweepCenterX - halfSpan,
                            endX = sweepCenterX + halfSpan
                        )
                    )
                }
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = accentTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.quotes_card_header),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = accentTint
                    )
                }
                IconButton(
                    onClick = cycleQuote,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("soothing_quote_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.quotes_card_next_desc),
                        tint = accentTint.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = currentQuote,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(420)) +
                        slideInVertically(animationSpec = tween(420)) { it / 8 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(260)) +
                                slideOutVertically(animationSpec = tween(260)) { -it / 8 }
                        )
                },
                label = "soothing_quote_transition"
            ) { quote ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "“${quote.text}”",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 25.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("soothing_quote_text")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "— ${quote.attribution}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp
                            ),
                            color = accentTint,
                            modifier = Modifier.testTag("soothing_quote_attribution")
                        )
                        Text(
                            text = stringResource(R.string.quotes_card_tap_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}
