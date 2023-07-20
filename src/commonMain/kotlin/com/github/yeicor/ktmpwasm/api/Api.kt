@file:Suppress("unused")

package com.github.yeicor.ktmpwasm.api

import com.github.yeicor.ktmpwasm.base.*
import com.github.yeicor.ktmpwasm.base.Environment
import com.github.yeicor.ktmpwasm.base.F32Value
import com.github.yeicor.ktmpwasm.base.F64Value
import com.github.yeicor.ktmpwasm.base.FunctionRef
import com.github.yeicor.ktmpwasm.base.GlobalRef
import com.github.yeicor.ktmpwasm.base.I32Value
import com.github.yeicor.ktmpwasm.base.I64Value
import com.github.yeicor.ktmpwasm.base.MapEnvironment
import com.github.yeicor.ktmpwasm.base.Memory
import com.github.yeicor.ktmpwasm.base.Namespace
import com.github.yeicor.ktmpwasm.base.Signature
import com.github.yeicor.ktmpwasm.base.Table
import com.github.yeicor.ktmpwasm.base.Type
import com.github.yeicor.ktmpwasm.base.UnitValue
import com.github.yeicor.ktmpwasm.base.WasmValue
import com.github.yeicor.ktmpwasm.binary.parseBinaryModule
import com.github.yeicor.ktmpwasm.interpreter.Module

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

fun parseModule(bytes: ByteArray, env: Environment = EmptyEnvironment()): Module =
    parseBinaryModule(bytes, env)

operator fun FunctionRef.invoke(vararg arguments: Any): Any? =
    call(arguments.map { WasmValue.wrap(it) }.toList()).unwrap()

operator fun Namespace.get(name: String): Any? = this.lookupGlobal(name, null, null).get()?.unwrap()

operator fun Namespace.set(name: String, value: Any): Unit =
    this.lookupGlobal(name, null, null).set(WasmValue.wrap(value))
