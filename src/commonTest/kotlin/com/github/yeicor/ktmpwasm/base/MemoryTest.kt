package com.github.yeicor.ktmpwasm.base

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryTest {
  @Test
  fun memory_examples() {
    val memory = Memory(1, 1)

    memory.storeI32(0, 0, 32)
    assertEquals(32, memory.loadI32(0, 0))
  }
}
