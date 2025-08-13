# Android Room データベース設計

## 概要

Android Room（SQLite）を使用したローカルデータベース設計。
GPS軌跡の記録・保存、一覧表示、地図表示機能を提供する。

## データベース構成

### データベース情報

- **データベース名**: PathlyDatabase
- **バージョン**: 1
- **Room バージョン**: 2.6.1

## テーブル設計

### 1. tracks テーブル（GPS記録セッション）

GPS記録の1回分のセッション情報を管理する。

| カラム名       | 型      | 制約               | 説明                               |
| -------------- | ------- | ------------------ | ---------------------------------- |
| id             | TEXT    | PRIMARY KEY        | UUID（同期時の重複回避）           |
| start_time     | INTEGER | NOT NULL           | 記録開始日時（Unix timestamp）     |
| end_time       | INTEGER | NULL               | 記録終了日時（Unix timestamp）     |
| is_active      | INTEGER | NOT NULL DEFAULT 1 | 記録中フラグ（1: 記録中, 0: 終了） |
| total_distance | REAL    | NULL               | 総移動距離（メートル）             |
| total_duration | INTEGER | NULL               | 総記録時間（秒）                   |
| created_at     | INTEGER | NOT NULL           | 作成日時（Unix timestamp）         |
| updated_at     | INTEGER | NOT NULL           | 更新日時（Unix timestamp）         |

#### インデックス

```sql
CREATE INDEX idx_tracks_start_time ON tracks(start_time);
CREATE INDEX idx_tracks_is_active ON tracks(is_active);
```

### 2. gps_points テーブル（GPS座標）

個別のGPS座標情報を管理する。

| カラム名   | 型      | 制約                  | 説明                          |
| ---------- | ------- | --------------------- | ----------------------------- |
| id         | TEXT    | PRIMARY KEY           | UUID                          |
| track_id   | TEXT    | NOT NULL, FOREIGN KEY | 所属するtrack.id              |
| latitude   | REAL    | NOT NULL              | 緯度                          |
| longitude  | REAL    | NOT NULL              | 経度                          |
| accuracy   | REAL    | NULL                  | 精度（メートル）              |
| altitude   | REAL    | NULL                  | 高度（メートル）              |
| speed      | REAL    | NULL                  | 速度（m/s）                   |
| bearing    | REAL    | NULL                  | 方角（度）                    |
| timestamp  | INTEGER | NOT NULL              | GPS取得日時（Unix timestamp） |
| created_at | INTEGER | NOT NULL              | 作成日時（Unix timestamp）    |

#### インデックス

```sql
CREATE INDEX idx_gps_points_track_id ON gps_points(track_id);
CREATE INDEX idx_gps_points_timestamp ON gps_points(timestamp);
```

#### 外部キー制約

```sql
FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
```

## Room Entity 設計

### Track Entity

```kotlin
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["start_time"]),
        Index(value = ["is_active"])
    ]
)
data class Track(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "total_distance")
    val totalDistance: Float? = null,
    
    @ColumnInfo(name = "total_duration")
    val totalDuration: Long? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

### GpsPoint Entity

```kotlin
@Entity(
    tableName = "gps_points",
    foreignKeys = [
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["timestamp"])
    ]
)
data class GpsPoint(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "track_id")
    val trackId: String,
    
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val timestamp: Long,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

## DAO インターフェース設計

### TrackDao

```kotlin
@Dao
interface TrackDao {
    @Insert
    suspend fun insertTrack(track: Track): Long
    
    @Update
    suspend fun updateTrack(track: Track)
    
    @Delete
    suspend fun deleteTrack(track: Track)
    
    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): Track?
    
    @Query("SELECT * FROM tracks ORDER BY start_time DESC")
    fun getAllTracks(): Flow<List<Track>>
    
    @Query("SELECT * FROM tracks WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveTrack(): Track?
    
    @Query("UPDATE tracks SET end_time = :endTime, is_active = 0, total_distance = :distance, total_duration = :duration, updated_at = :updatedAt WHERE id = :trackId")
    suspend fun finishTrack(trackId: String, endTime: Long, distance: Float, duration: Long, updatedAt: Long)
    
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int
}
```

### GpsPointDao

```kotlin
@Dao
interface GpsPointDao {
    @Insert
    suspend fun insertGpsPoint(gpsPoint: GpsPoint): Long
    
    @Insert
    suspend fun insertGpsPoints(gpsPoints: List<GpsPoint>)
    
    @Delete
    suspend fun deleteGpsPoint(gpsPoint: GpsPoint)
    
    @Query("SELECT * FROM gps_points WHERE track_id = :trackId ORDER BY timestamp ASC")
    suspend fun getGpsPointsByTrackId(trackId: String): List<GpsPoint>
    
    @Query("SELECT * FROM gps_points WHERE track_id = :trackId ORDER BY timestamp ASC")
    fun getGpsPointsByTrackIdFlow(trackId: String): Flow<List<GpsPoint>>
    
    @Query("DELETE FROM gps_points WHERE track_id = :trackId")
    suspend fun deleteGpsPointsByTrackId(trackId: String)
    
    @Query("SELECT COUNT(*) FROM gps_points WHERE track_id = :trackId")
    suspend fun getGpsPointCount(trackId: String): Int
    
    @Query("SELECT * FROM gps_points WHERE track_id = :trackId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestGpsPoint(trackId: String): GpsPoint?
}
```

## Database クラス設計

```kotlin
@Database(
    entities = [Track::class, GpsPoint::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class PathlyDatabase : RoomDatabase() {
    
    abstract fun trackDao(): TrackDao
    abstract fun gpsPointDao(): GpsPointDao
    
    companion object {
        @Volatile
        private var INSTANCE: PathlyDatabase? = null
        
        fun getDatabase(context: Context): PathlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PathlyDatabase::class.java,
                    "pathly_database"
                )
                .addTypeConverter(DateConverter())
                .fallbackToDestructiveMigration() // 開発時のみ
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

## Type Converter

```kotlin
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
```

## データ操作例

### GPS記録の開始

```kotlin
suspend fun startTracking(): String {
    val track = Track(
        startTime = System.currentTimeMillis()
    )
    trackDao.insertTrack(track)
    return track.id
}
```

### GPS座標の保存

```kotlin
suspend fun saveGpsPoint(trackId: String, location: Location) {
    val gpsPoint = GpsPoint(
        trackId = trackId,
        latitude = location.latitude,
        longitude = location.longitude,
        accuracy = location.accuracy,
        altitude = location.altitude,
        speed = location.speed,
        bearing = location.bearing,
        timestamp = location.time
    )
    gpsPointDao.insertGpsPoint(gpsPoint)
}
```

### GPS記録の終了

```kotlin
suspend fun finishTracking(trackId: String) {
    val gpsPoints = gpsPointDao.getGpsPointsByTrackId(trackId)
    val distance = calculateTotalDistance(gpsPoints)
    val startTime = trackDao.getTrackById(trackId)?.startTime ?: 0
    val duration = System.currentTimeMillis() - startTime
    
    trackDao.finishTrack(
        trackId = trackId,
        endTime = System.currentTimeMillis(),
        distance = distance,
        duration = duration,
        updatedAt = System.currentTimeMillis()
    )
}
```

## パフォーマンス考慮事項

### バッチInsert

GPS座標の保存はバッチ処理で効率化：

```kotlin
// 30秒間隔で複数ポイントをまとめて保存
suspend fun saveGpsPointsBatch(gpsPoints: List<GpsPoint>) {
    gpsPointDao.insertGpsPoints(gpsPoints)
}
```

### インデックス活用

- `tracks.start_time`: 日付順ソート用
- `tracks.is_active`: アクティブなトラック検索用
- `gps_points.track_id`: 軌跡データ取得用
- `gps_points.timestamp`: 時系列ソート用

### メモリ効率化

- Flow使用でリアクティブなデータ更新
- 大量データはページング対応（将来）

## セキュリティ

### データ暗号化

```kotlin
// Room Database の暗号化（将来実装）
Room.databaseBuilder(context, PathlyDatabase::class.java, "pathly_database")
    .openHelperFactory(SupportFactory(SqlCipherUtils.encrypt(context, passphrase)))
    .build()
```

### 位置情報保護

- 精度レベルに応じたデータ保存
- 不要な高精度データの自動削除
- ユーザーによるデータ削除機能
