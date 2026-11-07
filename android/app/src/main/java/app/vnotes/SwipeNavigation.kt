package app.vnotes

import kotlin.math.abs

enum class ScreenSwipe { NONE, TODO, DONE }

/** Screen navigation has priority over row actions. Distances use the caller's pixel scale. */
fun screenSwipe(inDone: Boolean, dx: Float, dy: Float, minimum: Float): ScreenSwipe {
    if (abs(dx) < minimum || abs(dx) <= abs(dy) * 1.3f) return ScreenSwipe.NONE
    return when {
        !inDone && dx < 0 -> ScreenSwipe.DONE
        inDone && dx > 0 -> ScreenSwipe.TODO
        else -> ScreenSwipe.NONE
    }
}

fun completesNote(inDone: Boolean, dx: Float, dy: Float, minimum: Float): Boolean =
    !inDone && dx >= minimum && dx > abs(dy) * 1.3f
