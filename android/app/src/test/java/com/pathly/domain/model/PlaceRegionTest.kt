package com.pathly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRegionTest {

  private val shibuya = PlaceRegion.of("JP", "東京都", "渋谷区")
  private val shinjuku = PlaceRegion.of("JP", "東京都", "新宿区")
  private val kyoto = PlaceRegion.of("JP", "京都府", "京都市")
  private val newYork = PlaceRegion.of("US", "ニューヨーク州", "ニューヨーク")
  private val honolulu = PlaceRegion.of("US", "ハワイ州", "ホノルル")
  private val paris = PlaceRegion.of("FR", "イル＝ド＝フランス", "パリ")

  @Test
  fun `保存した値から揃える_海外は州を落とし_日本は都道府県が無ければ地域不明`() {
    assertEquals(PlaceRegion("US", null, "ニューヨーク"), newYork)
    assertEquals(PlaceRegion("JP", "東京都", "渋谷区"), shibuya)
    assertEquals(PlaceRegion.UNKNOWN, PlaceRegion.of("JP", null, "どこか"))
    assertEquals(PlaceRegion.UNKNOWN, PlaceRegion.of(null, null, null))
    // 国が無くても日本の都道府県なら日本。
    assertEquals(PlaceRegion("JP", "東京都", "渋谷区"), PlaceRegion.of(null, "東京都", "渋谷区"))
    assertEquals(PlaceRegion.UNKNOWN, PlaceRegion.of(null, "カリフォルニア州", "ロサンゼルス"))
    assertEquals(PlaceRegion("US", null, null), PlaceRegion.of("us", " ", " "))
    // 海外で都市が分からなければ、州などを都市の代わりにする（ソウルは市が adminArea にしか入らない）。
    assertEquals(PlaceRegion("KR", null, "Seoul"), PlaceRegion.of("KR", "Seoul", null))
  }

  @Test
  fun `都道府県を選ぶとその中すべて_市区町村を選ぶとそこだけ`() {
    assertTrue(RegionKey("JP", "東京都").matches(shibuya))
    assertTrue(RegionKey("JP", "東京都").matches(shinjuku))
    assertTrue(RegionKey("JP", "東京都", "渋谷区").matches(shibuya))
    assertFalse(RegionKey("JP", "東京都", "渋谷区").matches(shinjuku))
    assertFalse(RegionKey("JP", "東京都").matches(kyoto))
  }

  @Test
  fun `国を選ぶとその国すべて_都市を選ぶとそこだけ`() {
    assertTrue(RegionKey("US").matches(newYork))
    assertTrue(RegionKey("US").matches(honolulu))
    assertFalse(RegionKey("US").matches(paris))
    assertFalse(RegionKey("US").matches(shibuya))
    assertTrue(RegionKey("US", city = "ホノルル").matches(honolulu))
    assertFalse(RegionKey("US", city = "ホノルル").matches(newYork))
    // 日本の都道府県のキーは海外に当たらない。
    assertFalse(RegionKey("JP", "東京都").matches(newYork))
  }

  @Test
  fun `地域不明は地域の分からない場所だけに当たる`() {
    assertTrue(RegionKey.UNKNOWN.matches(PlaceRegion.UNKNOWN))
    assertFalse(RegionKey.UNKNOWN.matches(shibuya))
    assertFalse(RegionKey.UNKNOWN.matches(newYork))
    assertFalse(RegionKey("JP", "東京都").matches(PlaceRegion.UNKNOWN))
    assertFalse(RegionKey("US").matches(PlaceRegion.UNKNOWN))
  }

  @Test
  fun `選択が空なら絞らず_複数選べばどれかに当たれば残す`() {
    assertTrue(emptySet<RegionKey>().matchesAnyOf(listOf(kyoto)))
    val selected = setOf(RegionKey("JP", "京都府"), RegionKey("JP", "東京都", "渋谷区"), RegionKey("FR"))
    assertTrue(selected.matchesAnyOf(listOf(kyoto)))
    assertTrue(selected.matchesAnyOf(listOf(shinjuku, shibuya)))
    assertTrue(selected.matchesAnyOf(listOf(paris)))
    assertFalse(selected.matchesAnyOf(listOf(shinjuku, newYork)))
  }

  @Test
  fun `件数_都道府県は北から_次に海外の国_地域不明は最後_市区町村は多い順`() {
    val items = listOf(
      listOf(kyoto),
      listOf(shibuya),
      listOf(newYork),
      listOf(shinjuku),
      listOf(shibuya),
      listOf(PlaceRegion.UNKNOWN),
      listOf(paris),
      listOf(honolulu),
    )

    val counts = regionCounts(items, { it }) { true }

    assertEquals(
      listOf("東京都", "京都府", "アメリカ合衆国", "フランス", "地域不明"),
      counts.map { it.key.label },
    )
    assertEquals(3, counts[0].count)
    assertEquals(listOf("渋谷区" to 2, "新宿区" to 1), counts[0].cities)
    val us = counts.first { it.key == RegionKey("US") }
    assertEquals(2, us.count)
    assertEquals(setOf("ニューヨーク", "ホノルル"), us.cities.map { it.first }.toSet())
    assertEquals(RegionKey("US", city = "ホノルル"), us.cityKey("ホノルル"))
    assertEquals(1, counts.last().count)
  }

  @Test
  fun `件数_1件が同じ地域に何度当たっても1と数え_数えないものは0で残る`() {
    // 経路は立ち寄りが複数ある。同じ渋谷区に 2 回立ち寄っても 1 件。
    val track = listOf(shibuya, shibuya.copy(), shinjuku)
    val other = listOf(kyoto)

    val counts = regionCounts(listOf(track, other), { it }) { it === track }

    val tokyo = counts.first { it.key == RegionKey("JP", "東京都") }
    assertEquals(1, tokyo.count)
    assertEquals(listOf("渋谷区" to 1, "新宿区" to 1), tokyo.cities)
    assertEquals(0, counts.first { it.key == RegionKey("JP", "京都府") }.count)
  }

  @Test
  fun `名前_チップでは同じ名前の市を見分けられるよう1段目を添える`() {
    assertEquals("東京都 府中市", RegionKey("JP", "東京都", "府中市").fullLabel)
    assertEquals("府中市", RegionKey("JP", "東京都", "府中市").label)
    assertEquals("東京都", RegionKey("JP", "東京都").fullLabel)
    assertEquals("アメリカ合衆国", RegionKey("US").label)
    assertEquals("アメリカ合衆国 ホノルル", RegionKey("US", city = "ホノルル").fullLabel)
    assertEquals("地域不明", RegionKey.UNKNOWN.fullLabel)
  }

  @Test
  fun `表示の並びは選んだ順に依らない`() {
    val sorted = setOf(
      RegionKey("US"),
      RegionKey("JP", "京都府"),
      RegionKey.UNKNOWN,
      RegionKey("JP", "東京都", "渋谷区"),
      RegionKey("JP", "東京都"),
    ).sortedForDisplay()

    assertEquals(
      listOf(
        RegionKey("JP", "東京都"),
        RegionKey("JP", "東京都", "渋谷区"),
        RegionKey("JP", "京都府"),
        RegionKey("US"),
        RegionKey.UNKNOWN,
      ),
      sorted,
    )
  }
}
