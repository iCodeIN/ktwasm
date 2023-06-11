package com.github.yeicor.ktmpwasm.binary

fun parseElementSection(contents: ModuleContents, parser: ByteParser) {
  require(contents.elements == null)

  val types = contents.types ?: listOf()
  contents.elements = parseVector(parser) { parseElement(parser, types) }
}

fun parseElement(parser: ByteParser, types: List<FuncType>): ElementDef {
  val table = parseU32(parser)
  if (table != 0) {
    throw Error("Cannot handle table index $table != 0")
  }

  require(table == 0) { "ATM only table 0 can be addressed (found: $table)" }

  val offset = parseExpression(parser, types)
  val init = parseVector(parser, ::parseU32)

  return ElementDef(table, offset, init)
}
