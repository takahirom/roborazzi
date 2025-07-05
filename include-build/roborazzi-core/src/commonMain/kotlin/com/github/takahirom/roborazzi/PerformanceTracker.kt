package com.github.takahirom.roborazzi

import kotlin.time.TimeSource

@InternalRoborazziApi
object PerformanceTracker {
    // Static storage for all measurements across test cases
    private val allMeasurements = mutableMapOf<String, MutableList<Long>>()
    private val startTimes = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()
    private val timeSource = TimeSource.Monotonic

    fun startMeasurement(operation: String) {
      if (!roborazziDebugPerformance()) return
        startTimes[operation] = timeSource.markNow()
    }

    fun endMeasurement(operation: String) {
        if (!roborazziDebugPerformance()) return
        val startTime = startTimes[operation] ?: return
        val duration = startTime.elapsedNow()
        
        // Store measurement in the list for statistical analysis
        allMeasurements.getOrPut(operation) { mutableListOf() }.add(duration.inWholeMilliseconds)
        startTimes.remove(operation)
        
        // Print measurements when all operations are complete
        if (startTimes.isEmpty()) {
            printMeasurements()
        }
    }

    fun getMeasurement(operation: String): List<Long> {
        return allMeasurements[operation] ?: emptyList()
    }

    fun getAllMeasurements(): Map<String, List<Long>> {
        return allMeasurements.toMap()
    }

    fun printMeasurements() {
        if (!roborazziDebugPerformance()) return
        if (allMeasurements.isEmpty()) return
        
        println("Roborazzi Performance Statistics:")
        allMeasurements.forEach { (operation, times) ->
            val count = times.size
            val average = times.average()
            val median = times.sorted().let { sorted ->
                if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                } else {
                    sorted[sorted.size / 2].toDouble()
                }
            }
            
            println("  $operation:")
            println("    count: $count")
            println("    average: ${(average * 10).toInt() / 10.0}ms")
            println("    median: ${(median * 10).toInt() / 10.0}ms")
            println("    all values: $times")
        }
    }

    fun printStatistics() {
        printMeasurements()
    }

    fun clearMeasurements() {
        allMeasurements.clear()
        startTimes.clear()
    }
}

@InternalRoborazziApi
inline fun <T> measurePerformance(operation: String, block: () -> T): T {
    if (!roborazziDebugPerformance()) {
        return block()
    }
    
    PerformanceTracker.startMeasurement(operation)
    try {
        return block()
    } finally {
        PerformanceTracker.endMeasurement(operation)
    }
}