package com.freerdp.freerdpcore.presentation;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImeCompositionBufferTest
{
	@Test
	public void composingUpdatesStayLocalAndCommitOnlyOnce()
	{
		ImeCompositionBuffer buffer = new ImeCompositionBuffer();
		buffer.setComposingText("h");
		buffer.setComposingText("hello");
		assertEquals("hello", buffer.getComposingText());
		assertEquals("hello", buffer.commitText("hello"));
		assertEquals("", buffer.finishComposingText());
	}

	@Test
	public void finishCompositionFlushesUncommittedTextOnce()
	{
		ImeCompositionBuffer buffer = new ImeCompositionBuffer();
		buffer.setComposingText("text");
		assertEquals("text", buffer.finishComposingText());
		assertEquals("", buffer.finishComposingText());
	}

	@Test
	public void deleteEditsCompositionBeforeSendingRemoteBackspaces()
	{
		ImeCompositionBuffer buffer = new ImeCompositionBuffer();
		buffer.setComposingText("ab");
		assertEquals(0, buffer.deleteBeforeCursor(1));
		assertEquals("a", buffer.getComposingText());
		assertEquals(2, buffer.deleteBeforeCursor(3));
		assertEquals("", buffer.getComposingText());
	}

	@Test
	public void deletionDoesNotSplitASurrogatePair()
	{
		ImeCompositionBuffer buffer = new ImeCompositionBuffer();
		buffer.setComposingText("A\uD83D\uDE00");
		assertEquals(0, buffer.deleteBeforeCursor(1));
		assertEquals("A", buffer.getComposingText());
	}

	@Test
	public void duplicatePrintableSoftImeEventsAreSuppressedButPhysicalEventsRemain()
	{
		assertTrue(ImeCompositionBuffer.isPrintableImeTextEvent(
		        KeyEvent.FLAG_SOFT_KEYBOARD, KeyEvent.ACTION_DOWN, 'x', false));
		assertTrue(ImeCompositionBuffer.isPrintableImeTextEvent(
		        0, KeyEvent.ACTION_DOWN, 'x', true));
		assertFalse(ImeCompositionBuffer.isPrintableImeTextEvent(
		        0, KeyEvent.ACTION_DOWN, 'x', false));
		assertFalse(ImeCompositionBuffer.isPrintableImeTextEvent(
		        KeyEvent.FLAG_SOFT_KEYBOARD, KeyEvent.ACTION_DOWN, '\n', false));
	}
}
