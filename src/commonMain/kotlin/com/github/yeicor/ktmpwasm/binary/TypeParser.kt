package com.github.yeicor.ktmpwasm.binary

import com.github.yeicor.ktmpwasm.base.Type

fun parseTypeSection(contents: ModuleContents, parser: ByteParser) {
  require(contents.types == null)
  contents.types = parseVector(parser, ::parseFuncType)
}

fun parseFuncType(parser: ByteParser): FuncType {
  val header = parser.readByte()
  if (header != 0x60.toByte()) {
    throw Error("Invalid header $header @ ${parser.offset}")
  }

  val args = parseVector(parser, ::parseValueType)
  val result = parseVector(parser, ::parseValueType)
  return FuncType(args, result)
}

fun parseValueType(parser: ByteParser): Type {
  return when (val id = parser.readByte()) {
    0x7F.toByte() -> Type.I32
    0x7E.toByte() -> Type.I64
    0x7D.toByte() -> Type.F32
    0x7C.toByte() -> Type.F64
    else -> throw Error("Invalid value type $id @ ${parser.offset}")
  }
}
