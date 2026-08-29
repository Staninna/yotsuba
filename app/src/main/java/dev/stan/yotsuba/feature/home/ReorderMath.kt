package dev.stan.yotsuba.feature.home

/*
 * The arithmetic behind dragging a Home tab, kept free of Compose so it runs on the JVM.
 */

/**
 * Which slot a tab dragged from [from] by [offset] pixels lands in. [widths] are the tabs'
 * laid-out widths in order; the dragged tab's centre is compared with the centres of the
 * others, so a tab has to be pulled more than halfway past a neighbour to take its place.
 */
fun dropTarget(from: Int, offset: Float, widths: List<Int>): Int {
    if (widths.isEmpty()) return 0
    val starts = IntArray(widths.size)
    for (i in 1 until widths.size) starts[i] = starts[i - 1] + widths[i - 1]
    val centre = starts[from] + widths[from] / 2f + offset
    var target = from
    if (offset < 0) {
        for (i in from - 1 downTo 0) {
            if (centre < starts[i] + widths[i] / 2f) target = i else break
        }
    } else {
        for (i in from + 1 until widths.size) {
            if (centre > starts[i] + widths[i] / 2f) target = i else break
        }
    }
    return target.coerceIn(0, widths.lastIndex)
}

/**
 * How far tab [index] slides, in units of the dragged tab's width, while the tab at [from]
 * hovers over slot [to]: -1 for tabs it passed on the way right, +1 on the way left.
 */
fun shiftFor(index: Int, from: Int, to: Int): Int = when {
    index == from -> 0
    from < to && index in (from + 1)..to -> -1
    to < from && index in to until from -> 1
    else -> 0
}

/** Where the page at [current] ends up once the tab at [from] has moved to [to]. */
fun remapPage(current: Int, from: Int, to: Int): Int = when {
    current == from -> to
    from < current && current <= to -> current - 1
    to <= current && current < from -> current + 1
    else -> current
}
