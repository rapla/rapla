package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.internal.UserImpl;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 129 — the operator's cache of a user's groups including parents. */
class UserGroupsCacheTest extends FacadeTestSupport
{
    private Category userGroups() throws Exception
    {
        return operator.getSuperCategory().getCategory("user-groups");
    }

    private User createUserIn(String username, Category group) throws Exception
    {
        User u = facade.newUser();
        u.setUsername(username);
        u.setName(username);
        u.addGroup(group);
        facade.store(u);
        return operator.getUser(username);
    }

    @Test
    void sameAnswerAsTheUncachedComputationForEveryUser() throws Exception
    {
        for (User user : operator.getUsers())
        {
            assertEquals(UserImpl.getGroupsIncludingParents(user), operator.getGroupsIncludingParents(user), user.getUsername());
        }
    }

    @Test
    void repeatedCallsReuseTheCachedSet() throws Exception
    {
        User monty = operator.getUser("monty");
        assertSame(operator.getGroupsIncludingParents(monty), operator.getGroupsIncludingParents(monty));
    }

    @Test
    void userChangeDropsOnlyThatUsersEntry() throws Exception
    {
        Category myGroup = userGroups().getCategory("my-group");
        User lisa = createUserIn("lisa", myGroup);
        User monty = operator.getUser("monty");
        Collection<String> montyBefore = operator.getGroupsIncludingParents(monty);
        operator.getGroupsIncludingParents(lisa);

        User edit = facade.edit(lisa);
        edit.removeGroup(myGroup);
        facade.store(edit);

        assertNotSame(lisa, operator.getUserGroupsCache().cachedUser(lisa.getId()), "the old instance's entry is evicted");
        User residentLisa = operator.getUser("lisa");
        assertFalse(operator.getGroupsIncludingParents(residentLisa).contains(myGroup.getId()), "lisa's change is seen");
        assertSame(montyBefore, operator.getGroupsIncludingParents(operator.getUser("monty")), "monty's entry survives");
    }

    @Test
    void categoryChangeDropsEveryEntry() throws Exception
    {
        Category myGroup = userGroups().getCategory("my-group");
        Category sub = facade.newCategory();
        sub.setKey("sub");
        Category myGroupEdit = facade.edit(myGroup);
        myGroupEdit.addCategory(sub);
        facade.storeObjects(new org.rapla.entities.Entity[] { myGroupEdit, sub });

        User lisa = createUserIn("lisa", userGroups().getCategory("my-group").getCategory("sub"));
        assertTrue(operator.getGroupsIncludingParents(lisa).contains(myGroup.getId()), "parent group included");

        Category residentSub = userGroups().getCategory("my-group").getCategory("sub");
        Category oldParent = facade.edit(userGroups().getCategory("my-group"));
        Category newParent = facade.edit(userGroups());
        Category movedSub = facade.edit(residentSub);
        oldParent.removeCategory(movedSub);
        newParent.addCategory(movedSub);
        facade.storeObjects(new org.rapla.entities.Entity[] { oldParent, newParent, movedSub });

        User residentLisa = operator.getUser("lisa");
        assertEquals(UserImpl.getGroupsIncludingParents(residentLisa), operator.getGroupsIncludingParents(residentLisa));
        assertFalse(operator.getGroupsIncludingParents(residentLisa).contains(myGroup.getId()), "old parent gone after the move");
    }

    @Test
    void anUnstoredDraftIsNeverServedFromTheCache() throws Exception
    {
        Category myGroup = userGroups().getCategory("my-group");
        User lisa = createUserIn("lisa", myGroup);
        Collection<String> stored = operator.getGroupsIncludingParents(lisa);

        User draft = facade.edit(lisa);
        draft.removeGroup(myGroup);
        assertFalse(operator.getGroupsIncludingParents(draft).contains(myGroup.getId()), "draft answers for itself");
        assertSame(stored, operator.getGroupsIncludingParents(lisa), "the draft did not replace the stored entry");
    }

    @Test
    void reloadDropsEveryEntry() throws Exception
    {
        User monty = operator.getUser("monty");
        operator.getGroupsIncludingParents(monty);
        assertSame(monty, operator.getUserGroupsCache().cachedUser(monty.getId()));

        operator.disconnect();
        operator.connect();

        assertNotSame(monty, operator.getUserGroupsCache().cachedUser(monty.getId()), "the pre-reload instance's entry is gone");
    }

    @Test
    void anInvalidationRightAfterTheGenerationCheckKeepsTheEntryOut() throws Exception
    {
        UserGroupsCache cache = new UserGroupsCache()
        {
            private int reads;

            @Override
            protected long currentGeneration()
            {
                long gen = super.currentGeneration();
                if (++reads == 2)
                {
                    invalidateAll();
                }
                return gen;
            }
        };
        User monty = operator.getUser("monty");
        cache.groupsOf(monty);
        assertNull(cache.cachedUser(monty.getId()), "a computation that raced with invalidateAll is not stored");
    }

    @Test
    void userRemovalDropsThatUsersEntry() throws Exception
    {
        User lisa = createUserIn("lisa", userGroups().getCategory("my-group"));
        operator.getGroupsIncludingParents(lisa);
        assertSame(lisa, operator.getUserGroupsCache().cachedUser(lisa.getId()));

        facade.remove(lisa);

        assertNull(operator.getUserGroupsCache().cachedUser(lisa.getId()));
    }

    @Test
    void resourceChangeKeepsTheCache() throws Exception
    {
        User monty = operator.getUser("monty");
        Collection<String> before = operator.getGroupsIncludingParents(monty);
        Allocatable room = facade.newAllocatable(facade.getDynamicType("room").newClassification(), operator.getUser("homer"));
        room.getClassification().setValue("name", "PRD129-room");
        facade.store(room);
        assertSame(before, operator.getGroupsIncludingParents(monty));
        assertNotSame(before, UserImpl.getGroupsIncludingParents(monty));
    }
}
