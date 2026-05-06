package com.simplebackup.app.html

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural assertions on the real `viewer_template.html` asset.
 *
 * Catches regressions in the responsive mobile layout — specifically the kind of
 * "contact selector hidden with no way to open it" bug we hit on first device run.
 *
 * The test reads the asset directly (JVM unit-test working dir is the module root)
 * rather than going through HtmlGenerator, so it asserts on the source-of-truth
 * template, not the test fixture.
 */
class ViewerTemplateTest {

    private val template: String = run {
        val candidates = listOf(
            File("src/main/assets/viewer_template.html"),
            File("app/src/main/assets/viewer_template.html")
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("viewer_template.html not found from cwd=${File(".").absolutePath}")
        f.readText()
    }

    @Test fun `viewport meta tag is present`() {
        assertThat(template).contains("name=\"viewport\"")
        assertThat(template).contains("width=device-width")
    }

    @Test fun `mobile media query keeps sidebar visible by default`() {
        // The mobile breakpoint MUST NOT hide the sidebar without a thread-active class —
        // otherwise the contact list becomes unreachable on phones.
        val mobileBlock = mobileMediaQueryBlock()
        // Find the rule for the BARE `.sidebar` selector (not `.app.thread-active .sidebar`).
        // That rule's body must set display: block, not none.
        val bareSidebarRule = Regex("""(^|\n)\s*\.sidebar\s*\{[^}]*}""")
            .find(mobileBlock)?.value
            ?: error("no bare .sidebar rule in mobile media query")
        assertThat(bareSidebarRule).contains("display: block")
        assertThat(bareSidebarRule).doesNotContain("display: none")
    }

    @Test fun `mobile media query hides main pane until thread-active`() {
        val mobileBlock = mobileMediaQueryBlock()
        assertThat(mobileBlock).contains(".main { display: none")
        assertThat(mobileBlock).contains(".app.thread-active .main")
        assertThat(mobileBlock).contains(".app.thread-active .sidebar { display: none")
    }

    @Test fun `selectContact toggles thread-active on app container`() {
        assertThat(template).contains("classList.add('thread-active')")
    }

    @Test fun `back button removes thread-active`() {
        assertThat(template).contains("classList.remove('thread-active')")
    }

    @Test fun `dead show class logic is gone`() {
        // The old broken pattern toggled `.show` on the sidebar element.
        // It should not be present anywhere now.
        assertThat(template).doesNotContain("classList.add('show')")
        assertThat(template).doesNotContain("classList.remove('show')")
        assertThat(template).doesNotContain(".sidebar.show")
    }

    @Test fun `filter chips are present in toolbar`() {
        assertThat(template).contains("data-kind=\"all\"")
        assertThat(template).contains("data-kind=\"sms\"")
        assertThat(template).contains("data-kind=\"call\"")
    }

    private fun mobileMediaQueryBlock(): String {
        val start = template.indexOf("@media (max-width: 800px)")
        check(start >= 0) { "no mobile media query in template" }
        // Find the matching closing brace of the @media block (depth=0 after going in).
        var depth = 0
        var i = template.indexOf('{', start)
        check(i >= 0)
        depth = 1
        i++
        while (i < template.length && depth > 0) {
            when (template[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return template.substring(start, i)
    }
}
