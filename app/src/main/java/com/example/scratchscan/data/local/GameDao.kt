package com.example.scratchscan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.scratchscan.data.ScratchOffGame
import com.example.scratchscan.data.PrizeTier
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY price DESC, name ASC")
    fun getAllGames(): Flow<List<ScratchOffGame>>

    @Query("SELECT * FROM games WHERE gameNumber = :gameNumber")
    suspend fun getGameByNumber(gameNumber: Int): ScratchOffGame?

    @Query("SELECT * FROM prize_tiers WHERE gameNumber = :gameNumber")
    fun getPrizesForGame(gameNumber: Int): Flow<List<PrizeTier>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<ScratchOffGame>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrizes(prizes: List<PrizeTier>)

    @Query("DELETE FROM prize_tiers WHERE gameNumber = :gameNumber")
    suspend fun deletePrizesForGame(gameNumber: Int)

    @Query("UPDATE games SET isFavorite = :isFavorite WHERE gameNumber = :gameNumber")
    suspend fun updateFavoriteStatus(gameNumber: Int, isFavorite: Boolean)

    @Transaction
    suspend fun updateGameWithPrizes(game: ScratchOffGame, prizes: List<PrizeTier>) {
        insertGames(listOf(game))
        deletePrizesForGame(game.gameNumber)
        insertPrizes(prizes)
    }
}
