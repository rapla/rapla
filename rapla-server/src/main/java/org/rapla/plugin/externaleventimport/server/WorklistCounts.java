package org.rapla.plugin.externaleventimport.server;

import java.util.Collection;

public record WorklistCounts(int open, int linked, int changed, int ignored, int total)
{
    public static WorklistCounts zero()
    {
        return new WorklistCounts(0, 0, 0, 0, 0);
    }

    public static WorklistCounts of(Collection<WorklistItem> items)
    {
        int open = 0;
        int linked = 0;
        int changed = 0;
        int ignored = 0;
        for (WorklistItem item : items)
        {
            switch (item.state())
            {
                case OPEN -> open++;
                case LINKED -> linked++;
                case CHANGED -> changed++;
                case IGNORED -> ignored++;
            }
        }
        return new WorklistCounts(open, linked, changed, ignored, items.size());
    }

    public WorklistCounts plus(WorklistCounts other)
    {
        return new WorklistCounts(open + other.open, linked + other.linked, changed + other.changed,
                ignored + other.ignored, total + other.total);
    }
}
