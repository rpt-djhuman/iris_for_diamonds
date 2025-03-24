package com.nervesparks.iris.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import android.app.DownloadManager
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File

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

data class ResourceGraphData(
    val cpuHistory: List<Float> = emptyList(),
    val memoryHistory: List<Float> = emptyList(),
    val temperatureHistory: List<Float> = emptyList(),
    val batteryDrawHistory: List<Int> = emptyList()
)

private fun safeFormatDouble(value: Double): String {
    return try {
        "%.2f".format(value)
    } catch (e: Exception) {
        "0.00" // Fallback value
    }
}

@Composable
fun BenchMarkScreen(viewModel: MainViewModel, dm: DownloadManager, extFileDir: File?) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // State for model selection and benchmark process
    var selectedModelForBenchmark by remember { mutableStateOf<String?>(null) }
    var isBenchmarkStarted by remember { mutableStateOf(false) }
    var benchmarkStage by remember { mutableStateOf("Not started") }

    // Benchmark statistics
    var benchmarkStats by remember { mutableStateOf(BenchmarkStats()) }
    var modelLoadTime by remember { mutableStateOf(0L) }
    var modelLoadMemoryImpact by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        viewModel.initResourceMonitor(context)
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
            colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                deviceInfo.lines().forEach { line ->
                    Text(line, modifier = Modifier.padding(vertical = 2.dp), color = Color.White)
                }
            }
        }

        // Model Selection Section (only shown before benchmark starts)
        if (!isBenchmarkStarted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Select a model to benchmark",
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(viewModel.allModels) { model ->
                            val modelName = model["name"].toString()
                            val modelExists = extFileDir?.let { File(it, modelName).exists() } ?: false

                            if (modelExists) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedModelForBenchmark = modelName
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedModelForBenchmark == modelName)
                                            Color(0xFF2563EB) else Color(0xff1e293b)
                                    )
                                ) {
                                    Text(
                                        text = modelName,
                                        modifier = Modifier.padding(16.dp),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    androidx.compose.material3.Button(
                        onClick = {
                            if (selectedModelForBenchmark != null) {
                                isBenchmarkStarted = true
                                benchmarkStage = "Preparing"
                                Log.d("Benchmark", benchmarkStage)

                                // Start the comprehensive benchmark process
                                scope.launch {
                                    try {
                                        // Initialize resource monitor
                                        viewModel.initResourceMonitor(context)

                                        // Unload current model
                                        benchmarkStage = "Unloading current model"
                                        Log.d("Benchmark", benchmarkStage)
                                        try {
                                            viewModel.unload()
                                            viewModel.loadedModelName.value = ""
                                            delay(1000) // Give time for unloading to complete
                                        } catch (e: Exception) {
                                            Log.e("Benchmark", "Error unloading model: ${e.message}")
                                        }

                                        // Force garbage collection for more accurate baseline
                                        System.gc()
                                        delay(500)  // Give GC time to complete

                                        // Measure baseline memory
                                        benchmarkStage = "Measuring baseline metrics"
                                        Log.d("Benchmark", benchmarkStage)
                                        val baselineMetrics = viewModel.resourceMonitor.collectMetrics()
                                        viewModel.baselineMemoryUsage = baselineMetrics.memoryUsageMB
                                        val baselineBatteryLevel = baselineMetrics.batteryLevel
                                        val baselineBatteryTemp = baselineMetrics.batteryTemperature

                                        // Load the selected model and measure time
                                        benchmarkStage = "Loading model"
                                        Log.d("Benchmark", benchmarkStage)
                                        val loadStartTime = System.currentTimeMillis()

                                        // Start collecting metrics during loading
                                        viewModel.resourceMetricsList.clear()
                                        val metricsJob = scope.launch {
                                            while (benchmarkStage == "Loading model") {
                                                val metrics = viewModel.resourceMonitor.collectMetrics()
                                                viewModel.resourceMetricsList.add(metrics)
                                                delay(200) // Sample every 200ms during loading
                                            }
                                        }

                                        // Load the model
                                        val modelPath = extFileDir?.let { File(it, selectedModelForBenchmark!!).path }
                                        if (modelPath != null) {
                                            viewModel.load(modelPath, viewModel.user_thread.toInt())

                                            // Calculate load time
                                            val loadEndTime = System.currentTimeMillis()
                                            modelLoadTime = loadEndTime - loadStartTime

                                            // Measure memory after loading
                                            val postLoadMetrics = viewModel.resourceMonitor.collectMetrics()
                                            modelLoadMemoryImpact = postLoadMetrics.memoryUsageMB - viewModel.baselineMemoryUsage

                                            // Calculate peak memory during loading
                                            viewModel.peakMemoryUsage = viewModel.resourceMetricsList
                                                .maxOfOrNull { it.memoryUsageMB } ?: postLoadMetrics.memoryUsageMB

                                            // Stop collecting loading metrics
                                            metricsJob.cancel()

                                            // Run the inference benchmark
                                            benchmarkStage = "Running inference benchmark"
                                            Log.d("Benchmark", benchmarkStage)
                                            viewModel.resourceMetricsList.clear() // Clear for inference metrics
                                            viewModel.tokensList.clear()
                                            viewModel.startComprehensiveBenchmark(context, modelPath)


                                            benchmarkStage = "Completed"
                                            Log.d("Benchmark", benchmarkStage)
                                        } else {
                                            benchmarkStage = "Error: Model path is null"
                                        }
                                    } catch (e: Exception) {
                                        benchmarkStage = "Error: ${e.message}"
                                        Log.e("Benchmark", "Benchmark error: ${e.message}")
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please select a model first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedModelForBenchmark != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Start Benchmark")
                    }
                }
            }
        } else {
            // Benchmark in progress or results
            if (benchmarkStage != "Completed" && !viewModel.isBenchmarkingComplete) {
                // Progress indicator
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Benchmark in progress: $benchmarkStage",
                            color = Color.White
                        )
                    }
                }
            } else {
                // Model loading metrics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Model Loading Metrics",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Load time: $modelLoadTime ms", color = Color.White)
                        Text("Memory impact: ${modelLoadMemoryImpact.toInt()} MB", color = Color.White)
                    }
                }

                // Inference metrics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Inference Metrics",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tokens per second: ${safeFormatDouble(viewModel.tokensPerSecondsFinal)}", color = Color.White)
                        Text("CPU usage: ${safeFormatDouble(viewModel.averageResourceMetrics.cpuUsage.toDouble())}%", color = Color.White)
                        Text("Peak memory: ${viewModel.peakMemoryUsage.toInt()} MB", color = Color.White)
                    }
                }

                // Battery and temperature metrics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Power Metrics",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Battery level: ${viewModel.averageResourceMetrics.batteryLevel}%", color = Color.White)
                        Text("Battery temperature: ${safeFormatDouble(viewModel.averageResourceMetrics.batteryTemperature.toDouble())}°C", color = Color.White)
                        Text("Battery current: ${viewModel.averageResourceMetrics.batteryCurrentDrawMa} mA", color = Color.White)
                    }
                }

                // Tokens Per Second Graph
                if (benchmarkStats.tokensPerSecondHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Tokens Per Second Over Time",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
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

                // Resource metrics graphs
                if (viewModel.resourceMetricsList.isNotEmpty()) {
                    ResourceMetricsCard(viewModel)
                }

                // Run another benchmark button
                androidx.compose.material3.Button(
                    onClick = {
                        isBenchmarkStarted = false
                        selectedModelForBenchmark = null
                        benchmarkStats = BenchmarkStats()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    )
                ) {
                    Text("Run Another Benchmark")
                }
            }
        }
    }
}

// Keep the existing graph components
@Composable
fun TokensPerSecondGraph(
    dataPoints: List<Double>,
    modifier: Modifier = Modifier
) {
    // Existing implementation
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

@Composable
fun ResourceMetricsCard(viewModel: MainViewModel) {
    // Existing implementation
    val averageMetrics = viewModel.averageResourceMetrics
    val resourceHistory = remember {
        mutableStateOf(
            ResourceGraphData(
                cpuHistory = viewModel.resourceMetricsList.map { it.cpuUsage },
                memoryHistory = viewModel.resourceMetricsList.map { it.memoryUsageMB },
                temperatureHistory = viewModel.resourceMetricsList.map { it.batteryTemperature },
                batteryDrawHistory = viewModel.resourceMetricsList.map { it.batteryCurrentDrawMa }
            )
        )
    }

    // Update resource history when metrics change
    LaunchedEffect(viewModel.resourceMetricsList.size) {
        resourceHistory.value = ResourceGraphData(
            cpuHistory = viewModel.resourceMetricsList.map { it.cpuUsage },
            memoryHistory = viewModel.resourceMetricsList.map { it.memoryUsageMB },
            temperatureHistory = viewModel.resourceMetricsList.map { it.batteryTemperature },
            batteryDrawHistory = viewModel.resourceMetricsList.map { it.batteryCurrentDrawMa }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xff0f172a))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Resource Usage (During Benchmark)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // CPU Usage
            Text(
                "CPU Usage: ${safeFormatDouble(averageMetrics.cpuUsage.toDouble())}% " +
                        "(${if (averageMetrics.cpuUsage < 1f) "Very low - may need permissions" else "Normal"})",
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White
            )

            // Memory Usage with percentage
            val memPercent = if (averageMetrics.totalMemoryMB > 0) {
                (averageMetrics.memoryUsageMB / averageMetrics.totalMemoryMB * 100).toDouble()
            } else 0.0

            Text(
                "Memory: ${safeFormatDouble(averageMetrics.memoryUsageMB.toDouble())} MB / " +
                        "${safeFormatDouble(averageMetrics.totalMemoryMB.toDouble())} MB " +
                        "(${safeFormatDouble(memPercent)}%)",
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White
            )

            // Battery Temperature
            Text(
                "Battery Temperature: ${safeFormatDouble(averageMetrics.batteryTemperature.toDouble())}°C",
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White
            )

            // Battery Current Draw
            Text(
                "Battery Current: ${averageMetrics.batteryCurrentDrawMa} mA",
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White
            )

            // CPU Usage Graph
            if (resourceHistory.value.cpuHistory.isNotEmpty()) {
                Text(
                    "CPU Usage Over Time",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                ResourceGraph(
                    dataPoints = resourceHistory.value.cpuHistory.map { it.toDouble() },
                    color = Color(0xFF4CAF50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // Memory Usage Graph
            if (resourceHistory.value.memoryHistory.isNotEmpty()) {
                Text(
                    "Memory Usage Over Time (MB)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                ResourceGraph(
                    dataPoints = resourceHistory.value.memoryHistory.map { it.toDouble() },
                    color = Color(0xFF2196F3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // Temperature Graph
            if (resourceHistory.value.temperatureHistory.isNotEmpty()) {
                Text(
                    "Temperature Over Time (°C)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                ResourceGraph(
                    dataPoints = resourceHistory.value.temperatureHistory.map { it.toDouble() },
                    color = Color(0xFFF44336),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // Battery Draw Graph
            if (resourceHistory.value.batteryDrawHistory.isNotEmpty()) {
                Text(
                    "Battery Current Draw Over Time (mA)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                ResourceGraph(
                    dataPoints = resourceHistory.value.batteryDrawHistory.map { it.toDouble() },
                    color = Color(0xFFFF9800),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

@Composable
fun ResourceGraph(
    dataPoints: List<Double>,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Existing implementation
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

        // Draw the actual graph
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
                            color = color,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y.toFloat())
                        )
                    }

                    // Draw line
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else if (dataPoints.size == 1) {
                    // If we only have one point, just draw it
                    val x = width / 2
                    val y = height / 2
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            } catch (e: Exception) {
                Log.e("ResourceGraph", "Error drawing graph: ${e.message}", e)
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