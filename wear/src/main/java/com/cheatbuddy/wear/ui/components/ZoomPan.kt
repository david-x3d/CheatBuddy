package com.cheatbuddy.wear.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isFinite
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Which zoom limit, if any, a zoom request ran into. Used for haptic feedback. */
enum class ZoomLimit { None, Min, Max }

/**
 * Zoom and pan state of one image or table. Offsets are in scaled content pixels, measured from the content's
 * top-left corner to the viewport's top-left corner.
 */
@Stable
class ZoomState(val minScale: Float = 1f, val maxScale: Float = 4f) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Final scale of a running animation; rotary steps accumulate on this. */
    private var targetScale = 1f
    private val mutex = MutatorMutex()

    private var viewportWidth by mutableIntStateOf(0)
    private var viewportHeight by mutableIntStateOf(0)
    private var contentWidth by mutableIntStateOf(0)
    private var contentHeight by mutableIntStateOf(0)
    private var headerHeight by mutableIntStateOf(0)

    val isZoomed: Boolean get() = scale > minScale + 0.01f

    private val bodyViewportHeight: Float get() = viewportHeight - headerHeight * scale
    private val maxOffsetX: Float get() = max(0f, contentWidth * scale - viewportWidth)
    private val maxOffsetY: Float get() = max(0f, contentHeight * scale - bodyViewportHeight)

    val canPanHorizontally: Boolean get() = maxOffsetX > 0.5f
    val canPanVertically: Boolean get() = maxOffsetY > 0.5f

    internal fun updateGeometry(viewportW: Int, viewportH: Int, contentW: Int, contentH: Int, headerH: Int) {
        if (viewportWidth != viewportW) viewportWidth = viewportW
        if (viewportHeight != viewportH) viewportHeight = viewportH
        if (contentWidth != contentW) contentWidth = contentW
        if (contentHeight != contentH) contentHeight = contentH
        if (headerHeight != headerH) headerHeight = headerH
    }

    /** Horizontal shift that centres content narrower than the viewport. */
    internal fun centeringShift(): Float = max(0f, (viewportWidth - contentWidth * scale) / 2f)

    internal fun clampedOffset(): Offset =
        Offset(offset.x.coerceIn(0f, maxOffsetX), offset.y.coerceIn(0f, maxOffsetY))

    /** Pans by a finger [delta] and returns the part that was consumed (same sign as [delta]). */
    fun panBy(delta: Offset): Offset {
        val current = clampedOffset()
        val next = Offset(
            (current.x - delta.x).coerceIn(0f, maxOffsetX),
            (current.y - delta.y).coerceIn(0f, maxOffsetY),
        )
        offset = next
        return Offset(current.x - next.x, current.y - next.y)
    }

    /** Sets the zoom immediately, keeping the content point under [pivot] (viewport coords) in place. */
    fun zoomTo(requested: Float, pivot: Offset = viewportCenter()): ZoomLimit {
        val newScale = requested.coerceIn(minScale, maxScale)
        val limit = limitOf(requested)
        applyScale(newScale, pivot)
        targetScale = newScale
        return limit
    }

    /** Animated zoom by a multiplicative [factor]; consecutive calls accumulate smoothly (rotary input). */
    suspend fun animateZoomBy(factor: Float, pivot: Offset = viewportCenter(), onLimit: (ZoomLimit) -> Unit = {}) {
        val requested = targetScale * factor
        val newTarget = requested.coerceIn(minScale, maxScale)
        val limit = limitOf(requested)
        // Fire once when the limit is reached, not on every further crown step against it.
        if (limit != ZoomLimit.None && newTarget != targetScale) onLimit(limit)
        targetScale = newTarget
        mutex.mutate {
            animate(scale, newTarget, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                applyScale(value, pivot)
            }
        }
    }

    suspend fun animateReset() {
        targetScale = minScale
        val start = clampedOffset()
        val startScale = scale
        mutex.mutate {
            animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { fraction, _ ->
                scale = startScale + (minScale - startScale) * fraction
                offset = Offset(start.x * (1 - fraction), start.y * (1 - fraction))
            }
        }
        offset = Offset.Zero
    }

    private fun applyScale(newScale: Float, pivot: Offset) {
        val old = scale
        if (old == newScale) return
        val current = clampedOffset()
        val pivotBodyY = pivot.y - headerHeight * old
        val contentX = (current.x + pivot.x - centeringShift()) / old
        val contentY = (current.y + pivotBodyY) / old
        scale = newScale
        offset = Offset(contentX * newScale - pivot.x + centeringShift(), contentY * newScale - (pivot.y - headerHeight * newScale))
        offset = clampedOffset()
    }

    private fun limitOf(requested: Float) = when {
        requested >= maxScale -> ZoomLimit.Max
        requested <= minScale -> ZoomLimit.Min
        else -> ZoomLimit.None
    }

    private fun viewportCenter() = Offset(viewportWidth / 2f, viewportHeight / 2f)
}

/**
 * A clipped viewport that shows [content] scaled and panned according to [state].
 *
 * - The optional [header] stays pinned to the top (sticky table header) but follows horizontal panning and zoom.
 * - Drag pans when there is room to pan; otherwise the drag goes to the parent list via nested scrolling.
 * - Pinch zooms, double tap resets.
 */
@Composable
fun ZoomPanBox(
    state: ZoomState,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Infinity,
    onZoomLimit: (ZoomLimit) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val horizontal = rememberScrollableState { delta -> state.panBy(Offset(delta, 0f)).x }
    val vertical = rememberScrollableState { delta -> state.panBy(Offset(0f, delta)).y }

    Layout(
        contents = listOf(header ?: {}, content),
        modifier = modifier
            .clipToBounds()
            .pointerInput(state) {
                // Pinch: intercept multi-touch on the Initial pass, before scrollables see it.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var lastLimit = ZoomLimit.None
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                val limit = state.zoomTo(state.scale * zoom, event.calculateCentroid(useCurrent = true))
                                if (limit != ZoomLimit.None && limit != lastLimit) onZoomLimit(limit)
                                lastLimit = limit
                            }
                            state.panBy(event.calculatePan())
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(state) {
                detectTapGestures(onDoubleTap = { if (state.isZoomed) scope.launch { state.animateReset() } })
            }
            .scrollable(horizontal, Orientation.Horizontal, enabled = state.canPanHorizontally)
            .scrollable(vertical, Orientation.Vertical, enabled = state.canPanVertically),
    ) { (headerMeasurables, contentMeasurables), constraints ->
        val unbounded = Constraints()
        val headerPlaceable = headerMeasurables.firstOrNull()?.measure(unbounded)
        val body = contentMeasurables.first().measure(unbounded)
        val headerHeight = headerPlaceable?.height ?: 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else body.width
        val cap = if (maxHeight.isFinite) maxHeight.roundToPx() else Int.MAX_VALUE
        val height = min(headerHeight + body.height, cap).coerceIn(constraints.minHeight, constraints.maxHeight)
        state.updateGeometry(width, height, max(body.width, headerPlaceable?.width ?: 0), body.height, headerHeight)

        layout(width, height) {
            body.placeWithLayer(0, 0) {
                val s = state.scale
                val o = state.clampedOffset()
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s
                scaleY = s
                translationX = state.centeringShift() - o.x
                translationY = headerHeight * s - o.y
            }
            headerPlaceable?.placeWithLayer(0, 0, zIndex = 1f) {
                val s = state.scale
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s
                scaleY = s
                translationX = state.centeringShift() - state.clampedOffset().x
            }
        }
    }
}
