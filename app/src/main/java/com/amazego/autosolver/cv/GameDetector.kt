package com.amazego.autosolver.cv

import android.graphics.Bitmap
import android.graphics.Rect
import com.amazego.autosolver.model.BoardState

/**
 * Risultato completo dell'analisi del gioco da uno screenshot.
 */
data class GameDetectionResult(
    val isGameFound: Boolean,
    val isLevelCompleted: Boolean,
    val boardBounds: Rect,
    val boardState: BoardState?,
    val overallConfidence: Float,
    val statusMessage: String
)

/**
 * Orchestratore di Computer Vision che combina rilevamento del tabellone,
 * analisi della griglia, identificazione delle frecce e rilevamento degli stati di vittoria.
 */
class GameDetector(
    private val boardDetector: BoardDetector = BoardDetector(),
    private val gridAnalyzer: GridAnalyzer = GridAnalyzer(),
    private val arrowDetector: ArrowDetector = ArrowDetector()
) {

    /**
     * Analizza uno screenshot del dispositivo per estrarre lo stato corrente del gioco.
     */
    fun processFrame(bitmap: Bitmap, customBoardBounds: Rect? = null, forceGridSize: Pair<Int, Int>? = null): GameDetectionResult {
        // 1. Rileva tabellone
        val boardResult = boardDetector.detectBoard(bitmap, customBoardBounds)
        if (!boardResult.isFound) {
            return GameDetectionResult(
                isGameFound = false,
                isLevelCompleted = false,
                boardBounds = Rect(),
                boardState = null,
                overallConfidence = 0f,
                statusMessage = "Tabellone di gioco non rilevato sullo schermo."
            )
        }

        // 2. Analizza la griglia
        val gridInfo = gridAnalyzer.analyzeGrid(
            bitmap = bitmap,
            boardBounds = boardResult.bounds,
            forceRows = forceGridSize?.first ?: 0,
            forceCols = forceGridSize?.second ?: 0
        )

        // 3. Rileva frecce e direzioni
        val arrowResult = arrowDetector.detectArrows(bitmap, gridInfo)

        // 4. Verifica se il livello è completato (nessuna freccia trovata o schermata di vittoria)
        val isLevelCompleted = arrowResult.arrows.isEmpty() && checkLevelCompletedScreen(bitmap, boardResult.bounds)

        val boardState = if (!isLevelCompleted && arrowResult.arrows.isNotEmpty()) {
            BoardState(
                rows = gridInfo.rows,
                cols = gridInfo.cols,
                arrows = arrowResult.arrows
            )
        } else null

        val globalConfidence = (boardResult.confidence * 0.4f) + (arrowResult.overallConfidence * 0.6f)

        return GameDetectionResult(
            isGameFound = true,
            isLevelCompleted = isLevelCompleted,
            boardBounds = boardResult.bounds,
            boardState = boardState,
            overallConfidence = globalConfidence,
            statusMessage = if (isLevelCompleted) {
                "Livello completato con successo!"
            } else {
                "Rilevate ${arrowResult.arrows.size} frecce su griglia ${gridInfo.rows}x${gridInfo.cols}."
            }
        )
    }

    /**
     * Verifica la presenza di banner di vittoria o pulsante "Next" / "Continua".
     */
    private fun checkLevelCompletedScreen(bitmap: Bitmap, boardBounds: Rect): Boolean {
        // Analisi di uniformità dell'area del tabellone o rilevamento di testo/banner vittoria
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false

        // Controllo zona pulsante Next Level (solitamente sotto il tabellone)
        val bottomAreaY = (boardBounds.bottom + 20).coerceAtMost(height - 40)
        if (bottomAreaY < height - 100) {
            var vibrantPixels = 0
            for (x in (width * 0.25f).toInt()..(width * 0.75f).toInt() step 6) {
                val pixel = bitmap.getPixel(x, bottomAreaY)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Pulsanti "Next" o "Claim" hanno colori accesi (verde/oro/blu)
                if ((g > 140 && r < 100) || (r > 200 && g > 150 && b < 50)) {
                    vibrantPixels++
                }
            }
            if (vibrantPixels > 10) return true
        }

        return false
    }
}
