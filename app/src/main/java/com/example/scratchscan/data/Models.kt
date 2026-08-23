package com.example.scratchscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "games")
data class ScratchOffGame(
    @PrimaryKey val gameNumber: Int,
    val name: String,
    val price: Int,
    val status: String,
    val topPrize: String? = null,
    val topPrizesRemaining: Int? = null,
    val chancesToWin: String? = null,
    val releaseDate: String? = null,
    val probability: Double? = null,
    val allPrizesRemaining: Long? = null,
    val artworkUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Serializable
@Entity(tableName = "prize_tiers")
data class PrizeTier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameNumber: Int,
    val amount: Long,
    val initialCount: Int,
    val remainingCount: Int
)

data class GameWithPrizes(
    val game: ScratchOffGame,
    val prizes: List<PrizeTier>
)

data class CalculatedStats(
    val gameNumber: Int,
    val topPrizeAmount: Long,
    val topPrizesRemaining: Int,
    val topPrizeRemainingRatio: Double, // top prizes remaining / initial
    val prizeToTicketRatio: Double, // total remaining prizes / estimated remaining tickets
    val estimatedTicketsRemaining: Int,
    val score: Double // Internal recommendation score
)
