package com.fujitake.cribbage.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujitake.cribbage.engine.HandRecord
import com.fujitake.cribbage.engine.Performance
import com.fujitake.cribbage.engine.Rating
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val ROLLING_HANDS = 10
private const val RECENT_ROWS = 10

fun percentWhole(value: Double) = "${(value * 100).roundToInt()}%"

/** The rating change as a colored arrow and number. */
@Composable
fun RatingDelta(delta: Int, size: Int = 16) {
    val (text, color) = when {
        delta > 0 -> "▲ $delta" to Ink.Accent
        delta < 0 -> "▼ ${-delta}" to Ink.Red
        else -> "± 0" to Ink.Muted
    }
    Txt(text, size = size, family = Mono, weight = FontWeight.Bold, color = color)
}

/**
 * A single-series line chart. Press and drag to read the value at any hand. The chart's title says
 * what the series is, so there's no legend.
 */
@Composable
fun LineChart(
    values: List<Double>,
    low: Double,
    high: Double,
    gridlines: List<Double>,
    axisLabel: (Double) -> String,
    pointLabel: (Int, Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var selected by remember { mutableStateOf<Int?>(null) }
    val motion = LocalMotion.current
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(values.size) { reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val axisStyle = TextStyle(color = Ink.Muted, fontFamily = Mono, fontSize = 10.sp)
    val labelStyle = TextStyle(color = Ink.Text, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                val gutter = 44.dp.toPx()
                fun indexAt(x: Float): Int {
                    if (values.size < 2) return 0
                    val fraction = ((x - gutter) / (size.width - gutter)).coerceIn(0f, 1f)
                    return (fraction * (values.size - 1)).roundToInt()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selected = indexAt(down.position.x)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        selected = indexAt(change.position.x)
                    }
                    selected = null
                }
            },
    ) {
        val gutter = 44.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val width = size.width - gutter
        fun x(i: Int) = if (values.size < 2) gutter + width / 2 else gutter + width * i / (values.size - 1)
        fun y(v: Double) = (bottom - (bottom - top) * ((v - low) / (high - low))).toFloat()

        gridlines.forEach { g ->
            drawLine(Ink.Hairline, Offset(gutter, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
            val text = measurer.measure(axisLabel(g), axisStyle)
            drawText(text, topLeft = Offset(0f, y(g) - text.size.height / 2f))
        }
        val first = measurer.measure("Hand 1", axisStyle)
        drawText(first, topLeft = Offset(gutter, bottom + 4.dp.toPx()))
        if (values.size > 1) {
            val last = measurer.measure("Hand ${values.size}", axisStyle)
            drawText(last, topLeft = Offset(size.width - last.size.width, bottom + 4.dp.toPx()))
        }

        val path = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        clipRect(right = gutter + width * reveal.value + 8.dp.toPx()) {
            drawPath(path, Ink.Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val marked = selected ?: values.lastIndex
        val point = Offset(x(marked), y(values[marked]))
        if (selected != null) {
            drawLine(Ink.Muted, Offset(point.x, top), Offset(point.x, bottom), 1.dp.toPx())
        }
        if (reveal.value >= 1f || selected != null) {
            drawCircle(Ink.Ground, 6.dp.toPx(), point)
            drawCircle(Ink.Accent, 4.dp.toPx(), point)
            val text = measurer.measure(pointLabel(marked, values[marked]), labelStyle)
            val pad = 6.dp.toPx()
            val boxWidth = text.size.width + pad * 2
            val boxHeight = text.size.height + pad
            val left = (point.x - boxWidth / 2).coerceIn(gutter, size.width - boxWidth)
            val boxTop = (point.y - boxHeight - 10.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(Ink.Raised, Offset(left, boxTop), Size(boxWidth, boxHeight), CornerRadius(6.dp.toPx()))
            drawText(text, topLeft = Offset(left + pad, boxTop + pad / 2))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(value, size = 24, family = Mono, weight = FontWeight.Light)
        Eyebrow(label, Modifier.padding(top = 2.dp))
    }
}

private fun ratingRange(values: List<Double>): Pair<Double, Double> {
    val step = 50.0
    val low = floor((values.min() - 20) / step) * step
    val high = ceil((values.max() + 20) / step) * step
    return low to high
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

private fun handLabel(record: HandRecord) =
    (if (record.youDealt) "Your crib" else "Their crib") + if (record.fullGame) " · Game" else " · Drill"

@Composable
fun ProfileScreen(ui: UiState, onBack: () -> Unit) {
    val stats: Performance = ui.performance
    val records = stats.records
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Eyebrow("← Menu", Modifier.clickable(onClick = onBack).padding(vertical = 8.dp), color = Ink.Accent)
        Txt("Profile", Modifier.padding(top = 8.dp, bottom = 18.dp), size = 32, family = Mono, weight = FontWeight.Light)

        if (records.isEmpty()) {
            Txt("No hands yet.", size = 18, weight = FontWeight.SemiBold)
            Txt(
                "Finish a hand in any mode and your rating and accuracy start here. Everyone starts at ${Rating.START.toInt()}.",
                Modifier.padding(top = 6.dp),
                color = Ink.Muted,
            )
            return@Column
        }

        val ratings = stats.ratings
        val tenAgo = if (ratings.size > ROLLING_HANDS) ratings[ratings.size - 1 - ROLLING_HANDS] else Rating.START
        Eyebrow("Skill rating")
        Row(Modifier.padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Txt("${stats.rating.roundToInt()}", size = 56, family = Mono, weight = FontWeight.Light, lineHeight = 62)
            Column(Modifier.padding(start = 12.dp, bottom = 10.dp)) {
                RatingDelta(stats.rating.roundToInt() - tenAgo.roundToInt())
                Txt(
                    "over the last ${minOf(ROLLING_HANDS, ratings.size)} ${if (ratings.size == 1) "hand" else "hands"}",
                    size = 12,
                    color = Ink.Muted,
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Tile("Accuracy", percentWhole(stats.accuracy), Modifier.weight(1f))
            Tile("Discards", percentWhole(stats.discardAccuracy), Modifier.weight(1f))
            Tile("Pegging", stats.peggingAccuracy?.let(::percentWhole) ?: "–", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Tile("Hands", "${records.size}", Modifier.weight(1f))
            Tile("Avg given up", points(stats.averageLost), Modifier.weight(2f))
        }

        SectionTitle("Rating over time", "After each hand. Press and drag to read any hand.")
        val (low, high) = ratingRange(ratings + Rating.START)
        LineChart(
            values = ratings,
            low = low,
            high = high,
            gridlines = listOf(low, (low + high) / 2, high),
            axisLabel = { "${it.roundToInt()}" },
            pointLabel = { i, v -> "Hand ${i + 1} · ${v.roundToInt()}" },
            description = "Skill rating over ${ratings.size} hands, now ${stats.rating.roundToInt()}",
        )

        SectionTitle("Accuracy over time", "Top choices across your last $ROLLING_HANDS hands, at each hand")
        val rolling = stats.rollingAccuracy(ROLLING_HANDS)
        LineChart(
            values = rolling,
            low = 0.0,
            high = 1.0,
            gridlines = listOf(0.0, 0.5, 1.0),
            axisLabel = ::percentWhole,
            pointLabel = { i, v -> "Hand ${i + 1} · ${percentWhole(v)}" },
            description = "Recent accuracy over ${rolling.size} hands, now ${percentWhole(rolling.last())}",
        )

        SectionTitle("Recent hands")
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Eyebrow("Hand", Modifier.weight(1f))
            Eyebrow("Top", Modifier.width(48.dp))
            Eyebrow("Given up", Modifier.width(76.dp))
            Eyebrow("Rating", Modifier.width(60.dp))
        }
        Hairline()
        records.indices.reversed().take(RECENT_ROWS).forEach { i ->
            val record = records[i]
            val before = if (i == 0) Rating.START else ratings[i - 1]
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(handLabel(record), size = 14)
                    Txt(dateFormat.format(Date(record.time)), size = 11, family = Mono, color = Ink.Muted)
                }
                Txt("${record.best}/${record.decisions}", Modifier.width(48.dp), size = 13, family = Mono)
                Txt(points(record.pointsLost), Modifier.width(76.dp), size = 13, family = Mono)
                Column(Modifier.width(60.dp)) {
                    RatingDelta(ratings[i].roundToInt() - before.roundToInt(), size = 13)
                }
            }
            Hairline()
        }
    }
}
