package com.amazego.autosolver.model

/**
 * Rappresenta una singola freccia sul tabellone.
 *
 * @property id Identificativo univoco progressivo della freccia
 * @property row Riga logica nella griglia (0..rows-1)
 * @property col Colonna logica nella griglia (0..cols-1)
 * @property direction Direzione verso cui punta la freccia (UP, DOWN, LEFT, RIGHT)
 * @property centerX Coordinata orizzontale X in pixel sullo schermo reale
 * @property centerY Coordinata verticale Y in pixel sullo schermo reale
 * @property confidence Confidenza del riconoscimento da 0.0 a 1.0 (es. 0.98 = 98%)
 * @property isRemoved Indica se la freccia è già stata espulsa con successo dal tabellone
 */
data class Arrow(
    val id: Int,
    val row: Int,
    val col: Int,
    val direction: Direction,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val confidence: Float = 1.0f,
    var isRemoved: Boolean = false
) {
    /**
     * Calcola se il percorso di uscita lungo la direzione verso il bordo è libero da ostacoli/altre frecce.
     */
    fun canEscape(grid: Array<Array<Arrow?>>, totalRows: Int, totalCols: Int): Boolean {
        if (isRemoved) return false

        var currRow = row + direction.dy
        var currCol = col + direction.dx

        while (currRow in 0 until totalRows && currCol in 0 until totalCols) {
            val blockingArrow = grid[currRow][currCol]
            if (blockingArrow != null && !blockingArrow.isRemoved) {
                // Trovata un'altra freccia che blocca la traiettoria di uscita
                return false
            }
            currRow += direction.dy
            currCol += direction.dx
        }
        // Il raggio ha raggiunto il bordo esterno della griglia senza collisioni
        return true
    }
}
