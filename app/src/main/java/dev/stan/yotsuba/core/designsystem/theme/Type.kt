package dev.stan.yotsuba.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val YotsubaTypography = Typography().let { base ->
    base.copy(
        bodyMedium = base.bodyMedium.copy(lineHeight = 1.45.em, fontSize = 14.sp),
    )
}
