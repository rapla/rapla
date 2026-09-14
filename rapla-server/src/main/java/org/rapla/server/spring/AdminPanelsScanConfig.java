package org.rapla.server.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Component scan that picks up server-side {@code @Service}-annotated
 *  {@code PreferencesPanel} impls (PRD 020).
 *
 *  <p>Pulled into the auto-config from {@link RaplaServerAutoConfiguration}.
 *  Kept as a separate config class so the panel scan is explicit and
 *  greppable — the rest of the server tier stays on the explicit
 *  {@code @Bean}-factory pattern documented in AGENTS.md §4. The panel
 *  classes here have no overlap with those factories (new package,
 *  PRD-020-only types). */
@Configuration
@ComponentScan(basePackages = {
        "org.rapla.server.adminpanels",
        "org.rapla.plugin.planningstatus.server",
        "org.rapla.plugin.appointmentnote.server",
        "org.rapla.plugin.csvexport.server",
        "org.rapla.plugin.autoexport.server",
        "org.rapla.plugin.timeslot.server",
        "org.rapla.plugin.archiver.server",
        "org.rapla.plugin.mail.server",
        "org.rapla.plugin.eventtimecalculator.server",
        "org.rapla.plugin.exchangeconnector.server",
        "org.rapla.plugin.export2ical.server",
        "org.rapla.plugin.jndi.server"
})
public class AdminPanelsScanConfig
{
}
