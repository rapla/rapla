package org.rapla.plugin.externaleventimport.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rapla.rest.JsonParserWrapper;

/**
 * Rows outlive the code that wrote them: a field removed from {@link StagedRowData} still sits
 * in every row stored earlier. Parsing must ignore it, or the whole staging store turns
 * unreadable after a deployment — the worklist would silently go empty.
 */
class StagedRowDataCompatibilityTest
{
    private final JsonParserWrapper.JsonParser json = JsonParserWrapper.defaultJson().get();

    @Test
    void aRowWrittenBeforeAFieldWasRemovedStillParses()
    {
        final String storedBeforeTheRemoval = """
                {"source":{"sourceItemId":"v:1","columns":{"name":"Programmieren I"}},
                 "scopeKey":"SoSe 2026","vanishedSince":"2026-08-11T00:00:00Z","changedSince":null}
                """;

        final StagedRowData data = json.fromJson(storedBeforeTheRemoval, StagedRowData.class);

        assertThat(data.getSource().getSourceItemId()).isEqualTo("v:1");
        assertThat(data.getScopeKey()).isEqualTo("SoSe 2026");
    }
}
