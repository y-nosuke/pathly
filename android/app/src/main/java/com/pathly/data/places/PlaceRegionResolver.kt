package com.pathly.data.places

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.pathly.data.local.dao.PlaceRegionDao
import com.pathly.domain.model.PlaceRegion
import com.pathly.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 場所の地域（都道府県・市区町村）を、場所のアンカー座標から Geocoder で引いて `places` に書き足す
 * （→ adr/0029）。Google の施設情報の住所は使わない。
 *
 * 値を揃えるため、端末の言語に依らず日本語で引く。住所・座標はログに出さない（件数だけ）。
 */
@Singleton
class PlaceRegionResolver @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val placeRegionDao: PlaceRegionDao,
) {

  private val logger = Logger("PlaceRegionResolver")

  /**
   * 地域をまだ引いていない場所を、無くなるまで引く。引けなかった場所があれば最後に [IOException] を投げる
   * （呼び出し側が再試行する。引き終えた分は残る）。
   */
  suspend fun resolveAll() {
    if (!Geocoder.isPresent()) {
      logger.w("Geocoder is not available; place regions stay unknown")
      return
    }
    val geocoder = Geocoder(context, Locale.JAPAN)
    resolveWith { latitude, longitude -> geocoder.lookup(latitude, longitude)?.let(::regionOf) ?: PlaceRegion.UNKNOWN }
  }

  /**
   * [lookup] で地域を引いて書き足す。1 件が引けなくても、その後ろの場所は続けて引く（1 件のせいで残りが
   * 止まらないように）。ただし続けて [MAX_FAILURES_IN_A_ROW] 件失敗したら、通信断などとみてそこでやめる。
   * 引けなかった場所は地域を書かずに残し、次の再試行で引き直す。
   */
  internal suspend fun resolveWith(lookup: suspend (latitude: Double, longitude: Double) -> PlaceRegion) {
    // 引けなかった場所を同じ回で読み直さないよう、id の続きから読む（新しい場所は id が大きいので拾える）。
    var afterId = 0L
    var failed = 0
    var failedInARow = 0
    while (true) {
      val batch = placeRegionDao.getUnresolved(afterId, BATCH_SIZE)
      if (batch.isEmpty()) break
      var resolved = 0
      for (row in batch) {
        afterId = row.placeId
        val region = try {
          lookup(row.latitude, row.longitude)
        } catch (e: IOException) {
          failed++
          if (++failedInARow >= MAX_FAILURES_IN_A_ROW) throw e
          null
        }
        if (region != null) {
          failedInARow = 0
          resolved++
          // 引いている間に場所が消されていたら、更新は 0 行で終わるだけ。
          placeRegionDao.updateRegion(row.placeId, region.prefecture, region.city, Date())
        }
        // 短い間に問い合わせを詰め込まない（Geocoder は回数の上限が公開されていない）。
        delay(LOOKUP_INTERVAL_MS)
      }
      logger.i("Resolved regions for $resolved of ${batch.size} places")
    }
    if (failed > 0) throw IOException("Geocoder failed for $failed places")
  }

  /** 座標の住所を 1 件引く。見つからなければ null（海の上など）。通信の失敗は [IOException]。 */
  private suspend fun Geocoder.lookup(latitude: Double, longitude: Double): Address? = suspendCancellableCoroutine { cont ->
    getFromLocation(
      latitude,
      longitude,
      1,
      object : Geocoder.GeocodeListener {
        override fun onGeocode(addresses: MutableList<Address>) {
          if (cont.isActive) cont.resume(addresses.firstOrNull())
        }

        override fun onError(errorMessage: String?) {
          if (cont.isActive) cont.resumeWithException(IOException("Geocoder failed"))
        }
      },
    )
  }

  internal companion object {
    const val BATCH_SIZE = 20
    const val LOOKUP_INTERVAL_MS = 200L
    const val MAX_FAILURES_IN_A_ROW = 3
  }
}

/** 住所から地域を取り出す。都道府県は `adminArea`。 */
private fun regionOf(address: Address): PlaceRegion = PlaceRegion(address.adminArea?.takeIf { it.isNotBlank() }, cityOf(address))

/**
 * 住所から市区町村を取り出す。東京の区・市・町・村は `locality` に入る。郡の町村などで `locality` が無ければ
 * `subAdminArea` を使う。どちらも無ければ null。
 */
internal fun cityOf(address: Address): String? = cityOf(address.locality, address.subAdminArea)

internal fun cityOf(locality: String?, subAdminArea: String?): String? = locality?.takeIf { it.isNotBlank() } ?: subAdminArea?.takeIf { it.isNotBlank() }
