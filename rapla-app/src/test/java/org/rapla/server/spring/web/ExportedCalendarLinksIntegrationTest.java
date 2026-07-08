package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Under Spring Boot the servlet context path is empty, so links built from
 * {@code contextPath + "/calendar"} point at {@code /calendar} — an unmapped,
 * auth-gated URL that renders as a blank page. The exported-calendar pages are
 * mounted at {@code /rapla/calendar}; every generated link must carry that
 * prefix. The index-page entry must also stay hidden unless the admin option
 * {@link AutoExportPlugin#SHOW_CALENDAR_LIST_IN_HTML_MENU} is enabled.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ExportedCalendarLinksIntegrationTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Autowired
    RaplaFacade facade;

    private void setShowCalendarList(boolean enabled) throws Exception
    {
        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, enabled);
        facade.store(edit);
    }

    @Test
    void indexHidesExportedCalendarsEntryWhenDisabled() throws Exception
    {
        setShowCalendarList(false);
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("calendar"))));
    }

    @Test
    void indexLinksExportedCalendarsWithRaplaPrefixWhenEnabled() throws Exception
    {
        setShowCalendarList(true);
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/rapla/calendar\"")))
                .andExpect(content().string(not(containsString("href=\"/calendar\""))));
    }

    @Test
    void calendarListPageLinksCarryRaplaPrefix() throws Exception
    {
        setShowCalendarList(true);
        User admin = facade.getUser("admin");
        CalendarSelectionModel model = facade.newCalendarModel(admin);
        model.setOption(AutoExportPlugin.HTML_EXPORT, "true");
        SynchronizedCompletablePromise.waitFor(model.save("linktest"), 10000);

        mockMvc.perform(get("/rapla/calendar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/rapla/calendar?user=admin")))
                .andExpect(content().string(not(containsString("href=\"/calendar?"))));
    }
}
