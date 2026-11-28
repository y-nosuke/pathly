package com.pathly.data.repository

import org.json.JSONArray
import org.json.JSONObject

/**
 * 計画の下書きの中身（`plan_drafts.json`）。行は場所を id で指すだけで、名前・座標は読むときに場所から引く。
 * 並びは配列の順（日ごとの順番は読むときに振り直す）。
 */
internal object PlanDraftCodec {

  data class Content(
    val title: String,
    val note: String?,
    val startDate: Long?,
    val dayCount: Int,
    val items: List<Item>,
    /** 日付の変更で「結び付きを外す」を選んだ経路（保存したときに外す → docs/specs/plans.md の「結び付いた経路」）。 */
    val unlinkTrackIds: List<Long> = emptyList(),
  )

  data class Item(
    val dayIndex: Int,
    val placeId: Long?,
    val title: String?,
    val note: String?,
    val plannedTime: Int?,
    val stayMinutes: Int?,
  )

  private const val VERSION = 1

  fun encode(content: Content): String = JSONObject().apply {
    put("v", VERSION)
    put("title", content.title)
    putOpt("note", content.note)
    putOpt("startDate", content.startDate)
    put("dayCount", content.dayCount)
    put(
      "items",
      JSONArray().apply {
        content.items.forEach { item ->
          put(
            JSONObject().apply {
              put("day", item.dayIndex)
              putOpt("placeId", item.placeId)
              putOpt("title", item.title)
              putOpt("note", item.note)
              putOpt("time", item.plannedTime)
              putOpt("stay", item.stayMinutes)
            },
          )
        }
      },
    )
    if (content.unlinkTrackIds.isNotEmpty()) put("unlink", JSONArray().apply { content.unlinkTrackIds.forEach { put(it) } })
  }.toString()

  /** 読めなければ null（壊れた・知らない形の下書きは無いものとして扱う）。 */
  fun decode(json: String): Content? = runCatching {
    val o = JSONObject(json)
    val items = o.optJSONArray("items") ?: JSONArray()
    val unlink = o.optJSONArray("unlink") ?: JSONArray()
    Content(
      title = o.getString("title"),
      note = o.optStringOrNull("note"),
      startDate = if (o.isNull("startDate")) null else o.getLong("startDate"),
      dayCount = o.getInt("dayCount").coerceAtLeast(1),
      items = (0 until items.length()).map { i ->
        val it = items.getJSONObject(i)
        Item(
          dayIndex = it.getInt("day"),
          placeId = if (it.isNull("placeId")) null else it.getLong("placeId"),
          title = it.optStringOrNull("title"),
          note = it.optStringOrNull("note"),
          plannedTime = if (it.isNull("time")) null else it.getInt("time"),
          stayMinutes = if (it.isNull("stay")) null else it.getInt("stay"),
        )
      },
      unlinkTrackIds = (0 until unlink.length()).map { unlink.getLong(it) },
    )
  }.getOrNull()

  private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
