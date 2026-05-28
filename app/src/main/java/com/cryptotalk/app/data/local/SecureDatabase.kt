package com.cryptotalk.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cryptotalk.app.data.local.dao.MessageDao
import com.cryptotalk.app.data.local.entity.MessageEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * SecureDatabase is an encrypted local database (using SQLCipher) where all your messages are stored on the phone.
 * Because it's encrypted with a password (passphrase), even if someone roots the phone and steals the database file, 
 * they cannot read the messages inside it without the correct password.
 */
@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class SecureDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "cryptotalk_secure.db"

        @Volatile
        private var INSTANCE: SecureDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): SecureDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
