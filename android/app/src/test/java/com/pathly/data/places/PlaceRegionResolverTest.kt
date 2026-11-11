package com.pathly.data.places

import android.content.Context
import com.pathly.data.local.dao.PlaceRegionDao
import com.pathly.data.local.dao.UnresolvedRegionRow
import com.pathly.domain.model.PlaceRegion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaceRegionResolverTest {

  private val tokyo = PlaceRegion("JP", "東京都", "千代田区")

  /** places の代わり。id → 引いたか。getUnresolved は本物と同じく id の続きから未解決を返す。 */
  private class FakeDao(ids: List<Long>) {
    val resolved = mutableMapOf<Long, PlaceRegion>()
    val all = ids
    val dao: PlaceRegionDao = mockk(relaxed = true)

    init {
      coEvery { dao.getUnresolved(any(), any()) } answers {
        val afterId = firstArg<Long>()
        val limit = secondArg<Int>()
        all.filter { it > afterId && it !in resolved }.take(limit).map { UnresolvedRegionRow(it, it.toDouble(), 0.0) }
      }
      coEvery { dao.updateRegion(any(), any(), any(), any(), any()) } answers {
        resolved[firstArg()] = PlaceRegion(secondArg(), thirdArg(), arg(3))
      }
    }
  }

  private fun resolver(fake: FakeDao) = PlaceRegionResolver(mockk<Context>(relaxed = true), fake.dao)

  @Test
  fun `一括の大きさを超えても_無くなるまで引く`() = runTest {
    val fake = FakeDao((1L..45L).toList())

    resolver(fake).resolveWith { _, _ -> tokyo }

    assertEquals(45, fake.resolved.size)
  }

  @Test
  fun `1件が引けなくても後ろの場所は引き_最後に失敗を知らせる`() = runTest {
    val fake = FakeDao(listOf(1L, 2L, 3L))

    val error = runCatching {
      resolver(fake).resolveWith { lat, _ -> if (lat == 1.0) throw IOException("x") else tokyo }
    }.exceptionOrNull()

    assertTrue(error is IOException)

    // 引けなかった 1 は書かずに残し（次の再試行で引き直す）、2・3 は引けている。
    assertNull(fake.resolved[1L])
    assertEquals(setOf(2L, 3L), fake.resolved.keys)
  }

  @Test
  fun `続けて失敗したら通信断とみて_そこでやめる`() = runTest {
    val fake = FakeDao((1L..10L).toList())
    var calls = 0

    val error = runCatching {
      resolver(fake).resolveWith { _, _ ->
        calls++
        throw IOException("offline")
      }
    }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals(PlaceRegionResolver.MAX_FAILURES_IN_A_ROW, calls)
    coVerify(exactly = 0) { fake.dao.updateRegion(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `地域が分からない場所も引いたことにして_引き直さない`() = runTest {
    val fake = FakeDao(listOf(1L))

    resolver(fake).resolveWith { _, _ -> PlaceRegion.UNKNOWN }

    assertEquals(mapOf(1L to PlaceRegion.UNKNOWN), fake.resolved)
  }

  @Test
  fun `市区町村は locality を使い_無ければ subAdminArea`() {
    assertEquals("渋谷区", cityOf("渋谷区", null))
    assertEquals("横浜市", cityOf("横浜市", null))
    assertEquals("西多摩郡", cityOf(null, "西多摩郡"))
    assertEquals("西多摩郡", cityOf(" ", "西多摩郡"))
    assertNull(cityOf(null, null))
  }
}
