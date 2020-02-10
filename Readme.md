# A WebAssembly Interpreter in Kotlin

Usage:

```kotlin
val data = File("module.wasm").readBytes()
val module = parseBinaryModule("module.wasm", data)
val add = module.lookupFunction("add", null)

assert(add(1, 2) == 3)
```
