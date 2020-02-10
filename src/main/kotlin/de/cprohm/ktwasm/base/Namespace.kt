package de.cprohm.ktwasm.base

/**
 * A collection of definitions that can be imported
 */
interface Namespace {
    /**
     * Initialize the namespace (e.g., call the start function)
     */
    fun init() {

    }

    /**
     * Create a full copy of the namespace
     */
    fun clone(): Namespace {
        throw Error("Cloning is not supported")
    }

    /**
     * Lookup a global in this namespace
     */
    fun lookupGlobal(name: String, type: Type?, mutable: Boolean?): GlobalRef {
        throw Error("Cannot find global $name")
    }

    /**
     * Lookup a function in this namespace
     */
    fun lookupFunction(name: String, signature: Signature?): FunctionRef {
        throw Error("Cannot find function $name")
    }

    /**
     * Loopup a memory in this namespace
     */
    fun lookupMemory(name: String, min: Int, max: Int): Memory {
        throw Error("Cannot find memory $name")
    }

    fun lookupTable(name: String, min: Int, max: Int): Table {
        throw Error("Cannot find table $name")
    }
}

/**
 * Reference to a global stored in a namespace
 */
interface GlobalRef {
    fun set(value: WasmValue)
    fun get(): WasmValue?
}

/**
 * Reference to a function stored in a namespace
 */
interface FunctionRef {
    fun call(arguments: List<WasmValue>): WasmValue
    fun type(): Signature
}

/**
 * Describe the signature of a function
 */
data class Signature(val result: Type, val parameters: List<Type>) {
    companion object {
        fun of(result: Type, vararg parameters: Type): Signature =
            Signature(result, parameters.toList())
    }
}
