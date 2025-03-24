package com.nervesparks.iris.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

data class ResourceMetrics(
    val cpuUsage: Float = 0f,
    val memoryUsageMB: Float = 0f,
    val totalMemoryMB: Float = 0f,
    val batteryLevel: Int = 0,
    val batteryTemperature: Float = 0f,
    val batteryCurrentDrawMa: Int = 0
)

class ResourceMonitor(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pid = Process.myPid()

    // For CPU usage calculation
    private var lastCpuTime = 0L
    private var lastAppCpuTime = 0L

    fun collectMetrics(): ResourceMetrics {
        val cpuUsage = getCpuUsage()
        val memoryUsage = getMemoryUsage()
        val totalMemory = getTotalMemory()
        val batteryLevel = getBatteryLevel()
        val batteryTemp = getBatteryTemperature()
        val batteryCurrent = getBatteryCurrentDraw()

        Log.d("ResourceMonitor", "Metrics - CPU: $cpuUsage%, Mem: $memoryUsage MB, " +
                "Battery: $batteryLevel%, Temp: $batteryTemp°C, Current: $batteryCurrent mA")

        return ResourceMetrics(
            cpuUsage = cpuUsage,
            memoryUsageMB = memoryUsage,
            totalMemoryMB = totalMemory,
            batteryLevel = batteryLevel,
            batteryTemperature = batteryTemp,
            batteryCurrentDrawMa = batteryCurrent
        )
    }

    private fun getCpuUsage(): Float {
        try {
            // Get app CPU time
            val process = Runtime.getRuntime().exec("top -n 1")
            val reader = process.inputStream.bufferedReader()
            var line: String?
            var cpuUsage = 0f

            // Find our process in top output
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains(context.packageName) == true) {
                    // Extract CPU percentage - format varies by device
                    val parts = line!!.trim().split("\\s+".toRegex())
                    // Look for a percentage value in the line
                    for (part in parts) {
                        if (part.endsWith("%")) {
                            val percentage = part.replace("%", "").toFloatOrNull()
                            if (percentage != null) {
                                cpuUsage = percentage
                                break
                            }
                        }
                    }
                    break
                }
            }

            reader.close()
            process.destroy()

            return cpuUsage
        } catch (e: Exception) {
            Log.e("ResourceMonitor", "Error getting CPU usage: ${e.message}")
            return 0f
        }
    }

    private fun getNativeMemoryUsage(): Float {
        try {
            // Read from /proc/self/status to get VmRSS
            val file = File("/proc/self/status")
            if (file.exists()) {
                val lines = file.readLines()
                for (line in lines) {
                    if (line.startsWith("VmRSS:")) {
                        // Extract the value in kB
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            return parts[1].toFloat() / 1024f // Convert kB to MB
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ResourceMonitor", "Error getting native memory: ${e.message}")
        }
        return 0f
    }

    private fun getJavaMemoryUsage(): Float {
        try {
            // Get memory info for our process
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            // Get process-specific memory info
            val pids = intArrayOf(pid)
            val procMemInfo = activityManager.getProcessMemoryInfo(pids)

            if (procMemInfo.isNotEmpty()) {
                // Total PSS in KB (includes proportional memory shared with other processes)
                val totalPss = procMemInfo[0].totalPss
                return totalPss / 1024f  // Convert to MB
            }

            // Fallback to runtime memory if process info fails
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024).toFloat()
            return usedMemory
        } catch (e: Exception) {
            Log.e("ResourceMonitor", "Error getting memory usage: ${e.message}")

            // Last resort fallback
            val runtime = Runtime.getRuntime()
            return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024).toFloat()
        }
    }

    private fun getMemoryUsage(): Float {
        val javaMemory = getJavaMemoryUsage() // Rename current method
        val nativeMemory = getNativeMemoryUsage()

        // Log both for debugging
        Log.d("ResourceMonitor", "Java memory: $javaMemory MB, Native memory: $nativeMemory MB")

        // Return the larger of the two or their sum depending on your needs
        return maxOf(javaMemory, nativeMemory)
    }

    private fun getTotalMemory(): Float {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem / (1024 * 1024)).toFloat()
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).roundToInt()
        } else {
            -1
        }
    }

    private fun getBatteryTemperature(): Float {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        // Temperature is reported in tenths of a degree Celsius
        return temp / 10.0f
    }

    private fun getBatteryCurrentDraw(): Int {
        // Try multiple methods to get battery current

        // Method 1: BatteryManager API (Android 5.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

                // Convert to positive mA (some devices report in μA, some in mA)
                val currentMa = if (Math.abs(currentNow) > 1000) {
                    Math.abs(currentNow / 1000)
                } else {
                    Math.abs(currentNow)
                }

                if (currentMa > 0) {
                    return currentMa
                }
            } catch (e: Exception) {
                Log.e("ResourceMonitor", "Error with BatteryManager API: ${e.message}")
            }
        }

        // Method 2: Try reading from various system files
        val possiblePaths = arrayOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/batt_current_now",
            "/sys/class/power_supply/Battery/current_now",
            "/sys/devices/platform/battery/power_supply/battery/current_now"
        )

        for (path in possiblePaths) {
            try {
                if (File(path).exists()) {
                    val current = RandomAccessFile(path, "r").use {
                        it.readLine().toLong()
                    }

                    // Convert to positive mA (some devices report in μA)
                    val currentMa = if (Math.abs(current) > 1000) {
                        Math.abs(current / 1000).toInt()
                    } else {
                        Math.abs(current).toInt()
                    }

                    if (currentMa > 0) {
                        return currentMa
                    }
                }
            } catch (e: Exception) {
                Log.e("ResourceMonitor", "Error reading from $path: ${e.message}")
            }
        }

        // Method 3: Battery Intent (limited info)
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            // Estimate current based on voltage (very rough approximation)
            if (voltage > 0 && level > 0 && scale > 0) {
                // Rough estimate based on typical smartphone power consumption
                return 200 + (voltage / 1000) * 50  // Very rough estimate
            }
        } catch (e: Exception) {
            Log.e("ResourceMonitor", "Error with battery intent: ${e.message}")
        }

        // Default fallback value
        return 300  // Return a reasonable default value in mA
    }
}