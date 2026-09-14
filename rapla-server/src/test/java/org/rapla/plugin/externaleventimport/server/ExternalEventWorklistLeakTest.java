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

/**
 * §12 leak coverage for the Abgleich worklist: the scope is BOOKING RIGHTS on the group
 * allocatable, and a group the caller may not book must be indistinguishable from one that does
 * not exist — no id, no name, no count, not even the item that hangs off it.
 */
class ExternalEventWorklistLeakTest extends FacadeTestSupport
{
    private StubProvider provider;
    private ExternalEventStagingService service;
    private ExternalEventWorklistAssembler assembler;
    private Allocatable openGroup;
    private Allocatable restrictedGroup;
    private User admin;
    private User nonAdmin;

    @BeforeEach
    void setUpWorklist() throws Exception
    {
        admin = operator.getUser("homer");
        nonAdmin = operator.getUser("monty");
        provider = new StubProvider();
        service = new ExternalEventStagingService(provider, operator, admin, 0L, () -> 1_000_000L);
        assembler = new ExternalEventWorklistAssembler(operator, provider);

        openGroup = newGroup("open-group");
        restrictedGroup = newGroup("restricted-group");
        restrictOwnerOnly(restrictedGroup);
        facade.storeObjects(new org.rapla.entities.Entity[] { openGroup, restrictedGroup });

        provider.items.add(item("visible", "Event in the open group"));
        provider.items.add(item("hidden", "Event in the restricted group"));
        provider.groups.put("visible", List.of(openGroup.getId()));
        provider.groups.put("hidden", List.of(restrictedGroup.getId()));
        service.reconcile(true);
    }

    private Allocatable newGroup(String name) throws Exception
    {
        final DynamicType type = operator.getDynamicTypes().stream().filter(t -> t.getKey().equals("room")).findFirst()
                .orElseThrow();
        final Allocatable allocatable = facade.newAllocatable(type.newClassification(), admin);
        allocatable.getClassification().setValue("name", name);
        return allocatable;
    }

    /** Strip every permission and grant ALLOCATE to the owner only — nobody else may book it. */
    private void restrictOwnerOnly(Allocatable allocatable)
    {
        for (Permission permission : new ArrayList<>(allocatable.getPermissionList()))
        {
            allocatable.removePermission(permission);
        }
        final Permission ownerOnly = allocatable.newPermission();
        ownerOnly.setUser(admin);
        ownerOnly.setAccessLevel(Permission.ALLOCATE);
        allocatable.addPermission(ownerOnly);
    }

    /** The query is always scoped; the caller asks for both, the gate decides what comes back. */
    private List<String> bothGroups()
    {
        return List.of(openGroup.getId(), restrictedGroup.getId());
    }

    private static ImportItem item(String id, String name)
    {
        final ImportItem item = new ImportItem();
        item.setSourceItemId(id);
        final Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("name", name);
        item.setColumns(columns);
        return item;
    }

    @Test
    void ownerSeesBothGroups() throws Exception
    {
        final WorklistView view = assembler.assemble(admin, operator.getPermissionController(), bothGroups(), null);

        assertThat(view.groups()).hasSize(2);
        assertThat(view.counts().total()).isEqualTo(2);
    }

    @Test
    void callerWithoutBookingRightsSeesNeitherTheGroupNorItsItems() throws Exception
    {
        final WorklistView view = assembler.assemble(nonAdmin, operator.getPermissionController(), bothGroups(), null);

        assertThat(view.groups()).hasSize(1);
        assertThat(view.groups().get(0).allocatableId()).isEqualTo(openGroup.getId());
        assertThat(view.counts().total()).isEqualTo(1);

        final String rendered = view.toString();
        assertThat(rendered).doesNotContain(restrictedGroup.getId());
        assertThat(rendered).doesNotContain("restricted-group");
        assertThat(rendered).doesNotContain("Event in the restricted group");
    }

    /** Existence must not leak: a group that does not exist and one the caller may not book
     *  produce byte-identical responses. */
    @Test
    void forbiddenGroupIsIndistinguishableFromANonExistentOne() throws Exception
    {
        final WorklistView forbidden = assembler.assemble(nonAdmin, operator.getPermissionController(), bothGroups(), null);

        provider.groups.put("hidden", List.of("a-does-not-exist"));
        final WorklistView nonExistent = assembler.assemble(nonAdmin, operator.getPermissionController(), bothGroups(), null);

        assertThat(nonExistent.toString()).isEqualTo(forbidden.toString());
    }

    @Test
    void anonymousCallerSeesNothing() throws Exception
    {
        final WorklistView view = assembler.assemble(null, operator.getPermissionController(), bothGroups(), null);

        assertThat(view.groups()).isEmpty();
        assertThat(view.counts().total()).isZero();
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

        /** A derivable id — the leak test is about permissions, not about binding. */
        @Override
        public String externalIdOf(ImportItem item)
        {
            return "Test:" + item.getSourceItemId();
        }
    }
}
