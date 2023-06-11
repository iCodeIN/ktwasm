package com.github.yeicor.ktmpwasm.binary

import com.github.yeicor.ktmpwasm.base.Signature
import com.github.yeicor.ktmpwasm.base.Type
import com.github.yeicor.ktmpwasm.interpreter.*

fun parseCodeSection(contents: ModuleContents, parser: ByteParser) {
  val types = contents.types ?: listOf()

  require(contents.code == null)
  contents.code = parseVector(parser) { parseCode(parser, types) }
}

fun parseCode(p: ByteParser, types: List<FuncType>): Code {
  val size = parseUnsigned(p).toInt()

  return p.guard(size) { parser ->
    val compressedLocals = parseVector(parser, ::parseLocals)
    val locals = compressedLocals.flatMap { arg -> MutableList(arg.first) { arg.second } }
    val expression = parseExpression(parser, types)

    Code(locals, expression)
  }
}

fun parseExpression(parser: ByteParser, types: List<FuncType>): List<Instruction> {
  val result = mutableListOf<Instruction>()
  val blocks = mutableListOf<BlockInstruction>()

  fun push(block: BlockInstruction): Instruction {
    blocks.add(block)
    return block
  }

  fun pop(): BlockInstruction {
    return blocks.removeAt(blocks.size - 1)
  }

  Loop@ while (true) {
    // Read the next byte (opcode), as an unsigned int
    val instr =
        when (val opcode = parser.readByte().toUByte().toInt()) {
          0x00 -> Unreachable()
          0x01 -> NoOp()
          0x02 -> {
            val type = parseBlockType(parser)
            push(Block(type, result.size))
          }
          0x03 -> {
            parseBlockType(parser)
            // NOTE the labels of loops always have void type
            push(Loop(Type.Void, result.size))
          }
          0x04 -> {
            val type = parseBlockType(parser)
            push(If(type, result.size))
          }
          0x05 -> {
            blocks.last().elseInstruction = result.size + 1
            Else()
          }
          0x0B -> {
            if (blocks.isEmpty()) {
              break@Loop
            } else {
              pop().endInstruction = result.size
            }
            EndBlock()
          }
          0x0C -> Br(parseU32(parser))
          0x0D -> BrIf(parseU32(parser))
          0x0E -> {
            val labels = parseVector(parser, ::parseU32).toMutableList()
            val lastLabel = parseU32(parser)
            labels.add(lastLabel)

            BrTable(labels)
          }
          0x0F -> Return()
          0x10 -> Call(parser.readU32())
          0x11 -> {
            val typeIDx = parser.readU32()
            val byte = parser.readByte()
            require(byte == 0.toByte())

            val type = types.getOrNull(typeIDx) ?: throw Error("Cannot get type $typeIDx")

            require(type.result.size <= 1) { "Can only handle single return values" }

            val returnType = type.result.firstOrNull() ?: Type.Void
            val paramTypes = type.args

            CallIndirect(Signature(returnType, paramTypes))
          }
          0x1A -> Drop()
          0x1B -> Select()
          0x20 -> LocalGet(parser.readU32())
          0x21 -> LocalSet(parser.readU32())
          0x22 -> LocalTee(parseU32(parser))
          0x23 -> GlobalGet(parseU32(parser))
          0x24 -> GlobalSet(parseU32(parser))
          0x28 -> parseMemArg(parser).let { I32Load(offset = it.offset, align = it.align) }
          0x29 -> parseMemArg(parser).let { I64Load(offset = it.offset, align = it.align) }
          0x2A -> parseMemArg(parser).let { F32Load(offset = it.offset, align = it.align) }
          0x2B -> parseMemArg(parser).let { F64Load(offset = it.offset, align = it.align) }
          0x2C -> parseMemArg(parser).let { I32Load8S(offset = it.offset, align = it.align) }
          0x2D -> parseMemArg(parser).let { I32Load8U(offset = it.offset, align = it.align) }
          0x2E -> parseMemArg(parser).let { I32Load16S(offset = it.offset, align = it.align) }
          0x2F -> parseMemArg(parser).let { I32Load16U(offset = it.offset, align = it.align) }
          0x30 -> parseMemArg(parser).let { I64Load8S(offset = it.offset, align = it.align) }
          0x31 -> parseMemArg(parser).let { I64Load8U(offset = it.offset, align = it.align) }
          0x32 -> parseMemArg(parser).let { I64Load16S(offset = it.offset, align = it.align) }
          0x33 -> parseMemArg(parser).let { I64Load16U(offset = it.offset, align = it.align) }
          0x34 -> parseMemArg(parser).let { I64Load32S(offset = it.offset, align = it.align) }
          0x35 -> parseMemArg(parser).let { I64Load32U(offset = it.offset, align = it.align) }
          0x36 -> parseMemArg(parser).let { I32Store(offset = it.offset, align = it.align) }
          0x37 -> parseMemArg(parser).let { I64Store(offset = it.offset, align = it.align) }
          0x38 -> parseMemArg(parser).let { F32Store(offset = it.offset, align = it.align) }
          0x39 -> parseMemArg(parser).let { F64Store(offset = it.offset, align = it.align) }
          0x3A -> parseMemArg(parser).let { I32Store8(offset = it.offset, align = it.align) }
          0x3B -> parseMemArg(parser).let { I32Store16(offset = it.offset, align = it.align) }
          0x3C -> parseMemArg(parser).let { I64Store8(offset = it.offset, align = it.align) }
          0x3D -> parseMemArg(parser).let { I64Store16(offset = it.offset, align = it.align) }
          0x3E -> parseMemArg(parser).let { I64Store32(offset = it.offset, align = it.align) }
          0x3F -> {
            parseByte(parser).also { require(it == 0.toByte()) }
            MemorySize()
          }
          0x40 -> {
            parseByte(parser).also { require(it == 0.toByte()) }
            MemoryGrow()
          }
          0x41 -> I32Const(parser.readI32())
          0x42 -> I64Const(parser.readI64())
          0x43 -> F32Const(parser.readF32())
          0x44 -> F64Const(parser.readF64())
          0x45 -> I32EqZ()
          0x46 -> I32Eq()
          0x47 -> I32Ne()
          0x48 -> I32LtS()
          0x49 -> I32LtU()
          0x4A -> I32GtS()
          0x4B -> I32GtU()
          0x4C -> I32LeS()
          0x4D -> I32LeU()
          0x4E -> I32GeS()
          0x4F -> I32GeU()
          0x50 -> I64EqZ()
          0x51 -> I64Eq()
          0x52 -> I64Ne()
          0x53 -> I64LtS()
          0x54 -> I64LtU()
          0x55 -> I64GtS()
          0x56 -> I64GtU()
          0x57 -> I64LeS()
          0x58 -> I64LeU()
          0x59 -> I64GeS()
          0x5A -> I64GeU()
          0x5B -> F32Eq()
          0x5C -> F32Ne()
          0x5D -> F32Lt()
          0x5E -> F32Gt()
          0x5F -> F32Le()
          0x60 -> F32Ge()
          0x61 -> F64Eq()
          0x62 -> F64Ne()
          0x63 -> F64Lt()
          0x64 -> F64Gt()
          0x65 -> F64Le()
          0x66 -> F64Ge()
          0x67 -> I32Clz()
          0x68 -> I32Ctz()
          0x69 -> I32PopCnt()
          0x6A -> I32Add()
          0x6B -> I32Sub()
          0x6C -> I32Mul()
          0x6D -> I32DivS()
          0x6E -> I32DivU()
          0x6F -> I32RemS()
          0x70 -> I32RemU()
          0x71 -> I32And()
          0x72 -> I32Or()
          0x73 -> I32Xor()
          0x74 -> I32Shl()
          0x75 -> I32ShrS()
          0x76 -> I32ShrU()
          0x77 -> I32Rotl()
          0x78 -> I32Rotr()
          0x79 -> I64Clz()
          0x7A -> I64Ctz()
          0x7B -> I64PopCnt()
          0x7C -> I64Add()
          0x7D -> I64Sub()
          0x7E -> I64Mul()
          0x7F -> I64DivS()
          0x80 -> I64DivU()
          0x81 -> I64RemS()
          0x82 -> I64RemU()
          0x83 -> I64And()
          0x84 -> I64Or()
          0x85 -> I64Xor()
          0x86 -> I64Shl()
          0x87 -> I64ShrS()
          0x88 -> I64ShrU()
          0x89 -> I64Rotl()
          0x8A -> I64Rotr()
          0x8B -> F32Abs()
          0x8C -> F32Neg()
          0x8D -> F32Ceil()
          0x8E -> F32Floor()
          0x8F -> F32Trunc()
          0x90 -> F32Nearest()
          0x91 -> F32Sqrt()
          0x92 -> F32Add()
          0x93 -> F32Sub()
          0x94 -> F32Mul()
          0x95 -> F32Div()
          0x96 -> F32Min()
          0x97 -> F32Max()
          0x98 -> F32CopySign()
          0x99 -> F64Abs()
          0x9A -> F64Neg()
          0x9B -> F64Ceil()
          0x9C -> F64Floor()
          0x9D -> F64Trunc()
          0x9E -> F64Nearest()
          0x9F -> F64Sqrt()
          0xA0 -> F64Add()
          0xA1 -> F64Sub()
          0xA2 -> F64Mul()
          0xA3 -> F64Div()
          0xA4 -> F64Min()
          0xA5 -> F64Max()
          0xA6 -> F64CopySign()
          0xA7 -> I32WrapI64()
          0xA8 -> I32TruncF32S()
          0xA9 -> I32TruncF32U()
          0xAA -> I32TruncF64S()
          0xAB -> I32TruncF64U()
          0xAC -> I64ExtendI32S()
          0xAD -> I64ExtendI32U()
          0xAE -> I64TruncF32S()
          0xAF -> I64TruncF32U()
          0xB0 -> I64TruncF64S()
          0xB1 -> I64TruncF64U()
          0xB2 -> F32ConvertI32S()
          0xB3 -> F32ConvertI32U()
          0xB4 -> F32ConvertI64S()
          0xB5 -> F32ConvertI64U()
          0xB6 -> F32DemoteF64()
          0xB7 -> F64ConvertI32S()
          0xB8 -> F64ConvertI32U()
          0xB9 -> F64ConvertI64S()
          0xBA -> F64ConvertI64U()
          0xBB -> F64PromoteF32()
          0xBC -> I32ReinterpretF32()
          0xBD -> I64ReinterpretF64()
          0xBE -> F32ReinterpretI32()
          0XBF -> F64ReinterpretI64()
          else -> throw Error("Unknown opcode ${opcode.toUByte().toString(16)}")
        }

    result.add(instr)
  }

  return result
}

fun parseMemArg(parser: ByteParser): MemArg {
  val align = parser.readU32()
  val offset = parser.readU32()
  return MemArg(offset = offset, align = align)
}

data class MemArg(val offset: Int, val align: Int)

fun parseLocals(parser: ByteParser): Pair<Int, Type> {
  val count = parseUnsigned(parser).toInt()
  val type = parseValueType(parser)
  return Pair(count, type)
}

fun parseBlockType(parser: ByteParser): Type {
  return when (val id = parser.readByte()) {
    0x40.toByte() -> Type.Void
    0x7F.toByte() -> Type.I32
    0x7E.toByte() -> Type.I64
    0x7D.toByte() -> Type.F32
    0x7C.toByte() -> Type.F64
    else -> throw Error("Invalid block type $id @ ${parser.offset}")
  }
}
