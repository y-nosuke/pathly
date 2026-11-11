package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRegionTest {

  private val shibuya = PlaceRegion("東京都", "渋谷区")
  private val shinjuku = PlaceRegion("東京都", "新宿区")
  private val kyoto = PlaceRegion("京都府", "京都市")

  @Test
  fun `都道府県を選ぶとその中すべて_市区町村を選ぶとそこだけ`() {
    assertTrue(RegionKey("東京都").matches(shibuya))
    assertTrue(RegionKey("東京都").matches(shinjuku))
    assertTrue(RegionKey("東京都", "渋谷区").matches(shibuya))
    assertFalse(RegionKey("東京都", "渋谷区").matches(shinjuku))
    assertFalse(RegionKey("東京都").matches(kyoto))
  }

  @Test
  fun `地域不明は都道府県の分からない場所だけに当たる`() {
    assertTrue(RegionKey.UNKNOWN.matches(PlaceRegion.UNKNOWN))
    assertFalse(RegionKey.UNKNOWN.matches(shibuya))
    assertFalse(RegionKey("東京都").matches(PlaceRegion.UNKNOWN))
  }

  @Test
  fun `選択が空なら絞らず_複数選べばどれかに当たれば残す`() {
    assertTrue(emptySet<RegionKey>().matchesAnyOf(listOf(kyoto)))
    val selected = setOf(RegionKey("京都府"), RegionKey("東京都", "渋谷区"))
    assertTrue(selected.matchesAnyOf(listOf(kyoto)))
    assertTrue(selected.matchesAnyOf(listOf(shinjuku, shibuya)))
    assertFalse(selected.matchesAnyOf(listOf(shinjuku)))
  }

  @Test
  fun `件数_都道府県は北から並び地域不明は最後_市区町村は多い順`() {
    val items = listOf(listOf(kyoto), listOf(shibuya), listOf(shinjuku), listOf(shibuya), listOf(PlaceRegion.UNKNOWN))

    val counts = regionCounts(items, { it }) { true }

    assertEquals(listOf("東京都", "京都府", null), counts.map { it.prefecture })
    assertEquals(3, counts[0].count)
    assertEquals(listOf("渋谷区" to 2, "新宿区" to 1), counts[0].cities)
    assertEquals(1, counts[2].count)
  }

  @Test
  fun `件数_1件が同じ地域に何度当たっても1と数え_数えないものは0で残る`() {
    // 経路は立ち寄りが複数ある。同じ渋谷区に 2 回立ち寄っても 1 件。
    val track = listOf(shibuya, shibuya.copy(), shinjuku)
    val other = listOf(kyoto)

    val counts = regionCounts(listOf(track, other), { it }) { it === track }

    val tokyo = counts.first { it.prefecture == "東京都" }
    assertEquals(1, tokyo.count)
    assertEquals(listOf("渋谷区" to 1, "新宿区" to 1), tokyo.cities)
    assertEquals(0, counts.first { it.prefecture == "京都府" }.count)
  }

  @Test
  fun `チップの名前は同じ名前の市を見分けられるよう都道府県を添える`() {
    assertEquals("東京都 府中市", RegionKey("東京都", "府中市").fullLabel)
    assertEquals("府中市", RegionKey("東京都", "府中市").label)
    assertEquals("東京都", RegionKey("東京都").fullLabel)
    assertEquals("地域不明", RegionKey.UNKNOWN.fullLabel)
  }

  @Test
  fun `表示の並びは選んだ順に依らない`() {
    val sorted = setOf(RegionKey("京都府"), RegionKey("東京都", "渋谷区"), RegionKey("東京都")).sortedForDisplay()

    assertEquals(listOf(RegionKey("東京都"), RegionKey("東京都", "渋谷区"), RegionKey("京都府")), sorted)
  }
}
