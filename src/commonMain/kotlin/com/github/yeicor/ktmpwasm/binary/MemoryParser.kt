package com.github.yeicor.ktmpwasm.binary

fun parseMemorySection(contents: ModuleContents, parser: ByteParser) {
  require(contents.memories == null)
  contents.memories = parseVector(parser, ::parseMemory)
}

fun parseMemory(parser: ByteParser): MemoryDef =
    parseLimits(parser).let { MemoryDef(it.first, it.second) }
