package org.rapla.plugin.externaleventimport;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the wire shape of {@link ExternalEventImportService}. Any future change to
 * the interface paths, method names, parameter types or annotations breaks this
 * test, forcing a coordinated update across all impls (vanilla rapla wizard +
 * any downstream deployment that ships a service impl, e.g. dhbwrapla).
 */
class ExternalEventImportServiceContractTest
{
    @Test
    void rootPathIsExternalEventImport()
    {
        HttpExchange root = ExternalEventImportService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root, "Service must be annotated @HttpExchange");
        assertEquals("/externaleventimport", root.value(), "Service root path must be /externaleventimport");
    }

    @Test
    void getMetadataExposed()
    {
        Method m = methodNamed("getMetadata");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "getMetadata must be GET");
        assertEquals("/metadata", ge.value());
        assertEquals(ExternalEventImportMetadata.class, m.getReturnType());
        assertEquals(0, m.getParameterCount(), "getMetadata takes no params — locale comes from request headers");
    }

    @Test
    void loadEventsExposed()
    {
        Method m = methodNamed("loadEvents");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "loadEvents must be POST");
        assertEquals("/loadEvents", pe.value());
        assertEquals(ExternalEventImportResult.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(ImportCriteria.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class), "loadEvents criteria must be @RequestBody");
    }

    @Test
    void uploadCsvExposed()
    {
        Method m = methodNamed("uploadCsv");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "uploadCsv must be POST");
        assertEquals("/uploadCsv", pe.value());
        assertEquals(ExternalEventImportResult.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(MultipartFile.class, p.getType());
        RequestPart rp = p.getAnnotation(RequestPart.class);
        assertNotNull(rp, "uploadCsv file must be @RequestPart");
        assertEquals("file", rp.value());
    }

    @Test
    void createReservationsExposed()
    {
        Method m = methodNamed("createReservations");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "createReservations must be POST");
        assertEquals("/createReservations", pe.value());
        assertEquals(java.util.List.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(CreateReservationsRequest.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class), "request must be @RequestBody");
    }

    @Test
    void createReservationsRequestDtoIsBeanShaped()
    {
        CreateReservationsRequest req = new CreateReservationsRequest();
        req.setSourceItemIds(java.util.List.of("a", "b"));
        req.setTemplateAllocatableId("template-1");
        req.setUpdateExistingReservation(true);
        req.setExistingReservationId("res-9");
        assertEquals(2, req.getSourceItemIds().size());
        assertEquals("template-1", req.getTemplateAllocatableId());
        assertTrue(req.isUpdateExistingReservation());
        assertEquals("res-9", req.getExistingReservationId());
        assertNotNull(req.getAdditionalAllocatableIds(), "additionalAllocatableIds default must not be null");
    }

    @Test
    void metadataDtoIsBeanShaped()
    {
        ExternalEventImportMetadata m = new ExternalEventImportMetadata();
        m.setSourceName("Dualis");
        assertEquals("Dualis", m.getSourceName());
        assertNotNull(m.getHierarchyLevels(), "default hierarchyLevels must not be null");
        assertNotNull(m.getResultColumns(), "default resultColumns must not be null");
        assertNotNull(m.getUiMessageOverrides(), "default uiMessageOverrides must not be null");
    }

    @Test
    void importItemDtoIsBeanShaped()
    {
        ImportItem item = new ImportItem();
        item.setSourceItemId("abc");
        assertEquals("abc", item.getSourceItemId());
        assertNotNull(item.getHierarchy());
        assertNotNull(item.getColumns());
    }

    @Test
    void pluginConstantsStable()
    {
        assertEquals("org.rapla.plugin.externaleventimport", ExternalEventImportPlugin.PLUGIN_ID);
        assertEquals("rapla.externalevents.enabled", ExternalEventImportPlugin.ENABLE_PROPERTY);
        assertTrue(!ExternalEventImportPlugin.ENABLE_BY_DEFAULT, "feature must be opt-in");
    }

    private static Method methodNamed(String name)
    {
        for (Method m : ExternalEventImportService.class.getDeclaredMethods())
        {
            if (m.getName().equals(name)) return m;
        }
        fail("Method not found: " + name);
        return null;
    }
}
