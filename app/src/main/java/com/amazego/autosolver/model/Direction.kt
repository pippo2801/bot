package com.amazego.autosolver.model

/**
 * Rappresenta la direzione di orientamento di una freccia in Amaze GO!.
 */
enum class Direction(val dx: Int, val dy: Int, val symbol: String) {
    UP(0, -1, "↑"),
    DOWN(0, 1, "↓"),
    LEFT(-1, 0, "←"),
    RIGHT(1, 0, "→");

    companion object {
        fun fromSymbol(symbol: String): Direction? {
            return entries.find { it.symbol == symbol || it.name.equals(symbol, ignoreCase = true) }
        }
    }
}
