package com.github.yeicor.ktmpwasm

import com.github.yeicor.ktmpwasm.api.*
import kotlin.test.Test
import kotlin.test.assertEquals

class Examples {
  @Test
  fun add() {
    val module: Namespace = parseModule(addBytes)
    val add = module.lookupFunction("add", null)

    assertEquals(3, add(1, 2))
  }

  @Test
  fun global() {
    val module: Namespace = parseModule(globalBytes)
    val addGlobal = module.lookupFunction("add_global", null)

    assertEquals(0, module["g"])
    assertEquals(2, addGlobal(2))

    module["g"] = 5
    assertEquals(5, module["g"])
    assertEquals(8, addGlobal(3))
  }

  companion object {
    private val addBytes: ByteArray =
        listOf(
                0x00,
                0x61,
                0x73,
                0x6d,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x07,
                0x01,
                0x60,
                0x02,
                0x7f,
                0x7f,
                0x01,
                0x7f,
                0x03,
                0x02,
                0x01,
                0x00,
                0x07,
                0x07,
                0x01,
                0x03,
                0x61,
                0x64,
                0x64,
                0x00,
                0x00,
                0x0a,
                0x09,
                0x01,
                0x07,
                0x00,
                0x20,
                0x00,
                0x20,
                0x01,
                0x6a,
                0x0b)
            .map { it.toByte() }
            .toByteArray()

    val globalBytes: ByteArray =
        listOf(
                0x00,
                0x61,
                0x73,
                0x6d,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x06,
                0x01,
                0x60,
                0x01,
                0x7f,
                0x01,
                0x7f,
                0x03,
                0x02,
                0x01,
                0x00,
                0x06,
                0x06,
                0x01,
                0x7f,
                0x01,
                0x41,
                0x00,
                0x0b,
                0x07,
                0x12,
                0x02,
                0x0a,
                0x61,
                0x64,
                0x64,
                0x5f,
                0x67,
                0x6c,
                0x6f,
                0x62,
                0x61,
                0x6c,
                0x00,
                0x00,
                0x01,
                0x67,
                0x03,
                0x00,
                0x0a,
                0x09,
                0x01,
                0x07,
                0x00,
                0x23,
                0x00,
                0x20,
                0x00,
                0x6a,
                0x0b)
            .map { it.toByte() }
            .toByteArray()
  }
}
