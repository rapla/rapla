package org.rapla.server.spring;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Matches when the server has <em>a</em> datasource configured — either the XML
 * file store ({@code rapla.file-datasources.raplafile}) or the primary database
 * ({@code rapla.db-datasources.rapladb}). PRD 045 Phase 3.
 *
 * <p>{@code @ConditionalOnProperty} cannot OR across two distinct prefixes;
 * {@link AnyNestedCondition} does — it matches if any nested member condition
 * matches. Used to gate {@code ServerServiceConfig}, {@code JwtConfig} and
 * {@code ApiKeyController}, which previously keyed only on {@code raplafile}
 * and so could never boot a database-only deployment.
 */
public class DatasourceConfiguredCondition extends AnyNestedCondition
{
    public DatasourceConfiguredCondition()
    {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")
    static class FileDatasource
    {
    }

    @ConditionalOnProperty(prefix = "rapla.db-datasources.rapladb", name = "url")
    static class DbDatasource
    {
    }
}
