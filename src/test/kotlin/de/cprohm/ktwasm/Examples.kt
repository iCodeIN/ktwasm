package de.cprohm.ktwasm

import de.cprohm.ktwasm.api.*
import org.junit.Test
import kotlin.test.assertEquals

class Examples {
    @Test
    fun add() {
        val url = javaClass.classLoader?.getResource("modules/add_module.wasm")
            ?: throw Error("Cannot read 'modules/add_module.wasm'")

        val module: Namespace = parseModule(url)
        val add = module.lookupFunction("add", null)

        assertEquals(3, add(1, 2))
    }

    @Test
    fun global() {
        val url = javaClass.classLoader?.getResource("modules/global.wasm")
            ?: throw Error("Cannot read 'modules/add_module.wasm'")

        val module: Namespace = parseModule(url)
        val addGlobal = module.lookupFunction("add_global", null)

        assertEquals(0, module["g"])
        assertEquals(2, addGlobal(2))

        module["g"] = 5
        assertEquals(5, module["g"])
        assertEquals(8, addGlobal(3))
    }
}