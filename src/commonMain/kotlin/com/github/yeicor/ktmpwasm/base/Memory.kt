package com.github.yeicor.ktmpwasm.base

import com.github.yeicor.ktmpwasm.interpreter.Math

const val PAGE_SIZE = 65536

/** A WASM Memory object supporting store / loads. */
class Memory(var min: Int, val max: Int, var data: ByteArray = ByteArray(PAGE_SIZE * min)) {
  fun resize(newSize: Int): Boolean {
    if ((max > 0) && (newSize > max)) {
      return false
    }

    try {
      val newLen = Math.multiplyExact(PAGE_SIZE, newSize)
      data = data.copyInto(ByteArray(newLen))
    } catch (error: Throwable) {
      return false
    }

    this.min = newSize
    return true
  }

  @Suppress("unused")
  fun store(start: Int, length: Int, buf: ByteArray) {
    if (start <= 0) {
      throw Error("Cannot handle negative starts")
    }
    if (length < 0) {
      throw Error("Cannot handle negative lengths")
    }
    val end = Math.addExact(start, length)
    if (end > data.size) {
      throw Error("Cannot read [$start,$end) from a Memory with size ${data.size}")
    }

    for (offset in 0 until length) {
      this.data[start + offset] = buf[offset]
    }
  }

  @Suppress("unused")
  fun load(start: Int, length: Int): ByteArray {
    requireRange(start, start + length)
    val result = ByteArray(length)
    for (offset in 0 until length) {
      result[offset] = this.data[start + offset]
    }
    return result
  }

  fun storeI8(address: Int, offset: Int, data: Byte) = storeI8(safeOffset(address, offset), data)

  fun storeI16(address: Int, offset: Int, data: Short) = storeI16(safeOffset(address, offset), data)

  fun storeI32(address: Int, offset: Int, data: Int) = storeI32(safeOffset(address, offset), data)

  fun storeI64(address: Int, offset: Int, data: Long) = storeI64(safeOffset(address, offset), data)

  private fun storeI8(address: Int, data: Byte) {
    requireRange(address, address + 1)
    this.data[address + 0] = data
  }

  private fun storeI16(address: Int, data: Short) {
    requireRange(address, address + 2)
    this.data[address + 0] = (data.toInt() shr 0).toByte()
    this.data[address + 1] = (data.toInt() shr 8).toByte()
  }

  private fun storeI32(address: Int, data: Int) {
    requireRange(address, address + 4)
    this.data[address + 0] = (data shr 0).toByte()
    this.data[address + 1] = (data shr 8).toByte()
    this.data[address + 2] = (data shr 16).toByte()
    this.data[address + 3] = (data shr 24).toByte()
  }

  private fun storeI64(address: Int, data: Long) {
    requireRange(address, address + 8)
    this.data[address + 0] = (data shr 0).toByte()
    this.data[address + 1] = (data shr 8).toByte()
    this.data[address + 2] = (data shr 16).toByte()
    this.data[address + 3] = (data shr 24).toByte()
    this.data[address + 4] = (data shr 32).toByte()
    this.data[address + 5] = (data shr 40).toByte()
    this.data[address + 6] = (data shr 48).toByte()
    this.data[address + 7] = (data shr 56).toByte()
  }

  /**
   * Check that the range of address is valid
   *
   * @param start the start of the range
   * @param end the end of the range (exclusive)
   */
  private fun requireRange(start: Int, end: Int) {
    val startValid = (start >= 0) && (start < this.data.size)
    val endValid = (end >= 0) && (end <= this.data.size)

    if (!startValid || !endValid) {
      throw IndexOutOfBoundsException(
          "Cannot access memory range [$start:$end], available [0:${this.data.size}]")
    }
  }

  fun loadI8(address: Int, offset: Int): Byte = loadI8(safeOffset(address, offset))
  fun loadI16(address: Int, offset: Int): Short = loadI16(safeOffset(address, offset))
  fun loadI32(address: Int, offset: Int): Int = loadI32(safeOffset(address, offset))
  fun loadI64(address: Int, offset: Int): Long = loadI64(safeOffset(address, offset))

  private fun loadI8(address: Int): Byte {
    requireRange(address, address + 1)
    return this.data[address]
  }

  // Short does not seem to support bitwise and
  private fun loadI16(address: Int): Short {
    requireRange(address, address + 2)
    val raw =
        ((this.data[address + 0].toInt() and 0xFF) shl 0) +
            ((this.data[address + 1].toInt() and 0xFF) shl 8)
    return raw.toShort()
  }

  private fun loadI32(address: Int): Int {
    requireRange(address, address + 4)
    return ((this.data[address + 0].toInt() and 0xFF) shl 0) +
        ((this.data[address + 1].toInt() and 0xFF) shl 8) +
        ((this.data[address + 2].toInt() and 0xFF) shl 16) +
        ((this.data[address + 3].toInt() and 0xFF) shl 24)
  }

  private fun loadI64(address: Int): Long {
    requireRange(address, address + 8)
    return ((this.data[address + 0].toLong() and 0xFF) shl 0) +
        ((this.data[address + 1].toLong() and 0xFF) shl 8) +
        ((this.data[address + 2].toLong() and 0xFF) shl 16) +
        ((this.data[address + 3].toLong() and 0xFF) shl 24) +
        ((this.data[address + 4].toLong() and 0xFF) shl 32) +
        ((this.data[address + 5].toLong() and 0xFF) shl 40) +
        ((this.data[address + 6].toLong() and 0xFF) shl 48) +
        ((this.data[address + 7].toLong() and 0xFF) shl 56)
  }

  override fun equals(other: Any?): Boolean {
    return (other is Memory) &&
        (min == other.min) &&
        (max == other.max) &&
        data.contentEquals(other.data)
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }

  private fun safeOffset(address: Int, offset: Int): Int {
    if ((offset < 0) || (address < 0)) {
      throw Error("Invalid memory location  {address: $address, offset: $offset}")
    }
    return Math.addExact(address, offset)
  }

  fun clone(): Memory = Memory(min = min, max = max, data = data.copyOf())
}
