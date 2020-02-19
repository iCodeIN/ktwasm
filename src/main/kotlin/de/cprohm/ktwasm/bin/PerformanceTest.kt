package de.cprohm.ktwasm.bin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.cprohm.ktwasm.api.*
import java.io.File

/**
 * Execute a performance test using a wasm-bindgen based module
 */
fun main(args: Array<String>) {
    try {
        println("ktwasm performance test")
        if (args.size != 1) {
            throw Error("Wrong number of arguments. Please call with the spec file.")
        }
        PerformanceTest.main(args[0])
    } catch (e: Throwable) {
        println("Error: $e")
    }
}

object PerformanceTest {
    private val objectMapper = ObjectMapper()

    fun main(path: String) {
        println("Execute performance test $path")

        val specFile = File(path)
        val spec = objectMapper.readTree(specFile)

        val state = buildState(spec)
        val moduleFile = File(specFile.parentFile, spec.get("module").asText())

        println("Build module")
        val wrapper = buildWrapper(moduleFile, state)

        val warmup = spec.getOrNull("warmup")?.asInt() ?: 5
        val reps = spec.getOrNull("repeat")?.asInt() ?: 10
        val callSpecs = buildCallSpecs(spec)

        println("Wramup ${callSpecs.size} calls for $warmup reps")
        val (warmupTime, avgWarmupTime) = executeWarmup(wrapper, callSpecs, warmup)
        println("Took:  %.2f s".format(warmupTime / 1000))
        println("Average: %.2f ms".format(avgWarmupTime))

        println("Time execution of ${callSpecs.size} calls for $reps reps")
        val (executionTime, avgExecutionTime) = timeExecution(wrapper, callSpecs, reps)
        println("Took: %.2f s".format(executionTime / 1000))
        println("Average: %.2f ms".format(avgExecutionTime))
    }

    private fun executeWarmup(
        wrapper: ModuleWrapper,
        callSpecs: List<Pair<JsonNode, JsonNode>>,
        reps: Int
    ): Pair<Double, Double> {
        val start = System.currentTimeMillis()
        for (rep in 0 until reps) {
            for ((input, expected) in callSpecs) {
                val actual = wrapper.handle(input)

                if (actual != expected) {
                    throw Error("Error in case: actual ($actual) != expected ($expected)")
                }
            }
        }
        val end = System.currentTimeMillis()
        return Pair(
            (end - start).toDouble(),
            (end - start).toDouble() / (callSpecs.size * reps).toDouble()
        )
    }

    private fun timeExecution(
        wrapper: ModuleWrapper,
        callSpecs: List<Pair<JsonNode, JsonNode>>,
        reps: Int
    ): Pair<Double, Double> {
        val start = System.currentTimeMillis()
        for (rep in 0 until reps) {
            for ((input, _) in callSpecs) {
                wrapper.handle(input)
            }
        }

        val end = System.currentTimeMillis()
        return Pair(
            (end - start).toDouble(),
            (end - start).toDouble() / (callSpecs.size * reps).toDouble()
        )
    }

    private fun buildState(spec: JsonNode) =
        spec.getOrNull("state").let {
            val result = mutableMapOf<String, JsonNode>()
            it?.fields()?.forEach { entry -> result[entry.key] = entry.value }
            result
        }

    private fun buildWrapper(moduleFile: File, state: Map<String, JsonNode>): ModuleWrapper {
        if (!moduleFile.exists()) {
            throw Error("Cannot find module $moduleFile")
        }
        return ModuleWrapper.build(moduleFile, state)
    }

    private fun buildCallSpecs(spec: JsonNode): List<Pair<JsonNode, JsonNode>> {
        val calls = mutableListOf<Pair<JsonNode, JsonNode>>()
        for (call in spec.get("calls")) {
            calls.add(Pair(call.get("input"), call.get("expected")))
        }
        return calls
    }
}

fun JsonNode.getOrNull(name: String): JsonNode? = if (has(name)) get(name) else null

class ModuleWrapper(val module: Namespace, val state: Map<String, JsonNode>) {
    val objectMapper = ObjectMapper()
    val iface: WbindgenInterface = WbindgenInterface(
        module = module,
        fromJson = { content -> objectMapper.readTree(content) },
        toJson = { obj -> objectMapper.writeValueAsString(obj) })

    companion object {
        fun build(file: File, state: Map<String, JsonNode>): ModuleWrapper {
            val exports = Exports()
            val env = SingleDynamicModuleEnv(exports)
            val module = parseModule(file, env)
            module.init()

            return ModuleWrapper(module, state).also { it.bind(exports) }
        }
    }

    fun handle(input: JsonNode): JsonNode {
        val idx = iface.heap.add(input)
        val ret = wasm_handler.call(listOf(I32Value(idx))).toI32()
        return iface.heap.take(ret) as JsonNode
    }

    val wasm_handler = module.lookupFunction("handle", Signature.of(Type.I32, Type.I32))

    fun lookup(key: String): JsonNode? = state[key]

    val wbg_lookup = object : FunctionRef {
        override fun call(arguments: List<WasmValue>): WasmValue {
            assert(arguments.size == 2)
            val name = iface.getStringFromWasm(arguments[0].toI32(), arguments[1].toI32())
            val obj = lookup(name)
            return iface.heap.add(obj).let(::I32Value)
        }

        override fun type(): Signature = Signature.of(Type.I32, Type.I32, Type.I32)
    }

    class Exports : Namespace {
        val wbindgen_json_parse = ForwardFunctionRef.of(Type.I32, Type.I32, Type.I32)
        val wbindgen_object_drop_ref = ForwardFunctionRef.of(Type.Void, Type.I32)
        val wbg_lookup = ForwardFunctionRef.of(Type.I32, Type.I32, Type.I32)
        val wbindgen_json_serialize = ForwardFunctionRef.of(Type.Void, Type.I32, Type.I32)

        override fun lookupFunction(name: String, signature: Signature?): FunctionRef =
            if (name.startsWith("__wbg_lookup_")) {
                wbg_lookup
            } else when (name) {
                "__wbindgen_json_parse" -> wbindgen_json_parse
                "__wbindgen_object_drop_ref" -> wbindgen_object_drop_ref
                "__wbindgen_json_serialize" -> wbindgen_json_serialize
                else -> throw Error("Cannot find $name")
            }
    }

    fun bind(exports: Exports) {
        exports.wbindgen_json_parse.bind(iface.wbindgen_json_parse)
        exports.wbg_lookup.bind(wbg_lookup)
        exports.wbindgen_object_drop_ref.bind(iface.wbindgen_object_drop_ref)
        exports.wbindgen_json_serialize.bind(iface.wbindgen_json_serialize)
    }
}

class SingleDynamicModuleEnv(val namespace: Namespace) : Environment {
    private val seen: MutableSet<String> = mutableSetOf()

    private fun get(module: String): Namespace {
        if (seen.isEmpty()) {
            seen.add(module)
        }
        if (!seen.contains(module)) {
            throw Error("Cannot lookup different modules (before $seen, now: $module)")
        }
        return namespace
    }

    override fun lookupFunction(
        module: String,
        name: String,
        signature: Signature?
    ): de.cprohm.ktwasm.base.FunctionRef =
        get(module).lookupFunction(name, signature)

    override fun lookupGlobal(
        module: String,
        name: String,
        type: Type?,
        mutable: Boolean?
    ): GlobalRef =
        get(module).lookupGlobal(name, type, mutable)

    override fun lookupMemory(module: String, name: String, min: Int, max: Int): Memory =
        get(module).lookupMemory(name, min, max)

    override fun lookupTable(module: String, name: String, min: Int, max: Int): Table =
        get(module).lookupTable(name, min, max)
}

class WbindgenInterface(
    val module: Namespace,
    val fromJson: (String) -> Any?,
    val toJson: (Any?) -> String
) {
    val heap: WbindgenHeap = WbindgenHeap()

    val memory: Memory by lazy {
        module.lookupMemory("memory", 0, 0)
    }
    val wbindgen_malloc: FunctionRef by lazy {
        module.lookupFunction("__wbindgen_malloc", Signature.of(Type.I32, Type.I32))
    }
    val wbindgen_realloc: FunctionRef by lazy {
        module.lookupFunction("__wbindgen_realloc", Signature.of(Type.I32, Type.I32, Type.I32, Type.I32))
    }
    val wbindgen_free: FunctionRef by lazy {
        module.lookupFunction("__wbindgen_free", Signature.of(Type.Void, Type.I32, Type.I32))
    }

    fun passStringToWasm(s: String): Pair<Int, Int> {
        val buf = s.toByteArray()
        val ptr = malloc(buf.size)
        memory.store(ptr, buf.size, buf)
        return Pair(ptr, buf.size)
    }

    fun getStringFromWasm(ptr: Int, size: Int): String {
        val bytes = memory.load(ptr, size)
        return String(bytes)
    }

    fun malloc(size: Int): Int = wbindgen_malloc.call(listOf(I32Value(size))).toI32()

    // translated from:
    // type: (i32, i32) -> i32
    // export const __wbindgen_json_parse = function(arg0, arg1) {
    //   var ret = JSON.parse(getStringFromWasm0(arg0, arg1));
    //   return addHeapObject(ret);
    // }
    val wbindgen_json_parse = object : FunctionRef {
        override fun call(arguments: List<WasmValue>): WasmValue {
            val ptr0 = arguments[0].toI32()
            val len0 = arguments[1].toI32()
            val content = getStringFromWasm(ptr0, len0)
            val obj = fromJson(content)
            return heap.add(obj).let(::I32Value)
        }

        override fun type(): Signature = Signature.of(Type.I32, Type.I32, Type.I32)
    }

    // translated from
    // type: (i32) -> nil
    // export const __wbindgen_object_drop_ref = function(arg0) {
    //   takeObject(arg0);
    // };
    val wbindgen_object_drop_ref = object : FunctionRef {
        override fun call(arguments: List<WasmValue>): WasmValue {
            val idx = arguments[0].toI32()
            heap.take(idx)
            return UnitValue()
        }

        override fun type(): Signature = Signature.of(Type.Void, Type.I32)
    }

    // type: (i32, i32) -> nil
    // export const __wbindgen_json_serialize = function(arg0, arg1) {
    //   const obj = getObject(arg1);
    //   var ret = JSON.stringify(obj === undefined ? null : obj);
    //   var ptr0 = passStringToWasm0(ret, wasm.__wbindgen_malloc, wasm.__wbindgen_realloc);
    //   var len0 = WASM_VECTOR_LEN;
    //   getInt32Memory0()[arg0 / 4 + 1] = len0;
    //   getInt32Memory0()[arg0 / 4 + 0] = ptr0;
    //   };
    val wbindgen_json_serialize = object : FunctionRef {
        override fun call(arguments: List<WasmValue>): WasmValue {
            val address = arguments[0].toI32()
            val idx = arguments[1].toI32()
            val data = heap.get(idx).let(toJson)
            val (ptr0, len0) = passStringToWasm(data)
            memory.storeI32(address, 0, ptr0)
            memory.storeI32(address + 4, 0, len0)
            return UnitValue()
        }

        override fun type(): Signature = Signature.of(Type.Void, Type.I32, Type.I32)
    }
}

/// A heap as used by wbindgen
class WbindgenHeap {
    // TODO: rewrite heap to no mix indices and object in the same structure
    private val heap: MutableList<Any?> = MutableList(32) { null }

    init {
        heap.add("undefined")
        heap.add("null")
        heap.add("true")
        heap.add("false")
    }

    var heapNext: Int = heap.size

    fun add(obj: Any?): Int {
        if (heapNext == heap.size) {
            heap.add(heap.size + 1)
        }
        val idx = heapNext;
        heapNext = heap[idx] as Int

        heap[idx] = obj
        return idx
    }

    fun drop(idx: Int) {
        if (idx < 36) {
            return
        }

        heap[idx] = heapNext
        heapNext = idx
    }

    fun get(idx: Int): Any? = heap[idx]
    fun take(idx: Int): Any? = get(idx).also { drop(idx) }
}

class ForwardFunctionRef(val signature: Signature, var ref: FunctionRef? = null) : FunctionRef {
    companion object {
        fun of(result: Type, vararg parameters: Type): ForwardFunctionRef =
            ForwardFunctionRef(Signature(result, parameters.toList()))
    }

    private val boundRef: FunctionRef by lazy { ref ?: throw Error("Cannot call unbound forward ref") }

    override fun type(): Signature = signature
    override fun call(arguments: List<WasmValue>): WasmValue = boundRef.call(arguments)

    fun bind(ref: FunctionRef) {
        if (this.ref != null) {
            throw Error("Cannot rebind forward ref")
        }

        if (this.signature != ref.type()) {
            throw Error("Incompatible signatures when binding forward function ref")
        }
        this.ref = ref
    }
}
