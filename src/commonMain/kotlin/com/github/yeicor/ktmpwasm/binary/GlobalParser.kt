package com.github.yeicor.ktmpwasm.binary

fun parseGlobalSection(contents: ModuleContents, parser: ByteParser) {
  require(contents.globals == null)

  val types = contents.types ?: listOf()
  contents.globals = parseVector(parser) { parseGlobal(parser, types) }
}

fun parseGlobal(parser: ByteParser, types: List<FuncType>): GlobalDef {
  val type = parseValueType(parser)
  val mut = parseByte(parser) != 0x00.toByte()
  val init = parseExpression(parser, types)

  return GlobalDef(type, mut, init)
}
