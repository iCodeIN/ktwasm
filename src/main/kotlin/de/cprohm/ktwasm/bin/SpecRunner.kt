package de.cprohm.ktwasm.bin

import com.fasterxml.jackson.databind.ObjectMapper
import de.cprohm.ktwasm.api.parseModule
import de.cprohm.ktwasm.base.I64Value
import de.cprohm.ktwasm.base.Signature
import de.cprohm.ktwasm.base.Type
import java.io.File
import java.lang.Error

fun main(args: Array<String>) {
    if (args.size != 1) {
        throw Error("Need the spec description as an arugment")
    }

    val objectMapper = ObjectMapper();
    val specFile = File(args[0])
    val spec = objectMapper.readTree(specFile)

    val moduleFile = File(specFile.parentFile, spec.get("module").asText())
    val module = parseModule(moduleFile)

    val func = module.lookupFunction("root", Signature.of(Type.I64, Type.I64))

    var count = 0
    var failures = 0
    var errors = 0

    for (case in spec.get("test_cases")) {
        val type = case.get("type").asText()
        count += 1

        if (type == "Success") {
            val input = case.get("input").asLong()
            val expected = case.get("output").asLong()

            val actual = try {
                func.call(listOf(I64Value(input))).toI64()
            } catch (e: Throwable) {
                errors += 1
                println("[error] Unexpected error for input $input: $e")
                continue
            }
            if (actual != expected) {
                failures += 1
                println("[error] Unexpected output  for input $input: actual ($actual) != expected ($expected)")
            }
        } else if (type == "Failure") {
            val input = case.get("input").asLong()
            try {
                func.call(listOf(I64Value(input))).toI64()
                println("[error] Expected failure for $input")
                errors += 1
            } catch (e: Throwable) {
                continue
            }
        } else {
            errors += 1
            println("Unknown test case type: $type")
        }
    }

    println("Tests run: $count, Failures: $failures, Errors: $errors, Skipped: 0")
}