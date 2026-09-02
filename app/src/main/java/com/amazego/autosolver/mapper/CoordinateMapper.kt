package com.amazego.autosolver.mapper

import android.graphics.PointF
import android.graphics.Rect

/**
 * Mappa le coordinate discrete della matrice di gioco (riga, colonna)
 * nelle coordinate continue assolute in pixel dello schermo del dispositivo Android.
 */
class CoordinateMapper {

    /**
     * Calcola la coordinata centrale dello schermo (X, Y) per una specifica cella.
     */
    fun getScreenCoordinates(
        row: Int,
        col: Int,
        totalRows: Int,
        totalCols: Int,
        boardBounds: Rect
    ): PointF {
        require(totalRows > 0 && totalCols > 0) { "Dimensioni della griglia non valide" }

        val cellWidth = boardBounds.width().toFloat() / totalCols
        val cellHeight = boardBounds.height().toFloat() / totalRows

        val screenX = boardBounds.left + (col + 0.5f) * cellWidth
        val screenY = boardBounds.top + (row + 0.5f) * cellHeight

        return PointF(screenX, screenY)
    }

    /**
     * Calcola la coordinata del pulsante 'Next Level' / 'Continua'.
     */
    fun getNextLevelButtonCoordinate(boardBounds: Rect, screenHeight: Int): PointF {
        val centerX = boardBounds.centerX().toFloat()
        val centerY = (boardBounds.bottom + (screenHeight - boardBounds.bottom) * 0.45f)
        return PointF(centerX, centerY)
    }
}
