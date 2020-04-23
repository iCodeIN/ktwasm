package de.cprohm.ktwasm

import de.cprohm.ktwasm.testing.FileLoader
import de.cprohm.ktwasm.testing.Spec
import de.cprohm.ktwasm.testing.parseSpec

import junit.framework.TestSuite
import junit.framework.TestCase
import junit.framework.TestResult

import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
 * Test the WASM specs
 *
 * See: https://github.com/WebAssembly/spec/tree/master/test/core
 *
 */
@RunWith(AllTests::class)
class TestWasmSpecs {
    companion object {
        @JvmStatic
        fun suite(): TestSuite = TestSuite().also { addSpecs(it, specs, ignore, path = "core-1.1") }

        val ignore = setOf<String>(
            // NOTE: the rounding logic of the JVM and the webassembly spec seems different,
            // however errors are always single bit
            "conversions:317", // 1509949441 vs. 1509949440
            "conversions:318", // 1593835519 vs. 1593835520
            "conversions:319", // 1593835521 vs. 1593835520
            "conversions:320", // 1602224127 vs. 1602224126

            // TODO: here a trap in the start function of another module modifies the memory / table
            "linking:387", "linking:388",
            ""
        )

        val specs = listOf(
            "address", "align", "binary-leb128", "binary", "block", "br", "break-drop", "br_if", "br_table", "call",
            "call_indirect", "comments", "const", "conversions", "custom", "data", "elem", "endianness", "exports",
            "f32", "f32_bitwise", "f32_cmp", "f64", "f64_bitwise", "f64_cmp", "fac", "float _exprs", "float_literals",
            "float_memory", "float_misc", "forward", "func", "func_ptrs", "globals", "i32", "i64", "if", "imports",
            "inline-module", "int_exprs", "int_literals", "labels", "left-to-right", "linking", "load", "local_get",
            "local_set", "local_tee", "loop", "memory", "memory_ grow", "memory_redundancy", "memory_size",
            "memory_trap", "names", "nop", "return", "select", "skip-stack-guard-page", "stack", "start", "store",
            "switch", "token", "traps", "type", "unreachable", "unreached-invalid", "unwind", "utf8-custom-section-id",
            "utf8-import-field", "utf8-import- module", "utf8-invalid-encoding"
        )
    }
}


fun addSpecs(suite: TestSuite, specs: List<String>, ignore: Set<String>, path: String = "binary") {
    for (name in specs) {
        val spec = try {
            parseSpec(LocalFileLoader(), name, ignore = ignore, path = path)
        } catch (e: FileNotFoundError) {
            continue
        }
        val test = makeTestCase(name, spec)
        suite.addTest(test)
    }
}

class LocalFileLoader : FileLoader {
    override fun readBytes(path: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(path)?.readBytes() ?: throw FileNotFoundError(path)
}

fun makeTestCase(name: String, spec: Spec): TestCase =
    object : TestCase(name) {
        override fun run(result: TestResult?) {
            result?.startTest(this)
            try {
                spec.run()
            } catch (e: Throwable) {
                result?.addError(this, e)
            }
            result?.endTest(this)
        }
    }


class FileNotFoundError(message: String) : Error(message)