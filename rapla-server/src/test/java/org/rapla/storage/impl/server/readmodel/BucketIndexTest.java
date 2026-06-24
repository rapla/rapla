package org.rapla.storage.impl.server.readmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class BucketIndexTest
{
    @Test
    public void putThenMembers()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("Raum", "a1");
        index.put("Raum", "a2");
        index.put("Person", "p1");

        assertEquals(Set.of("a1", "a2"), index.members("Raum"));
        assertEquals(Set.of("p1"), index.members("Person"));
    }

    @Test
    public void membersAbsentKeyIsEmptyNeverNull()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        Set<String> result = index.members("missing");
        assertTrue(result.isEmpty());
    }

    @Test
    public void membersUnionIsUnionOverSeveralKeys()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");
        index.put("A", "x2");
        index.put("B", "x2");
        index.put("B", "x3");
        index.put("C", "x4");

        Set<String> union = index.membersUnion(Arrays.asList("A", "B", "C"));
        assertEquals(Set.of("x1", "x2", "x3", "x4"), union);
    }

    @Test
    public void membersUnionDeduplicatesAcrossBuckets()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "shared");
        index.put("B", "shared");

        assertEquals(Set.of("shared"), index.membersUnion(Arrays.asList("A", "B")));
    }

    @Test
    public void membersUnionWithUnknownKeysIsEmpty()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");

        assertTrue(index.membersUnion(Arrays.asList("missing1", "missing2")).isEmpty());
        assertTrue(index.membersUnion(Collections.emptyList()).isEmpty());
    }

    @Test
    public void moveRelocatesMember()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("Raum", "a1");
        index.put("Raum", "a2");

        index.move("Raum", "Saal", "a1");

        assertEquals(Set.of("a2"), index.members("Raum"));
        assertEquals(Set.of("a1"), index.members("Saal"));
    }

    @Test
    public void moveOfOnlyMemberPrunesOldBucket()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("Raum", "a1");

        index.move("Raum", "Saal", "a1");

        assertTrue(index.members("Raum").isEmpty());
        assertEquals(Set.of("a1"), index.members("Saal"));
    }

    @Test
    public void idempotentPutNoDuplicate()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");
        index.put("A", "x1");
        index.put("A", "x1");

        assertEquals(1, index.members("A").size());
        assertEquals(Set.of("x1"), index.members("A"));
    }

    @Test
    public void removeThenGone()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");
        index.put("A", "x2");

        index.remove("A", "x1");

        assertEquals(Set.of("x2"), index.members("A"));
    }

    @Test
    public void removeAbsentIsNoOp()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");

        // member not present
        index.remove("A", "missingMember");
        // key not present
        index.remove("missingKey", "x1");

        assertEquals(Set.of("x1"), index.members("A"));
    }

    @Test
    public void emptyBucketPrunedOnRemove()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");

        index.remove("A", "x1");

        // The bucket is gone, not lingering as an empty set; observable as empty members.
        assertTrue(index.members("A").isEmpty());
        // And membersUnion over it contributes nothing.
        assertTrue(index.membersUnion(List.of("A")).isEmpty());
    }

    @Test
    public void returnedMembersSetIsImmutableSnapshot()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put("A", "x1");

        Set<String> snapshot = index.members("A");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("x2"));

        // mutating the index after taking the snapshot does not change it
        index.put("A", "x2");
        assertEquals(Set.of("x1"), snapshot);
        assertEquals(Set.of("x1", "x2"), index.members("A"));
    }

    @Test
    public void nullKeyOrMemberIsIgnored()
    {
        BucketIndex<String, String> index = new BucketIndex<>();
        index.put(null, "x1");
        index.put("A", null);
        index.remove(null, "x1");
        index.remove("A", null);

        assertTrue(index.members(null).isEmpty());
        assertTrue(index.members("A").isEmpty());
        assertFalse(index.membersUnion(null).iterator().hasNext());
    }
}
