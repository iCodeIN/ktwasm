package de.cprohm.ktwasm.interpreter

import de.cprohm.ktwasm.base.Signature
import de.cprohm.ktwasm.base.Type

sealed class Instruction(val arity: Int)

sealed class PrimtiveInstruction(arity: Int) : Instruction(arity) {
    override fun equals(other: Any?): Boolean = (other != null) && (other.javaClass == javaClass)
    override fun hashCode(): Int = javaClass.hashCode()
    override fun toString(): String = "${javaClass.simpleName}()"
}

sealed class BlockInstruction(val type: Type, val startInstruction: Int, arity: Int = 0) :
    Instruction(arity = arity) {
    var _elseInstruction: Int = startInstruction
    var _endInstruction: Int = startInstruction
    var _finalized: Boolean = false

    private fun assertNotFinalized() = assert(!_finalized) { "block was finalized" }

    fun finalize() {
        assertNotFinalized()
        _finalized = true
    }

    var elseInstruction: Int
        get() = _elseInstruction
        set(value) = _setElse(value)

    var endInstruction: Int
        get() = _endInstruction
        set(value) {
            assertNotFinalized()
            _endInstruction = value
        }

    abstract val continuation: Int get

    abstract fun _setElse(value: Int)
}

// 5.4.1 control instructions
// 0x00
class Unreachable : PrimtiveInstruction(0)

// 0x01
class NoOp : PrimtiveInstruction(0)

// 0x02
class Block(type: Type, startInstruction: Int) :
    BlockInstruction(type = type, startInstruction = startInstruction) {
    override fun _setElse(value: Int) {
        throw Error("Block cannot have an else instruction")
    }

    override val continuation: Int
        get() = endInstruction + 1

}

// 0x03
class Loop(type: Type, startInstruction: Int) :
    BlockInstruction(type = type, startInstruction = startInstruction) {

    override fun _setElse(value: Int) {
        throw Error("Block cannot have an else instruction")
    }

    override val continuation: Int
        get() = startInstruction
}

// 0x04
class If(type: Type, startInstruction: Int) :
    BlockInstruction(type = type, startInstruction = startInstruction, arity = 1) {

    override fun _setElse(value: Int) {
        _elseInstruction = value
    }

    override val continuation: Int
        get() = endInstruction + 1
}

// 0x06
class Else : PrimtiveInstruction(arity = 0)

// 0x0B
class EndBlock : PrimtiveInstruction(0)

// 0x0C
data class Br(val label: Int) : Instruction(0)

// 0x0D
data class BrIf(val label: Int) : Instruction(1)

// 0x0E
data class BrTable(val labels: List<Int>) : Instruction(1)

// 0x0F
class Return : PrimtiveInstruction(0)

// 0x10
data class Call(val function: Int) : Instruction(0)

// extension to handle calls by names
data class DynamicCall(val function: String) : Instruction(0)

// 0x11
data class CallIndirect(val type: Signature) : Instruction(1)

// 5.4.2 Parametric instructions
// 0x1
class Drop : PrimtiveInstruction(1)

// 0x1B
class Select : PrimtiveInstruction(3)

// 5.4.3 variable instructions
// 0x20
data class LocalGet(val reference: Int) : Instruction(arity = 0)

// 0x21
data class LocalSet(val reference: Int) : Instruction(arity = 1)

// 0x22
data class LocalTee(val reference: Int) : Instruction(arity = 1)

// 0x23
data class GlobalGet(val reference: Int) : Instruction(arity = 0)

// 0x24
data class GlobalSet(val reference: Int) : Instruction(arity = 1)

// 5.4.4 Memory instructions
// 0x28
data class I32Load(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x29
data class I64Load(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x2A
data class F32Load(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x2B
data class F64Load(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x2C
data class I32Load8S(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x2D
data class I32Load8U(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x2E
data class I32Load16S(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x2F
data class I32Load16U(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x30
data class I64Load8S(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x31
data class I64Load8U(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x32
data class I64Load16S(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x33
data class I64Load16U(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x34
data class I64Load32S(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x35
data class I64Load32U(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 1)

// 0x36
data class I32Store(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x37
data class I64Store(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x38
data class F32Store(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x39
data class F64Store(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x3A
data class I32Store8(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x3B
data class I32Store16(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x3C
data class I64Store8(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x3D
data class I64Store16(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x3E
data class I64Store32(val offset: Int = 0, val align: Int = 0) : Instruction(arity = 2)

// 0x3F
class MemorySize : PrimtiveInstruction(arity = 0)

// 0x40
class MemoryGrow : PrimtiveInstruction(arity = 1)

// 5.4.5 Numeric instructions
// 0x41
data class I32Const(val value: Int) : Instruction(0)

// 0x42
data class I64Const(val value: Long) : Instruction(0)

// 0x43
data class F32Const(val value: Float) : Instruction(0)

// 0x44
data class F64Const(val value: Double) : Instruction(0)

// 0x45
class I32EqZ : PrimtiveInstruction(1)

// 0x46
class I32Eq : PrimtiveInstruction(2)

// 0x47
class I32Ne : PrimtiveInstruction(2)

// 0x48
class I32LtS : PrimtiveInstruction(2)

// 0x49
class I32LtU : PrimtiveInstruction(2)

// 0x4A
class I32GtS : PrimtiveInstruction(2)

// 0x4B
class I32GtU : PrimtiveInstruction(2)

// 0x4C
class I32LeS : PrimtiveInstruction(2)

// 0x4D
class I32LeU : PrimtiveInstruction(2)

// 0x4E
class I32GeS : PrimtiveInstruction(2)

// 0x4F
class I32GeU : PrimtiveInstruction(2)

// 0x50
class I64EqZ : PrimtiveInstruction(1)

// 0x51
class I64Eq : PrimtiveInstruction(2)

// 0x52
class I64Ne : PrimtiveInstruction(2)

// 0x53
class I64LtS : PrimtiveInstruction(2)

// 0x54
class I64LtU : PrimtiveInstruction(2)

// 0x55
class I64GtS : PrimtiveInstruction(2)

// 0x56
class I64GtU : PrimtiveInstruction(2)

// 0x57
class I64LeS : PrimtiveInstruction(2)

// 0x58
class I64LeU : PrimtiveInstruction(2)

// 0x59
class I64GeS : PrimtiveInstruction(2)

// 0x5A
class I64GeU : PrimtiveInstruction(2)

// 0x5B
class F32Eq : PrimtiveInstruction(2)

// 0x5C
class F32Ne : PrimtiveInstruction(2)

// 0x5D
class F32Lt : PrimtiveInstruction(2)

// 0x5E
class F32Gt : PrimtiveInstruction(2)

// 0x5F
class F32Le : PrimtiveInstruction(2)

// 0x60
class F32Ge : PrimtiveInstruction(2)

// 0x61
class F64Eq : PrimtiveInstruction(2)

// 0x62
class F64Ne : PrimtiveInstruction(2)

// 0x63
class F64Lt : PrimtiveInstruction(2)

// 0x64
class F64Gt : PrimtiveInstruction(2)

// 0x65
class F64Le : PrimtiveInstruction(2)

// 0x66
class F64Ge : PrimtiveInstruction(2)

// 0x67
class I32Clz : PrimtiveInstruction(1)

// 0x68
class I32Ctz : PrimtiveInstruction(1)

// 0x69
class I32PopCnt : PrimtiveInstruction(1)

// 0x6A
class I32Add : PrimtiveInstruction(2)

// 0x6B
class I32Sub : PrimtiveInstruction(2)

// 0x6C
class I32Mul : PrimtiveInstruction(2)

// 0x6D
class I32DivS : PrimtiveInstruction(2)

// 0x6E
class I32DivU : PrimtiveInstruction(2)

// 0x6F
class I32RemS : PrimtiveInstruction(2)

// 0x70
class I32RemU : PrimtiveInstruction(2)

// 0x71
class I32And : PrimtiveInstruction(2)

// 0x72
class I32Or : PrimtiveInstruction(2)

// 0x73
class I32Xor : PrimtiveInstruction(2)

// 0x74
class I32Shl : PrimtiveInstruction(2)

// 0x75
class I32ShrS : PrimtiveInstruction(2)

// 0x76
class I32ShrU : PrimtiveInstruction(2)

// 0x77
class I32Rotl : PrimtiveInstruction(2)

// 0x78
class I32Rotr : PrimtiveInstruction(2)

// 0x79
class I64Clz : PrimtiveInstruction(1)

// 0x7A
class I64Ctz : PrimtiveInstruction(1)

// 0x7B
class I64PopCnt : PrimtiveInstruction(1)

// 0x7C
class I64Add : PrimtiveInstruction(2)

// 0x7D
class I64Sub : PrimtiveInstruction(2)

// 0x7E
class I64Mul : PrimtiveInstruction(2)

// 0x7F
class I64DivS : PrimtiveInstruction(2)

// 0x80
class I64DivU : PrimtiveInstruction(2)

// 0x81
class I64RemS : PrimtiveInstruction(2)

// 0x82
class I64RemU : PrimtiveInstruction(2)

// 0x83
class I64And : PrimtiveInstruction(2)

// 0x84
class I64Or : PrimtiveInstruction(2)

// 0x85
class I64Xor : PrimtiveInstruction(2)

// 0x86
class I64Shl : PrimtiveInstruction(2)

// 0x87
class I64ShrS : PrimtiveInstruction(2)

// 0x88
class I64ShrU : PrimtiveInstruction(2)

// 0x89
class I64Rotl : PrimtiveInstruction(2)

// 0x8A
class I64Rotr : PrimtiveInstruction(2)

// 0x8B
class F32Abs : PrimtiveInstruction(1)

// 0x8C
class F32Neg : PrimtiveInstruction(1)

// 0x8D
class F32Ceil : PrimtiveInstruction(1)

// 0x8E
class F32Floor : PrimtiveInstruction(1)

// 0x8F
class F32Trunc : PrimtiveInstruction(1)

// 0x90
class F32Nearest : PrimtiveInstruction(1)

// 0x91
class F32Sqrt : PrimtiveInstruction(1)

// 0x92
class F32Add : PrimtiveInstruction(2)

// 0x93
class F32Sub : PrimtiveInstruction(2)

// 0x94
class F32Mul : PrimtiveInstruction(2)

// 0x95
class F32Div : PrimtiveInstruction(2)

// 0x96
class F32Min : PrimtiveInstruction(2)

// 0x97
class F32Max : PrimtiveInstruction(2)

// 0x98
class F32CopySign : PrimtiveInstruction(2)

// 0x99
class F64Abs : PrimtiveInstruction(1)

// 0x9A
class F64Neg : PrimtiveInstruction(1)

// 0x9B
class F64Ceil : PrimtiveInstruction(1)

// 0x9C
class F64Floor : PrimtiveInstruction(1)

// 0x9D
class F64Trunc : PrimtiveInstruction(1)

// 0x9E
class F64Nearest : PrimtiveInstruction(1)

// 0x9F
class F64Sqrt : PrimtiveInstruction(1)

// 0xA0
class F64Add : PrimtiveInstruction(2)

// 0xA1
class F64Sub : PrimtiveInstruction(2)

// 0xA2
class F64Mul : PrimtiveInstruction(2)

// 0xA3
class F64Div : PrimtiveInstruction(2)

// 0xA4
class F64Min : PrimtiveInstruction(2)

// 0xA5
class F64Max : PrimtiveInstruction(2)

// 0xA6
class F64CopySign : PrimtiveInstruction(2)

// 0xA7
class I32WrapI64 : PrimtiveInstruction(1)

// 0xA8
class I32TruncF32S : PrimtiveInstruction(1)

// 0xA9
class I32TruncF32U : PrimtiveInstruction(1)

// 0xAA
class I32TruncF64S : PrimtiveInstruction(1)

// 0xAB
class I32TruncF64U : PrimtiveInstruction(1)

// 0xAC
class I64ExtendI32S : PrimtiveInstruction(1)

// 0xAD
class I64ExtendI32U : PrimtiveInstruction(1)

// 0xAE
class I64TruncF32S : PrimtiveInstruction(1)

// 0xAF
class I64TruncF32U : PrimtiveInstruction(1)

// 0xB0
class I64TruncF64S : PrimtiveInstruction(1)

// 0xB1
class I64TruncF64U : PrimtiveInstruction(1)

// 0xB2
class F32ConvertI32S : PrimtiveInstruction(1)

// 0xB3
class F32ConvertI32U : PrimtiveInstruction(1)

// 0xB4
class F32ConvertI64S : PrimtiveInstruction(1)

// 0xB5
class F32ConvertI64U : PrimtiveInstruction(1) {
    companion object {
        fun call(arg: Long): Long = I64.convertToF32U(arg.toI64()).toI64()
    }
}

// 0xB6
class F32DemoteF64 : PrimtiveInstruction(1)

// 0xB7
class F64ConvertI32S : PrimtiveInstruction(1)

// 0xB8
class F64ConvertI32U : PrimtiveInstruction(1)

// 0xB9
class F64ConvertI64S : PrimtiveInstruction(1)

// 0xBA
class F64ConvertI64U : PrimtiveInstruction(1)

// 0xBB
class F64PromoteF32 : PrimtiveInstruction(1)

// 0xBC
class I32ReinterpretF32 : PrimtiveInstruction(1)

// 0xBD
class I64ReinterpretF64 : PrimtiveInstruction(1)

// 0xBE
class F32ReinterpretI32 : PrimtiveInstruction(1)

// 0xBF
class F64ReinterpretI64 : PrimtiveInstruction(1)
