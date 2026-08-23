package com.example.scratchscan

import com.example.scratchscan.data.repository.GameRepository
import com.example.scratchscan.data.remote.MarylandLotteryDataSource
import com.example.scratchscan.data.local.GameDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class CrashTest {
    
    @Test
    fun `test repository handles data source crash gracefully`() = runBlocking {
        val mockDao = mockk<GameDao>(relaxed = true)
        val mockDataSource = mockk<MarylandLotteryDataSource>()
        
        // Simulate a network crash/exception
        coEvery { mockDataSource.fetchAllGames() } throws RuntimeException("Network Error")
        
        val repository = GameRepository(mockDao, mockDataSource)
        
        try {
            repository.refreshGames()
            // Should not crash the app
        } catch (e: Exception) {
            fail("Repository should catch exceptions from data source to prevent app crashes")
        }
    }
}
