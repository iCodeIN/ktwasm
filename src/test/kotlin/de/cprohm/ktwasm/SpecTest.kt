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
        fun suite(): TestSuite = TestSuite().also { addSpecs(it, specs, ignore) }

        // all known failures
        val ignore = setOf(
            // conversions are not checked ATM
            "conversions:70", "conversions:71", "conversions:72", "conversions:73",
            "conversions:74", "conversions:75", "conversions:76", "conversions:77",
            "conversions:92", "conversions:93", "conversions:94", "conversions:95",
            "conversions:96", "conversions:97", "conversions:98", "conversions:99",
            "conversions:115", "conversions:116", "conversions:117", "conversions:118",
            "conversions:119", "conversions:120", "conversions:121", "conversions:122",
            "conversions:138", "conversions:139", "conversions:140", "conversions:141",
            "conversions:142", "conversions:143", "conversions:144", "conversions:145",
            "conversions:146", "conversions:147", "conversions:148", "conversions:166",
            "conversions:167", "conversions:168", "conversions:169", "conversions:170",
            "conversions:171", "conversions:172", "conversions:173", "conversions:186",
            "conversions:187", "conversions:188", "conversions:189", "conversions:190",
            "conversions:191", "conversions:192", "conversions:193", "conversions:211",
            "conversions:212", "conversions:213", "conversions:214", "conversions:215",
            "conversions:216", "conversions:217", "conversions:218", "conversions:235",
            "conversions:236", "conversions:237", "conversions:238", "conversions:239",
            "conversions:240", "conversions:241", "conversions:242",
            // the nan with extra data is not supported ATM
            "conversions:434", "conversions:443", "conversions:444", "conversions:445",
            "conversions:450", "conversions:459", "conversions:460", "conversions:461",
            // TODO: investigate the reason for these failures
            // unclear float errors
            "float_exprs:1392", "float_exprs:1394", "float_exprs:1395", "float_exprs:2335",
            "float_exprs:2336", "float_exprs:2337", "float_exprs:2338", "float_exprs:2345",
            "float_exprs:2346", "float_exprs:2347", "float_exprs:2348",
            // NAN related errors
            "float_literals:107", "float_literals:109", "float_literals:110",
            "float_literals:111", "float_literals:112", "float_literals:113",
            "float_literals:139", "float_literals:141", "float_literals:142",
            "float_literals:143", "float_literals:144", "float_literals:145",
            // "NANs are canonicalized by fromBits"
            "float_memory:21", "float_memory:46", "float_memory:73",
            "float_memory:98", "float_memory:125", "float_memory:150",
            // Integer overflows are not checked atm
            "i32:64", "i64:64",
            // missing checks
            "int_exprs:349", "int_exprs:350",
            // ops are not checked atm
            "traps:20", "traps:21", "traps:50", "traps:51", "traps:52",
            "traps:53", "traps:54", "traps:55", "traps:56", "traps:57",
            ""
        )

        val specs = listOf(
            "address", "align", "binary", "block", "br", "br_if", "br_table", "break-drop",
            "call", "call_indirect", "comments", "const", "conversions", "custom", "data",
            // TODO: the module elem.0wasm seems to be malformed + other modules require imports
            // "elem",
            "endianness", "exports", "f32", "f32_bitwise", "f32_cmp", "f64", "f64_bitwise",
            "f64_cmp", "fac", "float_exprs", "float_literals", "float_memory", "float_misc",
            "forward", "func", "func_ptrs", "globals", "i32", "i64", "if", "imports",
            "inline-module", "int_exprs", "int_literals", "labels", "left-to-right", "linking",
            "load", "local_get", "local_set", "local_tee", "loop", "memory", "memory_grow",
            "memory_redundancy", "memory_trap", "names", "nop", "return", "select",
            "skip-stack-guard-page", "stack", "start", "store", "switch", "token", "traps",
            "type", "typecheck", "unreachable", "unreached-invalid", "unwind",
            "utf8-custom-section-id", "utf8-import-field", "utf8-import-module",
            "utf8-invalid-encoding"
        )
    }
}


fun addSpecs(suite: TestSuite, specs: List<String>, ignore: Set<String>) {
    for (name in specs) {
        val spec = try {
            parseSpec(LocalFileLoader(), name, ignore = ignore)
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