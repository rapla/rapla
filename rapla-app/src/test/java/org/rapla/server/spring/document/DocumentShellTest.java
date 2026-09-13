package org.rapla.server.spring.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 097 D7a — the shell is reduced to the minimal wrapper for body-only templates + the preview script. */
class DocumentShellTest
{
    @Test
    void aFragmentGetsDoctypeCharsetTitleAndNothingElse()
    {
        String page = DocumentShell.wrapFragment("a<b", "<p>x</p>", "en-GB");
        assertTrue(page.startsWith("<!doctype html>"), page);
        assertTrue(page.contains("<html lang=\"en-GB\">"), page);
        assertTrue(page.contains("<meta charset=\"utf-8\">"), page);
        assertTrue(page.contains("<title>a&lt;b</title>"), page);
        assertTrue(page.contains("<body>\n<p>x</p>"), page);
        assertFalse(page.contains("<style"), "no CSS from the server any more: " + page);
        assertFalse(page.contains("<script"), page);
    }

    @Test
    void thePreviewScriptIsInjectedBeforeTheClosingBody()
    {
        String page = DocumentShell.injectPreviewScript("<!doctype html><html><body><p>x</p></body></html>");
        int script = page.indexOf("<script>");
        assertTrue(script > page.indexOf("<p>x</p>") && script < page.indexOf("</body>"), page);
        assertTrue(page.contains("rapla-preview-nav"), page);
    }
}
