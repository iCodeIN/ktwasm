package de.cprohm.ktwasm.interpreter

@UseExperimental(ExperimentalStdlibApi::class)
object I32 {
    fun remU(a: Int, b: Int): Int {
        return (a.toUInt() % b.toUInt()).toInt()
    }

    fun divU(a: Int, b: Int): Int {
        return (a.toUInt() / b.toUInt()).toInt()
    }

    fun gtU(a: Int, b: Int): Boolean {
        return a.toUInt() > b.toUInt()
    }

    fun geU(a: Int, b: Int): Boolean {
        return a.toUInt() >= b.toUInt()
    }

    fun ltU(a: Int, b: Int): Boolean {
        return a.toUInt() < b.toUInt()
    }

    fun leU(a: Int, b: Int): Boolean {
        return a.toUInt() <= b.toUInt()
    }

    fun rotateLeft(a: Int, b: Int): Int {
        return a.rotateLeft(b)
    }

    fun rotateRight(a: Int, b: Int): Int {
        return a.rotateRight(b)
    }

    fun countLeadingZeroBits(a: Int): Int {
        return a.countLeadingZeroBits()
    }

    fun countTrailingZeroBits(a: Int): Int {
        return a.countTrailingZeroBits()
    }

    fun countOneBits(a: Int): Int {
        return a.countOneBits()
    }

    fun convertToF32U(a: Int): Float {
        // TODO: write tests and check it
        return a.toUInt().toFloat()
    }

    fun convertToF64U(a: Int): Double {
        return a.toUInt().toDouble()
    }

    fun fromF32U(a: Float): Int {
        return a.toUInt().toInt()
    }

    fun fromF64U(a: Double): Int {
        return a.toUInt().toInt()
    }

    fun extendS(a: Byte): Int = a.toInt()
    fun extendU(a: Byte): Int = a.toInt() and 0xFF

    fun extendS(a: Short): Int = a.toInt()
    fun extendU(a: Short): Int = a.toInt() and 0xFF_FF
}

@UseExperimental(ExperimentalStdlibApi::class)
object I64 {
    fun remU(a: Long, b: Long): Long {
        return (a.toULong() % b.toULong()).toLong()
    }

    fun divU(a: Long, b: Long): Long {
        return (a.toULong() / b.toULong()).toLong()
    }

    fun gtU(a: Long, b: Long): Boolean {
        return a.toULong() > b.toULong()
    }

    fun geU(a: Long, b: Long): Boolean {
        return a.toULong() >= b.toULong()
    }

    fun ltU(a: Long, b: Long): Boolean {
        return a.toULong() < b.toULong()
    }

    fun leU(a: Long, b: Long): Boolean {
        return a.toULong() <= b.toULong()
    }

    fun countLeadingZeroBits(a: Long): Long {
        return a.countLeadingZeroBits().toLong()
    }

    fun countTrailingZeroBits(a: Long): Long {
        return a.countTrailingZeroBits().toLong()
    }

    fun countOneBits(a: Long): Long {
        return a.countOneBits().toLong()
    }

    fun rotateLeft(a: Long, b: Long): Long {
        return a.rotateLeft(b.toInt()).toLong()
    }

    fun rotateRight(a: Long, b: Long): Long {
        return a.rotateRight(b.toInt()).toLong()
    }

    fun convertToF32U(a: Long): Float {
        return a.toULong().toFloat()
    }

    fun convertToF64U(a: Long): Double {
        return a.toULong().toDouble()
    }

    fun fromF32U(a: Float): Long {
        return a.toULong().toLong()
    }

    fun fromF64U(a: Double): Long {
        return a.toULong().toLong()
    }

    fun extendS(a: Byte): Long = a.toLong()
    fun extendU(a: Byte): Long = a.toLong() and 0xFF

    fun extendS(a: Short): Long = a.toLong()
    fun extendU(a: Short): Long = a.toLong() and 0xFF_FF

    fun extendS(a: Int): Long = a.toLong()
    fun extendU(a: Int): Long = a.toLong() and 0xFF_FF_FF_FF
}

object F32 {
    fun nearest(a: Float): Float = Math.rint(a.toDouble()).toFloat()
}

object F64 {
    fun nearest(a: Double): Double = Math.rint(a)
}