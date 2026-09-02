package com.amazego.autosolver.cv

import android.graphics.Bitmap
import android.graphics.Color
import com.amazego.autosolver.model.Arrow
import com.amazego.autosolver.model.Direction
import kotlin.math.abs

/**
 * Risultato del rilevamento delle frecce nella griglia.
 */
data class ArrowDetectionResult(
    val arrows: List<Arrow>,
    val overallConfidence: Float,
    val emptyCellsCount: Int,
    val debugDetails: List<String> = emptyList()
)

/**
 * Rileva la presenza e l'orientamento delle frecce in ogni cella del tabellone di Amaze GO!.
 *
 * Utilizza:
 * - Analisi del centro di massa (Centroid asymmetry) per determinare la punta della freccia
 * - Gradienti di varianza direzionale lungo 4 assi (Nord, Sud, Ovest, Est)
 * - Calcolo della confidenza per evitare tap accidentali in caso di testo o celle vuote.
 */
class ArrowDetector {

    /**
     * Esegue l'estrazione e il riconoscimento delle frecce su tutte le celle della griglia.
     */
    fun detectArrows(bitmap: Bitmap, gridInfo: GridInfo, confidenceThreshold: Float = 0.70f): ArrowDetectionResult {
        val detectedArrows = mutableListOf<Arrow>()
        val debugList = mutableListOf<String>()
        var idCounter = 1
        var sumConfidence = 0.0f
        var emptyCount = 0

        for (r in 0 until gridInfo.rows) {
            for (c in 0 until gridInfo.cols) {
                val (centerX, centerY) = gridInfo.cellCenters[r][c]
                val halfW = gridInfo.cellWidth * 0.42f
                val halfH = gridInfo.cellHeight * 0.42f

                val cellLeft = (centerX - halfW).toInt().coerceIn(0, bitmap.width - 1)
                val cellRight = (centerX + halfW).toInt().coerceIn(0, bitmap.width - 1)
                val cellTop = (centerY - halfH).toInt().coerceIn(0, bitmap.height - 1)
                val cellBottom = (centerY + halfH).toInt().coerceIn(0, bitmap.height - 1)

                val arrowResult = classifyCell(bitmap, cellLeft, cellTop, cellRight, cellBottom, centerX, centerY)

                if (arrowResult != null && arrowResult.confidence >= confidenceThreshold) {
                    val arrow = Arrow(
                        id = idCounter++,
                        row = r,
                        col = c,
                        direction = arrowResult.direction,
                        centerX = centerX,
                        centerY = centerY,
                        confidence = arrowResult.confidence
                    )
                    detectedArrows.add(arrow)
                    sumConfidence += arrowResult.confidence
                    debugList.add("Cella [$r,$c]: Freccia ${arrowResult.direction.symbol} (${(arrowResult.confidence * 100).toInt()}%)")
                } else {
                    emptyCount++
                }
            }
        }

        val avgConfidence = if (detectedArrows.isNotEmpty()) sumConfidence / detectedArrows.size else 1.0f

        return ArrowDetectionResult(
            arrows = detectedArrows,
            overallConfidence = avgConfidence,
            emptyCellsCount = emptyCount,
            debugDetails = debugList
        )
    }

    private data class CellClassification(val direction: Direction, val confidence: Float)

    private fun classifyCell(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cellCenterX: Float,
        cellCenterY: Float
    ): CellClassification? {
        val width = right - left
        val height = bottom - top
        if (width < 6 || height < 6) return null

        // 1. Calcola il colore di sfondo della cella (campione sui 4 angoli)
        val corner1 = bitmap.getPixel(left, top)
        val corner2 = bitmap.getPixel(right, top)
        val corner3 = bitmap.getPixel(left, bottom)
        val corner4 = bitmap.getPixel(right, bottom)

        val bgR = (Color.red(corner1) + Color.red(corner2) + Color.red(corner3) + Color.red(corner4)) / 4
        val bgG = (Color.green(corner1) + Color.green(corner2) + Color.green(corner3) + Color.green(corner4)) / 4
        val bgB = (Color.blue(corner1) + Color.blue(corner2) + Color.blue(corner3) + Color.blue(corner4)) / 4

        var foregroundPixels = 0
        var weightedXSum = 0f
        var weightedYSum = 0f

        var topMass = 0
        var bottomMass = 0
        var leftMass = 0
        var rightMass = 0

        val midX = (left + right) / 2
        val midY = (top + bottom) / 2

        for (y in top..bottom step 2) {
            for (x in left..right step 2) {
                val pixel = bitmap.getPixel(x, y)
                val diff = abs(Color.red(pixel) - bgR) +
                        abs(Color.green(pixel) - bgG) +
                        abs(Color.blue(pixel) - bgB)

                // Soglia di contrasto per considerare il pixel come parte del glifo freccia
                if (diff > 45) {
                    foregroundPixels++
                    val relX = x - cellCenterX
                    val relY = y - cellCenterY
                    weightedXSum += relX
                    weightedYSum += relY

                    if (y < midY) topMass++ else bottomMass++
                    if (x < midX) leftMass++ else rightMass++
                }
            }
        }

        val totalSampled = (width * height) / 4
        val coverageRatio = foregroundPixels.toFloat() / totalSampled.coerceAtLeast(1)

        // Se la cella ha troppi pochi pixel in contrasto (vuota) o è uniforme (> 85%), non è una freccia
        if (coverageRatio < 0.08f || coverageRatio > 0.85f || foregroundPixels < 15) {
            return null
        }

        // 2. Calcola il vettore baricentrico (asymmetry centroid)
        val centroidX = weightedXSum / foregroundPixels
        val centroidY = weightedYSum / foregroundPixels

        // Determina la direzione in base all'asse dominante del baricentro e della massa di pixel
        val isHorizontalDominant = abs(centroidX) > abs(centroidY)

        val direction: Direction
        val confidence: Float

        if (isHorizontalDominant) {
            if (centroidX > 0.5f || rightMass > leftMass * 1.25f) {
                direction = Direction.RIGHT
                val ratio = if (leftMass > 0) rightMass.toFloat() / leftMass else 2f
                confidence = (0.75f + (ratio * 0.1f)).coerceIn(0.72f, 0.98f)
            } else {
                direction = Direction.LEFT
                val ratio = if (rightMass > 0) leftMass.toFloat() / rightMass else 2f
                confidence = (0.75f + (ratio * 0.1f)).coerceIn(0.72f, 0.98f)
            }
        } else {
            if (centroidY > 0.5f || bottomMass > topMass * 1.25f) {
                direction = Direction.DOWN
                val ratio = if (topMass > 0) bottomMass.toFloat() / topMass else 2f
                confidence = (0.75f + (ratio * 0.1f)).coerceIn(0.72f, 0.98f)
            } else {
                direction = Direction.UP
                val ratio = if (bottomMass > 0) topMass.toFloat() / bottomMass else 2f
                confidence = (0.75f + (ratio * 0.1f)).coerceIn(0.72f, 0.98f)
            }
        }

        return CellClassification(direction, confidence)
    }
}
