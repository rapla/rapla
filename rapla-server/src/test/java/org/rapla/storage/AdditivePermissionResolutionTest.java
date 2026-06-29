package org.rapla.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the <b>purely additive</b> resolution of
 * [[0003-permissions-are-grant-only]] (revised 2026-06-28) / PRD 090: a user's
 * effective access is the <em>highest</em> level granted by <em>any</em> matching
 * row (user / group / world). There is <b>no precedence and no subtraction</b> —
 * {@code DENIED} (0) is the floor and a more-specific lower-level row can never
 * cap a broader grant downward.
 *
 * <p>This suite supersedes the precedence-era
 * {@code GrantOverridesDenyAtEqualPrecedenceTest}; the cases that used to assert a
 * downward override ({@code userDenied…}, {@code userReadCapsBelowGroupAllocate})
 * <em>invert</em> here.
 *
 * <p>Fixture: {@code monty} is a member of both {@code my-group} and
 * {@code powerplant}; the resource is owned by the admin so monty's access comes
 * purely from the permission rows.
 */
class AdditivePermissionResolutionTest extends FacadeTestSupport
{
    private PermissionController pc;
    private DynamicType roomType;
    private User monty;
    private Category myGroup;
    private Category powerplant;
    private Category powerplantAdmins;

    @BeforeEach
    void lookups() throws Exception
    {
        pc = operator.getPermissionController();
        roomType = facade.getDynamicType("room");
        assertNotNull(roomType, "fixture must define a 'room' type");
        monty = findUser("monty");
        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        myGroup = userGroups.getCategory("my-group");
        powerplant = userGroups.getCategory("powerplant");
        powerplantAdmins = powerplant.getCategory("powerplant-admins");
        assertNotNull(myGroup);
        assertNotNull(powerplant);
        assertNotNull(powerplantAdmins, "monty is a member of powerplant and powerplant-admins");
    }

    // ---------- DENIED is inert under additive (the floor) ----------

    @Test
    void groupGrantBeatsGroupDenied_grantAddedFirst() throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GRANT-THEN-DENY");
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);
        addGroupPermission(a, powerplant, AccessLevel.DENIED);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "grant on my-group wins; DENIED on powerplant is inert");
    }

    @Test
    void groupGrantBeatsGroupDenied_denyAddedFirst() throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "DENY-THEN-GRANT");
        addGroupPermission(a, powerplant, AccessLevel.DENIED);
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "row order is irrelevant — DENIED never subtracts");
    }

    @Test
    void groupDeniedNoLongerOverridesAllUsersGrant() throws Exception
    {
        // INVERTS vs the precedence model: an all-users (WORLD) ALLOCATE grant now
        // wins over a GROUP DENIED — the DENIED is the floor and subtracts nothing.
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "ALLUSERS-GRANT-GROUP-DENY");
        clearPermissions(a);
        addAllUsersPermission(a, AccessLevel.ALLOCATE);
        addGroupPermission(a, powerplant, AccessLevel.DENIED);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "all-users ALLOCATE grant survives the inert group DENIED");
    }

    @Test
    void loneGroupDeniedIsStillNoAccess() throws Exception
    {
        // a DENIED-only list grants nothing — identical to an empty list. Not a
        // subtraction, simply the absence of any grant.
        Allocatable denied = facade.newAllocatable(roomType.newClassification(), getAdmin());
        denied.getClassification().setValue("name", "DENY-ONLY");
        clearPermissions(denied);
        addGroupPermission(denied, powerplant, AccessLevel.DENIED);

        Allocatable empty = facade.newAllocatable(roomType.newClassification(), getAdmin());
        empty.getClassification().setValue("name", "NO-ROWS");
        clearPermissions(empty);

        assertFalse(pc.hasUserAccessAtLeast(denied, monty, AccessLevel.READ),
                "lone group-DENIED yields no access");
        assertFalse(pc.hasUserAccessAtLeast(empty, monty, AccessLevel.READ),
                "empty permission list yields no access — identical to the DENIED-only case");
    }

    // ---------- hierarchy: grants from any matching group add ----------

    @Test
    void grantOnSubGroupSurvivesDenyOnParentGroup() throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GRANT-SUB-DENY-PARENT");
        addGroupPermission(a, powerplantAdmins, AccessLevel.ALLOCATE);
        addGroupPermission(a, powerplant, AccessLevel.DENIED);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "grant on sub-group adds; DENIED on the parent is inert");
    }

    @Test
    void denyOnSubGroupCannotCarveOutAParentGroupGrant() throws Exception
    {
        // unchanged conclusion (grant-only has no exclusion primitive) but now for
        // the additive reason: the sub-group DENIED is the floor, the parent grant wins.
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GRANT-PARENT-DENY-SUB");
        addGroupPermission(a, powerplant, AccessLevel.ALLOCATE);
        addGroupPermission(a, powerplantAdmins, AccessLevel.DENIED);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "DENIED on the sub-group cannot subtract from the parent-group grant");
    }

    // ---------- USER rows no longer cap below GROUP/WORLD (the soft deny is gone) ----------

    @Test
    void userDeniedNoLongerOverridesGroupGrant() throws Exception
    {
        // INVERTS: "open to the group, deny this one person" no longer bites — the
        // group grant wins, the USER DENIED is the floor.
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GROUP-GRANT-USER-DENY");
        clearPermissions(a);
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);
        addUserPermission(a, monty, AccessLevel.DENIED);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "GROUP ALLOCATE grant wins; the USER DENIED subtracts nothing");
    }

    @Test
    void userGrantStillElevatesOverGroupDenied() throws Exception
    {
        // "closed for the group, allow this one person" — still works (it is an add).
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GROUP-DENY-USER-GRANT");
        clearPermissions(a);
        addGroupPermission(a, myGroup, AccessLevel.DENIED);
        addUserPermission(a, monty, AccessLevel.ALLOCATE);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "USER ALLOCATE grant adds; the GROUP DENIED is inert");
    }

    @Test
    void userDeniedIsOrderIndependentlyInert() throws Exception
    {
        // INVERTS: USER DENIED added before a GROUP ADMIN grant — additive ignores it.
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "USER-DENY-FIRST");
        clearPermissions(a);
        addUserPermission(a, monty, AccessLevel.DENIED);
        addGroupPermission(a, myGroup, AccessLevel.ADMIN);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ADMIN),
                "GROUP ADMIN grant wins regardless of row order; USER DENIED is the floor");
    }

    @Test
    void userReadNoLongerCapsBelowGroupAllocate() throws Exception
    {
        // INVERTS: the canonical "soft deny" — USER READ next to GROUP ALLOCATE.
        // Under additive monty gets the HIGHER of the two (ALLOCATE), no cap.
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GROUP-ALLOCATE-USER-READ");
        clearPermissions(a);
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);
        addUserPermission(a, monty, AccessLevel.READ);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.READ),
                "monty can read");
        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "USER READ no longer caps below the GROUP ALLOCATE — additive takes the max");
    }

    @Test
    void groupBelowWorldGrantNoLongerCaps() throws Exception
    {
        // a GROUP READ alongside an all-users ALLOCATE: under additive every member
        // of the group still gets ALLOCATE (the world grant), no downward cap.
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "WORLD-ALLOCATE-GROUP-READ");
        clearPermissions(a);
        addAllUsersPermission(a, AccessLevel.ALLOCATE);
        addGroupPermission(a, myGroup, AccessLevel.READ);

        assertTrue(pc.hasUserAccessAtLeast(a, monty, AccessLevel.ALLOCATE),
                "all-users ALLOCATE adds; the lower GROUP READ does not cap");
    }

    private void addUserPermission(Allocatable a, User user, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setUser(user);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void addGroupPermission(Allocatable a, Category group, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setGroup(group);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void addAllUsersPermission(Allocatable a, AccessLevel level)
    {
        Permission p = a.newPermission(); // no user, no group → all-users (WORLD)
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void clearPermissions(Allocatable a)
    {
        for (Permission p : new java.util.ArrayList<>(a.getPermissionList()))
        {
            a.removePermission(p);
        }
    }

    private User findUser(String username) throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (username.equals(u.getUsername())) return u;
        }
        throw new IllegalStateException("fixture must include user " + username);
    }

    private User getAdmin() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include an admin user");
    }
}
