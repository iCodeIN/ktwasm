package de.cprohm.ktwasm.interpreter

import de.cprohm.ktwasm.base.Memory
import org.junit.Test
import kotlin.test.assertEquals

class UtilTest {
    @Test
    fun memory_examples() {
        assertEquals(I64.convertToF32U(3L), 3.0f)
        assertEquals(I64.convertToF32U(21L), 21.0f)
        assertEquals(I64.convertToF32U(42L), 42.0f)
    }

    @Test
    fun convert_to_f32u_examples() {
        val v = I64.convertToF32U(3L).toRawBits().toLong()
        assertEquals(Float.fromBits(v.toInt()), 3.0f)
    }
}
