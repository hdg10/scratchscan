package com.example.scratchscan.data.remote

import android.util.Log
import com.example.scratchscan.data.PrizeTier
import com.example.scratchscan.data.ScratchOffGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class MarylandLotteryDataSource {
    private val tag = "ScratchOffDebug"
    private val baseUrl = "https://www.mdlottery.com/games/scratch-offs/"

    suspend fun fetchAllGames(): List<ScratchOffGame> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(baseUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .referrer("https://www.google.com/")
                .ignoreHttpErrors(true)
                .timeout(15000)
                .get()
            
            // 1. Try Card/Grid Layout
            val cards = doc.select(".scratch-off-game, .game-card, .scratch-off-card, .game-list-item, div[class*='game-']")
            if (cards.isNotEmpty()) {
                val parsedGames = cards.mapNotNull { card ->
                    try {
                        val name = card.select(".game-name, .title, h3, .name").text().trim()
                        if (name.isEmpty()) return@mapNotNull null
                        
                        val priceText = card.select(".game-price, .price, .badge-price").text().replace("$", "").trim()
                        val price = priceText.toIntOrNull() ?: 0
                        
                        val topPrize = card.select(".top-prize, .prize, .badge-prize").text().trim()
                        
                        val img = card.select("img").firstOrNull()
                        val artworkUrl = getBestImageUrl(img)
                        
                        val gameLink = card.select("a").firstOrNull { it.attr("href").contains("scratch-offs") }?.attr("href") ?: ""
                        
                        var gameNumber = extractGameNumberFromUrl(gameLink)
                        if (gameNumber == null) {
                            val cardText = card.text()
                            gameNumber = Regex("#(\\d+)").find(cardText)?.groupValues?.get(1)?.toIntOrNull()
                        }
                        
                        val game = ScratchOffGame(
                            gameNumber = gameNumber ?: name.hashCode(),
                            name = name,
                            price = price,
                            status = "Active",
                            topPrize = topPrize,
                            artworkUrl = artworkUrl,
                            lastUpdated = System.currentTimeMillis()
                        )
                        Log.d(tag, "Extracted Game: ${game.name} -> Image URL: ${game.artworkUrl}")
                        game
                    } catch (_: Exception) {
                        null
                    }
                }
                if (parsedGames.isNotEmpty()) return@withContext parsedGames
            }

            // 2. Try Table Layout
            val gamesTable = doc.select("table.wpdtSimpleTable").firstOrNull() ?: doc.select("table").firstOrNull { 
                it.text().contains("Game Name", ignoreCase = true) 
            }
            
            if (gamesTable != null) {
                val rows = gamesTable.select("tbody tr")
                val tableGames = rows.mapNotNull { row ->
                    val cols = row.select("td")
                    if (cols.size < 2) return@mapNotNull null
                    
                    try {
                        val name = cols[0].text().trim()
                        val priceText = cols[1].text().replace("$", "").trim()
                        val price = priceText.toIntOrNull() ?: 0
                        val topPrize = if (cols.size >= 3) cols[2].text().trim() else null
                        
                        val img = row.select("img").firstOrNull()
                        val artworkUrl = getBestImageUrl(img)
                        
                        val gameLink = row.select("a").firstOrNull()?.attr("href") ?: ""
                        val gameNumber = extractGameNumberFromUrl(gameLink) ?: name.hashCode()
                        
                        val game = ScratchOffGame(
                            gameNumber = gameNumber,
                            name = name,
                            price = price,
                            status = "Active",
                            topPrize = topPrize,
                            artworkUrl = artworkUrl,
                            lastUpdated = System.currentTimeMillis()
                        )
                        Log.d(tag, "Extracted Game: ${game.name} -> Image URL: ${game.artworkUrl}")
                        game
                    } catch (_: Exception) {
                        null
                    }
                }
                if (tableGames.isNotEmpty()) return@withContext tableGames
            }
            
            // 3. Try Script extraction
            val scriptGames = extractFromScripts(doc)
            if (scriptGames.isNotEmpty()) return@withContext scriptGames

            emptyList<ScratchOffGame>()
        } catch (e: Exception) {
            Log.e(tag, "Scraping failed: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    private fun extractFromScripts(doc: org.jsoup.nodes.Document): List<ScratchOffGame> {
        // MD Lottery sometimes uses wpDataTables or similar which stores data in scripts
        val scripts = doc.select("script")
        for (script in scripts) {
            val content = script.html()
            if (content.contains("wpDataTable") || content.contains("scratch_offs")) {
                // Simplified regex to find image URLs and names in JSON-like structures
                // This is a broad fallback; real production code might use a JSON parser
                val imgRegex = Regex("\"(https://[^\"]+/wp-content/uploads/[^\"]+\\.(?:png|jpg|jpeg))\"")
                val nameRegex = Regex("\"game_name\":\"([^\"]+)\"")
                
                val images = imgRegex.findAll(content).map { it.groupValues[1] }.toList()
                val names = nameRegex.findAll(content).map { it.groupValues[1] }.toList()
                
                if (names.isNotEmpty() && images.isNotEmpty()) {
                    return names.zip(images).mapIndexed { index, pair ->
                        ScratchOffGame(
                            gameNumber = 1000 + index,
                            name = pair.first,
                            price = 0, // Hard to extract from raw regex
                            status = "Active",
                            artworkUrl = pair.second,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
        return emptyList()
    }

    private fun getBestImageUrl(img: org.jsoup.nodes.Element?): String? {
        if (img == null) return null
        
        val attrs = listOf("data-src", "data-lazy-src", "data-original", "srcset", "src")
        var url: String? = null
        
        for (attr in attrs) {
            val value = img.attr(attr)
            if (value.isNotEmpty()) {
                val candidate = if (attr == "srcset") {
                    value.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                } else {
                    value
                }
                
                if (candidate != null && 
                    candidate.isNotBlank() &&
                    !candidate.contains("placeholder") && 
                    !candidate.contains("blank.gif") &&
                    !candidate.startsWith("data:image") &&
                    !candidate.endsWith(".svg") &&
                    candidate.contains("/wp-content/uploads/")
                ) {
                    url = candidate
                    break
                }
            }
        }
        
        return url?.let { formatUrl(it) }
    }

    private fun formatUrl(url: String): String {
        var absoluteUrl = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://www.mdlottery.com$url"
            !url.startsWith("http") -> "https://www.mdlottery.com/$url"
            else -> url
        }
        
        // Strip WordPress resize parameters to get the high-resolution original image
        // Matches patterns like -300x535.png, -150x150.jpg, -scaled.jpg, etc.
        absoluteUrl = absoluteUrl.replace(Regex("-\\d+x\\d+(?=\\.\\w{3,4}(\\?|$))"), "")
        absoluteUrl = absoluteUrl.replace("-scaled", "")
        
        // Clean up any remaining query parameters that might trigger resizing
        if (absoluteUrl.contains("?")) {
            absoluteUrl = absoluteUrl.substringBefore("?")
        }

        return absoluteUrl.replace(" ", "%20")
            .replace("$", "%24")
            .replace("&", "%26")
    }

    suspend fun fetchGameDetails(gameNumber: Int): List<PrizeTier> = withContext(Dispatchers.IO) {
        val url = "$baseUrl$gameNumber/"
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer("https://www.mdlottery.com/")
                .timeout(10000)
                .get()
            
            val prizeTable = doc.select("table").firstOrNull { 
                it.text().contains("Prize Amount", ignoreCase = true) || it.text().contains("Remaining", ignoreCase = true)
            } ?: return@withContext emptyList<PrizeTier>()
            
            val rows = prizeTable.select("tbody tr")
            rows.mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size < 3) return@mapNotNull null
                
                try {
                    val amountText = cols[0].text().replace(Regex("[^0-9]"), "")
                    val amount = amountText.toLongOrNull() ?: 0L
                    val initialCountText = cols[1].text().replace(Regex("[^0-9]"), "")
                    val initialCount = initialCountText.toIntOrNull() ?: 0
                    val remainingCountText = cols[2].text().replace(Regex("[^0-9]"), "")
                    val remainingCount = remainingCountText.toIntOrNull() ?: 0
                    
                    PrizeTier(
                        gameNumber = gameNumber,
                        amount = amount,
                        initialCount = initialCount,
                        remainingCount = remainingCount
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractGameNumberFromUrl(url: String): Int? {
        val regex = Regex(".*/(\\d+)-.*")
        return regex.find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}
