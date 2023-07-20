package com.github.yeicor.ktmpwasm

// TODO: implement these tests (cross-platform resource loading)
// import com.github.yeicor.ktmpwasm.testing.FileLoader
// import com.github.yeicor.ktmpwasm.testing.Spec
// import com.github.yeicor.ktmpwasm.testing.parseSpec
//
// import kotlin.jvm.JvmStatic
//
/// **
// * Test the WASM specs
// *
// * See: https://github.com/WebAssembly/spec/tree/master/test/core
// *
// */
// class TestWasmSpecs {
//    companion object {
//        @JvmStatic
//        fun suite(): TestSuite = TestSuite().also {
//            for (name in specs) {
//                val spec = parseSpec(LocalFileLoader(), name, ignore = ignore, path = "core-1.1")
//                val test = makeTestCase(name, spec)
//                it.addTest(test)
//            }
//        }
//
//        val ignore = setOf(
//            // NOTE: the rounding logic of the JVM and the webassembly spec seems different,
//            // however errors are always single bit
//            "conversions:317", // 1509949441 vs. 1509949440
//            "conversions:318", // 1593835519 vs. 1593835520
//            "conversions:319", // 1593835521 vs. 1593835520
//            "conversions:320", // 1602224127 vs. 1602224126
//
//            // TODO: here a trap in the start function of another module modifies the memory /
// table
//            "linking:387", "linking:388",
//            ""
//        )
//
//        val specs = listOf(
//            "address", "align", "binary-leb128", "binary", "block", "br", "br_if", "br_table",
// "break-drop", "call",
//            "call_indirect", "comments", "const", "conversions", "custom", "data", "elem",
// "endianness", "exports",
//            "f32", "f32_bitwise", "f32_cmp", "f64", "f64_bitwise", "f64_cmp", "fac",
// "float_exprs", "float_literals",
//            "float_memory", "float_misc", "forward", "func", "func_ptrs", "globals", "i32", "i64",
// "if", "imports",
//            "inline-module", "int_exprs", "int_literals", "labels", "left-to-right", "linking",
// "load", "local_get",
//            "local_set", "local_tee", "loop", "memory", "memory_grow", "memory_redundancy",
// "memory_size",
//            "memory_trap", "names", "nop", "return", "select", "skip-stack-guard-page", "stack",
// "start", "store",
//            "switch", "token", "traps", "type", "unreachable", "unreached-invalid", "unwind",
// "utf8-custom-section-id",
//            "utf8-import-field", "utf8-import-module", "utf8-invalid-encoding")
//    }
// }
//
// class LocalFileLoader : FileLoader {
//    override fun readBytes(path: String): ByteArray =
//        javaClass.classLoader!!.getResourceAsStream(path)?.readBytes() ?: throw
// FileNotFoundError(path)
// }
//
// fun makeTestCase(name: String, spec: Spec): TestCase =
//    object : TestCase(name) {
//        override fun run(result: TestResult?) {
//            result?.startTest(this)
//            try {
//                spec.run()
//            } catch (e: Throwable) {
//                result?.addError(this, e)
//            }
//            result?.endTest(this)
//        }
//    }
//
// class FileNotFoundError(message: String) : Error(message)
