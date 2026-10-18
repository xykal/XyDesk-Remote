package id.xydesk.remote.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** Masking alone does not request a password IME. Keep password type even when revealed. */
internal fun xyFieldKeyboardOptions(password: Boolean, requested: KeyboardType, action: ImeAction): KeyboardOptions =
    KeyboardOptions(
        keyboardType = if (password && requested != KeyboardType.NumberPassword) KeyboardType.Password else requested,
        imeAction = action,
        autoCorrectEnabled = if (password) false else null,
    )
