package com.example.scratchscan.data.repository

import android.content.Context
import com.example.scratchscan.data.CalculatedStats
import com.example.scratchscan.data.GameWithPrizes
import com.example.scratchscan.data.ScratchOffGame
import com.example.scratchscan.data.local.GameDao
import com.example.scratchscan.data.remote.MarylandLotteryDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

class ScratchOffRepository(
    private val context: Context,
    private val gameDao: GameDao,
    private val remoteDataSource: MarylandLotteryDataSource = MarylandLotteryDataSource(),
) {
    val allGames: Flow<List<ScratchOffGame>> = gameDao.getAllGames()

    suspend fun loadGamesFromAssets() = withContext(Dispatchers.IO) {
        val games = mutableListOf<ScratchOffGame>()
        try {
            val inputStream = context.assets.open("scratch_offs.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine() // Skip header
            
            var line: String? = reader.readLine()
            while (line != null) {
                val parts = parseCsvLine(line)
                if (parts.size >= 9) {
                    val price = parts[0].replace("$", "").toIntOrNull() ?: 0
                    val rawName = parts[1].replace("<br>", " ").replace("®", "").replace("™", "").trim()
                    val topPrize = parts[2]
                    val topPrizesRemaining = parts[3].toIntOrNull()
                    val chancesToWin = parts[4]
                    val gameStarted = parts[5]
                    val probability = parts[7].toDoubleOrNull()
                    val allPrizesRemaining = parts[8].toLongOrNull()
                    
                    val slug = rawName.lowercase()
                        .replace(" ", "-")
                        .replace(Regex("[^a-z0-9-]"), "")
                        .trim('-')
                    
                    val artworkUrl = "https://www.mdlottery.com/wp-content/uploads/$slug.png"
                    
                    games.add(
                        ScratchOffGame(
                            gameNumber = line.hashCode(),
                            name = rawName,
                            price = price,
                            status = "Active",
                            topPrize = topPrize,
                            topPrizesRemaining = topPrizesRemaining,
                            chancesToWin = chancesToWin,
                            releaseDate = gameStarted,
                            probability = probability,
                            allPrizesRemaining = allPrizesRemaining,
                            artworkUrl = artworkUrl,
                            lastUpdated = System.currentTimeMillis(),
                        )
                    )
                }
                line = reader.readLine()
            }
            
            if (games.isNotEmpty()) {
                val currentGames = gameDao.getAllGames().first()
                val favoritesMap = currentGames.associateBy({ it.gameNumber }) { it.isFavorite }
                val updatedGames = games.map { it.copy(isFavorite = favoritesMap[it.gameNumber] ?: false) }
                gameDao.insertGames(updatedGames)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false
        for (c in line) {
            if (c == '\"') inQuotes = !inQuotes
            else if ((c == ',') && !inQuotes) {
                result.add(cur.toString()); cur = StringBuilder()
            } else cur.append(c)
        }
        result.add(cur.toString())
        return result
    }

    suspend fun toggleFavorite(gameNumber: Int, isFavorite: Boolean) {
        gameDao.updateFavoriteStatus(gameNumber, isFavorite)
    }

    suspend fun refreshGames() {
        var attempt = 0
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            try {
                val remoteGames = remoteDataSource.fetchAllGames()
                if (remoteGames.isNotEmpty()) {
                    val currentGames = gameDao.getAllGames().first()
                    val favoritesMap = currentGames.associateBy({ it.gameNumber }) { it.isFavorite }
                    val updatedGames = remoteGames.map { it.copy(isFavorite = favoritesMap[it.gameNumber] ?: false) }
                    gameDao.insertGames(updatedGames)
                    return
                } else throw IOException("Empty data received")
            } catch (_: Exception) {
                attempt++
                if (attempt >= maxAttempts) break
                delay(2.0.pow(attempt.toDouble()).toLong().milliseconds * 1000)
            }
        }
    }

    suspend fun getGameWithPrizes(gameNumber: Int): Flow<GameWithPrizes?> {
        val prizesFlow = gameDao.getPrizesForGame(gameNumber)
        val currentPrizes = prizesFlow.first()
        if (currentPrizes.isEmpty()) {
            val remotePrizes = remoteDataSource.fetchGameDetails(gameNumber)
            if (remotePrizes.isNotEmpty()) gameDao.insertPrizes(remotePrizes)
        }
        return combine(allGames, prizesFlow) { games, prizes ->
            games.find { it.gameNumber == gameNumber }?.let { GameWithPrizes(it, prizes) }
        }
    }

    fun calculateStats(gameWithPrizes: GameWithPrizes): CalculatedStats {
        val prizes = gameWithPrizes.prizes
        val topPrize = prizes.maxByOrNull { it.amount }
        val totalInitialPrizes = prizes.sumOf { it.initialCount }
        val totalRemainingPrizes = prizes.sumOf { it.remainingCount }
        val estimatedTicketsRemaining = if (totalInitialPrizes > 0) {
            ((totalRemainingPrizes.toDouble() / totalInitialPrizes) * 1000000).toInt()
        } else 0
        return CalculatedStats(
            gameNumber = gameWithPrizes.game.gameNumber,
            topPrizeAmount = topPrize?.amount ?: 0L,
            topPrizesRemaining = topPrize?.remainingCount ?: 0,
            topPrizeRemainingRatio = if ((topPrize != null) && (topPrize.initialCount > 0)) {
                topPrize.remainingCount.toDouble() / topPrize.initialCount
            } else 0.0,
            prizeToTicketRatio = if (estimatedTicketsRemaining > 0) totalRemainingPrizes.toDouble() / estimatedTicketsRemaining else 0.0,
            estimatedTicketsRemaining = estimatedTicketsRemaining,
            score = (topPrize?.remainingCount ?: 0).toDouble() * 10.0,
        )
    }
}
