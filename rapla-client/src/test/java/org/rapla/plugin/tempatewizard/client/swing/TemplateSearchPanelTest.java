package org.rapla.plugin.tempatewizard.client.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/** PRD 104 Phase 7: above one menu's worth of templates Swing shows one sorted, filterable list. */
class TemplateSearchPanelTest
{
    private final List<String> names = TemplateSearchPanel.sorted(
            List.of("Yin Yoga (2 Tage)", "Ayurveda Kochen (5 Tage)", "Abendkurs", "asana Flow", "Ayurveda Balance (2 Tage)"), Function.identity(), Locale.GERMAN);

    @Test
    void sortsByNameIgnoringCase()
    {
        assertEquals(List.of("Abendkurs", "asana Flow", "Ayurveda Balance (2 Tage)", "Ayurveda Kochen (5 Tage)", "Yin Yoga (2 Tage)"), names);
    }

    @Test
    void emptyQueryKeepsTheWholeList()
    {
        assertEquals(names, TemplateSearchPanel.filter(names, Function.identity(), "  "));
        assertEquals(names, TemplateSearchPanel.filter(names, Function.identity(), null));
    }

    @Test
    void everyTermMustMatchIgnoringCaseAndOrderIsKept()
    {
        assertEquals(List.of("Ayurveda Balance (2 Tage)", "Ayurveda Kochen (5 Tage)"), TemplateSearchPanel.filter(names, Function.identity(), "AYUR tage"));
        assertEquals(List.of("Ayurveda Kochen (5 Tage)"), TemplateSearchPanel.filter(names, Function.identity(), "5 ayur"));
        assertEquals(List.of(), TemplateSearchPanel.filter(names, Function.identity(), "ayur yin"));
    }
}
