package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatCodeBlockTest {
    @Test fun plainTextIsNotGuessedAsScript() {
        assertEquals(listOf(ChatTextPart("return hello")), splitChatCode("return hello"))
    }
    @Test fun fencedCodePreservesSurroundingText() {
        assertEquals(listOf(ChatTextPart("before\n"), ChatTextPart("val x = 1", "kotlin"), ChatTextPart("\nafter")),
            splitChatCode("before\n```kotlin\nval x = 1\n```\nafter"))
    }
    @Test fun unclosedFenceRemainsReadableText() {
        val text = "```sh\necho hello"
        assertEquals(listOf(ChatTextPart(text)), splitChatCode(text))
    }
    @Test fun unspecifiedLanguageAndMultipleBlocks() {
        assertEquals(listOf(ChatTextPart("a", ""), ChatTextPart("b", "sh")), splitChatCode("```\na\n``````sh\nb\n```"))
    }
}
