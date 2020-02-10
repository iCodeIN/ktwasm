package de.cprohm.ktwasm.api

import de.cprohm.ktwasm.base.FunctionRef
import de.cprohm.ktwasm.base.GlobalRef
import de.cprohm.ktwasm.base.Memory
import de.cprohm.ktwasm.base.Namespace
import de.cprohm.ktwasm.base.Table
import de.cprohm.ktwasm.base.WasmValue
import de.cprohm.ktwasm.binary.parseBinaryModule
import java.io.File
import java.net.URL

// re-export the main types
typealias Namespace = Namespace

typealias Memory = Memory
typealias FunctionRef = FunctionRef
typealias GlobalRef = GlobalRef
typealias Table = Table
typealias WasmValue = WasmValue

fun parseModule(url: URL, env: Map<String, Namespace> = mapOf()): Namespace =
    parseBinaryModule(url.path, url.readBytes(), env)

fun parseModule(file: File, env: Map<String, Namespace> = mapOf()): Namespace =
    parseBinaryModule(file.name, file.readBytes(), env)

operator fun FunctionRef.invoke(vararg arguments: Any): Any? =
    call(arguments.map { WasmValue.wrap(it) }.toList()).unrwap()

operator fun Namespace.get(name: String): Any? =
    this.lookupGlobal(name, null, null).get()?.unrwap()

operator fun Namespace.set(name: String, value: Any): Unit =
    this.lookupGlobal(name, null, null).set(WasmValue.wrap(value))
