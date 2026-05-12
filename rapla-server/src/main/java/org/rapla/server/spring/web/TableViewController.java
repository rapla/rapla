package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.entities.configuration.Preferences;
import org.rapla.plugin.tableview.CellExtractor;
import org.rapla.plugin.tableview.EngineColumn;
import org.rapla.plugin.tableview.PageSpec;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.SortSpec;
import org.rapla.plugin.tableview.TableCellType;
import org.rapla.plugin.tableview.TableColumnDescriptor;
import org.rapla.plugin.tableview.TableColumnType;
import org.rapla.plugin.tableview.TableColumnsResponse;
import org.rapla.plugin.tableview.TablePage;
import org.rapla.plugin.tableview.TableViewEngine;
import org.rapla.plugin.tableview.TableViewService;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.scheduler.Promise;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST surface for the server-side table projection (PRD 030 Phase 2).
 *
 * <p>Thin glue: parse query params → query reservations / blocks via
 * {@link RaplaFacade} → resolve column ids via {@link TableConfig.TableConfigLoader} →
 * call {@link TableViewEngine} → return {@link TablePage}. Permission filter is
 * the facade's existing per-user read scope — only reservations the user can
 * read flow through.
 *
 * <p>Server-side cap on the no-{@code pageSize} response: {@link #DEFAULT_CAP}.
 * When the unbounded result exceeds the cap, the response carries
 * {@code incomplete: true} and a cursor so the client can keep loading.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/table", produces = "application/json")
public class TableViewController implements TableViewService
{
    /** Server-side safety cap on the no-{@code pageSize} response. */
    public static final int DEFAULT_CAP = 50_000;

    private final RemoteSession session;
    private final HttpServletRequest request;
    private final RaplaFacade facade;
    private final RaplaLocale raplaLocale;
    private final TableConfig.TableConfigLoader tableConfigLoader;

    public TableViewController(RemoteSession session,
                               HttpServletRequest request,
                               RaplaFacade facade,
                               RaplaLocale raplaLocale,
                               TableConfig.TableConfigLoader tableConfigLoader)
    {
        this.session = session;
        this.request = request;
        this.facade = facade;
        this.raplaLocale = raplaLocale;
        this.tableConfigLoader = tableConfigLoader;
    }

    // ---------- /reservations ----------

    @Override
    @GetMapping("/reservations")
    public TablePage reservations(@RequestParam("from") String fromIso,
                                  @RequestParam("to") String toIso,
                                  @RequestParam(value = "columns", required = false) List<String> columnIds,
                                  @RequestParam(value = "sort", required = false) List<String> sortSpecs,
                                  @RequestParam(value = "cursor", required = false) String cursor,
                                  @RequestParam(value = "pageSize", required = false) Integer pageSize)
            throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        LocalDate from = LocalDate.parse(fromIso);
        LocalDate to   = LocalDate.parse(toIso);

        Collection<Reservation> reservations = waitFor(
                facade.getReservations(user, from.atStartOfDay(), to.atStartOfDay(), null));

        List<EngineColumn<Reservation>> columns =
                resolveColumns(TableConfig.EVENTS_VIEW, user, columnIds);

        return TableViewEngine.project(
                reservations,
                columns,
                parseSort(sortSpecs),
                buildPageSpec(pageSize, cursor),
                Reservation::getId);
    }

    // ---------- /appointments ----------

    @Override
    @GetMapping("/appointments")
    public TablePage appointments(@RequestParam("from") String fromIso,
                                  @RequestParam("to") String toIso,
                                  @RequestParam(value = "columns", required = false) List<String> columnIds,
                                  @RequestParam(value = "sort", required = false) List<String> sortSpecs,
                                  @RequestParam(value = "cursor", required = false) String cursor,
                                  @RequestParam(value = "pageSize", required = false) Integer pageSize)
            throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        LocalDate from = LocalDate.parse(fromIso);
        LocalDate to   = LocalDate.parse(toIso);
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atStartOfDay();

        Collection<Reservation> reservations = waitFor(
                facade.getReservations(user, fromDt, toDt, null));

        // Expand reservations → flat appointment block list within the window.
        List<AppointmentBlock> blocks = new ArrayList<>();
        for (Reservation r : reservations)
        {
            for (Appointment a : r.getAppointments())
            {
                a.createBlocks(fromDt, toDt, blocks);
            }
        }

        List<EngineColumn<AppointmentBlock>> columns =
                resolveColumns(TableConfig.APPOINTMENTS_VIEW, user, columnIds);

        return TableViewEngine.project(
                blocks,
                columns,
                parseSort(sortSpecs),
                buildPageSpec(pageSize, cursor),
                TableViewController::appointmentBlockId);
    }

    /**
     * Stable per-block id: {@code reservationId#appointmentId#startEpochMs}.
     * Appointment blocks don't have native ids — recurring appointments are
     * synthetic per-block expansions of the parent. We compose a deterministic
     * key from (reservation, appointment, start) so the cursor pagination is
     * stable across requests.
     */
    private static String appointmentBlockId(AppointmentBlock block)
    {
        Appointment a = block.getAppointment();
        String reservationId = a.getReservation() != null ? a.getReservation().getId() : "_";
        String appointmentId = a.getId();
        return reservationId + "#" + appointmentId + "#" + block.getStart();
    }

    /** Package-private bridge so {@link ExportController} can reuse the
     *  same block id strategy without duplicating it. */
    static String appointmentBlockIdPublic(AppointmentBlock block)
    {
        return appointmentBlockId(block);
    }

    // ---------- /config + /columns/catalog (Phase 3) ----------

    /** Known table-view names — guard so we don't echo arbitrary user input. */
    private static final java.util.Set<String> KNOWN_TABLE_NAMES = java.util.Set.of(
            TableConfig.EVENTS_VIEW,
            TableConfig.APPOINTMENTS_VIEW,
            TableConfig.APPOINTMENTS_PER_DAY_VIEW);

    @Override
    @GetMapping("/config")
    public TableColumnsResponse config(@RequestParam("tableName") String tableName) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!KNOWN_TABLE_NAMES.contains(tableName))
        {
            // Unknown table name — return an empty-columns response (don't 404).
            // AGENTS.md §12: don't reveal whether tableName is plausible or not via
            // status-code divergence.
            return new TableColumnsResponse(tableName, List.of());
        }
        List<RaplaTableColumn<Object>> columns = tableConfigLoader.loadColumns(tableName, user);
        List<TableColumnDescriptor> descriptors = new ArrayList<>(columns.size());
        for (RaplaTableColumn<Object> col : columns)
        {
            descriptors.add(new TableColumnDescriptor(
                    col.getKey(),
                    col.getColumnName(),
                    mapCellType(col.getType())));
        }
        return new TableColumnsResponse(tableName, descriptors);
    }

    @Override
    @GetMapping("/columns/catalog")
    public TableColumnsResponse columnsCatalog(@RequestParam("tableName") String tableName) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!KNOWN_TABLE_NAMES.contains(tableName))
        {
            return new TableColumnsResponse(tableName, List.of());
        }
        final Preferences preferences = facade.getSystemPreferences();
        TableConfig config = tableConfigLoader.read(preferences, false);

        java.util.Locale locale = raplaLocale.getLocale();
        java.util.Set<TableColumnConfig> universe = config.getAllColumns();
        List<TableColumnDescriptor> descriptors = new ArrayList<>(universe.size());
        for (TableColumnConfig c : universe)
        {
            descriptors.add(new TableColumnDescriptor(
                    c.getKey(),
                    c.getName(locale),
                    mapColumnConfigType(c.getType())));
        }
        return new TableColumnsResponse(tableName, descriptors);
    }

    private static TableCellType mapColumnConfigType(String legacyTypeString)
    {
        if (legacyTypeString == null) return TableCellType.STRING;
        return switch (legacyTypeString.toLowerCase(java.util.Locale.ROOT))
        {
            case "date", "datetime" -> TableCellType.DATE;
            case "integer", "int", "long" -> TableCellType.INTEGER;
            case "double", "float", "number" -> TableCellType.DOUBLE;
            case "boolean", "bool" -> TableCellType.BOOLEAN;
            default -> TableCellType.STRING;
        };
    }

    // ---------- column resolution ----------

    private <T> List<EngineColumn<T>> resolveColumns(String tableName, User user, List<String> requestedIds)
            throws RaplaException
    {
        return resolveColumnsStatic(tableConfigLoader, tableName, user, requestedIds);
    }

    /** Package-private bridge so {@link ExportController} can reuse this
     *  resolution without duplicating it. */
    static <T> List<EngineColumn<T>> resolveColumnsStatic(TableConfig.TableConfigLoader loader,
                                                          String tableName, User user,
                                                          List<String> requestedIds) throws RaplaException
    {
        List<RaplaTableColumn<T>> all = loader.loadColumns(tableName, user);

        if (requestedIds == null || requestedIds.isEmpty())
        {
            return adaptAll(all);
        }
        Map<String, RaplaTableColumn<T>> byKey = new LinkedHashMap<>();
        for (RaplaTableColumn<T> col : all)
        {
            byKey.put(col.getKey(), col);
        }
        List<EngineColumn<T>> out = new ArrayList<>(requestedIds.size());
        for (String id : requestedIds)
        {
            RaplaTableColumn<T> col = byKey.get(id);
            if (col == null) continue;
            out.add(adapt(col));
        }
        return out;
    }

    private static <T> List<EngineColumn<T>> adaptAll(List<RaplaTableColumn<T>> columns)
    {
        List<EngineColumn<T>> out = new ArrayList<>(columns.size());
        for (RaplaTableColumn<T> col : columns)
        {
            out.add(adapt(col));
        }
        return out;
    }

    private static <T> EngineColumn<T> adapt(RaplaTableColumn<T> col)
    {
        TableColumnDescriptor descriptor = new TableColumnDescriptor(
                col.getKey(),
                col.getColumnName(),
                mapCellType(col.getType()));
        CellExtractor<T> extractor = row ->
                col.getValue(row, DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT);
        return new EngineColumn<>(descriptor, extractor);
    }

    private static TableCellType mapCellType(TableColumnType legacy)
    {
        if (legacy == null) return TableCellType.STRING;
        return switch (legacy)
        {
            case STRING  -> TableCellType.STRING;
            case INTEGER -> TableCellType.INTEGER;
            case DATE    -> TableCellType.DATE;
        };
    }

    // ---------- sort + page parsing ----------

    /**
     * Parse {@code sort} query parameters of the form {@code "<columnId>:<asc|desc>"}.
     * Unknown directions default to ASC. Empty / malformed entries are silently
     * dropped — same forgiving spirit as unknown column ids.
     */
    static SortSpec parseSort(List<String> sortSpecs)
    {
        if (sortSpecs == null || sortSpecs.isEmpty()) return SortSpec.NONE;
        List<SortSpec.SortField> fields = new ArrayList<>();
        for (String spec : sortSpecs)
        {
            if (spec == null || spec.isBlank()) continue;
            String columnId;
            SortSpec.Direction direction;
            int colon = spec.indexOf(':');
            if (colon < 0)
            {
                columnId = spec.trim();
                direction = SortSpec.Direction.ASC;
            }
            else
            {
                columnId = spec.substring(0, colon).trim();
                String dir = spec.substring(colon + 1).trim();
                direction = "desc".equalsIgnoreCase(dir) ? SortSpec.Direction.DESC : SortSpec.Direction.ASC;
            }
            if (columnId.isEmpty()) continue;
            fields.add(new SortSpec.SortField(columnId, direction));
        }
        return fields.isEmpty() ? SortSpec.NONE : new SortSpec(fields);
    }

    private static PageSpec buildPageSpec(Integer pageSize, String cursor)
    {
        if (pageSize == null || pageSize <= 0)
        {
            return PageSpec.allWithCap(DEFAULT_CAP);
        }
        if (cursor == null || cursor.isBlank())
        {
            return PageSpec.firstPage(pageSize);
        }
        return PageSpec.nextPage(pageSize, cursor);
    }

    // ---------- promise bridge (copy of CalendarViewController.waitFor) ----------

    private static <T> T waitFor(Promise<T> promise) throws RaplaException
    {
        return waitForCollection(promise, "table query");
    }

    /** Package-private bridge so {@link ExportController} can wait on the
     *  same async facade query without duplicating the latch wiring. */
    static <T> T waitForCollection(Promise<T> promise, String operationName) throws RaplaException
    {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        promise.thenAccept(value -> { result.set(value); done.countDown(); })
                .exceptionally(throwable -> { err.set(throwable); done.countDown(); });
        try
        {
            if (!done.await(30, TimeUnit.SECONDS))
            {
                throw new RaplaException(operationName + " timed out");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RaplaException(operationName + " interrupted", e);
        }
        if (err.get() != null)
        {
            Throwable t = err.get();
            if (t instanceof RaplaException re) throw re;
            throw new RaplaException(t.getMessage(), t);
        }
        return result.get();
    }
}
