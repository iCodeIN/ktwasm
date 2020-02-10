package de.cprohm.ktwasm.binary

fun parseFunctionSection(contents: ModuleContents, parser: ByteParser) {
    assert(contents.functions == null)
    contents.functions = parseVector(parser, ::parseUnsigned)
}
