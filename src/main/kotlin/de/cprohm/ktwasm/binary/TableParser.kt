package de.cprohm.ktwasm.binary

fun parseTableSection(contents: ModuleContents, parser: ByteParser) {
    contents.tables = parseVector(parser, ::parseTable)
}

fun parseTable(parser: ByteParser): Pair<Int, Int> {
    val marker = parseByte(parser)
    assert(marker == 0x70.toByte())
    return parseLimits(parser)
}
