package org.rapla.plugin.externaleventimport.server;

import org.rapla.entities.User;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.storage.CachableStorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for the external-event staging store.
 *
 *  <p>Opt-in via {@code rapla.externalevents.staging.enabled}: turning it on without an
 *  {@link ExternalEventSnapshotProvider} on the context fails fast with "no qualifying bean",
 *  which is the honest outcome — staging without a source has nothing to reconcile.
 *  Deliberately NOT {@code @ConditionalOnBean(provider)}: plugin autoconfigurations run after
 *  the server's, so a bean condition would evaluate before the provider is registered. */
@Configuration
@ConditionalOnProperty(prefix = "rapla.externalevents", name = "staging.enabled", havingValue = "true")
public class ExternalEventStagingConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalEventStagingConfig.class);

    /** Writes the staging store from the source — wired only where the reconciliation runs. */
    @Bean
    @ConditionalOnProperty(prefix = "rapla.externalevents", name = "staging.reconcile.enabled",
            havingValue = "true")
    public ExternalEventStagingService externalEventStagingService(ExternalEventSnapshotProvider provider,
            CachableStorageOperator operator,
            @Value("${rapla.externalevents.staging.import-user:}") String importUserName,
            @Value("${rapla.externalevents.staging.freshness-minutes:5}") long freshnessMinutes)
    {
        return new ExternalEventStagingService(provider, operator, stagingUser(operator, importUserName),
                freshnessMinutes * 60 * 1000L, System::currentTimeMillis);
    }

    /** The only bean that reads the source. A node that just serves the worklist or the create
     *  path leaves {@code staging.reconcile.enabled} unset — no cron, no startup run, and no
     *  connection to the source system is ever opened. OFF by default: a node reads the source
     *  only when explicitly told to. */
    @Bean
    @ConditionalOnProperty(prefix = "rapla.externalevents", name = "staging.reconcile.enabled",
            havingValue = "true")
    public ExternalEventStagingTask externalEventStagingTask(ExternalEventStagingService stagingService)
    {
        return new ExternalEventStagingTask(stagingService);
    }

    @Bean
    public ExternalEventStagingReader externalEventStagingReader(CachableStorageOperator operator,
            org.rapla.plugin.externaleventimport.ExternalEventAttribution attribution)
    {
        return new ExternalEventStagingReader(operator, attribution);
    }

    /** Staging rows are system bookkeeping — written as the configured staging user, never as
     *  the requesting one. Null when unconfigured or unknown. */
    private static User stagingUser(CachableStorageOperator operator, String importUserName)
    {
        if (importUserName == null || importUserName.isBlank())
        {
            return null;
        }
        try
        {
            return operator.getUser(importUserName);
        }
        catch (Exception ex)
        {
            LOGGER.error("Staging user '" + importUserName + "' not found — staging writes without user.");
            return null;
        }
    }

    @Bean
    public ExternalEventStagingMutator externalEventStagingMutator(CachableStorageOperator operator,
            org.rapla.plugin.externaleventimport.ExternalEventAttribution attribution,
            ExternalEventStagingReader reader,
            @Value("${rapla.externalevents.staging.import-user:}") String importUserName)
    {
        return new ExternalEventStagingMutator(operator, attribution, reader, stagingUser(operator, importUserName));
    }

    @Bean
    public ExternalEventBinder externalEventBinder(ExternalEventStagingReader reader,
            org.springframework.beans.factory.ObjectProvider<org.rapla.plugin.externaleventimport.ExternalEventCreateService> createService,
            CachableStorageOperator operator)
    {
        return new ExternalEventBinder(reader, createService.getIfAvailable(), operator);
    }

    @Bean
    public ExternalEventWorklistAssembler externalEventWorklistAssembler(CachableStorageOperator operator,
            org.rapla.plugin.externaleventimport.ExternalEventAttribution attribution)
    {
        return new ExternalEventWorklistAssembler(operator, attribution);
    }
}
