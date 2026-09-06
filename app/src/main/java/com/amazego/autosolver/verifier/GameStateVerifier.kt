package com.amazego.autosolver.verifier

import android.graphics.Bitmap
import android.graphics.Rect
import com.amazego.autosolver.cv.GameDetector
import com.amazego.autosolver.model.Arrow
import com.amazego.autosolver.model.BoardState

/**
 * Risultato della verifica post-mossa.
 */
sealed class VerificationResult {
    data class Success(val updatedState: BoardState?) : VerificationResult()
    data class Mismatch(val reason: String, val actualState: BoardState?) : VerificationResult()
    object LevelCompleted : VerificationResult()
    data class Error(val message: String) : VerificationResult()
}

/**
 * Verifica l'esito reale sullo schermo dopo ogni tap per evitare esecuzioni alla cieca.
 */
class GameStateVerifier(private val gameDetector: GameDetector = GameDetector()) {

    /**
     * Controlla che la freccia cliccata sia effettivamente scomparsa dal tabellone
     * e che lo stato effettivo coincida con quello atteso.
     */
    fun verifyPostMove(
        postMoveBitmap: Bitmap,
        expectedRemovedArrow: Arrow,
        previousState: BoardState,
        customBoardBounds: Rect? = null
    ): VerificationResult {
        val gameResult = gameDetector.processFrame(postMoveBitmap, customBoardBounds)

        if (!gameResult.isGameFound) {
            return VerificationResult.Error("Impossibile rilevare il tabellone dopo la mossa.")
        }

        if (gameResult.isLevelCompleted) {
            return VerificationResult.LevelCompleted
        }

        val actualState = gameResult.boardState
            ?: return VerificationResult.Error(
                "Tabellone rilevato ma stato della griglia non disponibile."
            )

        if (actualState.arrows.isEmpty()) {
            return VerificationResult.Error(
                "Nessuna freccia rilevata durante la verifica: possibile frame transitorio."
            )
        }

        // Controlla se la freccia cliccata è ancora presente nella stessa cella
        val stillPresent = actualState.arrows.any { 
            it.row == expectedRemovedArrow.row && it.col == expectedRemovedArrow.col 
        }

        if (stillPresent) {
            return VerificationResult.Mismatch(
                reason = "La freccia #${expectedRemovedArrow.id} in riga ${expectedRemovedArrow.row}, col ${expectedRemovedArrow.col} è ancora presente.",
                actualState = actualState
            )
        }

        return VerificationResult.Success(updatedState = actualState)
    }
}
