package de.cprohm.ktwasm.binary

fun parseStartSection(contents: ModuleContents, parser: ByteParser) {
    contents.start = parseU32(parser)
}
