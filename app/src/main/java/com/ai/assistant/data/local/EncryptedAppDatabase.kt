package com.ai.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory
@Database(entities = [UserMemoryEntity::class], version = 1, exportSchema = false)
abstract class EncryptedAppDatabase : RoomDatabase() {

    abstract fun userMemoryDao(): UserMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: EncryptedAppDatabase? = null

        fun getDatabase(context: Context, encryptionPassphrase: ByteArray): EncryptedAppDatabase {
            return INSTANCE ?: synchronized(this) {
                // SQLCipher OpenHelper Factory Passphrase ke saath
                val factory = SupportFactory(encryptionPassphrase)

                 val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EncryptedAppDatabase::class.java,
                    "secure_ai_memory.db"
                )
                .openHelperFactory(factory) // Encryption Layer Attached
                .fallbackToDestructiveMigration()
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
