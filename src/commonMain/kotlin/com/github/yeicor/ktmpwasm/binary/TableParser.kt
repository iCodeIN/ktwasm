package com.github.yeicor.ktmpwasm.binary

fun parseTableSection(contents: ModuleContents, parser: ByteParser) {
  contents.tables = parseVector(parser, ::parseTable)
}

fun parseTable(parser: ByteParser): Pair<Int, Int> {
  val marker = parseByte(parser)
  require(marker == 0x70.toByte())
  return parseLimits(parser)
}
