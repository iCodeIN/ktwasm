package com.github.yeicor.ktmpwasm.binary

import com.github.yeicor.ktmpwasm.base.ExportType
import com.github.yeicor.ktmpwasm.base.Type
import com.github.yeicor.ktmpwasm.interpreter.Instruction

enum class SectionType {
  CUSTOM,
  TYPE,
  IMPORT,
  FUNCTION,
  TABLE,
  MEMORY,
  GLOBAL,
  EXPORT,
  START,
  ELEMENT,
  CODE,
  DATA,
  DATA_COUNT;

  companion object {
    fun of(x: Byte): SectionType =
        when (x.toInt()) {
          0 -> CUSTOM
          1 -> TYPE
          2 -> IMPORT
          3 -> FUNCTION
          4 -> TABLE
          5 -> MEMORY
          6 -> GLOBAL
          7 -> EXPORT
          8 -> START
          9 -> ELEMENT
          10 -> CODE
          11 -> DATA
          12 -> DATA_COUNT
          else -> throw Error("Unknown section type $x")
        }
  }
}

data class ModuleContents(
    var types: List<FuncType>? = null,
    var imports: List<ImportDef>? = null,
    var tables: List<Pair<Int, Int>>? = null,
    var memories: List<MemoryDef>? = null,
    var globals: List<GlobalDef>? = null,
    var functions: List<Long>? = null,
    var exports: List<ExportDef>? = null,
    var start: Int? = null,
    var elements: List<ElementDef>? = null,
    var code: List<Code>? = null,
    var data: List<DataDef>? = null
)

data class MemoryDef(val min: Int, val max: Int)

data class Code(val locals: List<Type>, val expression: List<Instruction>)

data class FuncType(val args: List<Type>, val result: List<Type>)

data class ExportDef(val name: String, val type: ExportType, val idx: Int)

data class GlobalDef(val type: Type, val mutable: Boolean, val init: List<Instruction>)

data class ElementDef(val table: Int, val offset: List<Instruction>, val init: List<Int>)

class DataDef(val memory: Int, val offset: List<Instruction>, val init: List<Byte>)

sealed class ImportDef(
    val module: String,
    val name: String,
    @Suppress("unused") val importType: ExportType
)

class FunctionImportDef(module: String, name: String, val typeIDx: Int) :
    ImportDef(module, name, ExportType.FUNCTION)

class TableImportDef(module: String, name: String, val min: Int, val max: Int) :
    ImportDef(module, name, ExportType.TABLE)

class MemoryImportDef(module: String, name: String, val min: Int, val max: Int) :
    ImportDef(module, name, ExportType.MEMORY)

class GlobalImportDef(module: String, name: String, val mutable: Boolean, val type: Type) :
    ImportDef(module, name, ExportType.GLOBAL)
