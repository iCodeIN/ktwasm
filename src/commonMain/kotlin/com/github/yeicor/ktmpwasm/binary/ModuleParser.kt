package com.github.yeicor.ktmpwasm.binary

import com.github.yeicor.ktmpwasm.base.*
import com.github.yeicor.ktmpwasm.interpreter.*

fun parseBinaryModule(data: ByteArray, env: Environment = EmptyEnvironment()): Module {
  val magic = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
  val version = byteArrayOf(0x01, 0x00, 0x00, 0x00)

  expectBytes(data, 0, magic)
  expectBytes(data, 4, version)

  val parser = ByteParser(data, 8)

  val contents = ModuleContents()

  while (!parser.done) {
    val sectionTypeByte = parser.readByte()
    val sectionType = SectionType.of(sectionTypeByte)
    val size = parser.readUnsigned().toInt()

    when (sectionType) {
      SectionType.CUSTOM,
      SectionType.DATA_COUNT -> {
        /* do nothing */
      }
      SectionType.TYPE -> parseTypeSection(contents, parser.copy(size))
      SectionType.IMPORT -> parseImportSection(contents, parser.copy(size))
      SectionType.FUNCTION -> parseFunctionSection(contents, parser.copy(size))
      SectionType.TABLE -> parseTableSection(contents, parser.copy(size))
      SectionType.MEMORY -> parseMemorySection(contents, parser.copy(size))
      SectionType.GLOBAL -> parseGlobalSection(contents, parser.copy(size))
      SectionType.EXPORT -> parseExportSection(contents, parser.copy(size))
      SectionType.START -> parseStartSection(contents, parser.copy(size))
      SectionType.ELEMENT -> parseElementSection(contents, parser.copy(size))
      SectionType.CODE -> parseCodeSection(contents, parser.copy(size))
      SectionType.DATA -> parseDataSection(contents, parser.copy(size))
    }

    parser.advance(size)
  }

  return buildModule(contents, env)
}

fun buildModule(contents: ModuleContents, env: Environment): Module {
  val globals = buildGlobals(contents, env)
  val memory = buildMemory(contents, globals, env)
  val importedFunctions = buildImportedFunctions(contents, env)
  val functions = buildFunctions(contents)
  val exports = buildExports(contents)
  val start = contents.start ?: -1
  val table = buildTargetTable(contents, env)

  val module =
      Module(
          memory = memory,
          functions = functions,
          exports = exports,
          globals = globals,
          start = start,
          table = table,
          importedFunctions = importedFunctions)

  fillTable(module, contents)

  return module
}

fun buildImportedFunctions(contents: ModuleContents, env: Environment): List<FunctionRef> {
  val imports = contents.imports ?: listOf()
  val types = contents.types ?: listOf()

  val refs = mutableListOf<FunctionRef>()

  for (import in imports.filterIsInstance<FunctionImportDef>()) {
    val type =
        types.getOrNull(import.typeIDx)
            ?: throw Error(
                "Cannot retrieve type ${import.typeIDx} for import ${import.module}:${import.name}")

    require(type.result.size <= 1) { "Can only handle a single or no return" }

    val signature =
        Signature(result = type.result.firstOrNull() ?: Type.Void, parameters = type.args)

    val ref = env.lookupFunction(import.module, import.name, signature)
    refs.add(ref)
  }

  return refs
}

fun buildFunctions(contents: ModuleContents): List<WFunction> {
  val types = contents.types ?: listOf()
  val functions = contents.functions ?: listOf()
  val code = contents.code ?: listOf()

  require(functions.size == code.size) {
    "number of functions ${functions.size} != code ${code.size}"
  }

  val resultFunctions = mutableListOf<WFunction>()

  for (idx in code.indices) {
    val typeIDx = functions[idx].toInt()
    val parameters = types[typeIDx].args
    val result =
        types[typeIDx]
            .result
            .also { require(it.size <= 1) { "found ${it.size} types" } }
            .firstOrNull()
            ?: Type.Void
    val locals = code[idx].locals
    val instructions = code[idx].expression

    val resultFunction = WFunction(parameters, locals, result, instructions)
    resultFunctions.add(resultFunction)
  }

  return resultFunctions
}

fun buildExports(contents: ModuleContents): List<Export> {
  val exports = contents.exports ?: listOf()

  val resultExports = mutableListOf<Export>()

  for (idx in exports.indices) {
    val export = exports[idx]

    val resultExport = Export(export.name, export.type, export.idx)
    resultExports.add(resultExport)
  }
  return resultExports
}

fun buildMemory(contents: ModuleContents, globals: List<GlobalRef>, env: Environment): Memory {
  val memories = contents.memories ?: listOf()
  val memoryImports = (contents.imports ?: listOf()).filterIsInstance<MemoryImportDef>()
  val data = contents.data ?: listOf()

  if ((memories.size + memoryImports.size) > 1) {
    throw Error("Can only handle a single memory")
  }

  val result =
      when {
        memoryImports.isNotEmpty() -> {
          val import = memoryImports.first()
          env.lookupMemory(import.module, import.name, import.min, import.max)
        }
        memories.isNotEmpty() -> memories[0].let { Memory(min = it.min, max = it.max) }
        else -> {
          require(data.isEmpty())
          return Memory(min = 0, max = 0)
        }
      }

  for (def in data) {
    val offset = evaluateOffset(def.offset, Module(globals = globals))
    require(def.memory == 0) { "Can only access the first memory" }

    def.init.forEachIndexed { index, byte -> result.data[offset + index] = byte }
  }

  return result
}

fun buildGlobals(contents: ModuleContents, env: Environment): List<GlobalRef> {
  val globals = contents.globals ?: listOf()
  val imports = contents.imports ?: listOf()

  val result: MutableList<GlobalRef> = mutableListOf()

  for (import in imports.filterIsInstance<GlobalImportDef>()) {
    val global = env.lookupGlobal(import.module, import.name, import.type, import.mutable)
    result.add(global)
  }

  for (def in globals) {
    val value = evaluateGlobal(def.init, Module(globals = result), def.type)
    if (value.type != def.type) {
      throw Error("Incompatible types: ${def.type} != ${value.type}")
    }

    val global = Global(mutable = def.mutable, kind = def.type, value = value)

    result.add(global)
  }

  return result
}

fun buildTargetTable(contents: ModuleContents, env: Environment): Table {
  val tables = contents.tables ?: listOf()
  val tableImports = (contents.imports ?: listOf()).filterIsInstance<TableImportDef>()

  if ((tables.size + tableImports.size) > 1) {
    throw Error("A module can have at most one table (imported or defined)")
  }

  return when {
    tables.isNotEmpty() -> {
      val (min, _) = tables.first()
      Table(MutableList(min) { null })
    }
    tableImports.isNotEmpty() -> {
      val import = tableImports.first()
      env.lookupTable(import.module, import.name, import.min, import.max)
    }
    else -> Table()
  }
}

fun fillTable(module: Module, contents: ModuleContents) {
  val elements = contents.elements ?: listOf()

  for (element in elements) {
    // NOTE: globals can be accessed in the offset expressions.
    val offset = evaluateOffset(element.offset, Module(globals = module.globals))

    for ((index, funcIdx) in element.init.withIndex()) {
      module.table[offset + index] = module.refFunction(funcIdx)
    }
  }
}

fun evaluateGlobal(expr: List<Instruction>, module: Module, type: Type): WasmValue {
  val ctx = ExecutionContext(module)
  ctx.execute(expr)
  return ctx.pop().toType(type)
}

fun evaluateOffset(expr: List<Instruction>, module: Module): Int {
  val ctx = ExecutionContext(module)
  ctx.execute(expr)
  return ctx.stackLast().toI32()
}
