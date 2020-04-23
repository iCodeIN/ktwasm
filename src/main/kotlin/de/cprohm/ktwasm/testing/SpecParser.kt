package de.cprohm.ktwasm.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.cprohm.ktwasm.base.*
import de.cprohm.ktwasm.binary.parseBinaryModule
import de.cprohm.ktwasm.interpreter.*
import java.io.File
import java.math.BigInteger

interface FileLoader {
    fun readBytes(path: String): ByteArray
}

typealias Step = (env: MutableMap<String, Namespace>) -> Unit

data class Spec(val steps: List<Step>) {
    fun run() {
        val env = mutableMapOf<String, Namespace>("spectest" to SpectestNamespace())
        for (step in steps) {
            step(env)
        }
    }
}

fun parseSpec(fileloader: FileLoader, spec: String, ignore: Set<String>, path: String = "binary"): Spec {
    val actions: MutableList<Step> = mutableListOf()

    for (command in readCommands(fileloader, spec, path)) {
        val type = command.get("type").asText()
        val line = command.getOrNull("line")?.asInt() ?: 0

        if (ignore.contains("$spec:$line")) {
            continue
        }

        if (type == "module") {
            val filename = command.get("filename").asText()
            actions.add(makeLoadModuleAction(fileloader, path, filename, file = spec, line = line))

            if (command.has("name")) {
                val name = command.get("name").asText().trimStart('$')
                actions.add(makeRegisterModuleAction(name, file = spec, line = line))
            }
        } else if (
            (type == "assert_return") ||
            (type == "assert_return_canonical_nan") ||
            (type == "assert_return_arithmetic_nan")
        ) {
            val actual = Action.parse(command.get("action"))
            val expected = parseExpected(command.get("expected"))
            actions.add(makeAssertReturnAction(actual, expected, file = spec, line = line))
        } else if (type == "assert_trap") {
            val code = Action.parse(command.get("action"))
            val message = command.get("text").asText()
            actions.add(makeAssertTrapAction(code, message, file = spec, line = line))
        } else if (type == "action") {
            val code = Action.parse(command.get("action"))
            actions.add(makeSideEffectAction(code, file = spec, line = line))
        } else if (type == "register") {
            val name = command.get("as").asText().trimStart('$')
            actions.add(makeRegisterModuleAction(name, file = spec, line = line))
        } else if (
            (type == "assert_invalid") ||
            (type == "assert_malformed") ||
            (type == "assert_exhaustion") ||
            (type == "assert_uninstantiable") ||
            (type == "assert_unlinkable")
        ) {
            // IGNORE
        } else {
            throw Error("Unknown command $type")
        }
    }

    return Spec(actions)
}

fun readCommands(fileloader: FileLoader, spec: String, path: String = "binary"): JsonNode {
    val fullpath = File(path, "${spec}.json").toString()
    val lines = String(fileloader.readBytes(fullpath))
    val tree = ObjectMapper().readTree(lines)

    return tree.get("commands").also { assert(it.isArray) }
}

fun makeLoadModuleAction(fileloader: FileLoader, path: String, filename: String, file: String, line: Int): Step =
    { env ->
        try {
            val fullpath = File(path, filename).toString()
            val data = fileloader.readBytes(fullpath)

            env[""] = parseBinaryModule(filename, data, MapEnvironment(env)).also { it.init() }
        } catch (e: Throwable) {
            throw Error("Could not load module ($file:$line)", e)
        }
    }

fun makeRegisterModuleAction(name: String, file: String, line: Int): Step =
    { env ->
        env[name] = env[""] ?: throw Error("No module to register ($file:$line)")
    }

fun makeSideEffectAction(action: Action, file: String, line: Int): Step =
    { env ->
        try {
            action.invoke(env)
        } catch (e: Throwable) {
            throw Error("Error ($file:$line)", e)
        }
    }

fun makeAssertReturnAction(
    action: Action,
    expected: List<WasmValue>,
    file: String, line: Int
): Step =
    { env ->
        val actual = try {
            action.invoke(env)
        } catch (e: Throwable) {
            throw Error("Error during execution of $file:$line: $e", e)
        }
        if (actual != expected) {
            throw AssertionError("Expected $expected, found $actual ($file:$line)")
        }
    }

fun makeAssertTrapAction(
    action: Action,
    message: String,
    file: String,
    line: Int
): Step =
    { env ->

        try {
            action.invoke(env)
            throw Error("Expected: $message ($file@$line)")
        } catch (e: ExecutionError) {

        } catch (e: Throwable) {
            throw Error("Error ($file@$line)", e)
        }
    }

fun JsonNode.getOrNull(name: String): JsonNode? = if (has(name)) get(name) else null

interface Action {
    fun invoke(env: Map<String, Namespace>): List<WasmValue>

    companion object {
        fun parse(node: JsonNode): Action {
            val type = node.get("type").asText()

            if (type == "invoke") {
                val args = node.get("args").map(::parseJsonValue).toList()
                val module = (node.getOrNull("module")?.asText() ?: "").trimStart('$')
                val function = node.get("field").asText()

                return InvokeAction(module = module, function = function, args = args)
            } else if (type == "get") {
                val module = (node.getOrNull("module")?.asText() ?: "").trimStart('$')
                val name = node.get("field").asText()

                return GetAction(module = module, name = name)
            } else {
                throw Error("Unknown action $type")
            }
        }
    }
}

data class InvokeAction(val module: String, val function: String, val args: List<WasmValue>) :
    Action {
    override fun invoke(env: Map<String, Namespace>): List<WasmValue> {
        val module = env[this.module] ?: throw Error("Cannot resolve module: '${this.module}'")
        val function = module.lookupFunction(this.function, null)
        return function.call(args).toList()
    }
}

data class GetAction(val module: String, val name: String) : Action {
    override fun invoke(env: Map<String, Namespace>): List<WasmValue> {
        val module = env[this.module] ?: throw Error("Cannot resolve module: '${this.module}'")
        val global = module.lookupGlobal(name, null, null)
        return (global.get() ?: throw Error("Uninitialized global $name")).toList()
    }
}

fun parseExpected(node: JsonNode): List<WasmValue> {
    return node.map(::parseJsonValue).toList()
}

fun parseJsonValue(node: JsonNode): WasmValue = when (val type = node.get("type").asText()) {
    "i32" -> {
        val value = node.getOrNull("value")?.asText() ?: "0"
        I32Value(BigInteger(value).toInt())
    }
    "i64" -> {
        val value = node.getOrNull("value")?.asText() ?: "0"
        I64Value(BigInteger(value).toLong())
    }
    "f32" -> {
        val value = node.getOrNull("value")?.asText() ?: "nan:any"
        if (value.startsWith("nan:")) {
            F32Value(Float.NaN)
        } else {
            F32Value(Float.fromBits(BigInteger(value).toInt()))
        }
    }
    "f64" -> {
        val value = node.getOrNull("value")?.asText() ?: "nan:any"
        if (value.startsWith("nan:")) {
            F64Value(Double.NaN)
        } else {
            F64Value(Double.fromBits(BigInteger(value).toLong()))
        }
    }
    else -> throw Error("Unknown value type $type")
}

class SpectestNamespace : Namespace {
    val globals = mapOf<String, GlobalRef>(
        "global_i32" to Global(true, Type.I32, I32Value(666)),
        "global_i64" to Global(true, Type.I64, I64Value(666)),
        "global_f32" to Global(true, Type.F32, F32Value(666.6f)),
        "global_f64" to Global(true, Type.F64, F64Value(666.6))
    )

    val functions = mapOf<String, FunctionRef>(
        "print" to NoOpFunction(Type.Void),
        "print_i32" to NoOpFunction(Type.Void, Type.I32),
        "print_i32_f32" to NoOpFunction(Type.Void, Type.I32, Type.F32),
        "print_f64_f64" to NoOpFunction(Type.Void, Type.F64, Type.F64),
        "print_f32" to NoOpFunction(Type.Void, Type.F32),
        "print_f64" to NoOpFunction(Type.Void, Type.F64)
    )

    val memories = mapOf<String, Memory>("memory" to Memory(1, 2))
    val tables = mapOf<String, Table>("table" to Table(MutableList(10) { null }))

    override fun lookupGlobal(name: String, type: Type?, mutable: Boolean?): GlobalRef =
        globals[name] ?: throw Error("Cannot resolve global $name")

    override fun lookupFunction(name: String, signature: Signature?): FunctionRef =
        functions[name] ?: throw Error("Cannot resolve function $name")

    override fun lookupMemory(name: String, min: Int, max: Int): Memory =
        memories[name] ?: throw Error("Cannot resolve memory $name")

    override fun lookupTable(name: String, min: Int, max: Int): Table =
        tables[name] ?: throw Error("Cannot resolve table $name")

    override fun clone(): Namespace = SpectestNamespace()

    override fun init() = noop()
}

class NoOpFunction(private val result: Type, private vararg val parameters: Type) : FunctionRef {
    override fun type(): Signature = Signature(result = result, parameters = parameters.toList())
    override fun call(arguments: List<WasmValue>): WasmValue = UnitValue()
}

fun noop(): Unit = Unit
