package com.github.yeicor.ktmpwasm.binary

fun parseStartSection(contents: ModuleContents, parser: ByteParser) {
  contents.start = parseU32(parser)
}
