package com.freerdp.freerdpcore.presentation;

import android.view.KeyEvent;

/**
 * Holds soft-keyboard composing text locally until the IME commits it.
 * Remote text controls do not expose Android's composition range, so sending
 * every composing update would require remote backspace/retype and can flicker
 * or duplicate characters.
 */
final class ImeCompositionBuffer
{
	private static final int MAX_DELETE_UNITS = 256;
	private String composingText = "";

	void setComposingText(CharSequence text)
	{
		composingText = text == null ? "" : text.toString();
	}

	boolean hasComposingText()
	{
		return !composingText.isEmpty();
	}

	String getComposingText()
	{
		return composingText;
	}

	/** Returns committed text to send once, then clears the provisional text. */
	String commitText(CharSequence text)
	{
		String committed = text == null ? "" : text.toString();
		composingText = "";
		return committed;
	}

	/** Flushes an IME that finishes composition without calling commitText. */
	String finishComposingText()
	{
		String remaining = composingText;
		composingText = "";
		return remaining;
	}

	/**
	 * Applies deletion to the local composing range. The return value is the
	 * number of backspaces that still need to be sent to the remote window.
	 */
	int deleteBeforeCursor(int beforeLength)
	{
		if (beforeLength <= 0)
			return 0;

		int deleteUnits = Math.min(beforeLength, MAX_DELETE_UNITS);
		if (composingText.isEmpty())
			return deleteUnits;

		int start = Math.max(0, composingText.length() - deleteUnits);
		if (start > 0 && start < composingText.length() &&
		    Character.isHighSurrogate(composingText.charAt(start - 1)) &&
		    Character.isLowSurrogate(composingText.charAt(start)))
			start--;

		int removedUnits = composingText.length() - start;
		int outsideBackspaces = Math.max(0, deleteUnits - removedUnits);
		composingText = composingText.substring(0, start);
		return outsideBackspaces;
	}

	/** Clears provisional text and returns forward deletes for remote text. */
	int deleteAfterCursor(int afterLength)
	{
		composingText = "";
		return Math.min(Math.max(afterLength, 0), MAX_DELETE_UNITS);
	}

	/** Ignore printable soft-IME key events; their committed text uses commitText. */
	static boolean isPrintableImeTextEvent(int flags, int action, int unicodeChar,
	                                       boolean hasComposingText)
	{
		return ((flags & KeyEvent.FLAG_SOFT_KEYBOARD) != 0 || hasComposingText) &&
		       action == KeyEvent.ACTION_DOWN && unicodeChar > 0 &&
		       Character.isValidCodePoint(unicodeChar) && !Character.isISOControl(unicodeChar);
	}

	/** ACTION_MULTIPLE character payloads can duplicate committed/composing text. */
	static boolean isImeMultipleTextEvent(int flags, int action, String characters,
	                                      boolean hasComposingText)
	{
		return ((flags & KeyEvent.FLAG_SOFT_KEYBOARD) != 0 || hasComposingText) &&
		       action == KeyEvent.ACTION_MULTIPLE && characters != null && !characters.isEmpty();
	}
}
