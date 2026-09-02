package com.amazego.autosolver.model

/**
 * Risultato restituito dal motore Solver.
 */
data class SolverResult(
    val isSolved: Boolean,
    val moves: List<Move>,
    val exploredStates: Int,
    val calculationTimeMs: Long,
    val errorMessage: String? = null
)
