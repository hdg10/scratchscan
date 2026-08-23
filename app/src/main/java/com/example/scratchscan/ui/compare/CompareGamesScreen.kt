package com.example.scratchscan.ui.compare

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scratchscan.R
import com.example.scratchscan.data.CalculatedStats
import java.util.Locale

@Composable
fun CompareGamesScreen(
    viewModel: CompareViewModel = viewModel(
        factory = CompareViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val rankedGames by viewModel.rankedGames.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Statistical Recommendations",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rankedGames) { index, stats ->
                RecommendationCard(stats = stats, rank = index + 1)
            }
        }
    }
}

@Composable
fun RecommendationCard(stats: CalculatedStats, rank: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = if (rank == 1) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                 else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "#$rank Game #${stats.gameNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Score: ${String.format(Locale.US, "%.1f", stats.score)}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            Text(
                text = "Top Prize: $${stats.topPrizeAmount} (${stats.topPrizesRemaining} left)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Text(
                text = stringResource(R.string.buy_recommendation, "Statistically favorable"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
