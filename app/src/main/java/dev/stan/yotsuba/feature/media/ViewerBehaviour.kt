package dev.stan.yotsuba.feature.media

/**
 * The user's viewer preferences, resolved once per screen and passed down as one value
 * rather than as a handful of loose flags threaded through every composable.
 */
data class ViewerBehaviour(
    /** Hold the display awake while a video is playing. */
    val keepScreenOn: Boolean = false,
    /** Double-tapping a video's left or right edge jumps by [seekStepSeconds]. */
    val doubleTapSeek: Boolean = false,
    val seekStepSeconds: Int = 10,
    /** Long-pressing an open image or video saves it to the vault. */
    val holdToSave: Boolean = false,
) {
    /**
     * How far one double-tap actually jumps in a video of [durationMs].
     *
     * A fixed step is useless on a short clip; 10 s on a 2 s webm just lands at the end.
     * So the jump is capped at [MAX_SHARE] of the running time. Long videos get the
     * configured step verbatim; short ones scale down, so no clip takes more than
     * `1 / MAX_SHARE` taps to cross. An unknown duration falls back to the configured step.
     */
    fun seekStepMillis(durationMs: Long): Long {
        val configured = seekStepSeconds * 1000L
        if (durationMs <= 0) return configured
        val capped = (durationMs * MAX_SHARE).toLong()
        return capped.coerceIn(MIN_STEP_MS, configured)
    }

    private companion object {
        /** Largest share of a video one double-tap may cross. */
        const val MAX_SHARE = 0.25

        /** Below this a jump stops being perceptible, so it is not worth shrinking further. */
        const val MIN_STEP_MS = 250L
    }
}
