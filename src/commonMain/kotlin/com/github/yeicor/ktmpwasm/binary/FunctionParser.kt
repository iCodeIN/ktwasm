package com.github.yeicor.ktmpwasm.binary

fun parseFunctionSection(contents: ModuleContents, parser: ByteParser) {
  require(contents.functions == null)
  contents.functions = parseVector(parser, ::parseUnsigned)
}
