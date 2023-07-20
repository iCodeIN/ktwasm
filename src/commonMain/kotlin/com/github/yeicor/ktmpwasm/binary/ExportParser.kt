package com.github.yeicor.ktmpwasm.binary

import com.github.yeicor.ktmpwasm.base.ExportType

fun parseExportSection(contents: ModuleContents, parser: ByteParser) {
  require(contents.exports == null)
  contents.exports = parseVector(parser, ::parseExport)
}

fun parseExport(parser: ByteParser): ExportDef {
  val name = parseName(parser)
  val type = parseExportType(parser)
  val idx = parseUnsigned(parser).toInt()
  return ExportDef(name, type, idx)
}

fun parseExportType(parser: ByteParser): ExportType {
  return ExportType.of(parser.readByte())
}

fun parseName(parser: ByteParser): String {
  val chars = parseVector(parser) { parser.readByte().toInt().toChar() }
  return chars.toCharArray().concatToString()
}
