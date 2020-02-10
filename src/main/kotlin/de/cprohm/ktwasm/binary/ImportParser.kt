package de.cprohm.ktwasm.binary

import de.cprohm.ktwasm.base.ExportType

fun parseImportSection(contents: ModuleContents, parser: ByteParser) {
    contents.imports = parseVector(parser, ::parseImport)
}

fun parseImport(parser: ByteParser): ImportDef {
    val module = parseName(parser)
    val name = parseName(parser)
    val importType = parseByte(parser).let { ExportType.of(it) }

    return when (importType) {
        ExportType.FUNCTION -> {
            val typeidx = parseU32(parser)
            FunctionImportDef(module, name, typeidx)
        }
        ExportType.GLOBAL -> {
            val type = parseValueType(parser)
            val mut = parseByte(parser) != 0x00.toByte()
            GlobalImportDef(module, name, mut, type)
        }
        ExportType.TABLE -> {
            val type = parseByte(parser)
            assert(type == 0x70.toByte())
            val limits = parseLimits(parser)
            TableImportDef(module, name, limits.first, limits.second)
        }
        ExportType.MEMORY -> {
            val limits = parseLimits(parser)
            MemoryImportDef(module, name, limits.first, limits.second)
        }
    }
}
