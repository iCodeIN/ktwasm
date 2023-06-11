package com.github.yeicor.ktmpwasm.binary

fun parseDataSection(contents: ModuleContents, parser: ByteParser) {
  require(contents.data == null)

  val types = contents.types ?: listOf()
  contents.data = parseVector(parser) { parseData(parser, types) }
}

fun parseData(parser: ByteParser, types: List<FuncType>): DataDef {
  val memory = parseU32(parser)
  val offset = parseExpression(parser, types)
  val init = parseVector(parser, ::parseByte)

  return DataDef(memory, offset, init)
}
