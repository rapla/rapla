package org.rapla.plugin.exchangeconnector.server.exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** rapla.exchange.dry-run: collects every Exchange write the sync would do instead of doing it. One instance per sweep/poll run. */
public class DryRunLog
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DryRunLog.class);
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String RECREATE = "recreate";
    public static final String DELETE = "delete";
    public static final String SKIP_OWNER_EDIT = "skip-owner-edit";
    public static final String SKIP_FOREIGN_UPTODATE = "skip-foreign-uptodate";
    private static final String[] ACTIONS = { CREATE, UPDATE, RECREATE, DELETE, SKIP_OWNER_EDIT, SKIP_FOREIGN_UPTODATE };

    private final Map<String, Map<String, Integer>> counts = new TreeMap<>();

    public synchronized void record(String mailbox, String action, String raplaId, String subject, Object start)
    {
        LOGGER.info("DRY-RUN {} {} {} {} {}", mailbox, action, raplaId, subject, start);
        counts.computeIfAbsent(mailbox, m -> new TreeMap<>()).merge(action, 1, Integer::sum);
    }

    public synchronized List<String> summary(String run)
    {
        List<String> lines = new ArrayList<>();
        if (counts.isEmpty())
        {
            lines.add("DRY-RUN summary " + run + ": no changes");
        }
        for (Map.Entry<String, Map<String, Integer>> e : counts.entrySet())
        {
            StringBuilder line = new StringBuilder("DRY-RUN summary ").append(run).append(": ").append(e.getKey());
            for (String action : ACTIONS)
            {
                line.append(' ').append(action).append('=').append(e.getValue().getOrDefault(action, 0));
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
