package de.cprohm.ktwasm.binary

import de.cprohm.ktwasm.base.ExportType

fun parseExportSection(contents: ModuleContents, parser: ByteParser) {
    assert(contents.exports == null)
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
    val bytes = parseVector(parser, ::parseByte)
    return String(bytes.toByteArray())
}
