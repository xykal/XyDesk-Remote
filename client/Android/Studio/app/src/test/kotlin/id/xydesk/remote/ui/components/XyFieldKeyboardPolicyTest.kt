package id.xydesk.remote.ui.components

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.*
import org.junit.Test

class XyFieldKeyboardPolicyTest {
    @Test fun maskedFieldsUsePasswordImeAndDisableAutocorrect() {
        val options = xyFieldKeyboardOptions(true, KeyboardType.Text, ImeAction.Done)
        assertEquals(KeyboardType.Password, options.keyboardType)
        assertEquals(false, options.autoCorrectEnabled)
        assertEquals(ImeAction.Done, options.imeAction)
    }
    @Test fun pinKeepsNumericPasswordKeyboard() {
        assertEquals(KeyboardType.NumberPassword, xyFieldKeyboardOptions(true, KeyboardType.NumberPassword, ImeAction.Done).keyboardType)
    }
    @Test fun regularFieldsKeepRequestedKeyboard() {
        assertEquals(KeyboardType.Email, xyFieldKeyboardOptions(false, KeyboardType.Email, ImeAction.Next).keyboardType)
    }
}
