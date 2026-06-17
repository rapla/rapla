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
        assertEquals("/api/externaleventimport", root.value(), "Service root path must be /api/externaleventimport");
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
    void createReservationsExposed()
    {
        Method m = methodNamed("createReservations");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "createReservations must be POST");
        assertEquals("/createReservations", pe.value());
        assertEquals(java.util.List.class, m.getReturnType());
        java.lang.reflect.ParameterizedType returnType = (java.lang.reflect.ParameterizedType) m.getGenericReturnType();
        assertEquals(org.rapla.entities.domain.internal.ReservationImpl.class, returnType.getActualTypeArguments()[0],
                "wire type must be the concrete impl — springdoc reflects the signature, the Jackson "
                        + "abstract-type mapping never reaches the OpenAPI doc");
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(CreateReservationsRequest.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class), "request must be @RequestBody");
    }

    @Test
    void syncClassificationExposed()
    {
        Method m = methodNamed("syncClassification");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "syncClassification must be POST");
        assertEquals("/syncClassification", pe.value());
        assertEquals(SyncClassificationResult.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(SyncClassificationRequest.class, p.getType());
        assertNotNull(p.getAnnotation(RequestBody.class), "request must be @RequestBody");
    }

    @Test
    void createReservationsRequestDtoIsBeanShaped()
    {
        CreateReservationsRequest req = new CreateReservationsRequest();
        req.setSourceItemIds(java.util.List.of("a", "b"));
        req.setTemplateAllocatableId("template-1");
        assertEquals(2, req.getSourceItemIds().size());
        assertEquals("template-1", req.getTemplateAllocatableId());
        assertNotNull(req.getAdditionalAllocatableIds(), "additionalAllocatableIds default must not be null");
    }

    /** The sync flow moved to {@code syncClassification} (PRD 068) — the old piggyback flags on the
     *  create request must stay deleted, or the silent-no-op sync path can come back. */
    @Test
    void createReservationsRequestCarriesNoSyncFlags()
    {
        for (Method m : CreateReservationsRequest.class.getDeclaredMethods())
        {
            assertTrue(!m.getName().equals("setUpdateExistingReservation")
                    && !m.getName().equals("isUpdateExistingReservation")
                    && !m.getName().equals("setExistingReservationId")
                    && !m.getName().equals("getExistingReservationId"),
                    "sync flag leaked back into CreateReservationsRequest: " + m.getName());
        }
    }

    @Test
    void syncDtosAreBeanShaped()
    {
        SyncClassificationRequest req = new SyncClassificationRequest();
        ImportItem item = new ImportItem();
        item.setSourceItemId("v:42");
        req.setSelectedItem(item);
        assertEquals("v:42", req.getSelectedItem().getSourceItemId());

        SyncClassificationResult result = new SyncClassificationResult();
        result.setAllocatableIds(java.util.List.of("a1", "a2"));
        assertEquals(2, result.getAllocatableIds().size());
        assertNotNull(new SyncClassificationResult().getAllocatableIds(), "allocatableIds default must not be null");
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
