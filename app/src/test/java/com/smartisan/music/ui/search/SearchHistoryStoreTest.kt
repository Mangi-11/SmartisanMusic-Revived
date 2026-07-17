package com.smartisan.music.ui.search

import com.smartisan.music.data.search.decodeHistoryEntries
import com.smartisan.music.data.search.encodeHistoryEntries
import com.smartisan.music.data.search.trimHistoryEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryStoreTest {

    @Test
    fun trimHistoryEntriesDeduplicatesByRecentOrderAndCapsAtTen() {
        val entries = trimHistoryEntries(
            listOf(
                " 平凡之路 ",
                "程艾影",
                "road",
                "Road",
                "赵雷",
                "朴树",
                "海边",
                "白桦林",
                "送别",
                "猎户星座",
                "new boy",
                "在木星",
            ),
        )

        assertEquals(
            listOf("平凡之路", "程艾影", "road", "赵雷", "朴树", "海边", "白桦林", "送别", "猎户星座", "new boy"),
            entries,
        )
    }

    @Test
    fun encodeAndDecodeHistoryEntriesRoundTripUnicodeEntries() {
        val entries = listOf("平凡之路", "程艾影", "A/B Test", "Space Name")

        val decoded = decodeHistoryEntries(encodeHistoryEntries(entries))

        assertEquals(entries, decoded)
    }

    @Test
    fun decodeHistoryEntriesHandlesEmptyValue() {
        assertEquals(emptyList<String>(), decodeHistoryEntries(""))
    }
}
