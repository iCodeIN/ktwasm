# A WebAssembly Interpreter in Kotlin Multiplatform

This is a fork of the jvm-only webassembly interpreter from [iCodeIN/ktwasm](https://github.com/iCodeIN/ktwasm).

It supports all platforms supported by Kotlin Multiplatform, as it uses only common Kotlin code.

Usage:

```kotlin
import com.github.yeicor.ktwasm.api.*

val module = parseModule(ByteArray(/* wasm file data */), object : Environment { /* imports */ })
val add = module.lookupFunction("add", null)

assert(add(1, 2) == 3)
```
