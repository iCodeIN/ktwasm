package de.cprohm.ktwasm.base

import de.cprohm.ktwasm.interpreter.F64
import de.cprohm.ktwasm.interpreter.I64

/**
 * Marker for WASM types
 */
enum class Type { I32, I64, F32, F64, Void }

/**
 * A wasm value
 */
sealed class WasmValue {
    companion object {
        fun fromType(type: Type): WasmValue = when (type) {
            Type.Void -> throw Error("Cannot create a void variable")
            Type.I32 -> I32Value(0)
            Type.I64 -> I64Value(0L)
            Type.F32 -> F32Value(0.0f)
            Type.F64 -> F64Value(0.0)
        }

        fun wrap(obj: Any): WasmValue = when (obj) {
            is I32Value -> obj
            is I64Value -> obj
            is F32Value -> obj
            is F64Value -> obj
            is Int -> I32Value(obj)
            is Long -> I64Value(obj)
            is Float -> F32Value(obj)
            is Double -> F64Value(obj)
            else -> throw Error("Cannot convert ${this::class.qualifiedName} to a WasmValue")
        }
    }

    fun unrwap(): Any? = when (this) {
        is I32Value -> this.value
        is I64Value -> this.value
        is F32Value -> this.value
        is F64Value -> this.value
        is UnitValue -> null
    }

    fun toI32(): Int = when (this) {
        is I32Value -> value
        else -> throw Error("Cannot convert ${this::class.qualifiedName} to I32")
    }

    fun toI64(): Long = when (this) {
        is I64Value -> value
        else -> throw Error("Cannot convert ${this::class.qualifiedName} to I64")
    }

    fun toF32(): Float = when (this) {
        is F32Value -> value
        else -> throw Error("Cannot convert ${this::class.qualifiedName} to F32")
    }

    fun toF64(): Double = when (this) {
        is F64Value -> value
        else -> throw Error("Cannot convert ${this::class.qualifiedName} to F64")
    }

    fun assertI32(): WasmValue = when (this) {
        is I32Value -> this
        else -> throw Error("Value is ${this.type} not i32")
    }

    fun assertI64(): WasmValue = when (this) {
        is I64Value -> this
        else -> throw Error("Value is ${this.type} not i64")
    }

    fun assertF32(): WasmValue = when (this) {
        is F32Value -> this
        else -> throw Error("Value is ${this.type} not f32")
    }

    fun assertF64(): WasmValue = when (this) {
        is F64Value -> this
        else -> throw Error("Value is ${this.type} not f64")
    }

    fun assertVoid(): WasmValue = when (this) {
        is UnitValue -> this
        else -> throw Error("Value is ${this.type} not void")
    }

    val type: Type
        get() = when (this) {
            is I32Value -> Type.I32
            is I64Value -> Type.I64
            is F32Value -> Type.F32
            is F64Value -> Type.F64
            is UnitValue -> Type.Void
        }

    fun toList(): List<WasmValue> = when (this) {
        is UnitValue -> listOf()
        else -> listOf(this)
    }
}

data class I32Value(val value: Int) : WasmValue()
data class I64Value(val value: Long) : WasmValue()
data class F32Value(val value: Float) : WasmValue() {
    override fun toString(): String = "F32Value($value; ${value.toRawBits()})"
}

data class F64Value(val value: Double) : WasmValue()
data class UnitValue(val value: Unit = Unit) : WasmValue()

enum class ExportType {
    FUNCTION,
    TABLE,
    MEMORY,
    GLOBAL;

    companion object {
        fun of(byte: Byte): ExportType = when (byte.toInt()) {
            0 -> FUNCTION
            1 -> TABLE
            2 -> MEMORY
            3 -> GLOBAL
            else -> throw Error("Unknown export type $byte")
        }
    }

    override fun toString(): String = when (this) {
        FUNCTION -> "func"
        MEMORY -> "memory"
        TABLE -> "table"
        GLOBAL -> "global"
    }
}
