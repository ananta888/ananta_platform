package com.sovworks.eds.android.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sovworks.eds.android.security.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [ChatMessageEntity::class, FileTransferEntity::class, ChatGroupEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun fileTransferDao(): FileTransferDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = SecurityUtils.getDatabasePassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ananta_chat.db"
                )
                .openHelperFactory(factory)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
