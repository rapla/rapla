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
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.test.util.FacadeTestSupport;

/** Dismissing the change marker: clears only what the caller may book, and only the marker. */
class ExternalEventStagingMutatorTest extends FacadeTestSupport
{
    private StubProvider provider;
    private ExternalEventStagingMutator mutator;
    private ExternalEventWorklistAssembler assembler;
    private Allocatable openGroup;
    private Allocatable restrictedGroup;
    private User admin;
    private User nonAdmin;

    @BeforeEach
    void setUp() throws Exception
    {
        admin = operator.getUser("homer");
        nonAdmin = operator.getUser("monty");
        provider = new StubProvider();
        final ExternalEventStagingReader reader = new ExternalEventStagingReader(operator, provider);
        mutator = new ExternalEventStagingMutator(operator, provider, reader, admin);
        assembler = new ExternalEventWorklistAssembler(operator, provider);

        openGroup = group("open-group");
        restrictedGroup = group("restricted-group");
        for (Permission permission : new ArrayList<>(restrictedGroup.getPermissionList()))
        {
            restrictedGroup.removePermission(permission);
        }
        final Permission ownerOnly = restrictedGroup.newPermission();
        ownerOnly.setUser(admin);
        ownerOnly.setAccessLevel(Permission.ALLOCATE);
        restrictedGroup.addPermission(ownerOnly);
        facade.storeObjects(new org.rapla.entities.Entity[] { openGroup, restrictedGroup });

        provider.items.add(item("visible", "v1"));
        provider.items.add(item("hidden", "h1"));
        provider.groups.put("visible", List.of(openGroup.getId()));
        provider.groups.put("hidden", List.of(restrictedGroup.getId()));

        final ExternalEventStagingService service = new ExternalEventStagingService(provider, operator, admin, 0L,
                () -> 1_000L);
        service.reconcile(true);
        // bind both, then change the source: that is what sets changedSince
        bind("Test:visible");
        bind("Test:hidden");
        provider.items.clear();
        provider.items.add(item("visible", "v2"));
        provider.items.add(item("hidden", "h2"));
        service.reconcile(true);
    }

    private void bind(String externalId) throws Exception
    {
        final DynamicType type = operator.getDynamicTypes().stream().filter(t -> t.getKey().equals("room"))
                .findFirst().orElseThrow();
        final Allocatable stamped = facade.newAllocatable(type.newClassification(), admin);
        stamped.getClassification().setValue("name", "bound-" + externalId);
        stamped.setAnnotation(org.rapla.entities.domain.RaplaObjectAnnotations.KEY_EXTERNALID, externalId);
        facade.store(stamped);
    }

    private Allocatable group(String name) throws Exception
    {
        final DynamicType type = operator.getDynamicTypes().stream().filter(t -> t.getKey().equals("room"))
                .findFirst().orElseThrow();
        final Allocatable allocatable = facade.newAllocatable(type.newClassification(), admin);
        allocatable.getClassification().setValue("name", name);
        return allocatable;
    }

    private static ImportItem item(String id, String version)
    {
        final ImportItem item = new ImportItem();
        item.setSourceItemId(id);
        final Map<String, Object> sourceData = new LinkedHashMap<>();
        sourceData.put("dualisId", id);
        sourceData.put("NAME", version);
        item.setSourceData(sourceData);
        return item;
    }

    private StagedEventState stateOf(User caller, String sourceItemId, Allocatable group) throws RaplaException
    {
        return assembler.assemble(caller, operator.getPermissionController(), List.of(group.getId()), null).groups()
                .stream().flatMap(g -> g.items().stream()).filter(i -> i.sourceItemId().equals(sourceItemId))
                .findFirst().map(WorklistItem::state).orElse(null);
    }

    @Test
    void dismissingClearsTheMarkerOnlyForWhatTheCallerMayBook() throws Exception
    {
        assertThat(stateOf(admin, "visible", openGroup)).isEqualTo(StagedEventState.CHANGED);

        final int clearedByOther = mutator.dismissChanges(nonAdmin, operator.getPermissionController(),
                List.of("visible", "hidden"));
        final int clearedByOwner = mutator.dismissChanges(admin, operator.getPermissionController(),
                List.of("visible", "hidden"));

        assertThat(clearedByOther).isEqualTo(1); // only the open group, hidden stays untouched
        assertThat(clearedByOwner).isEqualTo(1); // the previously hidden one
        assertThat(stateOf(admin, "visible", openGroup)).isEqualTo(StagedEventState.LINKED);
        assertThat(stateOf(admin, "hidden", restrictedGroup)).isEqualTo(StagedEventState.LINKED);
    }

    @Test
    void dismissingTwiceClearsNothingTheSecondTime() throws Exception
    {
        mutator.dismissChanges(admin, operator.getPermissionController(), List.of("visible"));

        assertThat(mutator.dismissChanges(admin, operator.getPermissionController(), List.of("visible"))).isZero();
    }

    @Test
    void unknownIdsClearNothing() throws Exception
    {
        assertThat(mutator.dismissChanges(admin, operator.getPermissionController(), List.of("nope"))).isZero();
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

        @Override
        public String externalIdOf(ImportItem item)
        {
            return "Test:" + item.getSourceItemId();
        }
    }
}
