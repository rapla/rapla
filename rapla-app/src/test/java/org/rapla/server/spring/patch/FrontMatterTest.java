package org.rapla.server.spring.patch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 112 Option A — the front-matter head of a patch file and the create/update/skip rule. */
class FrontMatterTest
{
    @Test
    void aDocumentHeadIsAMustacheComment()
    {
        FrontMatter fm = FrontMatter.parse("""
                {{! rapla-document
                view: Leihschein
                public: true
                updated: 2026-09-02T10:00
                }}
                <!doctype html><p>{{name}}</p>""").orElseThrow();
        assertEquals("rapla-document", fm.kind());
        assertEquals("Leihschein", fm.value("view"));
        assertTrue(fm.isPublic());
        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 0), fm.updated());
        assertEquals(List.of(), fm.groups());
    }

    @Test
    void aViewHeadIsGraphqlCommentLines()
    {
        FrontMatter fm = FrontMatter.parse("""
                # rapla-view
                # public: false
                # groups: a, b
                # updated: 2026-09-02T10:00Z
                query Leihschein { serverTime }""").orElseThrow();
        assertEquals("rapla-view", fm.kind());
        assertTrue(!fm.isPublic());
        assertEquals(List.of("a", "b"), fm.groups());
        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 0), fm.updated());
    }

    @Test
    void anOffsetStampIsNormalisedToUtc()
    {
        FrontMatter fm = FrontMatter.parse("# rapla-view\n# updated: 2026-09-02T12:00+02:00\nquery X { serverTime }").orElseThrow();
        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 0), fm.updated());
    }

    @Test
    void aFileWithoutAHeadIsNotAPatch()
    {
        assertTrue(FrontMatter.parse("query X { serverTime }").isEmpty());
        assertTrue(FrontMatter.parse("<p>x</p>").isEmpty());
        assertTrue(FrontMatter.parse("{{! just a comment }}<p>x</p>").isEmpty());
    }

    @Test
    void theRuleIsCreateUpdateOrSkip()
    {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 2, 0, 0);
        assertEquals(FrontMatter.Action.CREATE, FrontMatter.decide(t1, Optional.empty()));
        assertEquals(FrontMatter.Action.UPDATE, FrontMatter.decide(t2, Optional.of(t1)));
        assertEquals(FrontMatter.Action.SKIP, FrontMatter.decide(t1, Optional.of(t2)));
        assertEquals(FrontMatter.Action.SKIP, FrontMatter.decide(t1, Optional.of(t1)));
    }
}
