// PR to enhance BenchMarkScreen with statistics and graph visualization

package com.nervesparks.iris.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nervesparks.iris.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.min
import android.util.Log

data class BenchmarkState(
    val isRunning: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val results: List<String> = emptyList(),
    val error: String? = null
)

data class BenchmarkStats(
    val average: Double = 0.0,
    val onePctLow: Double = 0.0,
    val onePctHigh: Double = 0.0,
    val tokensPerSecondHistory: List<Double> = emptyList()
)

private fun safeFormatDouble(value: Double): String {
    return try {
        "%.2f".format(value)
    } catch (e: Exception) {
        "0.00" // Fallback value
    }
}

@Composable
fun BenchMarkScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var state by remember { mutableStateOf(BenchmarkState()) }
    var benchmarkStats by remember { mutableStateOf(BenchmarkStats()) }

    val average by remember(benchmarkStats) { mutableStateOf(benchmarkStats.average) }
    val onePctLow by remember(benchmarkStats) { mutableStateOf(benchmarkStats.onePctLow) }
    val onePctHigh by remember(benchmarkStats) { mutableStateOf(benchmarkStats.onePctHigh) }
    val hasStats by remember(benchmarkStats) {
        mutableStateOf(benchmarkStats.tokensPerSecondHistory.isNotEmpty())
    }

    // Track tokens per second history and calculate statistics
    LaunchedEffect(viewModel.tokensPerSecondsFinal, viewModel.isBenchmarkingComplete) {
        try {
            if (viewModel.tokensPerSecondsFinal > 0) {
                // Add current tokens per second to history
                val updatedHistory = benchmarkStats.tokensPerSecondHistory + viewModel.tokensPerSecondsFinal

                // Calculate statistics on every update
                var updatedStats = benchmarkStats.copy(tokensPerSecondHistory = updatedHistory)

                if (updatedHistory.isNotEmpty()) {
                    val average = updatedHistory.sum() / updatedHistory.size

                    // Calculate 1% lows and highs
                    val sortedHistory = updatedHistory.sorted()
                    val lowCount = (updatedHistory.size * 0.01).toInt().coerceAtLeast(1)
                    val highCount = (updatedHistory.size * 0.01).toInt().coerceAtLeast(1)

                    val onePctLow = sortedHistory.take(lowCount).average()
                    val onePctHigh = sortedHistory.takeLast(highCount).average()

                    updatedStats = updatedStats.copy(
                        average = average,
                        onePctLow = onePctLow,
                        onePctHigh = onePctHigh
                    )
                }

                benchmarkStats = updatedStats
            }
        } catch (e: Exception) {
            Log.e("BenchMarkScreen", "Error calculating benchmark stats: ${e.message}", e)
        }
    }

    val deviceInfo = buildDeviceInfo(viewModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            "Benchmark Information",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Device Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                deviceInfo.lines().forEach { line ->
                    Text(line, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        val context = LocalContext.current

        // Benchmark Button
        androidx.compose.material3.Button(
            modifier = Modifier.padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB).copy(alpha = 1.0f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 3.dp
            ),
            onClick = {
                if(viewModel.loadedModelName.value == ""){
                    Toast.makeText(context, "Load A Model First", Toast.LENGTH_SHORT).show()
                }
                else{
                    // Reset benchmark stats when starting a new benchmark
                    benchmarkStats = BenchmarkStats()
                    state = state.copy(showConfirmDialog = true)
                }
            },
            enabled = !state.isRunning,
        ) {
            Text(if (state.isRunning) "Benchmarking..." else "Start Benchmark", color = Color.White)
        }

        // Progress Indicator
        if (state.isRunning) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    "Benchmarking in progress...",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Token Per Second Speed Display
        Text(
            text = if (viewModel.tokensPerSecondsFinal > 0) {
                "Tokens per second: %.2f".format(viewModel.tokensPerSecondsFinal)
            } else {
                "Calculating tokens per second..."
            },
            style = MaterialTheme.typography.body1,
            color = Color.Green,
            modifier = Modifier.padding(16.dp)
        )

        // Benchmark Statistics Card
        if (hasStats) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Benchmark Statistics",
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Use the safe formatting function
                    Text(
                        "Average: ${safeFormatDouble(average)} tokens/sec",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text(
                        "1% Lows: ${safeFormatDouble(onePctLow)} tokens/sec",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text(
                        "1% Highs: ${safeFormatDouble(onePctHigh)} tokens/sec",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Tokens Per Second Graph
        if (benchmarkStats.tokensPerSecondHistory.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(200.dp),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Tokens Per Second Over Time",
                        style = MaterialTheme.typography.subtitle1,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        // Draw the graph
                        TokensPerSecondGraph(
                            dataPoints = benchmarkStats.tokensPerSecondHistory,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Results Section
        if (state.results.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Benchmark Results",
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    state.results.forEach { result ->
                        Text(
                            result,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Error Display
        state.error?.let { error ->
            Text(
                error,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    // Confirmation Dialog
    if (state.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(showConfirmDialog = false)
            },
            title = { Text("Benchmarking Notice") },
            text = { Text("This process will 30 seconds to 1 minute. Do you want to continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state = state.copy(
                            showConfirmDialog = false,
                            isRunning = true,
                            results = emptyList(),
                            error = null
                        )
                        scope.launch {
                            try {
                                viewModel.myCustomBenchmark()

                                // Update tokens per second after benchmarking
                                state = state.copy(
                                    results = viewModel.tokensList.toList() // Fetch tokens collected
                                )
                            } catch (e: Exception) {
                                state = state.copy(
                                    error = "Error: ${e.message}"
                                )
                            } finally {
                                state = state.copy(isRunning = false)
                            }
                        }
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state = state.copy(showConfirmDialog = false)
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TokensPerSecondGraph(
    dataPoints: List<Double>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    // Safe calculation of min/max values
    val maxValue = dataPoints.maxOrNull() ?: 0.0
    val minValue = dataPoints.minOrNull() ?: 0.0

    Box(modifier = modifier) {
        // Add labels for min/max values
        Text(
            text = "%.1f".format(maxValue),
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 4.dp)
        )

        Text(
            text = "%.1f".format(minValue),
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp)
        )

        // Draw the actual graph with additional safety checks
        Canvas(modifier = Modifier.fillMaxSize()) {
            try {
                val width = size.width
                val height = size.height
                val padding = 24.dp.toPx()

                val range = (maxValue - minValue).coerceAtLeast(1.0)

                // Draw axes
                drawLine(
                    color = Color.Gray,
                    start = Offset(padding, height - padding),
                    end = Offset(width - padding, height - padding),
                    strokeWidth = 2f
                )

                drawLine(
                    color = Color.Gray,
                    start = Offset(padding, padding),
                    end = Offset(padding, height - padding),
                    strokeWidth = 2f
                )

                // Only draw points if we have enough data
                if (dataPoints.size > 1) {
                    val pointCount = dataPoints.size
                    val pointDistance = (width - 2 * padding) / (pointCount - 1).coerceAtLeast(1)

                    // Create path for the line
                    val path = Path()
                    var firstPoint = true

                    dataPoints.forEachIndexed { index, value ->
                        val x = padding + index * pointDistance
                        val y = height - padding - ((value - minValue) / range * (height - 2 * padding)).coerceIn(0.0, (height - 2 * padding).toDouble())

                        if (firstPoint) {
                            path.moveTo(x, y.toFloat())
                            firstPoint = false
                        } else {
                            path.lineTo(x, y.toFloat())
                        }

                        // Draw point
                        drawCircle(
                            color = Color(0xFF2563EB),
                            radius = 3.dp.toPx(),
                            center = Offset(x, y.toFloat())
                        )
                    }

                    // Draw line
                    drawPath(
                        path = path,
                        color = Color(0xFF2563EB),
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else if (dataPoints.size == 1) {
                    // If we only have one point, just draw it
                    val x = width / 2
                    val y = height / 2
                    drawCircle(
                        color = Color(0xFF2563EB),
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            } catch (e: Exception) {
                // Log the error but don't crash
                Log.e("TokensPerSecondGraph", "Error drawing graph: ${e.message}", e)
            }
        }
    }
}

private fun buildDeviceInfo(viewModel: MainViewModel): String {
    return buildString {
        append("Device: ${Build.MODEL}\n")
        append("Android: ${Build.VERSION.RELEASE}\n")
        append("Processor: ${Build.HARDWARE}\n")
        append("Available Threads: ${Runtime.getRuntime().availableProcessors()}\n")
        append("Current Model: ${viewModel.loadedModelName.value ?: "N/A"}\n")
        append("User Threads: ${viewModel.user_thread}")
    }
}