package com.example.scratchscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.scratchscan.data.ScratchOffGame
import com.example.scratchscan.data.PrizeTier
import com.example.scratchscan.data.ChatMessage
import com.example.scratchscan.data.ChatSession

@Database(entities = [ScratchOffGame::class, PrizeTier::class, ChatSession::class, ChatMessage::class], version = 5, exportSchema = false)
abstract class ScratchOffDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ScratchOffDatabase? = null

        fun getDatabase(context: Context): ScratchOffDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScratchOffDatabase::class.java,
                    "scratch_off_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
