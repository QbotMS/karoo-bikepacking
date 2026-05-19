package com.bikepacking.karoo.fitexport

import java.io.File

/**
 * FIT → QLab replay log exporter.
 *
 * Usage:
 *   java -jar fit-export.jar --fit <file.fit>              # single file
 *   java -jar fit-export.jar --dir <directory>              # batch directory
 *   java -jar fit-export.jar --fit <file.fit> --summary     # with summary
 *   java -jar fit-export.jar --help
 *
 * Output:
 *   <ride_name>.qbot_replay_log.json   — full tick-by-tick data
 *   <ride_name>.qbot_replay_summary.json — optional summary
 *
 * Each tick contains:
 *   - raw ReplayTick fields (sensor data)
 *   - pre-computed hudState (HUD decisions from CliRideEngine)
 *
 * Missing data → null (never guessed).
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
        printUsage()
        return
    }

    val fitPath = extractArg(args, "--fit")
    val dirPath = extractArg(args, "--dir")
    val outputPath = extractArg(args, "--out") ?: "."
    val includeSummary = args.contains("--summary")

    if (fitPath == null && dirPath == null) {
        System.err.println("ERROR: Provide --fit <file> or --dir <directory>")
        printUsage()
        return
    }

    if (fitPath != null && dirPath != null) {
        System.err.println("ERROR: Provide either --fit or --dir, not both.")
        return
    }

    val outputDir = File(outputPath).apply { mkdirs() }

    if (fitPath != null) {
        val file = File(fitPath)
        if (!file.exists() || !file.name.lowercase().endsWith(".fit")) {
            System.err.println("ERROR: File not found or not a .fit file: $fitPath")
            return
        }
        processSingleFile(file, outputDir, includeSummary)
        return
    }

    if (dirPath != null) {
        val dir = File(dirPath)
        if (!dir.isDirectory) {
            System.err.println("ERROR: Not a directory: $dirPath")
            return
        }
        val fitFiles = dir.listFiles { f -> f.name.lowercase().endsWith(".fit") }
            ?.sortedBy { it.name }
            ?: emptyArray()

        if (fitFiles.isEmpty()) {
            System.err.println("No .fit files found in $dirPath")
            return
        }

        System.out.println("Processing ${fitFiles.size} .fit files from $dirPath...")
        var successCount = 0
        var failCount = 0
        for (f in fitFiles) {
            try {
                processSingleFile(f, outputDir, includeSummary)
                successCount++
            } catch (e: Exception) {
                System.err.println("ERROR processing ${f.name}: ${e.message}")
                failCount++
            }
        }
        System.out.println("\nBatch complete: $successCount succeeded, $failCount failed")
    }
}

private fun processSingleFile(
    fitFile: File,
    outputDir: File,
    includeSummary: Boolean,
) {
    val baseName = fitFile.nameWithoutExtension
        .replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        .take(100)

    System.out.println("\n=== Processing: ${fitFile.name} ===")

    // Step 1: Parse FIT
    val decoder = FitDecoder()
    val result = decoder.decode(fitFile)

    if (result.ticks.isEmpty()) {
        System.err.println("  WARNING: No record messages found in ${fitFile.name}")
        return
    }

    System.out.println("  Records: ${result.ticks.size}")
    System.out.println("  Distance: ${"%.2f".format(result.ticks.lastOrNull()?.distanceM?.div(1000) ?: 0.0)} km")

    // Step 2: Process through CliRideEngine
    System.out.println("  Processing through RideEngine...")
    val engine = CliRideEngine()
    engine.reset()

    val outputTicks = mutableListOf<ReplayTick>()

    for ((i, tick) in result.ticks.withIndex()) {
        val engineResult = engine.process(tick, i)
        val enrichedTick = tick.copy(hudState = engineResult.hudState)
        outputTicks.add(enrichedTick)
    }

    // Step 3: Export JSON
    val exporter = JsonExporter()
    val logFile = exporter.exportReplayLog(
        ticks = outputTicks,
        sourceFile = fitFile.absolutePath,
        session = result.session,
        outputDir = outputDir,
        baseName = baseName,
    )
    System.out.println("  Exported: ${logFile.name} (${"%,d".format(outputTicks.size)} ticks)")

    if (includeSummary) {
        val summaryFile = exporter.exportSummary(
            ticks = result.ticks,
            engine = engine,
            sourceFile = fitFile.absolutePath,
            session = result.session,
            outputDir = outputDir,
            baseName = baseName,
        )
        if (summaryFile != null) {
            System.out.println("  Summary:  ${summaryFile.name}")
        }
    }

    // Print run stats
    System.out.println("  NP: ${engine.npWatts}W  IF: ${"%.2f".format(engine.ifValue)}  VI: ${"%.2f".format(engine.viValue)}")
    System.out.println("  TSS: ${"%.0f".format(engine.tssValue)}  RSRV: ${engine.rideReservePercent}%")
    System.out.println("  DRIFT: ${engine.decouplingPercent?.let { "+${"%.1f".format(it)}" } ?: "--"}")
    System.out.println("  CARB: ${engine.carbsGPerH}g/h  FLUID: ${"%.2f".format(engine.fluidLPerH)}L/h")
}

private fun extractArg(args: Array<String>, key: String): String? {
    val idx = args.indexOf(key)
    if (idx < 0 || idx >= args.size - 1) return null
    return args[idx + 1]
}

private fun printUsage() {
    System.out.println(
        """
FIT → QLab Replay Log Exporter
================================
Converts Garmin .fit files to qbot_replay_log.json for QLab.

Usage:
  java -jar fit-export.jar --fit <file.fit>                (single file)
  java -jar fit-export.jar --dir <directory>                (batch)
  java -jar fit-export.jar --fit <file.fit> --summary       (with summary)
  java -jar fit-export.jar --fit <file.fit> --out <dir>     (custom output dir)

Options:
  --fit <path>     Single .fit file to process
  --dir <path>     Directory containing .fit files (batch mode)
  --out <path>     Output directory (default: current directory)
  --summary        Also export qbot_replay_summary.json
  --help           Show this help

Output:
  <ride>.qbot_replay_log.json      Full tick-by-tick with HUD snapshots
  <ride>.qbot_replay_summary.json  Optional ride summary

QLab integration:
  Load the output JSON via QLab's 📂 button or place in input/ directory.
  QLab auto-detects qbot_replay_log.json format and enters passthrough mode.
        """.trimIndent()
    )
}
