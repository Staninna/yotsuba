package dev.stan.yotsuba.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.LineSpacing

/** App chrome: top bars, nav, settings. Follows the system size only. */
val YotsubaTypography = Typography().let { base ->
    base.copy(
        bodyMedium = base.bodyMedium.copy(lineHeight = LineSpacing.DEFAULT.em.em, fontSize = 14.sp),
    )
}

/**
 * Post text: bodies, excerpts and the labels and titles on a post card. Scaled by the
 * "Text size" and "Line spacing" settings. Read it through [postTypography] rather than
 * [androidx.compose.material3.MaterialTheme.typography] so the chrome stays system-sized.
 */
val LocalPostTypography = staticCompositionLocalOf { postTypography(FontSize.DEFAULT, LineSpacing.DEFAULT) }

val postTypography: Typography
    @Composable @ReadOnlyComposable get() = LocalPostTypography.current

/** Pure: builds the scaled set once per settings change. */
fun postTypography(fontSize: FontSize, lineSpacing: LineSpacing): Typography {
    val height = lineSpacing.em.em
    fun TextStyle.scaled() = copy(fontSize = this.fontSize * fontSize.scale, lineHeight = height)
    val base = YotsubaTypography
    return base.copy(
        bodySmall = base.bodySmall.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        labelSmall = base.labelSmall.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelLarge = base.labelLarge.scaled(),
        titleSmall = base.titleSmall.scaled(),
        titleMedium = base.titleMedium.scaled(),
    )
}
