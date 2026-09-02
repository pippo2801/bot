package com.amazego.autosolver

import android.graphics.Rect
import com.amazego.autosolver.mapper.CoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMapperTest {

    private val mapper = CoordinateMapper()

    @Test
    fun testScreenCoordinateCalculation() {
        val boardBounds = Rect(100, 200, 700, 800) // W=600, H=600
        val rows = 3
        val cols = 3

        // Cella (0,0) -> centro in (100 + 100, 200 + 100) = (200, 300)
        val p00 = mapper.getScreenCoordinates(0, 0, rows, cols, boardBounds)
        assertEquals(200f, p00.x, 0.01f)
        assertEquals(300f, p00.y, 0.01f)

        // Cella (1,1) -> centro in (100 + 300, 200 + 300) = (400, 500)
        val p11 = mapper.getScreenCoordinates(1, 1, rows, cols, boardBounds)
        assertEquals(400f, p11.x, 0.01f)
        assertEquals(500f, p11.y, 0.01f)
    }
}
