package org.rapla.plugin.eventimport;

import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/templateimport")
public interface TemplateImport
{
    TypedComponentRole<Boolean> TEMPLATE_IMPORT_ENABLED = new TypedComponentRole<>("org.rapla.plugin.eventimport.enabled");

    String BEGIN_KEY = "DatumVon";
    String STORNO_KEY = "StorniertAm";
    String PRIMARY_KEY = "Seminarnummer";
    String TEMPLATE_KEY = "TitelName";

    @PostExchange("/importFromServer")
    ParsedTemplateResult importFromServer() throws RaplaException;
}
