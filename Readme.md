# A WebAssembly Interpreter in Kotlin

Usage:

```kotlin
import de.cprohm.ktwasm.api.*

val module = parseModule(File("module.wasm"))
val add = module.lookupFunction("add", null)

assert(add(1, 2) == 3)
```
