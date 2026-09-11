package com.android.car.settings.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.uicomponents.button.BButton
import com.b231001.bmaterial.uicomponents.button.BButtonSize
import com.b231001.bmaterial.uicomponents.button.BButtonStyle
import com.b231001.bmaterial.uicomponents.slider.BSlider
import com.b231001.bmaterial.uicomponents.slider.BSliderDefaults
import com.b231001.bmaterial.uicomponents.slider.BSliderSize
import com.b231001.bmaterial.uicomponents.slider.BSliderStyle
import com.b231001.bmaterial.uicomponents.textfield.BTextField
import com.b231001.bmaterial.uicomponents.textfield.BTextFieldSize
import com.b231001.bmaterial.uicomponents.textfield.BTextFieldStyle

/** Shared B-Material command button used by forms and dialog actions. */
@Composable
fun AutomotiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: BButtonStyle = BButtonStyle.Filled,
    size: BButtonSize = BButtonSize.Md,
) {
    BButton(
        onClick = onClick,
        modifier = modifier.clip(SettingsTokens.DialogShape),
        enabled = enabled,
        style = style,
        size = size,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

/** Content-slot overload used when migrating existing form actions to B-Material. */
@Composable
fun AutomotiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: BButtonStyle = BButtonStyle.Filled,
    size: BButtonSize = BButtonSize.Md,
    content: @Composable RowScope.() -> Unit,
) {
    BButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = style,
        size = size,
        content = content,
    )
}

@Composable
fun AutomotiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    AutomotiveButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = BButtonStyle.Outlined,
        content = content,
    )
}

@Composable
fun AutomotiveTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: BButtonSize = BButtonSize.Md,
) {
    AutomotiveButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = BButtonStyle.Text,
        size = size,
    )
}

@Composable
fun AutomotiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: BButtonSize = BButtonSize.Md,
    content: @Composable RowScope.() -> Unit,
) {
    AutomotiveButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = BButtonStyle.Text,
        size = size,
        content = content,
    )
}

/**
 * Shared B-Material slider for non-vehicle settings surfaces.
 *
 * Keeping the value controlled by the caller is important: a VHAL/CarAudio callback may arrive
 * during a drag, so each screen keeps its draft value until [onValueChangeFinished].  The wrapper
 * makes the visual component consistent without changing that state contract.
 */
@Composable
fun AutomotiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    showValueLabel: Boolean = false,
) {
    val sliderColors =
        BSliderDefaults.colors(BSliderStyle.Primary).copy(
            thumb = MaterialTheme.colorScheme.primary,
            trackActive = MaterialTheme.colorScheme.primary,
        )
    BSlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps.coerceAtLeast(0),
        showValueLabel = showValueLabel,
        size = BSliderSize.Lg,
        colors = sliderColors,
        modifier = modifier,
    )
}

/** Shared B-Material text field; the value is intentionally controlled synchronously. */
@Composable
fun AutomotiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    BTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        errorMessage = errorMessage,
        style = BTextFieldStyle.Outlined,
        size = BTextFieldSize.Default,
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
    )
}

/**
 * Wraps a non-list action (media command, launcher tile or form command) in the same CCP contract
 * as settings rows. The child remains a normal touch/accessibility control in hostless mode while
 * rotary Center invokes the stable parent action exactly once.
 */
@Composable
fun RotaryAction(
    id: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: FocusItemRole = FocusItemRole.Button,
    content: @Composable (focused: Boolean) -> Unit,
) {
    FocusItem(
        id = FocusItemId("rotary-action-$id"),
        modifier = modifier,
        isEnabled = enabled,
        onClick = onClick,
        semantics = FocusItemSemantics(label = label, role = role),
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        layout = FocusItemLayout(fillCrossAxis = false, minHeight = 48.dp),
    ) { state ->
        // Preserve the caller's minimum width/height (notably dialog close actions) for the
        // actual button as well as the CCP focus item. Without this, a TextButton can render at
        // its intrinsic size inside a large focus target.
        Box(propagateMinConstraints = true) { content(state.isFocused) }
    }
}

/** Slot-label overload for forms that need B-Material's full text-field API. */
@Composable
fun AutomotiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    BTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        errorMessage = errorMessage,
        style = BTextFieldStyle.Outlined,
        size = BTextFieldSize.Default,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
    )
}

/** TextFieldValue overload preserves cursor/selection and IME composition state. */
@Composable
fun AutomotiveTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    BTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        errorMessage = errorMessage,
        style = BTextFieldStyle.Outlined,
        size = BTextFieldSize.Default,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
    )
}
