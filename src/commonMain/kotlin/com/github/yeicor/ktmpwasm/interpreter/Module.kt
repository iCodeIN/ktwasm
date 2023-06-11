package com.github.yeicor.ktmpwasm.interpreter

import com.github.yeicor.ktmpwasm.base.*

data class Module(
    val memory: Memory = Memory(0, 0),
    val table: Table = Table(),
    val functions: List<WFunction> = listOf(),
    val exports: List<Export> = listOf(),
    val globals: List<GlobalRef> = listOf(),
    val start: Int = -1,
    val importedFunctions: List<FunctionRef> = listOf()
) : Namespace {
  private val exportsByName: Map<String, Export> = exports.associateBy { it.name }

  fun lookup(name: String): FunctionRef {
    return lookupFunction(name, null)
  }

  override fun init() {
    if (start < 0) {
      return
    }
    refFunction(start).call(listOf()).assertVoid()
  }

  fun refFunction(idx: Int): FunctionRef =
      when {
        idx < importedFunctions.size -> importedFunctions[idx]
        idx < (importedFunctions.size + functions.size) -> {
          val localIdx = idx - importedFunctions.size
          BoundFunction(this, functions[localIdx])
        }
        else -> throw Error("Cannot resolve function $idx")
      }

  fun refFunctionTable(idx: Int): FunctionRef =
      table.getOrNull(idx) ?: throw Error("Uninitialized function $idx in dynamic call")

  fun refGlobal(idx: Int): GlobalRef =
      globals.getOrNull(idx) ?: throw Error("Cannot resolve global $idx")

  override fun clone(): Module =
      Module(
          memory = memory.clone(),
          table = table.clone(),
          functions = functions,
          exports = exports,
          globals = globals,
          start = start,
          importedFunctions = importedFunctions)

  override fun lookupFunction(name: String, signature: Signature?): FunctionRef {
    val export = exportsByName[name] ?: throw Error("Lookup error, cannot find export $name")
    if (export.type != ExportType.FUNCTION) {
      throw Error("Export $name is not a function")
    }

    return refFunction(export.reference)
  }

  override fun lookupGlobal(name: String, type: Type?, mutable: Boolean?): GlobalRef {
    val export = exportsByName[name] ?: throw Error("Cannot find export $name")
    if (export.type != ExportType.GLOBAL) {
      throw Error("Export $name is not a global")
    }
    return this.globals.getOrNull(export.reference)
        ?: throw Error("Cannot resolve global ${export.reference}")
  }

  override fun lookupMemory(name: String, min: Int, max: Int): Memory {
    val export = exportsByName[name] ?: throw Error("Cannot find export $name")
    if (export.type != ExportType.MEMORY) {
      throw Error("Export $name is not a memory")
    }
    if (export.reference != 0) {
      throw Error("Cannot resolve memory ${export.reference}")
    }
    return memory
  }

  override fun lookupTable(name: String, min: Int, max: Int): Table {
    val export = exportsByName[name] ?: throw Error("Cannot find export $name")
    if (export.type != ExportType.TABLE) {
      throw Error("Export $name is not a table")
    }
    if (export.reference != 0) {
      throw Error("Cannot resolve table ${export.reference}")
    }
    return table
  }
}

data class BoundFunction(val module: Module, val function: WFunction) : FunctionRef {
  override fun call(arguments: List<WasmValue>): WasmValue {
    return call(module, function, arguments)
  }

  override fun type(): Signature = Signature(function.result, function.parameters)
}

data class WFunction( // Avoid name conflict with Function on JS
    val parameters: List<Type>,
    val locals: List<Type>,
    val result: Type,
    val instructions: List<Instruction>
)

data class Export(val name: String, val type: ExportType, val reference: Int)

data class Global(val mutable: Boolean = true, val kind: Type, var value: WasmValue? = null) :
    GlobalRef {
  override fun get(): WasmValue? = value

  override fun set(value: WasmValue) {
    if (!mutable) {
      throw Error("Cannot set immutable global")
    }
    if (kind != value.type) {
      throw Error("Type mismatch for global expected ${kind}, got ${value.type}")
    }
    this.value = value
  }

  override fun type(): Type = kind
}
