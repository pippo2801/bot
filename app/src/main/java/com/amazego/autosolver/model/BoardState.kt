package com.amazego.autosolver.model

/**
 * Rappresenta lo stato matematico e topologico della scacchiera di gioco.
 */
data class BoardState(
    val rows: Int,
    val cols: Int,
    val arrows: List<Arrow>,
    val removedArrowIds: Set<Int> = emptySet()
) {
    // Matrice rapida bidimensionale di lookup per riga/colonna
    val grid: Array<Array<Arrow?>> = Array(rows) { Array(cols) { null } }

    init {
        for (arrow in arrows) {
            if (arrow.row in 0 until rows && arrow.col in 0 until cols) {
                if (!removedArrowIds.contains(arrow.id)) {
                    grid[arrow.row][arrow.col] = arrow.copy(isRemoved = false)
                } else {
                    grid[arrow.row][arrow.col] = null
                }
            }
        }
    }

    /**
     * Restituisce la lista di tutte le frecce attualmente libere e pronte per uscire senza collisioni.
     */
    fun getAvailableMoves(): List<Arrow> {
        val available = mutableListOf<Arrow>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val arrow = grid[r][c] ?: continue
                if (!removedArrowIds.contains(arrow.id) && arrow.canEscape(grid, rows, cols)) {
                    available.add(arrow)
                }
            }
        }
        return available
    }

    /**
     * Esegue la rimozione logica di una freccia e restituisce il nuovo stato della scacchiera.
     */
    fun applyMove(arrow: Arrow): BoardState {
        val newRemoved = removedArrowIds.toMutableSet().apply { add(arrow.id) }
        return BoardState(rows, cols, arrows, newRemoved)
    }

    /**
     * Verifica se il tabellone è completamente ripulito (tutte le frecce espulse).
     */
    fun isComplete(): Boolean {
        return removedArrowIds.size == arrows.size
    }

    /**
     * Genera una chiave hash immutabile per memoization e prevenzione loop nel solver.
     */
    fun getStateHash(): String {
        return removedArrowIds.sorted().joinToString(",")
    }
}
