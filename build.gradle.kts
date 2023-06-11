plugins {
  kotlin("multiplatform") version "1.8.21"
  `maven-publish`
  @Suppress("SpellCheckingInspection")
  id("com.ncorti.ktfmt.gradle") version "0.12.0"
}

group = "com.github.yeicor"

version = "1.0.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin {
  jvm {}

  js(IR) {
    browser {}
    nodejs {}
  }

  val hostOs = System.getProperty("os.name")
  val isMingwX64 = hostOs.startsWith("Windows")
  when {
    hostOs == "Mac OS X" -> macosX64("native")
    hostOs == "Linux" -> linuxX64("native")
    isMingwX64 -> mingwX64("native")
    else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
  } // Native config: .apply {}

  @Suppress("UNUSED_VARIABLE")
  sourceSets {
    val commonMain by getting
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmMain by getting
    val jvmTest by getting
    val jsMain by getting
    val jsTest by getting
    val nativeMain by getting
    val nativeTest by getting
  }
}

publishing {
  repositories {
    (System.getenv("GITHUB_ACTOR") to System.getenv("GITHUB_TOKEN"))
        .takeIf {
          if (it.first == null)
              logger.warn("GITHUB_ACTOR is not set, disabling GitHubPackages publishing")
          else if (it.second == null)
              logger.warn("GITHUB_TOKEN is not set, disabling GitHubPackages publishing")
          it.first != null && it.second != null
        }
        ?.let { (githubUsername, githubPassword) ->
          maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Yeicor/${project.name}")
            credentials {
              username = githubUsername
              password = githubPassword
            }
          }
        }
    (System.getenv("REPSY_USER") to System.getenv("REPSY_PASS"))
        .takeIf {
          if (it.first == null) logger.warn("REPSY_USER is not set, disabling Repsy publishing")
          else if (it.second == null)
              logger.warn("REPSY_TOKEN is not set, disabling Repsy publishing")
          it.first != null && it.second != null
        }
        ?.let { (repsyUsername, repsyPassword) ->
          maven {
            name = "Repsy"
            url = uri("https://repsy.io/mvn/yeicor/github-public/")
            credentials {
              username = repsyUsername
              password = repsyPassword
            }
          }
        }
  }
}
