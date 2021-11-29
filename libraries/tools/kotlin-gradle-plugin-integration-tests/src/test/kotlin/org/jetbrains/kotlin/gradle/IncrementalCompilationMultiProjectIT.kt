package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.util.*
import org.junit.Assert
import org.junit.Test
import java.io.File

class IncrementalCompilationJsMultiProjectIT : BaseIncrementalCompilationMultiProjectIT() {
    override fun defaultProject(): Project {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        for (subProject in arrayOf("app", "lib")) {
            val subProjectDir = project.projectDir.resolve(subProject)
            subProjectDir.resolve("src/main/java").deleteRecursively()
            val buildGradle = subProjectDir.resolve("build.gradle")
            val buildJsGradle = subProjectDir.resolve("build-js.gradle")
            buildJsGradle.copyTo(buildGradle, overwrite = true)
            buildJsGradle.delete()
        }

        return project
    }

    override val additionalLibDependencies: String =
        "implementation \"org.jetbrains.kotlin:kotlin-test-js:${'$'}kotlin_version\""

    override val compileKotlinTaskName: String
        get() = "compileKotlin2Js"
}

open class IncrementalCompilationJvmMultiProjectIT : BaseIncrementalCompilationMultiProjectIT() {
    override val additionalLibDependencies: String =
        "implementation \"org.jetbrains.kotlin:kotlin-test:${'$'}kotlin_version\""

    override val compileKotlinTaskName: String
        get() = "compileKotlin"

    override fun defaultProject(): Project =
        Project("incrementalMultiproject")

    // todo: do the same for js backend
    @Test
    fun testDuplicatedClass() {
        val project = Project("duplicatedClass")
        project.build("build") {
            assertSuccessful()
        }

        val usagesFiles = listOf("useBuzz.kt", "useA.kt").map { project.projectFile(it) }
        usagesFiles.forEach { file -> file.modify { "$it\n " } }

        project.build("build") {
            assertSuccessful()
            assertCompiledKotlinSources(project.relativize(usagesFiles))
        }
    }


    // checks that multi-project ic is disabled when there is a task that outputs to javaDestination dir
    // that is not JavaCompile or KotlinCompile
    @Test
    fun testCompileLibWithGroovy() {
        val project = defaultProject()
        project.setupWorkingDir()
        val lib = File(project.projectDir, "lib")
        val libBuildGradle = File(lib, "build.gradle")
        libBuildGradle.modify {
            """
            apply plugin: 'groovy'
            apply plugin: 'kotlin'

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:${"$"}kotlin_version"
                implementation 'org.codehaus.groovy:groovy-all:2.4.8'
            }
            """.trimIndent()
        }

        val libGroovySrcBar = File(lib, "src/main/groovy/bar").apply { mkdirs() }
        val groovyClass = File(libGroovySrcBar, "GroovyClass.groovy")
        groovyClass.writeText(
            """
            package bar

            class GroovyClass {}
        """
        )

        project.build("build") {
            assertSuccessful()
        }

        project.projectDir.getFileByName("barUseB.kt").delete()
        project.build("build") {
            assertSuccessful()
            val affectedSources = File(project.projectDir, "app").allKotlinFiles()
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }
    }

    /** Regression test for KT-43489. Make sure build history mapping is not initialized too early. */
    @Test
    fun testBuildHistoryMappingLazilyComputedWithWorkers() {
        val project = defaultProject()
        project.setupWorkingDir()
        project.projectDir.resolve("app/build.gradle").appendText(
            """
                // added to force eager configuration
                tasks.withType(JavaCompile) {
                    options.encoding = 'UTF-8'
                }
            """.trimIndent()
        )
        val options = defaultBuildOptions().copy(parallelTasksInProject = true)
        project.build(options = options, params = arrayOf("build")) {
            assertSuccessful()
        }

        val aKt = project.projectDir.getFileByName("A.kt")
        aKt.writeText(
            """
package bar

open class A {
    fun a() {}
    fun newA() {}
}
"""
        )

        project.build(options = options, params = arrayOf("build")) {
            assertSuccessful()
            val affectedSources = project.projectDir.getFilesByNames("A.kt", "B.kt", "AA.kt", "AAA.kt", "BB.kt")
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }
    }
}

class IncrementalCompilationFirJvmMultiProjectIT : IncrementalCompilationJvmMultiProjectIT() {
    override fun defaultBuildOptions(): BuildOptions {
        return super.defaultBuildOptions().copy(useFir = true)
    }
}

class IncrementalCompilationClasspathSnapshotJvmMultiProjectIT : IncrementalCompilationJvmMultiProjectIT() {
    override fun defaultBuildOptions() = super.defaultBuildOptions().copy(useClasspathSnapshot = true)
}

abstract class BaseIncrementalCompilationMultiProjectIT : IncrementalCompilationBaseIT() {

    @Test
    fun testAbiChangeInLib_changeMethodSignature() {
        doTest(
            "A.kt",
            { it.replace("fun a() {}", "fun a(): Int = 1") },
            expectedAffectedFileNames = listOf("A.kt", "B.kt", "AA.kt", "AAA.kt", "BB.kt", "barUseA.kt", "fooUseA.kt")
        )
    }

    @Test
    fun testAbiChangeInLib_addNewMethod() {
        doTest(
            "A.kt",
            { it.replace("fun a() {}", "fun a() {}\nfun newA() {}") },
            expectedAffectedFileNames = listOf("A.kt", "B.kt", "AA.kt", "AAA.kt", "BB.kt")
        )
    }

    @Test
    fun testNonAbiChangeInLib_changeMethodBody() {
        doTest(
            "A.kt",
            { it.replace("fun a() {}", "fun a() { println() }") },
            expectedAffectedFileNames = listOf("A.kt")
        )
    }

    @Test
    fun testMoveFunctionFromLibToApp() {
        doTest(
            { project ->
                val barUseABKt = project.projectDir.getFileByName("barUseAB.kt")
                val barInApp = File(project.projectDir, "app/src/main/kotlin/bar").apply { mkdirs() }
                barUseABKt.copyTo(File(barInApp, barUseABKt.name))
                barUseABKt.delete()
            },
            expectedAffectedFileNames = listOf("fooCallUseAB.kt", "barUseAB.kt")
        )
    }

    @Test
    fun testAddNewMethodToLib() {
        doTest(
            options = defaultBuildOptions().copy(abiSnapshot = true),
            { project ->
                val aKt = project.projectDir.getFileByName("A.kt")
                aKt.writeText(
                    """
package bar

open class A {
    fun a() {}
    fun newA() {}
}
"""
                )
            },
            //TODO for abi-snapshot "BB.kt" should not be recompiled
            expectedAffectedFileNames = listOf(
                "A.kt", "B.kt", "AA.kt", "BB.kt", "AAA.kt"
            )
        )
    }

    @Test
    fun testLibClassBecameFinal() {
        // TODO: fix fir IC and remove
        if (defaultBuildOptions().useFir) return

        val project = defaultProject()
        project.build("build") {
            assertSuccessful()
        }

        val bKt = project.projectDir.getFileByName("B.kt")
        bKt.modify { it.replace("open class", "class") }

        project.build("build") {
            assertFailed()
            val affectedSources = project.projectDir.getFilesByNames(
                "B.kt", "barUseAB.kt", "barUseB.kt",
                "BB.kt", "fooCallUseAB.kt", "fooUseB.kt"
            )
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }
    }

    @Test
    fun testCleanBuildLib() {
        val project = defaultProject()

        project.build("build") {
            assertSuccessful()
        }

        project.build(":lib:clean") {
            assertSuccessful()
        }

        // Change file so Gradle won't skip :app:compile
        project.projectFile("BarDummy.kt").modify {
            it.replace("class BarDummy", "open class BarDummy")
        }

        //don't need to recompile app classes because lib's proto stays the same
        project.build("build") {
            assertSuccessful()
            val affectedSources = project.projectDir.allKotlinFiles()
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }

        val aaKt = project.projectFile("AA.kt")
        aaKt.modify { "$it " }
        project.build("build") {
            assertSuccessful()
            assertCompiledKotlinSources(project.relativize(aaKt))
        }
    }

    @Test
    fun testCleanBuildLibForAbiSnapshot() {
        val options = defaultBuildOptions().copy(abiSnapshot = true)
        val project = defaultProject()

        project.build("build", options = options) {
            assertSuccessful()
        }

        project.build(":lib:clean", options = options) {
            assertSuccessful()
        }

        // Change file so Gradle won't skip :app:compile
        project.projectFile("BarDummy.kt").modify {
            it.replace("class BarDummy", "open class BarDummy")
        }

        //don't need to recompile app classes because lib's proto stays the same
        project.build("build", options = options) {
            val affectedSources = project.projectDir.allKotlinFiles()
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }

        val aaKt = project.projectFile("AA.kt")
        aaKt.modify { "$it " }
        project.build("build", options = options) {
            assertSuccessful()
            assertCompiledKotlinSources(project.relativize(aaKt))
        }
    }

    protected abstract val additionalLibDependencies: String
    protected abstract val compileKotlinTaskName: String

    @Test
    fun testAddMethodToLibForAbiSnapshot() {
        val project = defaultProject()
        val options = defaultBuildOptions().copy(abiSnapshot = true)

        project.build("build", options = options) {
            assertSuccessful()
        }

        // Change file so Gradle won't skip :app:compile
        project.projectFile("BarDummy.kt").modify {
            it.replace("class BarDummy", "open class BarDummy")
        }

        val barDummyClassFile = project.projectFile("BarDummy.kt")
        barDummyClassFile.modify { "$it { fun m() = 42}" }

        project.build("build", options = options) {
            assertSuccessful()
            val relativePaths = project.relativize(barDummyClassFile)
            assertCompiledKotlinSources(relativePaths)
        }

    }

    @Test
    fun testAddDependencyToLib() {
        val project = defaultProject()

        project.build("build") {
            assertSuccessful()
        }

        val libBuildGradle = File(project.projectDir, "lib/build.gradle")
        Assert.assertTrue("$libBuildGradle does not exist", libBuildGradle.exists())
        libBuildGradle.modify {
            """
                $it

                dependencies {
                    $additionalLibDependencies
                }
            """.trimIndent()
        }
        // Change file so Gradle won't skip :app:compile
        project.projectFile("BarDummy.kt").modify {
            it.replace("class BarDummy", "open class BarDummy")
        }

        project.build("build") {
            assertSuccessful()
            val affectedSources = project.projectDir.allKotlinFiles()
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }

        val aaKt = project.projectFile("AA.kt")
        aaKt.modify { "$it " }
        project.build("build") {
            assertSuccessful()
            assertCompiledKotlinSources(project.relativize(aaKt))
        }
    }

    @Test
    fun testCompileErrorInLib() {
        val project = defaultProject()
        project.build("build") {
            assertSuccessful()
        }

        val bKt = project.projectDir.getFileByName("B.kt")
        val bKtContent = bKt.readText()
        bKt.delete()

        fun runFailingBuild() {
            project.build("build") {
                assertFailed()
                assertContains("B.kt has been removed")
                assertTasksFailed(":lib:$compileKotlinTaskName")
                val affectedFiles = project.projectDir.getFilesByNames("barUseAB.kt", "barUseB.kt")
                assertCompiledKotlinSources(project.relativize(affectedFiles))
            }
        }

        runFailingBuild()
        runFailingBuild()

        bKt.writeText(bKtContent.replace("fun b", "open fun b"))

        project.build("build") {
            assertSuccessful()
            val affectedFiles = project.projectDir.getFilesByNames(
                "B.kt", "barUseAB.kt", "barUseB.kt",
                "BB.kt", "fooUseB.kt"
            )
            assertCompiledKotlinSources(project.relativize(affectedFiles))
        }
    }

    @Test
    fun testCompileErrorInLibWithWorkers() {
        val project = defaultProject()
        val buildOptions = defaultBuildOptions().copy(
            parallelTasksInProject = true
        )
        project.build("build", options = buildOptions) {
            assertSuccessful()
        }

        val bKt = project.projectDir.getFileByName("B.kt")
        val bKtContent = bKt.readText()
        bKt.delete()

        fun runFailingBuild() {
            project.build("build", options = buildOptions) {
                assertFailed()
                assertContains("B.kt has been removed")
                assertTasksFailed(":lib:$compileKotlinTaskName")
                val affectedFiles = project.projectDir.getFilesByNames("barUseAB.kt", "barUseB.kt")
                assertCompiledKotlinSources(project.relativize(affectedFiles))
            }
        }

        runFailingBuild()
        runFailingBuild()

        bKt.writeText(bKtContent.replace("fun b", "open fun b"))

        project.build("build", options = buildOptions) {
            assertSuccessful()
            val affectedFiles = project.projectDir.getFilesByNames(
                "B.kt", "barUseAB.kt", "barUseB.kt",
                "BB.kt", "fooUseB.kt"
            )
            assertCompiledKotlinSources(project.relativize(affectedFiles))
        }
    }

    @Test
    fun testRemoveLibFromClasspath() {
        val project = defaultProject()
        project.build("build") {
            assertSuccessful()
        }

        val appBuildGradle = project.projectDir.resolve("app/build.gradle")
        val appBuildGradleContent = appBuildGradle.readText()
        appBuildGradle.modify { it.checkedReplace("implementation project(':lib')", "") }
        val aaKt = project.projectDir.getFileByName("AA.kt")
        aaKt.addNewLine()

        project.build("build") {
            assertFailed()
        }

        appBuildGradle.writeText(appBuildGradleContent)
        aaKt.addNewLine()

        project.build("build") {
            assertSuccessful()
            assertCompiledKotlinSources(project.relativize(aaKt))
        }
    }

    /** Regression test for KT-40875. */
    @Test
    fun testMoveFunctionFromLibWithRemappedBuildDirs() {
        val project = defaultProject()
        project.setupWorkingDir()
        project.projectDir.resolve("build.gradle").appendText("""

            allprojects {
                it.buildDir = new File(rootDir,  "../out" + it.path.replace(":", "/") + "/build")
            }
        """.trimIndent())
        project.build("build") {
            assertSuccessful()
        }

        val barUseABKt = project.projectDir.getFileByName("barUseAB.kt")
        val barInApp = File(project.projectDir, "app/src/main/kotlin/bar").apply { mkdirs() }
        barUseABKt.copyTo(File(barInApp, barUseABKt.name))
        barUseABKt.delete()

        project.build("build") {
            assertSuccessful()
            val affectedSources = project.projectDir.getFilesByNames("fooCallUseAB.kt", "barUseAB.kt")
            val relativePaths = project.relativize(affectedSources)
            assertCompiledKotlinSources(relativePaths)
        }
    }
}

