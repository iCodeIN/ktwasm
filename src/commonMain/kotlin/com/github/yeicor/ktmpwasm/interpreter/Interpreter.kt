package com.github.yeicor.ktmpwasm.interpreter

import com.github.yeicor.ktmpwasm.base.*
import kotlin.math.*

fun call(module: Module, func: WFunction, args: List<WasmValue>): WasmValue {
  if (func.parameters.size != args.size) {
    throw Error("Invalid number of parameters expected ${func.parameters.size}, got ${args.size}")
  }
  for (i in func.parameters.indices) {
    if (func.parameters[i] != args[i].type) {
      throw Error("Invalid parameter $i expected ${func.parameters[i]} got ${args[i].type}")
    }
  }

  val sizeLocals = args.size + func.locals.size
  val ctx = ExecutionContext(module, sizeLocals)

  for ((idx, arg) in args.withIndex()) {
    ctx.locals[idx] = arg.toLong()
  }

  // TODO: fix this ...
  val instructions = func.instructions.plusElement(EndBlock())
  val startBlock = Block(func.result, 0)
  startBlock.endInstruction = instructions.size - 1
  startBlock.finalize()

  ctx.execute(listOf(startBlock))
  ctx.execute(instructions)

  require(ctx.blocks.isEmpty()) { "Still ${ctx.blocks.size} blocks active" }

  return when (func.result) {
    Type.Void -> UnitValue()
    Type.I32 -> ctx.stackLast().toI32Value()
    Type.I64 -> ctx.stackLast().toI64Value()
    Type.F32 -> ctx.stackLast().toF32Value()
    Type.F64 -> ctx.stackLast().toF64Value()
  }
}

class ExecutionContext(val module: Module, sizeLocals: Int = 0) {
  private var stack: LongArray = LongArray(128)
  private var stackSizeValue: Int = 0

  fun stackLast(): Long =
      if (stackSizeValue >= 1) {
        stack[stackSizeValue - 1]
      } else {
        throw Error()
      }

  private fun stackSize(): Int = stackSizeValue

  private fun push(value: Long) {
    if (stackSizeValue == stack.size) {
      stack = stack.copyInto(LongArray(2 * stack.size))
    }
    stack[stackSizeValue] = value
    stackSizeValue += 1
  }

  fun pop(): Long =
      if (stackSizeValue == 0) {
        throw Error()
      } else {
        stackSizeValue -= 1
        stack[stackSizeValue]
      }

  private fun pushI32(value: Int) = push(value.toLong())
  private fun pushI64(value: Long) = push(value)
  private fun pushF32(value: Float) = push(value.toRawBits().toLong())
  private fun pushF64(value: Double) = push(value.toRawBits())

  val blocks: MutableList<BlockInstruction> = mutableListOf()
  private val stackDepth: MutableList<Int> = mutableListOf()
  val locals: LongArray = LongArray(sizeLocals)

  private fun pushBlock(block: BlockInstruction) {
    blocks.add(block)
    stackDepth.add(stackSize())
  }

  private fun popBlock(label: Int, modifyStack: Boolean = true): BlockInstruction {
    require(blocks.size > label)

    val block = blocks[blocks.size - 1 - label]
    val targetDepth = stackDepth[blocks.size - 1 - label]

    for (i in 0..label) {
      blocks.removeAt(blocks.size - 1)
      stackDepth.removeAt(stackDepth.size - 1)
    }

    if (!modifyStack) {
      return block
    }

    val result =
        when (block.type) {
          Type.I32 -> pop()
          Type.I64 -> pop()
          Type.F32 -> pop()
          Type.F64 -> pop()
          Type.Void -> null
        }

    while (stackSize() > targetDepth) {
      pop()
    }
    result?.let { push(result) }

    return block
  }

  fun execute(instructions: List<Instruction>) {
    var instructionPointer = 0
    while ((instructionPointer >= 0) && (instructionPointer < instructions.size)) {
      val instruction = instructions[instructionPointer]
      try {
        instructionPointer = execute(instructionPointer, instruction)
      } catch (e: Throwable) {
        throw ExecutionError(instructionPointer, instruction, e)
      }
    }
  }

  private fun execute(instructionPointer: Int, instruction: Instruction): Int {
    val args = LongArray(4)
    for (idx in 0 until instruction.arity) {
      args[idx] = pop()
    }

    var nextInstruction = instructionPointer + 1

    @Suppress("UNUSED_VARIABLE")
    val ensureExhaustive: Unit =
        when (instruction) {
          // 5.4.1 control instructions
          // 0x00
          is Unreachable -> throw TrapError()
          // 0x01
          is NoOp -> Unit
          // 0x02
          is Block -> pushBlock(instruction)
          // 0x03
          is Loop -> pushBlock(instruction)
          // 0x04
          is If -> {
            pushBlock(instruction)
            if (args[0].toI32() == 0) {
              nextInstruction =
                  if (instruction.elseInstruction != instruction.startInstruction) {
                    instruction.elseInstruction
                  } else {
                    instruction.endInstruction
                  }
            }
            Unit
          }
          // 0x05
          is Else -> {
            val block = popBlock(0)
            require(block is If) {
              "Cannot perform else in non-if block (${block::class.simpleName}"
            }
            nextInstruction = block.continuation
          }
          // 0x0B
          is EndBlock -> popBlock(0, modifyStack = false).let { Unit }
          // 0x0C
          is Br -> {
            val block = popBlock(instruction.label)
            nextInstruction = block.continuation
          }
          // 0x0D
          is BrIf -> {
            if (args[0].toI32() != 0) {
              val block = popBlock(instruction.label)
              nextInstruction = block.continuation
            }
            Unit
          }
          // 0x0E
          is BrTable -> {
            val i = args[0].toI32()
            val label = instruction.labels.let { it.getOrNull(i) ?: it.last() }
            val block = popBlock(label)
            nextInstruction = block.continuation
          }
          // 0x0F
          is Return -> {
            blocks.clear()
            stackDepth.clear()

            nextInstruction = -1
          }
          // 0x10
          is Call -> {
            executeCall(module.refFunction(instruction.function))
          }
          is DynamicCall -> {
            executeCall(module.lookup(instruction.function))
          }

          // 0x11
          is CallIndirect -> {
            val idx = args[0].toI32()
            val function = module.refFunctionTable(idx)

            if (instruction.type != function.type()) {
              throw Error("Function types in dynamic call do not match")
            }
            executeCall(function)
          }

          // 5.4.2 parametric instructions
          // 0x1A
          is Drop -> Unit
          // 0x1B
          is Select -> push(if (args[0].toI32() != 0) args[2] else args[1])

          // 5.4.3 variable instructions
          // 0x20
          is LocalGet -> pushLocal(instruction.reference)
          // 0x21
          is LocalSet -> setLocal(instruction.reference, args[0])
          // 0x22
          is LocalTee -> {
            push(args[0])
            setLocal(instruction.reference, args[0])
          }
          // 0x23
          is GlobalGet -> {
            val value = module.refGlobal(instruction.reference).get()
            push(value?.toLong() ?: throw Error("Global ${instruction.reference} not initialized"))
          }
          // 0x24
          is GlobalSet -> {
            val global = module.refGlobal(instruction.reference)
            global.set(args[0].toType(global.type()))
          }

          // 5.4.3 memory instructions
          // 0x28
          is I32Load -> pushI32(module.memory.loadI32(args[0].toI32(), instruction.offset))
          // 0x29
          is I64Load -> pushI64(module.memory.loadI64(args[0].toI32(), instruction.offset))
          // 0x2A
          is F32Load ->
              pushF32(Float.fromBits(module.memory.loadI32(args[0].toI32(), instruction.offset)))
          // 0x2B
          is F64Load ->
              pushF64(Double.fromBits(module.memory.loadI64(args[0].toI32(), instruction.offset)))
          // 0x2C
          is I32Load8S ->
              pushI32(I32.extendS(module.memory.loadI8(args[0].toI32(), instruction.offset)))
          // 0x2D
          is I32Load8U ->
              pushI32(I32.extendU(module.memory.loadI8(args[0].toI32(), instruction.offset)))
          // 0x2E
          is I32Load16S ->
              pushI32(I32.extendS(module.memory.loadI16(args[0].toI32(), instruction.offset)))
          // 0x2F
          is I32Load16U ->
              pushI32(I32.extendU(module.memory.loadI16(args[0].toI32(), instruction.offset)))
          // 0x30
          is I64Load8S ->
              pushI64(I64.extendS(module.memory.loadI8(args[0].toI32(), instruction.offset)))
          // 0x31
          is I64Load8U ->
              pushI64(I64.extendU(module.memory.loadI8(args[0].toI32(), instruction.offset)))
          // 0x32
          is I64Load16S ->
              pushI64(I64.extendS(module.memory.loadI16(args[0].toI32(), instruction.offset)))
          // 0x33
          is I64Load16U ->
              pushI64(I64.extendU(module.memory.loadI16(args[0].toI32(), instruction.offset)))
          // 0x34
          is I64Load32S ->
              pushI64(I64.extendS(module.memory.loadI32(args[0].toI32(), instruction.offset)))
          // 0x35
          is I64Load32U ->
              pushI64(I64.extendU(module.memory.loadI32(args[0].toI32(), instruction.offset)))
          // 0x36
          is I32Store ->
              module.memory.storeI32(args[1].toI32(), instruction.offset, args[0].toI32())
          // 0x37
          is I64Store ->
              module.memory.storeI64(args[1].toI32(), instruction.offset, args[0].toI64())
          // 0x38
          is F32Store ->
              module.memory.storeI32(args[1].toI32(), instruction.offset, args[0].toI32())
          // 0x39
          is F64Store -> module.memory.storeI64(args[1].toI32(), instruction.offset, args[0])
          // 0x3A
          is I32Store8 ->
              module.memory.storeI8(args[1].toI32(), instruction.offset, args[0].toI32().toByte())
          // 0x3B
          is I32Store16 ->
              module.memory.storeI16(args[1].toI32(), instruction.offset, args[0].toI32().toShort())
          // 0x3C
          is I64Store8 ->
              module.memory.storeI8(args[1].toI32(), instruction.offset, args[0].toI64().toByte())
          // 0x3D
          is I64Store16 ->
              module.memory.storeI16(args[1].toI32(), instruction.offset, args[0].toI64().toShort())
          // 0x3E
          is I64Store32 ->
              module.memory.storeI32(args[1].toI32(), instruction.offset, args[0].toI64().toInt())
          // 0x3F
          is MemorySize -> pushI32(module.memory.min)
          // 0x40
          is MemoryGrow -> {
            val n = args[0].toI32()
            val sz = module.memory.min
            val newSize = Math.addExact(sz, n)

            val success = module.memory.resize(newSize)
            pushI32(if (success) sz else -1)
          }

          // 5.4.5 Numeric instructions
          // 0x41
          is I32Const -> pushI32(instruction.value)
          // 0x42
          is I64Const -> pushI64(instruction.value)
          // 0x43
          is F32Const -> pushF32(instruction.value)
          // 0x44
          is F64Const -> pushF64(instruction.value)

          // 0x45
          is I32EqZ -> pushI32(if (args[0].toI32() == 0) 1 else 0)
          // 0x46
          is I32Eq -> pushI32(if (args[0].toI32() == args[1].toI32()) 1 else 0)
          // 0x47
          is I32Ne -> pushI32(if (args[0].toI32() != args[1].toI32()) 1 else 0)
          // 0x48
          is I32LtS -> pushI32(if (args[1].toI32() < args[0].toI32()) 1 else 0)
          // 0x49
          is I32LtU -> pushI32(if (I32.ltU(args[1].toI32(), args[0].toI32())) 1 else 0)
          // 0x4A
          is I32GtS -> pushI32(if (args[1].toI32() > args[0].toI32()) 1 else 0)
          // 0x4B
          is I32GtU -> pushI32(if (I32.gtU(args[1].toI32(), args[0].toI32())) 1 else 0)
          // 0x4C
          is I32LeS -> pushI32(if (args[1].toI32() <= args[0].toI32()) 1 else 0)
          // 0x4D
          is I32LeU -> pushI32(if (I32.leU(args[1].toI32(), args[0].toI32())) 1 else 0)
          // 0x4E
          is I32GeS -> pushI32(if (args[1].toI32() >= args[0].toI32()) 1 else 0)
          // 0x4F
          is I32GeU -> pushI32(if (I32.geU(args[1].toI32(), args[0].toI32())) 1 else 0)

          // 0x050
          is I64EqZ -> pushI32(if (args[0].toI64() == 0L) 1 else 0)
          // 0x051
          is I64Eq -> pushI32(if (args[0].toI64() == args[1].toI64()) 1 else 0)
          // 0x052
          is I64Ne -> pushI32(if (args[0].toI64() != args[1].toI64()) 1 else 0)
          // 0x053
          is I64LtS -> pushI32(if (args[1].toI64() < args[0].toI64()) 1 else 0)
          // 0x054
          is I64LtU -> pushI32(if (I64.ltU(args[1].toI64(), args[0].toI64())) 1 else 0)
          // 0x055
          is I64GtS -> pushI32(if (args[1].toI64() > args[0].toI64()) 1 else 0)
          // 0x056
          is I64GtU -> pushI32(if (I64.gtU(args[1].toI64(), args[0].toI64())) 1 else 0)
          // 0x057
          is I64LeS -> pushI32(if (args[1].toI64() <= args[0].toI64()) 1 else 0)
          // 0x058
          is I64LeU -> pushI32(if (I64.leU(args[1].toI64(), args[0].toI64())) 1 else 0)
          // 0x059
          is I64GeS -> pushI32(if (args[1].toI64() >= args[0].toI64()) 1 else 0)
          // 0x05A
          is I64GeU -> pushI32(if (I64.geU(args[1].toI64(), args[0].toI64())) 1 else 0)

          // 0x5B
          is F32Eq -> pushI32(if (args[1].toF32() == args[0].toF32()) 1 else 0)
          // 0x5C
          is F32Ne -> pushI32(if (args[1].toF32() != args[0].toF32()) 1 else 0)
          // 0x5D
          is F32Lt -> pushI32(if (args[1].toF32() < args[0].toF32()) 1 else 0)
          // 0x5E
          is F32Gt -> pushI32(if (args[1].toF32() > args[0].toF32()) 1 else 0)
          // 0x5F
          is F32Le -> pushI32(if (args[1].toF32() <= args[0].toF32()) 1 else 0)
          // 0x60
          is F32Ge -> pushI32(if (args[1].toF32() >= args[0].toF32()) 1 else 0)

          // 0x61
          is F64Eq -> pushI32(if (args[1].toF64() == args[0].toF64()) 1 else 0)
          // 0x62
          is F64Ne -> pushI32(if (args[1].toF64() != args[0].toF64()) 1 else 0)
          // 0x63
          is F64Lt -> pushI32(if (args[1].toF64() < args[0].toF64()) 1 else 0)
          // 0x64
          is F64Gt -> pushI32(if (args[1].toF64() > args[0].toF64()) 1 else 0)
          // 0x65
          is F64Le -> pushI32(if (args[1].toF64() <= args[0].toF64()) 1 else 0)
          // 0x66
          is F64Ge -> pushI32(if (args[1].toF64() >= args[0].toF64()) 1 else 0)

          // 0x67
          is I32Clz -> pushI32(I32.countLeadingZeroBits(args[0].toI32()))
          // 0x68
          is I32Ctz -> pushI32(I32.countTrailingZeroBits(args[0].toI32()))
          // 0x69
          is I32PopCnt -> pushI32(I32.countOneBits(args[0].toI32()))
          // 0x6A
          is I32Add -> pushI32(args[1].toI32() + args[0].toI32())
          // 0x6B
          is I32Sub -> pushI32(args[1].toI32() - args[0].toI32())
          // 0x6C
          is I32Mul -> pushI32(args[1].toI32() * args[0].toI32())
          // 0x6D
          is I32DivS -> {
            val a = args[0].toI32()
            val b = args[1].toI32()
            val res = b / a

            // NOTE: for res == 0 && b != 0 can happen for truncation
            // NOTE: the signs are degenerate for zeros
            if ((b != 0) && (res != 0) && (((a > 0) xor (b > 0)) == (res > 0))) {
              throw Error("Integer overflow")
            }

            pushI32(res)
          }
          // 0x6E
          is I32DivU -> pushI32(I32.divU(args[1].toI32(), args[0].toI32()))
          // 0x6F
          is I32RemS -> pushI32(args[1].toI32() % args[0].toI32())
          // 0x70
          is I32RemU -> pushI32(I32.remU(args[1].toI32(), args[0].toI32()))
          // 0x71
          is I32And -> pushI32(args[1].toI32() and args[0].toI32())
          // 0x72
          is I32Or -> pushI32(args[1].toI32() or args[0].toI32())
          // 0x73
          is I32Xor -> pushI32(args[1].toI32() xor args[0].toI32())
          // 0x74
          is I32Shl -> pushI32(args[1].toI32() shl args[0].toI32())
          // 0x75
          is I32ShrS -> pushI32(args[1].toI32() shr args[0].toI32())
          // 0x76
          is I32ShrU -> pushI32(args[1].toI32() ushr args[0].toI32())
          // 0x77
          is I32Rotl -> pushI32(I32.rotateLeft(args[1].toI32(), args[0].toI32()))
          // 0x78
          is I32Rotr -> pushI32(I32.rotateRight(args[1].toI32(), args[0].toI32()))

          // 0x79
          is I64Clz -> pushI64(I64.countLeadingZeroBits(args[0].toI64()))
          // 0x7A
          is I64Ctz -> pushI64(I64.countTrailingZeroBits(args[0].toI64()))
          // 0x7B
          is I64PopCnt -> pushI64(I64.countOneBits(args[0].toI64()))
          // 0x7C
          is I64Add -> pushI64(args[1].toI64() + args[0].toI64())
          // 0x7D
          is I64Sub -> pushI64(args[1].toI64() - args[0].toI64())
          // 0x7E
          is I64Mul -> pushI64(args[1].toI64() * args[0].toI64())
          // 0x7F
          is I64DivS -> {
            val a = args[0].toI64()
            val b = args[1].toI64()
            val res = b / a

            // NOTE: for res == 0 && b != 0 can happen for truncation
            // NOTE: the signs are degenerate for zeros
            if ((b != 0L) && (res != 0L) && (((a > 0L) xor (b > 0L)) == (res > 0L))) {
              throw Error("Integer overflow")
            }

            pushI64(res)
          }
          // 0x80
          is I64DivU -> pushI64(I64.divU(args[1].toI64(), args[0].toI64()))
          // 0x81
          is I64RemS -> pushI64(args[1].toI64() % args[0].toI64())
          // 0x82
          is I64RemU -> pushI64(I64.remU(args[1].toI64(), args[0].toI64()))
          // 0x83
          is I64And -> pushI64(args[1].toI64() and args[0].toI64())
          // 0x84
          is I64Or -> pushI64(args[1].toI64() or args[0].toI64())
          // 0x85
          is I64Xor -> pushI64(args[1].toI64() xor args[0].toI64())
          // 0x86
          is I64Shl -> pushI64(args[1].toI64() shl args[0].toI64().toInt())
          // 0x87
          is I64ShrS -> pushI64(args[1].toI64() shr args[0].toI64().toInt())
          // 0x88
          is I64ShrU -> pushI64(args[1].toI64() ushr args[0].toI64().toInt())
          // 0x89
          is I64Rotl -> pushI64(I64.rotateLeft(args[1].toI64(), args[0].toI64()))
          // 0x8A
          is I64Rotr -> pushI64(I64.rotateRight(args[1].toI64(), args[0].toI64()))

          // 0x8B
          is F32Abs -> pushF32(args[0].toF32().absoluteValue)
          // 0x8C
          is F32Neg -> pushF32(-args[0].toF32())
          // 0x8D
          is F32Ceil -> pushF32(ceil(args[0].toF32()))
          // 0x8E
          is F32Floor -> pushF32(floor(args[0].toF32()))
          // 0x8F
          is F32Trunc -> pushF32(truncate(args[0].toF32()))
          // 0x90
          is F32Nearest -> pushF32(F32.nearest(args[0].toF32()))
          // 0x91
          is F32Sqrt -> pushF32(sqrt(args[0].toF32()))
          // 0x92
          is F32Add -> pushF32(args[1].toF32() + args[0].toF32())
          // 0x93
          is F32Sub -> pushF32(args[1].toF32() - args[0].toF32())
          // 0x94
          is F32Mul -> pushF32(args[1].toF32() * args[0].toF32())
          // 0x95
          is F32Div -> pushF32(args[1].toF32() / args[0].toF32())
          // 0x96
          is F32Max -> pushF32(max(args[0].toF32(), args[1].toF32()))
          // 0x97
          is F32Min -> pushF32(min(args[0].toF32(), args[1].toF32()))
          // 0x98
          is F32CopySign -> pushF32(args[1].toF32().withSign(args[0].toF32()))

          // 0x99
          is F64Abs -> pushF64(args[0].toF64().absoluteValue)
          // 0x9A
          is F64Neg -> pushF64(-args[0].toF64())
          // 0x9B
          is F64Ceil -> pushF64(ceil(args[0].toF64()))
          // 0x9C
          is F64Floor -> pushF64(floor(args[0].toF64()))
          // 0x9D
          is F64Trunc -> pushF64(truncate(args[0].toF64()))
          // 0x9E
          is F64Nearest -> pushF64(F64.nearest(args[0].toF64()))
          // 0x9F
          is F64Sqrt -> pushF64(sqrt(args[0].toF64()))
          // 0xA0
          is F64Add -> pushF64(args[1].toF64() + args[0].toF64())
          // 0xA1
          is F64Sub -> pushF64(args[1].toF64() - args[0].toF64())
          // 0xA2
          is F64Mul -> pushF64(args[1].toF64() * args[0].toF64())
          // 0xA3
          is F64Div -> pushF64(args[1].toF64() / args[0].toF64())
          // 0xA4
          is F64Min -> pushF64(min(args[0].toF64(), args[1].toF64()))
          // 0xA5
          is F64Max -> pushF64(max(args[0].toF64(), args[1].toF64()))
          // 0xA6
          is F64CopySign -> pushF64(args[1].toF64().withSign(args[0].toF64()))

          // 0xA7
          is I32WrapI64 -> pushI32(args[0].toI64().toInt())
          // 0xA8
          is I32TruncF32S -> {
            val arg = args[0].toF32().assertFinite()

            // constants copied from int def
            if ((arg < -2147483648.0) || (arg > 2147483647.0)) {
              throw Error("Integer overflow during I32TruncF32S")
            }
            pushI32(arg.toInt())
          }
          // 0xA9
          is I32TruncF32U -> {
            val arg = args[0].toF32().assertFinite()
            // NOTE: since trunc rounds towards zero, the limit is -1.0
            if ((arg <= -1.0f) || (arg > 4294967295.0)) {
              throw Error("Integer overflow during I32TruncF32U")
            }
            pushI32(I32.fromF32U(arg))
          }
          // 0xAA
          is I32TruncF64S -> {
            val arg = args[0].toF64().assertFinite()
            if ((arg < -2147483648.0) || (arg > 2147483647.0)) {
              throw Error("Integer overflow during I32TruncF64S")
            }
            pushI32(arg.toInt())
          }
          // 0xAB
          is I32TruncF64U -> {
            val arg = args[0].toF64().assertFinite()
            if ((arg <= -1.0f) || (arg > 4294967295.0)) {
              throw Error("Integer overflow during I32TruncF64U")
            }
            pushI32(I32.fromF64U(arg))
          }
          // 0xAC
          is I64ExtendI32S -> pushI64(I64.extendS(args[0].toI32()))
          // 0xAD
          is I64ExtendI32U -> pushI64(I64.extendU(args[0].toI32()))
          // 0xAE
          is I64TruncF32S -> {
            val arg = args[0].toF32().assertFinite()
            if ((arg < -9.223372036854776E18) || (arg >= 9.223372036854776E18)) {
              throw Error("Integer overflow during I64TruncF32S")
            }
            pushI64(arg.toLong())
          }
          // 0xAF
          is I64TruncF32U -> {
            val arg = args[0].toF32().assertFinite()
            if ((arg <= -1.0f) || (arg >= 1.8446744073709552E19)) {
              throw Error("Integer overflow during I64TruncF32U")
            }
            pushI64(I64.fromF32U(arg))
          }
          // 0xB0
          is I64TruncF64S -> {
            val arg = args[0].toF64().assertFinite()
            if ((arg < -9.223372036854776E18) || (arg >= 9.223372036854776E18)) {
              throw Error("Integer overflow during I64TruncF64S")
            }
            pushI64(arg.toLong())
          }
          // 0xB1
          is I64TruncF64U -> {
            val arg = args[0].toF64().assertFinite()
            if ((arg <= -1.0f) || (arg >= 1.8446744073709552E19)) {
              throw Error("Integer overflow during I64TruncF64U")
            }
            pushI64(I64.fromF64U(arg))
          }
          // 0xB2
          is F32ConvertI32S -> pushF32(args[0].toI32().toFloat())
          // 0xB3
          is F32ConvertI32U -> pushF32(I32.convertToF32U(args[0].toI32()))
          // 0xB4
          is F32ConvertI64S -> pushF32(args[0].toI64().toFloat())
          // 0xB5
          is F32ConvertI64U -> push(F32ConvertI64U.call(args[0]))
          // 0xB6
          is F32DemoteF64 -> pushF32(args[0].toF64().toFloat())
          // 0xB7
          is F64ConvertI32S -> pushF64(args[0].toI32().toDouble())
          // 0xB8
          is F64ConvertI32U -> pushF64(I32.convertToF64U(args[0].toI32()))
          // 0xB9
          is F64ConvertI64S -> pushF64(args[0].toI64().toDouble())
          // 0xBA
          is F64ConvertI64U -> pushF64(I64.convertToF64U(args[0].toI64()))
          // 0xBB
          is F64PromoteF32 -> pushF64(args[0].toF32().toDouble())
          // 0xBC
          is I32ReinterpretF32 -> pushI32(args[0].toF32().toRawBits())
          // 0xBD
          is I64ReinterpretF64 -> pushI64(args[0].toF64().toRawBits())
          // 0xBE
          is F32ReinterpretI32 -> pushF32(Float.fromBits(args[0].toI32()))
          // 0xBE
          is F64ReinterpretI64 -> pushF64(Double.fromBits(args[0].toI64()))
          // 0xC0
          is I32Extend8S -> pushI32(args[0].toI32().toByte().toInt())
          // 0xC1
          is I32Extend16S -> pushI32(args[0].toI32().toShort().toInt())
          // 0xC2
          is I64Extend8S -> pushI64(args[0].toI64().toByte().toLong())
          // 0xC3
          is I64Extend16S -> pushI64(args[0].toI64().toShort().toLong())
          // 0xC4
          is I64Extend32S -> pushI64(args[0].toI64().toInt().toLong())
          // 0xFC00 // TODO: These extensions may be wrong
          is I32TruncSatF32S -> pushI32(args[0].toF32().toInt())
          // 0xFC01
          is I32TruncSatF32U -> pushI32(args[0].toF32().toInt())
          // 0xFC02
          is I32TruncSatF64S -> pushI32(args[0].toF64().toInt())
          // 0xFC03
          is I32TruncSatF64U -> pushI32(args[0].toF64().toInt())
          // 0xFC04
          is I64TruncSatF32S -> pushI64(args[0].toF32().toLong())
          // 0xFC05
          is I64TruncSatF32U -> pushI64(args[0].toF32().toLong())
          // 0xFC06
          is I64TruncSatF64S -> pushI64(args[0].toF64().toLong())
          // 0xFC07
          is I64TruncSatF64U -> pushI64(args[0].toF64().toLong())
          // 0xFC08
          is MemoryInit -> TODO()
          // 0xFC09
          is DataDrop -> TODO()
          // 0xFC0A
          is MemoryCopy -> {
            val dest = args[0].toI32()
            val src = args[1].toI32()
            val len = args[2].toI32()
            module.memory.store(dest, len, module.memory.load(src, len))
            pushI32(dest)
          }
          // 0xFC0B
          is MemoryFill -> {
            val dest = args[0].toI32()
            val value = args[1].toI32()
            val len = args[2].toI32()
            module.memory.store(dest, len, ByteArray(len) { value.toByte() })
            pushI32(dest)
          }
          // 0xFC0C
          is TableInit -> TODO()
          // 0xFC0D
          is ElemDrop -> TODO()
          // 0xFC0E
          is TableCopy -> TODO()
        }

    return nextInstruction
  }

  private fun pushLocal(idx: Int) {
    push(locals[idx])
  }

  private fun setLocal(idx: Int, value: Long) {
    locals[idx] = value
  }

  private fun executeCall(function: FunctionRef) {
    val type = function.type()

    // NOTE pop in reverse order
    val arguments = type.parameters.reversed().map { pop().toType(it) }.reversed()

    val result = function.call(arguments)

    if (result !is UnitValue) {
      push(result.toLong())
    }
  }
}

class ExecutionError(instructionPointer: Int, instruction: Instruction, e: Throwable) :
    Error("Error during execution of $instruction @ $instructionPointer: $e", e)

class TrapError : Error()

fun Long.toI32(): Int = this.toInt()

fun Long.toI64(): Long = this

fun Long.toF32(): Float = Float.fromBits(this.toInt())

fun Long.toF64(): Double = Double.fromBits(this)

@Suppress("unused") fun Int.toI64(): Long = this.toLong()

fun Float.toI64(): Long = this.toRawBits().toLong()

@Suppress("unused") fun Double.toI64(): Long = this.toRawBits()

fun Long.toI32Value(): I32Value = I32Value(this.toI32())

fun Long.toI64Value(): I64Value = I64Value(this.toI64())

fun Long.toF32Value(): F32Value = F32Value(this.toF32())

fun Long.toF64Value(): F64Value = F64Value(this.toF64())

fun WasmValue.toLong(): Long =
    when (this) {
      is I32Value -> this.value.toLong()
      is I64Value -> this.value
      is F32Value -> this.value.toRawBits().toLong()
      is F64Value -> this.value.toRawBits()
      else -> throw Error()
    }

fun Long.toType(type: Type): WasmValue =
    when (type) {
      Type.I32 -> this.toI32Value()
      Type.I64 -> this.toI64Value()
      Type.F32 -> this.toF32Value()
      Type.F64 -> this.toF64Value()
      else -> throw Error()
    }

fun Float.assertFinite(): Float {
  if (!this.isFinite()) {
    throw Error("Expect finite float")
  }
  return this
}

fun Double.assertFinite(): Double {
  if (!this.isFinite()) {
    throw Error("Expect finite float")
  }
  return this
}
