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

    /** A ranked template suggestion; reason is "exact", "parent" or "generic". */
    record RankedTemplate(String templateId, String name, String reason)
    {
    }

    /**
     * Template suggestions for the sync dialog's chip menu (dhbwrapla PRD 004 § H5a), best first.
     * {@code kind} is "v" (lecture) or "p" (exam). Default: the one resolved default template as
     * "exact", so deployments without ranking keep working. §12 as above.
     */
    default List<RankedTemplate> rankTemplates(User caller, List<String> groupAllocatableIds, String kind)
            throws RaplaException
    {
        final DefaultTemplates d = resolveDefaultTemplates(caller, groupAllocatableIds);
        if (d == null)
        {
            return List.of();
        }
        final boolean exam = "p".equals(kind);
        final String id = exam ? d.examTemplateId() : d.lectureTemplateId();
        final String name = exam ? d.examTemplateName() : d.lectureTemplateName();
        return id == null ? List.of() : List.of(new RankedTemplate(id, name, "exact"));
    }
}
