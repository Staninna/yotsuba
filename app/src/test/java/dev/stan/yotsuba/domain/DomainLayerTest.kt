package dev.stan.yotsuba.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `domain` is the leaf: core and data may import it, it imports nothing from them.
 * Walks the source tree so a stray `import dev.stan.yotsuba.core` fails here, not in review.
 */
class DomainLayerTest {
    @Test
    fun `domain imports nothing from core or data`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/dev/stan/yotsuba/domain") }
            .first { it.isDirectory }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filter { it.startsWith("import dev.stan.yotsuba.core") || it.startsWith("import dev.stan.yotsuba.data") }
                    .map { "${file.relativeTo(root)}: $it" }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
