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
        // That rule's body must set display to a visible value (block or flex), not none.
        val bareSidebarRule = Regex("""(^|\n)\s*\.sidebar\s*\{[^}]*}""")
            .find(mobileBlock)?.value
            ?: error("no bare .sidebar rule in mobile media query")
        assertThat(bareSidebarRule).containsMatch("""display:\s*(block|flex)""")
        assertThat(bareSidebarRule).doesNotContain("display: none")
    }

    @Test fun `mobile media query hides main pane until thread-active`() {
        val mobileBlock = mobileMediaQueryBlock()
        assertThat(mobileBlock).contains(".main { display: none")
        assertThat(mobileBlock).contains(".app.thread-active .main")
        assertThat(mobileBlock).contains(".app.thread-active .sidebar { display: none")
    }

    @Test fun `view-thread button toggles thread-active on app container`() {
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

    @Test fun `kind filter chips are present in toolbar`() {
        assertThat(template).contains("data-kind=\"all\"")
        assertThat(template).contains("data-kind=\"sms\"")
        assertThat(template).contains("data-kind=\"call\"")
    }

    // ---- Multi-contact selection ----

    @Test fun `default selection is all contacts`() {
        // Initialization must populate selectedAddrs with every contact key.
        assertThat(template).contains("for (const addr of Object.keys(state.backup.contacts || {})) {")
        assertThat(template).contains("state.selectedAddrs.add(addr);")
    }

    @Test fun `each contact row renders a checkbox`() {
        // The contact-row builder creates an input[type=checkbox] for each contact.
        assertThat(template).contains("cb.type = 'checkbox'")
        assertThat(template).contains("toggleSelect(c.addr, cb.checked)")
    }

    @Test fun `select all and clear all controls present`() {
        assertThat(template).contains("id=\"btn-select-all\"")
        assertThat(template).contains("id=\"btn-clear-all\"")
        assertThat(template).contains("function selectAll()")
        assertThat(template).contains("function clearAll()")
    }

    @Test fun `view thread button on mobile sidebar`() {
        assertThat(template).contains("id=\"btn-view-thread\"")
        // Sidebar footer is mobile-only (display: none default, display: block at narrow).
        val mobileBlock = mobileMediaQueryBlock()
        assertThat(mobileBlock).contains(".sidebar-footer { display: block")
    }

    @Test fun `thread filter checks selectedAddrs membership`() {
        // The currentlyFiltered() function must filter by selectedAddrs.has(e.addr).
        assertThat(template).contains("state.selectedAddrs.has(e.addr)")
    }

    // ---- Filter toolbar (Row 2) ----

    @Test fun `direction filter has all received and sent buttons`() {
        assertThat(template).contains("data-dir=\"all\"")
        assertThat(template).contains("data-dir=\"in\"")
        assertThat(template).contains("data-dir=\"out\"")
    }

    @Test fun `date range inputs are present`() {
        assertThat(template).contains("id=\"date-from\"")
        assertThat(template).contains("id=\"date-to\"")
    }

    @Test fun `body search input is present`() {
        assertThat(template).contains("id=\"search-body\"")
        // Search text must be applied against e.body in the filter pipeline.
        assertThat(template).contains("(e.body || '').toLowerCase().includes(state.bodySearch)")
    }

    @Test fun `print button triggers window print`() {
        assertThat(template).contains("id=\"btn-print\"")
        assertThat(template).contains("window.print()")
    }

    @Test fun `export TXT button generates download blob`() {
        assertThat(template).contains("id=\"btn-export\"")
        assertThat(template).contains("function exportTxt()")
        assertThat(template).contains("new Blob([")
        assertThat(template).contains("type: 'text/plain'")
    }

    @Test fun `print stylesheet hides chrome`() {
        // @media print rule must hide sidebar/toolbar so the printed page is just the thread.
        assertThat(template).contains("@media print")
        // The print rule uses !important to override the desktop layout — only used here.
        assertThat(template).contains("display: none !important")
    }

    // ---- WebView render robustness ----

    @Test fun `app container uses 100 percent height not 100vh`() {
        // 100vh in Android WebView can resolve to 0 when the WebView's LayoutParams aren't
        // explicit, collapsing the grid layout. Use 100% relative to html/body instead.
        val rule = Regex("""\.app\s*\{[^}]*}""")
            .find(template)?.value
            ?: error("no .app rule in template")
        assertThat(rule).contains("height: 100%")
        assertThat(rule).doesNotContain("height: 100vh")
    }

    @Test fun `html and body lock overflow to prevent layout collapse`() {
        // Matches the legacy SMS_Viewer.html approach — html/body with overflow hidden so
        // panes can scroll independently and 100% heights resolve deterministically.
        val rule = Regex("""html,\s*body\s*\{[^}]*}""")
            .find(template)?.value
            ?: error("no html, body rule in template")
        assertThat(rule).contains("overflow: hidden")
    }

    @Test fun `template version sentinel is present and bumped`() {
        // The sentinel string drives AppContainer.regenerateHtmlIfStale — bumping it forces
        // a refresh of any on-device HTML files generated by older app builds.
        assertThat(template).contains("viewer-template-version: 4")
    }

    // ---- Print: render every event, not just the current paginated page ----

    @Test fun `doPrint flips state printing before rendering and printing`() {
        // The fix for "PDF only contains 1 paginated page" is to set state.printing = true,
        // call renderThread() so the DOM holds every filtered event, then hand off to the
        // bridge / window.print. Restoration happens via window.__onPrintFinished, fired
        // by the native PrintDocumentAdapter wrapper when the user closes the print sheet.
        val doPrintBlock = Regex("""function doPrint\(\)\s*\{[\s\S]*?\n\s*\}""")
            .find(template)?.value ?: error("doPrint() function missing")
        assertThat(doPrintBlock).contains("state.printing = true")
        assertThat(doPrintBlock).contains("renderThread()")
        assertThat(doPrintBlock).contains("window.AndroidBridge.print()")
    }

    @Test fun `renderThread skips pagination when printing`() {
        // The whole point of the fix: while state.printing is true, every filtered event
        // goes into the DOM in one shot. Otherwise the WebView snapshot only captures
        // PAGE_SIZE rows.
        assertThat(template).contains("const noPaginate = state.printing")
        assertThat(template).contains("noPaginate ? events : events.slice(start, start + PAGE_SIZE)")
    }

    @Test fun `bridge can fire onPrintFinished to restore pagination`() {
        // The native PrintDocumentAdapter wrapper calls
        // window.__onPrintFinished and the page must repaginate from there.
        assertThat(template).contains("window.__onPrintFinished = function()")
        assertThat(template).contains("state.printing = false")
    }

    // ---- Native bridge for print + export ----

    @Test fun `template detects AndroidBridge before calling browser fallbacks`() {
        // Android WebView doesn't implement window.print() or <a download>, so the page
        // checks for the native bridge first and only falls through to the browser APIs
        // when running outside the app.
        assertThat(template).contains("function isInApp()")
        assertThat(template).contains("window.AndroidBridge")
        assertThat(template).contains("typeof window.AndroidBridge.print === 'function'")
    }

    @Test fun `print button delegates to bridge when in app`() {
        // The shared doPrint() handler must check isInApp() before window.print().
        assertThat(template).contains("function doPrint()")
        assertThat(template).contains("window.AndroidBridge.print()")
    }

    @Test fun `export TXT delegates to bridge when in app`() {
        // exportTxt() must call AndroidBridge.exportTxt(content, filename) before falling
        // back to the browser <a download> path.
        assertThat(template).contains("window.AndroidBridge.exportTxt(content, filename)")
        // Browser fallback must remain in place for the desktop / shared-file case.
        assertThat(template).contains("a.download = filename")
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
