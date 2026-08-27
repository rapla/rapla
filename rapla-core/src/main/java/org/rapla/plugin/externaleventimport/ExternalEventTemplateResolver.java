package org.rapla.plugin.externaleventimport;

import java.util.List;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;

/**
 * Server-internal SPI (rapla PRD 104 v3): resolves the DEFAULT structure templates for the
 * sync dialog — which template shapes a new lecture / exam created from staged items of the
 * given groups. Deployment-owned so naming conventions (and later stored matching rules)
 * can evolve without any client change; the SPA only displays the result. §12: implementors
 * must only return templates the caller can read.
 */
public interface ExternalEventTemplateResolver
{
    record DefaultTemplates(String lectureTemplateId, String lectureTemplateName, String examTemplateId,
            String examTemplateName)
    {
    }

    DefaultTemplates resolveDefaultTemplates(User caller, List<String> groupAllocatableIds) throws RaplaException;
}
