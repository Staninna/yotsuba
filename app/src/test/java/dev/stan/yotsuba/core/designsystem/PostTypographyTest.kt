package dev.stan.yotsuba.core.designsystem

import dev.stan.yotsuba.core.designsystem.theme.YotsubaTypography
import dev.stan.yotsuba.core.designsystem.theme.postTypography
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.LineSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class PostTypographyTest {

    @Test fun `default settings leave the post body at the chrome size`() {
        val type = postTypography(FontSize.DEFAULT, LineSpacing.DEFAULT)
        assertEquals(YotsubaTypography.bodyMedium.fontSize, type.bodyMedium.fontSize)
        assertEquals(YotsubaTypography.bodyMedium.lineHeight, type.bodyMedium.lineHeight)
    }

    @Test fun `font size scales body, label and title styles by the same factor`() {
        val type = postTypography(FontSize.EXTRA_LARGE, LineSpacing.DEFAULT)
        assertEquals(14f * 1.3f, type.bodyMedium.fontSize.value, 0.001f)
        assertEquals(YotsubaTypography.labelSmall.fontSize.value * 1.3f, type.labelSmall.fontSize.value, 0.001f)
        assertEquals(YotsubaTypography.titleMedium.fontSize.value * 1.3f, type.titleMedium.fontSize.value, 0.001f)
        assertEquals(YotsubaTypography.bodyLarge.fontSize.value * 1.3f, type.bodyLarge.fontSize.value, 0.001f)
    }

    @Test fun `line spacing sets the em line height and is independent of the size`() {
        val type = postTypography(FontSize.SMALL, LineSpacing.RELAXED)
        assertEquals(1.7f, type.bodyMedium.lineHeight.value, 0.001f)
        assertEquals(true, type.bodyMedium.lineHeight.isEm)
        assertEquals(1.7f, type.labelSmall.lineHeight.value, 0.001f)
        assertEquals(14f * 0.875f, type.bodyMedium.fontSize.value, 0.001f)
    }

    @Test fun `headline and display styles are left alone`() {
        val type = postTypography(FontSize.EXTRA_LARGE, LineSpacing.RELAXED)
        assertEquals(YotsubaTypography.headlineSmall, type.headlineSmall)
        assertEquals(YotsubaTypography.displayLarge, type.displayLarge)
    }
}
