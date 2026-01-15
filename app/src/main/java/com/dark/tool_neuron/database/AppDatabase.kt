package com.dark.tool_neuron.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dark.tool_neuron.database.dao.ModelConfigDao
import com.dark.tool_neuron.database.dao.ModelDao
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.converters.Converters
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig

@Database(
    entities = [Model::class, ModelConfig::class, InstalledRag::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun ragDao(): RagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_rags (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        source_type TEXT NOT NULL,
                        file_path TEXT,
                        node_count INTEGER NOT NULL DEFAULT 0,
                        embedding_dimension INTEGER NOT NULL DEFAULT 0,
                        embedding_model TEXT NOT NULL DEFAULT '',
                        domain TEXT NOT NULL DEFAULT 'general',
                        language TEXT NOT NULL DEFAULT 'en',
                        version TEXT NOT NULL DEFAULT '1.0',
                        tags TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'INSTALLED',
                        is_enabled INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_loaded_at INTEGER,
                        size_bytes INTEGER NOT NULL DEFAULT 0,
                        metadata_json TEXT
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "llm_models_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}