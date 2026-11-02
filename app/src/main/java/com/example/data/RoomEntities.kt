package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileName: String,
    val localFilePath: String,
    val format: String, // DocFormat.name
    val fileSizeBytes: Long,
    val totalUnits: Int = 1, // pages, slides, sheets, or lines
    val currentUnit: Int = 0,
    val progressPercent: Int = 0,
    val isFavorite: Boolean = false,
    val isPinnedOffline: Boolean = true,
    val tags: String = "",
    val summarySnippet: String = "",
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val docFormat: DocFormat
        get() = runCatching { DocFormat.valueOf(format) }.getOrDefault(DocFormat.TXT)
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val documentTitle: String,
    val format: String,
    val unitIndex: Int, // page number, slide number, sheet index, or line
    val label: String,
    val excerpt: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val documentTitle: String,
    val unitIndex: Int,
    val noteText: String,
    val highlightColorHex: String = "#F59E0B",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface OmniDocDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE localFilePath = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :docId ORDER BY unitIndex ASC")
    fun getBookmarksForDocument(docId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    // Annotations
    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun getAllAnnotations(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :docId ORDER BY unitIndex ASC, createdAt DESC")
    fun getAnnotationsForDocument(docId: Long): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)
}

@Database(
    entities = [DocumentEntity::class, BookmarkEntity::class, AnnotationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OmniDocDatabase : RoomDatabase() {
    abstract fun omniDocDao(): OmniDocDao

    companion object {
        @Volatile
        private var INSTANCE: OmniDocDatabase? = null

        fun getInstance(context: Context): OmniDocDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniDocDatabase::class.java,
                    "omnidoc_offline.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
