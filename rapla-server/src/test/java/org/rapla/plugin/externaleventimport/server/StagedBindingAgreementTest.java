package org.rapla.plugin.externaleventimport.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.test.util.FacadeTestSupport;

/**
 * Worklist and create path must agree on what is OPEN. When they drift, the UI offers items the
 * create path then silently drops — observed 2026-08-11 with four exams listed as OPEN while the
 * create path considered them bound.
 */
class StagedBindingAgreementTest extends FacadeTestSupport
{
    private StubProvider provider;
    private ExternalEventWorklistAssembler assembler;
    private ExternalEventStagingReader reader;
    private Allocatable group;
    private User admin;

    @BeforeEach
    void setUp() throws Exception
    {
        admin = operator.getUser("homer");
        provider = new StubProvider();
        assembler = new ExternalEventWorklistAssembler(operator, provider);
        reader = new ExternalEventStagingReader(operator, provider);

        final DynamicType type = operator.getDynamicTypes().stream().filter(t -> t.getKey().equals("room"))
                .findFirst().orElseThrow();
        group = facade.newAllocatable(type.newClassification(), admin);
        group.getClassification().setValue("name", "kurs-group");
        facade.store(group);

        provider.items.add(item("checkable"));
        provider.items.add(item("uncheckable"));
        provider.groups.put("checkable", List.of(group.getId()));
        provider.groups.put("uncheckable", List.of(group.getId()));
        new ExternalEventStagingService(provider, operator, admin, 0L, () -> 1_000L).reconcile(true);
    }

    private static ImportItem item(String id)
    {
        final ImportItem item = new ImportItem();
        item.setSourceItemId(id);
        final Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("name", "Event " + id);
        item.setColumns(columns);
        return item;
    }

    /** An item whose external id cannot be derived is offered by NEITHER path. */
    @Test
    void anItemWithoutAnExternalIdIsListedNowhere() throws Exception
    {
        final WorklistView view = assembler.assemble(admin, operator.getPermissionController(),
                List.of(group.getId()), null);
        final List<String> listed = view.groups().stream().flatMap(g -> g.items().stream())
                .map(WorklistItem::sourceItemId).toList();
        final List<String> creatable = reader.loadOpenItems(List.of("checkable", "uncheckable")).stream()
                .map(ImportItem::getSourceItemId).toList();

        assertThat(listed).containsExactly("checkable");
        assertThat(creatable).containsExactly("checkable");
        assertThat(listed).isEqualTo(creatable);
    }

    /** The bound reservation is named to a caller who may read it.
     *
     *  <p>NOT covered: the negative case. Building a reservation this fixture's non-admin user
     *  may not read turned out to need more of rapla's reservation-read model than this test
     *  should carry — restricting the allocated resource does not restrict the reservation.
     *  The gate itself is a plain {@code permissionController.canRead(reservation, caller)} in
     *  {@code readableReservationId}; it is unproven by test, so treat it as such. */
    @Test
    void boundReservationIdIsNamedToAReaderOfTheReservation() throws Exception
    {
        final org.rapla.entities.dynamictype.DynamicType eventType = facade.getDynamicTypes(
                org.rapla.entities.dynamictype.DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        final org.rapla.entities.domain.Reservation reservation = facade
                .newReservation(eventType.newClassification(), admin);
        reservation.addAppointment(facade.newAppointmentWithUser(
                java.time.LocalDateTime.of(2026, 6, 10, 9, 0),
                java.time.LocalDateTime.of(2026, 6, 10, 10, 0), admin));
        reservation.setAnnotation(org.rapla.entities.domain.RaplaObjectAnnotations.KEY_EXTERNALID,
                "Test:checkable");
        facade.store(reservation);

        final WorklistView view = assembler.assemble(admin, operator.getPermissionController(),
                List.of(group.getId()), null);

        assertThat(idOf(view)).isEqualTo(reservation.getId());
    }

    /** An unbound item names no reservation. */
    @Test
    void anUnboundItemHasNoBoundReservationId() throws Exception
    {
        final WorklistView view = assembler.assemble(admin, operator.getPermissionController(),
                List.of(group.getId()), null);

        assertThat(idOf(view)).isNull();
    }

    private static String idOf(WorklistView view)
    {
        return view.groups().stream().flatMap(g -> g.items().stream())
                .filter(i -> i.sourceItemId().equals("checkable")).findFirst()
                .map(WorklistItem::boundReservationId).orElse(null);
    }

    private class StubProvider implements ExternalEventSnapshotProvider
    {
        private final List<ImportItem> items = new ArrayList<>();
        private final Map<String, Collection<String>> groups = new LinkedHashMap<>();

        @Override
        public String systemId()
        {
            return "TEST";
        }

        @Override
        public Collection<ImportItem> readAll() throws RaplaException
        {
            return new ArrayList<>(items);
        }

        @Override
        public Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items)
        {
            return groups;
        }

        /** No external id for one item — the "cannot determine" case. */
        @Override
        public String externalIdOf(ImportItem item)
        {
            return "uncheckable".equals(item.getSourceItemId()) ? null : "Test:" + item.getSourceItemId();
        }
    }
}
