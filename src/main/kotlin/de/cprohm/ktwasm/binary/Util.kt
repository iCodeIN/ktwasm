package de.cprohm.ktwasm.binary


class ByteParser(val data: ByteArray, var offset: Int, val maxOffset: Int) {
    init {
        assert(maxOffset <= data.size)
    }

    constructor(data: ByteArray, offset: Int) : this(data, offset, data.size)

    val done: Boolean get() = offset >= data.size

    fun copy(): ByteParser = ByteParser(data, offset)

    fun copy(size: Int): ByteParser = ByteParser(data, offset, offset + size)

    fun <T> guard(size: Int, op: (ByteParser) -> T): T {
        val subparser = copy(size)
        val result = op(subparser)
        offset = subparser.offset
        return result
    }

    fun advance(delta: Int) {
        offset += delta
    }

    fun readByte(): Byte {
        assert(!done)
        assert((offset + 1) <= maxOffset) { "${offset + 1} > $maxOffset" }

        return data[offset].also { offset++ }
    }

    fun readF32(): Float {
        assert(!done)
        assert((offset + 4) <= maxOffset) { "${offset + 4} > $maxOffset" }

        val bits = ((data[offset + 0].toInt() and 0xff) shl 0) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 24)

        return Float.fromBits(bits).also { offset += 4 }
    }

    fun readF64(): Double {
        assert(!done)
        assert((offset + 8) <= maxOffset) { "${offset + 8} > $maxOffset" }

        val bits = ((data[offset + 0].toLong() and 0xff) shl 0) +
                ((data[offset + 1].toLong() and 0xff) shl 8) +
                ((data[offset + 2].toLong() and 0xff) shl 16) +
                ((data[offset + 3].toLong() and 0xff) shl 24) +
                ((data[offset + 4].toLong() and 0xff) shl 32) +
                ((data[offset + 5].toLong() and 0xff) shl 40) +
                ((data[offset + 6].toLong() and 0xff) shl 48) +
                ((data[offset + 7].toLong() and 0xff) shl 56)

        return Double.fromBits(bits).also { offset += 8 }
    }

    fun readU32(): Int = readUnsigned().toInt()
    fun readU64(): Long = readUnsigned()

    fun readI32(): Int {
        assert(!done)
        val (result, newOffset) = decodeLEB128Signed(data, offset, 32)
        assert(newOffset <= maxOffset) { "$newOffset > $maxOffset" }
        offset = newOffset
        return result.toInt()
    }

    fun readI64(): Long {
        assert(!done)
        val (result, newOffset) = decodeLEB128Signed(data, offset, 64)
        assert(newOffset <= maxOffset) { "$newOffset > $maxOffset" }
        offset = newOffset
        return result
    }

    fun readUnsigned(): Long {
        assert(!done)

        val (result, newOffset) = decodeLEB128Unsigned(data, offset)

        assert(newOffset <= maxOffset) { "$newOffset > $maxOffset" }
        offset = newOffset
        return result
    }
}

/**
 * Parse a vector of elements
 *
 * @param subparser a callable used to parsed the vector elements
 * @return a list of the parsed elements
 */
fun <T> parseVector(parser: ByteParser, subparser: (ByteParser) -> T): List<T> {
    val num = parser.readUnsigned()
    val result: MutableList<T> = mutableListOf()

    for (index in 0 until num) {
        val item = try {
            subparser(parser)
        } catch (e: Throwable) {
            throw Error("Error at element $index", e)
        }
        result.add(item)
    }
    return result
}

/**
 * Check that at offset the given bytes are found
 */
fun expectBytes(data: ByteArray, offset: Int, expected: ByteArray) {
    if (data.size < offset + expected.size) {
        throw Error("Not enough element")
    }

    for (idx in expected.indices) {
        if (data[offset + idx] != expected[idx]) {
            throw Error("Invalid byte at $idx: ${data[offset + idx]} != ${expected[idx]}")
        }
    }
}

fun parseByte(parser: ByteParser): Byte = parser.readByte()
fun parseUnsigned(parser: ByteParser): Long = parser.readUnsigned()

fun parseU32(parser: ByteParser): Int = parser.readU32()
fun parseU64(parser: ByteParser): Long = parser.readU64()

fun parseLimits(parser: ByteParser): Pair<Int, Int> {
    val marker = parser.readByte()
    return when (marker) {
        0.toByte() -> {
            val min = parser.readU32()
            Pair(min, 0)
        }
        1.toByte() -> {
            val min = parser.readU32()
            val max = parser.readU32()
            Pair(min, max)
        }
        else -> throw Error("Invalid limits type $marker")
    }
}

/**
 * Parse a LEB128 Unsigned integer
 *
 * @param start the offset at which parsing begins
 * @return the parsed number and the new offset
 */
fun decodeLEB128Unsigned(data: ByteArray, start: Int): Pair<Long, Int> {
    // adapted from https://en.wikipedia.org/wiki/LEB128
    var result = 0L
    var shift = 0
    var offset = start

    val highBits = 0x80L
    val lowBits = 0x7FL

    while (true) {
        val byte = data[offset].toLong()
        offset++

        result = result or ((byte and lowBits) shl shift)

        val continueBit = byte and highBits
        if (continueBit == 0L) {
            break
        }
        shift += 7
    }

    return Pair(result, offset)
}

/**
 * Parse a LEB128 Signed Integer
 *
 * @param start the offset at which parsing begins
 * @param size the size of the desired result in bits
 * @return the parsed number and the new offset
 */
fun decodeLEB128Signed(data: ByteArray, start: Int, size: Int): Pair<Long, Int> {
    // adapted from https://en.wikipedia.org/wiki/LEB128
    var result = 0L
    var shift = 0
    var offset = start

    val highBits = 0x80L
    val lowBits = 0x7FL
    var byte: Long

    while (true) {
        byte = data[offset].toLong()
        offset++

        result = result or ((byte and lowBits) shl shift)
        shift += 7

        val continueBit = byte and highBits
        if (continueBit == 0L) {
            break
        }
    }

    if ((shift < size) && ((byte and 0x40L) != 0L)) {
        val zero = 0L
        result = result or (zero.inv() shl shift)
    }

    return Pair(result, offset)
}
