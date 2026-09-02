package com.amazego.autosolver.model

/**
 * Rappresenta un'azione / tap su una freccia da parte del Solver o dell'AccessibilityController.
 */
data class Move(
    val stepIndex: Int,
    val arrowId: Int,
    val targetRow: Int,
    val targetCol: Int,
    val direction: Direction,
    val tapScreenX: Float,
    val tapScreenY: Float
)
