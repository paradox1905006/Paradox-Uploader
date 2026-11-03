package com.dparadox.paradoxuploader.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.UploadRequest
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "uploads", indices = [Index("status")])
data class UploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identifier: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val createdAt: Long,
    val status: UploadStatus,
    val directUrl: String? = null,
    val lastCheckedAt: Long? = null,
    val errorMessage: String? = null,
    val sourceUri: String? = null,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val collection: String = "opensource",
    val mediatype: String = "data",
    val processingStartedAt: Long? = null,
    val folderId: Long? = null,
    val licenseUrl: String? = null
)

class Converters {
    @TypeConverter
    fun fromStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}

@Dao
interface UploadDao {
    @Query("SELECT * FROM uploads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UploadEntity>>

    @Query("SELECT * FROM uploads ORDER BY createdAt DESC")
    suspend fun getAll(): List<UploadEntity>

    @Query("SELECT * FROM uploads WHERE id = :id")
    suspend fun get(id: Long): UploadEntity?

    @Query("SELECT * FROM uploads WHERE status IN (:statuses)")
    suspend fun getByStatus(statuses: List<UploadStatus>): List<UploadEntity>

    @Insert
    suspend fun insert(entity: UploadEntity): Long

    @Update
    suspend fun update(entity: UploadEntity)

    @Query("DELETE FROM uploads WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

@Entity(tableName = "folders", indices = [Index(value = ["identifier"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val identifier: String,
    val createdAt: Long,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val licenseUrl: String? = null
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE identifier = :identifier LIMIT 1")
    suspend fun findByIdentifier(identifier: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): FolderEntity?

    @Insert
    suspend fun insert(entity: FolderEntity): Long

    @Update
    suspend fun update(entity: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE uploads SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachUploads(folderId: Long)
}

@Database(entities = [UploadEntity::class, FolderEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadDao(): UploadDao
    abstract fun folderDao(): FolderDao
}

/** v1 -> v2: folders (one archive.org item per folder) and the folder an upload belongs to. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE uploads ADD COLUMN folderId INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_folders_identifier ON folders (identifier)")
    }
}

/** v2 -> v3: default metadata per folder (title, description, tags, license) and the license per upload. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN title TEXT")
        db.execSQL("ALTER TABLE folders ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE folders ADD COLUMN tags TEXT")
        db.execSQL("ALTER TABLE folders ADD COLUMN licenseUrl TEXT")
        db.execSQL("ALTER TABLE uploads ADD COLUMN licenseUrl TEXT")
    }
}

fun UploadEntity.toDomain() = UploadItem(
    id = id,
    identifier = identifier,
    fileName = fileName,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    createdAt = createdAt,
    status = status,
    directUrl = directUrl,
    lastCheckedAt = lastCheckedAt,
    errorMessage = errorMessage,
    folderId = folderId,
    title = title,
    licenseUrl = licenseUrl
)

fun UploadEntity.toRequest() = UploadRequest(
    identifier = identifier,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    title = title,
    description = description,
    tags = tags,
    collection = collection,
    mediatype = mediatype,
    licenseUrl = licenseUrl
)
