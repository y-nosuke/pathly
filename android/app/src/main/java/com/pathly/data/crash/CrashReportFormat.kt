package com.pathly.data.crash

/**
 * 異常終了の記録の中身の作り方と、ファイルへの書き方・読み方。
 *
 * ファイルは「`キー: 値` の見出し → `---` → 中身」のテキスト。端末の中だけで読み書きし、
 * 版をまたいで読めればよいので、JSON ライブラリに頼らない単純な形にしている（JVM のユニットテストでも読める）。
 */
object CrashReportFormat {

  /** 1 件あたりの中身の上限（文字数）。ANR のスレッドの様子は大きいので切り詰める。 */
  const val MAX_DETAIL_CHARS = 64 * 1024

  private const val MAGIC = "pathly-crash-report: 1"
  private const val SEPARATOR = "---"
  private const val TRUNCATED_MARK = "\n…（64KB を超えたので以下を省きました）"

  /**
   * 座標の形の数（小数点以下が 4 桁以上）を伏せる。例外のメッセージに座標が紛れ込んでも、
   * Issue に貼るときに場所が分からないようにするための最後の守り（→ docs/specs/crash-reports.md）。
   */
  private val COORDINATE_LIKE = Regex("""-?\d{1,3}\.\d{4,}""")

  fun sanitize(text: String): String = COORDINATE_LIKE.replace(text, "<数値>")

  fun truncate(text: String): String = if (text.length <= MAX_DETAIL_CHARS) text else text.take(MAX_DETAIL_CHARS) + TRUNCATED_MARK

  /** 書く前の整え（伏せてから切り詰める）。 */
  fun prepareDetail(text: String): String = truncate(sanitize(text))

  /** 落ちる瞬間にアプリ自身が書く記録。 */
  fun fromThrowable(
    threadName: String,
    throwable: Throwable,
    occurredAtMillis: Long,
    pid: Int,
    versionName: String,
    versionCode: Long,
    device: String,
  ): CrashReport = CrashReport(
    occurredAtMillis = occurredAtMillis,
    kind = CrashKind.CRASH,
    versionName = versionName,
    versionCode = versionCode,
    pid = pid,
    device = device,
    detail = prepareDetail("スレッド: $threadName\n${throwable.stackTraceToString()}"),
  )

  /** OS の記録から作る記録。異常でなければ null。 */
  fun fromExitRecord(record: ExitRecord, device: String): CrashReport? {
    val kind = ExitClassifier.kindOf(record.reason, record.importance) ?: return null
    val (versionName, versionCode) = decodeVersion(record.processStateSummary)
    val detail = buildString {
      appendLine("理由: ${ExitClassifier.reasonName(record.reason)}（${record.reason}）")
      appendLine("そのときの状態: ${ExitClassifier.importanceName(record.importance)}（${record.importance}）")
      appendLine("終了コード: ${record.status}")
      record.description?.takeIf { it.isNotBlank() }?.let { appendLine("説明: $it") }
      appendLine("メモリ: PSS ${record.pssKb / 1024} MB / RSS ${record.rssKb / 1024} MB")
      record.trace?.takeIf { it.isNotBlank() }?.let {
        appendLine()
        appendLine("--- スレッドの様子 ---")
        append(it)
      }
    }.trimEnd()
    return CrashReport(
      occurredAtMillis = record.timestampMillis,
      kind = kind,
      versionName = versionName,
      versionCode = versionCode,
      pid = record.pid,
      osReason = record.reason,
      device = device,
      detail = prepareDetail(detail),
    )
  }

  /** 起動時に OS へ預ける版（`setProcessStateSummary` は 128 バイトまで）。 */
  fun encodeVersion(versionName: String, versionCode: Long): String {
    // 版の名前が長すぎても上限を超えないよう、名前の側を削る（版の番号は残す）。
    var name = versionName
    while ("$name\n$versionCode".toByteArray(Charsets.UTF_8).size > MAX_STATE_SUMMARY_BYTES) name = name.dropLast(1)
    return "$name\n$versionCode"
  }

  fun decodeVersion(summary: String?): Pair<String?, Long?> {
    val lines = summary?.lines() ?: return null to null
    return lines.getOrNull(0)?.takeIf { it.isNotBlank() } to lines.getOrNull(1)?.toLongOrNull()
  }

  private const val MAX_STATE_SUMMARY_BYTES = 128

  fun encode(report: CrashReport): String = buildString {
    appendLine(MAGIC)
    appendLine("occurredAt: ${report.occurredAtMillis}")
    appendLine("kind: ${report.kind.name}")
    report.versionName?.let { appendLine("versionName: ${it.oneLine()}") }
    report.versionCode?.let { appendLine("versionCode: $it") }
    appendLine("pid: ${report.pid}")
    report.osReason?.let { appendLine("osReason: $it") }
    appendLine("device: ${report.device.oneLine()}")
    appendLine(SEPARATOR)
    append(report.detail)
  }

  /** 読めないファイル（壊れている・知らない形）は null。 */
  fun decode(text: String): CrashReport? {
    val lines = text.lines()
    if (lines.firstOrNull() != MAGIC) return null
    val separatorAt = lines.indexOf(SEPARATOR).takeIf { it > 0 } ?: return null
    val headers = lines.subList(1, separatorAt).mapNotNull { line ->
      val i = line.indexOf(": ")
      if (i <= 0) null else line.substring(0, i) to line.substring(i + 2)
    }.toMap()
    return CrashReport(
      occurredAtMillis = headers["occurredAt"]?.toLongOrNull() ?: return null,
      kind = headers["kind"]?.let { name -> CrashKind.entries.firstOrNull { it.name == name } } ?: return null,
      versionName = headers["versionName"],
      versionCode = headers["versionCode"]?.toLongOrNull(),
      pid = headers["pid"]?.toIntOrNull() ?: 0,
      osReason = headers["osReason"]?.toIntOrNull(),
      device = headers["device"].orEmpty(),
      detail = lines.drop(separatorAt + 1).joinToString("\n"),
    )
  }

  private fun String.oneLine(): String = replace('\n', ' ').replace('\r', ' ')
}
