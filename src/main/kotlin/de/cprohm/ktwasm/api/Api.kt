package de.cprohm.ktwasm.api

import de.cprohm.ktwasm.base.*
import de.cprohm.ktwasm.base.Environment
import de.cprohm.ktwasm.base.F32Value
import de.cprohm.ktwasm.base.F64Value
import de.cprohm.ktwasm.base.FunctionRef
import de.cprohm.ktwasm.base.GlobalRef
import de.cprohm.ktwasm.base.I32Value
import de.cprohm.ktwasm.base.I64Value
import de.cprohm.ktwasm.base.MapEnvironment
import de.cprohm.ktwasm.base.Memory
import de.cprohm.ktwasm.base.Namespace
import de.cprohm.ktwasm.base.Signature
import de.cprohm.ktwasm.base.Table
import de.cprohm.ktwasm.base.Type
import de.cprohm.ktwasm.base.UnitValue
import de.cprohm.ktwasm.base.WasmValue
import de.cprohm.ktwasm.binary.parseBinaryModule
import java.io.File
import java.net.URL

// re-export the main types
typealias Namespace = Namespace

typealias Environment = Environment
typealias MapEnvironment = MapEnvironment

typealias Memory = Memory
typealias FunctionRef = FunctionRef
typealias GlobalRef = GlobalRef
typealias Table = Table

typealias WasmValue = WasmValue
typealias I32Value = I32Value
typealias I64Value = I64Value
typealias F32Value = F32Value
typealias F64Value = F64Value
typealias UnitValue = UnitValue

typealias Type = Type
typealias Signature = Signature

fun parseModule(url: URL, env: Environment = EmptyEnvironment()): Namespace =
    parseBinaryModule(url.path, url.readBytes(), env)

fun parseModule(file: File, env: Environment = EmptyEnvironment()): Namespace =
    parseBinaryModule(file.name, file.readBytes(), env)

operator fun FunctionRef.invoke(vararg arguments: Any): Any? =
    call(arguments.map { WasmValue.wrap(it) }.toList()).unrwap()

operator fun Namespace.get(name: String): Any? =
    this.lookupGlobal(name, null, null).get()?.unrwap()

operator fun Namespace.set(name: String, value: Any): Unit =
    this.lookupGlobal(name, null, null).set(WasmValue.wrap(value))
