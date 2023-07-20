package com.github.yeicor.ktmpwasm.base

/** A collection of definitions that can be imported */
interface Namespace {
  /** Initialize the namespace (e.g., call the start function) */
  fun init() {}

  fun clone(): Namespace {
    throw Error("Cloning is not supported")
  }

  fun lookupGlobal(name: String, type: Type?, mutable: Boolean?): GlobalRef {
    throw Error("Cannot find global $name")
  }

  fun lookupFunction(name: String, signature: Signature?): FunctionRef {
    throw Error("Cannot find function $name")
  }

  fun lookupMemory(name: String, min: Int, max: Int): Memory {
    throw Error("Cannot find memory $name")
  }

  fun lookupTable(name: String, min: Int, max: Int): Table {
    throw Error("Cannot find table $name")
  }
}

/** A single nested hierarchy of importable objects */
interface Environment {
  fun lookupGlobal(module: String, name: String, type: Type?, mutable: Boolean?): GlobalRef {
    throw Error("Cannot find global $module.$name")
  }

  fun lookupFunction(module: String, name: String, signature: Signature?): FunctionRef {
    throw Error("Cannot find function $module.$name")
  }

  fun lookupMemory(module: String, name: String, min: Int, max: Int): Memory {
    throw Error("Cannot find memory $name")
  }

  fun lookupTable(module: String, name: String, min: Int, max: Int): Table {
    throw Error("Cannot find table $name")
  }
}

/** A baseline Environment based on a fixed map of Namespaces */
class MapEnvironment(private val namespaces: Map<String, Namespace>) : Environment {
  private fun get(module: String): Namespace =
      namespaces[module] ?: throw Error("Cannot find module $module in ${namespaces.keys}")

  override fun lookupFunction(module: String, name: String, signature: Signature?): FunctionRef =
      get(module).lookupFunction(name, signature)

  override fun lookupGlobal(
      module: String,
      name: String,
      type: Type?,
      mutable: Boolean?
  ): GlobalRef = get(module).lookupGlobal(name, type, mutable)

  override fun lookupMemory(module: String, name: String, min: Int, max: Int): Memory =
      get(module).lookupMemory(name, min, max)

  override fun lookupTable(module: String, name: String, min: Int, max: Int): Table =
      get(module).lookupTable(name, min, max)
}

class EmptyEnvironment : Environment

/** Reference to a global stored in a namespace */
interface GlobalRef {
  fun set(value: WasmValue)
  fun get(): WasmValue?
  fun type(): Type
}

/** Reference to a function stored in a namespace */
interface FunctionRef {
  fun call(arguments: List<WasmValue>): WasmValue
  fun type(): Signature
}

/** Describe the signature of a function */
data class Signature(val result: Type, val parameters: List<Type>) {
  companion object {
    fun of(result: Type, vararg parameters: Type): Signature =
        Signature(result, parameters.toList())
  }
}
