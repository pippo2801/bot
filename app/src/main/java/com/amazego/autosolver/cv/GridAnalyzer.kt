package com.amazego.autosolver.cv

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Informazioni geometriche sulla griglia di gioco.
 */
data class GridInfo(
    val rows: Int,
    val cols: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    val boardBounds: Rect,
    val cellCenters: List<List<Pair<Float, Float>>>
)

/**
 * Analizza l'area del tabellone per determinare la suddivisione in righe e colonne,
 * la dimensione delle singole celle e le coordinate centrali di ciascuna cella.
 */
class GridAnalyzer {

    /**
     * Stima la griglia analizzando le frequenze spaziali e le variazioni periodiche dei bordi celle.
     */
    fun analyzeGrid(bitmap: Bitmap, boardBounds: Rect, forceRows: Int = 0, forceCols: Int = 0): GridInfo {
        val boardW = boardBounds.width()
        val boardH = boardBounds.height()

        val rows: Int
        val cols: Int

        if (forceRows > 0 && forceCols > 0) {
            rows = forceRows
            cols = forceCols
        } else {
            // Rileva automaticamente il numero di righe e colonne cercando picchi di gradiente
            val detectedCols = detectPeriodicPeaks(bitmap, boardBounds, isHorizontal = true)
            val detectedRows = detectPeriodicPeaks(bitmap, boardBounds, isHorizontal = false)

            cols = detectedCols.coerceIn(3, 12)
            rows = detectedRows.coerceIn(3, 12)
        }

        val cellW = boardW.toFloat() / cols
        val cellH = boardH.toFloat() / rows

        val centers = mutableListOf<List<Pair<Float, Float>>>()
        for (r in 0 until rows) {
            val rowCenters = mutableListOf<Pair<Float, Float>>()
            for (c in 0 until cols) {
                val cx = boardBounds.left + (c + 0.5f) * cellW
                val cy = boardBounds.top + (r + 0.5f) * cellH
                rowCenters.add(cx to cy)
            }
            centers.add(rowCenters)
        }

        return GridInfo(
            rows = rows,
            cols = cols,
            cellWidth = cellW,
            cellHeight = cellH,
            boardBounds = boardBounds,
            cellCenters = centers
        )
    }

    private fun detectPeriodicPeaks(bitmap: Bitmap, bounds: Rect, isHorizontal: Boolean): Int {
        // Analisi di autocorrelazione / transizioni di luminanza lungo la linea mediana
        val sampleSize = if (isHorizontal) bounds.width() else bounds.height()
        if (sampleSize <= 0) return 6

        var transitions = 0
        var prevBrightness = -1
        val step = 3

        if (isHorizontal) {
            val midY = bounds.centerY().coerceIn(0, bitmap.height - 1)
            for (x in bounds.left until bounds.right step step) {
                if (x >= bitmap.width) break
                val pixel = bitmap.getPixel(x, midY)
                val br = ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
                if (prevBrightness != -1 && kotlin.math.abs(br - prevBrightness) > 28) {
                    transitions++
                }
                prevBrightness = br
            }
        } else {
            val midX = bounds.centerX().coerceIn(0, bitmap.width - 1)
            for (y in bounds.top until bounds.bottom step step) {
                if (y >= bitmap.height) break
                val pixel = bitmap.getPixel(midX, y)
                val br = ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
                if (prevBrightness != -1 && kotlin.math.abs(br - prevBrightness) > 28) {
                    transitions++
                }
                prevBrightness = br
            }
        }

        // Stima delle divisioni
        val estimatedDivisions = (transitions / 3.2).toInt().coerceIn(4, 8)
        return estimatedDivisions
    }
}
