package com.dark.plugins.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dark.plugins.model.InstalledPlugin
import com.dark.plugins.model.PluginTypeConverters


@Database(entities = [InstalledPlugin::class], version = 1, exportSchema = true)
@TypeConverters(PluginTypeConverters::class)
abstract class DatabaseProvider : RoomDatabase() {
    abstract fun getInstalledPluginDao(): PluginLocalDBDao

    companion object {
        @Volatile
        private var INSTANCE: DatabaseProvider? = null

        fun getDatabase(context: Context): DatabaseProvider {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext, DatabaseProvider::class.java, "local_plugin_db"
                ).build()
            }
        }
    }
}