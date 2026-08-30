package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A single-choice segmented row over [options], usually an enum's entries. [label] draws
 * each segment; [icon], when given, replaces the default selected tick (pass an empty
 * lambda to have none, e.g. when the label itself is an icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumSegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (T) -> Unit)? = null,
    label: @Composable (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = if (icon != null) ({ icon(option) }) else ({ SegmentedButtonDefaults.Icon(isSelected) }),
                label = { label(option) },
            )
        }
    }
}
