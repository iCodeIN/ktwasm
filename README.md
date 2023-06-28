# A WebAssembly Interpreter in Kotlin Multiplatform

This is a fork of the JVM-only webassembly interpreter from [iCodeIN/ktwasm](https://github.com/iCodeIN/ktwasm).

It supports all platforms supported by Kotlin Multiplatform, as it uses only common Kotlin code.

## Usage

build.gradle.kts:

```kotlin
repositories {
    // ...
    maven("https://repsy.io/mvn/yeicor/github-public")
}
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // ...
                implementation("com.github.yeicor:ktmpwasm:1.0.0")
            }
        }
    }
}
```

src/commonMain/kotlin/Main.kt:

```kotlin
import com.github.yeicor.ktmpwasm.api.*

fun main() {
  val module = parseModule(ByteArray(/* wasm file data */ ), MapEnvironment(mapOf(/* imports */ )))
  val add = module.lookupFunction("add", null)

  assert(add(1, 2) == 3)
}

```

Check out the example [Kraphviz](https://github.com/Yeicor/Kraphviz) project for more information.
