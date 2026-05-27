package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.SyncCalendarModel;
import org.rapla.facade.internal.CalendarModelImpl;
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
import org.rapla.plugin.tableview.TableQueryRequest;
import org.rapla.plugin.tableview.TableViewEngine;
import org.rapla.plugin.tableview.TableViewService;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the server-side table projection (PRD 030 Phase 2).
 *
 * <p>Thin glue: parse query params → build a {@link CalendarSelectionModel}
 * with the caller's resolved tree-selection + reservation filter → reuse
 * the same {@code queryReservationsSync} path the week view goes through →
 * resolve column ids via {@link TableConfig.TableConfigLoader} → call
 * {@link TableViewEngine} → return {@link TablePage}. Permission filter is
 * built into the calendar-model path: resources the caller can't read are
 * silently dropped (AGENTS.md §12).
 *
 * <p>No server-side pagination — column-projected rows are small. A safety
 * cap of {@link #DEFAULT_CAP} rows still applies; when exceeded the response
 * carries {@code incomplete: true}.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
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
    public TablePage reservations(TableQueryRequest body) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        LocalDate from = LocalDate.parse(body.from());
        LocalDate to   = LocalDate.parse(body.to());

        CalendarSelectionModel model = buildModel(user, from.atStartOfDay(), to.atStartOfDay(), body);

        Collection<Reservation> reservations =
                ((SyncCalendarModel) model).queryReservationsSync(model.getTimeIntervall());

        String configName = body.tableName() != null && !body.tableName().isBlank()
                ? body.tableName() : TableConfig.EVENTS_VIEW;
        List<EngineColumn<Reservation>> columns =
                resolveColumns(configName, user, body.columns());

        return TableViewEngine.project(
                reservations,
                columns,
                parseSort(body.sort()),
                PageSpec.allWithCap(DEFAULT_CAP),
                Reservation::getId);
    }

    // ---------- /appointments ----------

    @Override
    public TablePage appointments(TableQueryRequest body) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        LocalDate from = LocalDate.parse(body.from());
        LocalDate to   = LocalDate.parse(body.to());
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atStartOfDay();

        CalendarSelectionModel model = buildModel(user, fromDt, toDt, body);

        Collection<Reservation> reservations =
                ((SyncCalendarModel) model).queryReservationsSync(model.getTimeIntervall());

        // Expand reservations → flat appointment block list within the window.
        List<AppointmentBlock> blocks = new ArrayList<>();
        for (Reservation r : reservations)
        {
            for (Appointment a : r.getAppointments())
            {
                a.createBlocks(fromDt, toDt, blocks);
            }
        }

        // Choose which view's column set to resolve against. The per-day
        // variant has different ids ("times" instead of start/end + a
        // leading "date" column). loadColumns(APPOINTMENTS_PER_DAY_VIEW)
        // handles the date column itself, so no special-casing here.
        String configName = body.tableName() != null && !body.tableName().isBlank()
                ? body.tableName() : TableConfig.APPOINTMENTS_VIEW;
        List<EngineColumn<AppointmentBlock>> columns =
                resolveColumns(configName, user, body.columns());

        return TableViewEngine.project(
                blocks,
                columns,
                parseSort(body.sort()),
                PageSpec.allWithCap(DEFAULT_CAP),
                TableViewController::appointmentBlockId);
    }

    /**
     * Build a {@link CalendarSelectionModel} for the request, resolving the
     * wire-format id lists to entities through the permission gate. Unknown
     * or unreadable ids are silently dropped — AGENTS.md §12.
     *
     * <p>Selection semantics:
     * <ul>
     *   <li>If any of {@code allocatables} / {@code types} / {@code owners}
     *       is non-empty, the union of resolved entities becomes the model's
     *       selected set.</li>
     *   <li>If all three are empty, the model is seeded with
     *       {@link CalendarModelImpl#ALLOCATABLES_ROOT} — the marker that
     *       expands to every readable allocatable inside
     *       {@code getSelectedObjectsAndChildren()}.</li>
     * </ul>
     *
     * <p>Filter:
     * <ul>
     *   <li>{@link TableQueryRequest#reservationFilter} empty/null — model
     *       keeps its default (every reservation type, no attribute
     *       conditions).</li>
     *   <li>Non-empty — translate each {@link TableQueryRequest.ReservationFilter}
     *       DTO into a {@link ClassificationFilter} on the resolved
     *       reservation {@link DynamicType}, apply its rules, and install
     *       the array via {@link CalendarSelectionModel#setReservationFilter}.</li>
     * </ul>
     */
    private CalendarSelectionModel buildModel(User user,
                                              LocalDateTime fromDt, LocalDateTime toDt,
                                              TableQueryRequest body) throws RaplaException
    {
        CalendarSelectionModel model = facade.newCalendarModel(user);
        model.setStartDate(fromDt);
        model.setEndDate(toDt);

        PermissionController pc = facade.getPermissionController();
        LinkedHashSet<Object> selection = new LinkedHashSet<>();

        if (body.allocatables() != null)
        {
            for (String id : body.allocatables())
            {
                Allocatable a = facade.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
                if (a != null && pc.canRead(a, user)) selection.add(a);
            }
        }
        if (body.types() != null)
        {
            for (String id : body.types())
            {
                DynamicType t = facade.tryResolve(new ReferenceInfo<>(id, DynamicType.class));
                if (t != null) selection.add(t); // DynamicType visibility is system-wide
            }
        }
        if (body.owners() != null)
        {
            for (String id : body.owners())
            {
                User u = facade.tryResolve(new ReferenceInfo<>(id, User.class));
                if (u != null) selection.add(u);
            }
        }

        if (selection.isEmpty())
        {
            // Default: every readable allocatable. ALLOCATABLES_ROOT is the
            // marker getSelectedObjectsAndChildren() recognises and expands
            // to the user's full readable allocatable set.
            selection.add(CalendarModelImpl.ALLOCATABLES_ROOT);
        }
        model.setSelectedObjects(selection);

        List<TableQueryRequest.ReservationFilter> resvFilter = body.reservationFilter();
        if (resvFilter != null && !resvFilter.isEmpty())
        {
            List<ClassificationFilter> filters = new ArrayList<>(resvFilter.size());
            for (TableQueryRequest.ReservationFilter dto : resvFilter)
            {
                ClassificationFilter cf = buildFilter(dto);
                if (cf != null) filters.add(cf);
            }
            if (!filters.isEmpty())
            {
                model.setReservationFilter(filters.toArray(new ClassificationFilter[0]));
            }
        }

        return model;
    }

    /**
     * Translate a {@link TableQueryRequest.ReservationFilter} DTO into an
     * in-process {@link ClassificationFilter}. Returns null when the type
     * id is unknown, isn't a reservation type, or has no resolvable
     * attribute referenced by any rule (per AGENTS.md §12: drop silently,
     * don't differentiate via status-code).
     */
    private ClassificationFilter buildFilter(TableQueryRequest.ReservationFilter dto) throws RaplaException
    {
        if (dto == null || dto.typeId() == null) return null;
        DynamicType type = facade.tryResolve(new ReferenceInfo<>(dto.typeId(), DynamicType.class));
        if (type == null) return null;
        if (!DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION
                .equals(type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE))) return null;

        ClassificationFilter filter = type.newClassificationFilter();
        List<TableQueryRequest.Rule> rules = dto.rules();
        if (rules == null || rules.isEmpty()) return filter; // type-only filter, no rules
        for (TableQueryRequest.Rule rule : rules)
        {
            if (rule == null || rule.attributeKey() == null) continue;
            // Skip rules referencing attributes the type doesn't have — keeps
            // wire format forward-compatible (older client sending a rule
            // against a removed attribute → silently dropped, not 500).
            if (type.getAttribute(rule.attributeKey()) == null) continue;
            List<TableQueryRequest.Condition> conds = rule.conditions();
            if (conds == null || conds.isEmpty()) continue;
            Object[][] matrix = new Object[conds.size()][];
            for (int i = 0; i < conds.size(); i++)
            {
                TableQueryRequest.Condition c = conds.get(i);
                if (c == null) { matrix[i] = new Object[]{ "=", null }; continue; }
                matrix[i] = new Object[]{ c.operator(), c.value() };
            }
            filter.addRule(rule.attributeKey(), matrix);
        }
        return filter;
    }

    /**
     * Stable per-block id: {@code reservationId#appointmentId#startEpochMs}.
     * Appointment blocks don't have native ids — recurring appointments are
     * synthetic per-block expansions of the parent. Used by {@link TableViewEngine}
     * as the cursor key.
     */
    private static String appointmentBlockId(AppointmentBlock block)
    {
        Appointment a = block.getAppointment();
        String reservationId = a.getReservation() != null ? a.getReservation().getId() : "_";
        String appointmentId = a.getId();
        return reservationId + "#" + appointmentId + "#" + block.getStart();
    }

    // ---------- /config + /columns/catalog (Phase 3) ----------

    /** Known table-view names — guard so we don't echo arbitrary user input. */
    private static final java.util.Set<String> KNOWN_TABLE_NAMES = java.util.Set.of(
            TableConfig.EVENTS_VIEW,
            TableConfig.APPOINTMENTS_VIEW,
            TableConfig.APPOINTMENTS_PER_DAY_VIEW);

    @Override
    public TableColumnsResponse config(String tableName) throws RaplaException
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
    public TableColumnsResponse columnsCatalog(String tableName) throws RaplaException
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
        List<RaplaTableColumn<T>> all = tableConfigLoader.loadColumns(tableName, user);

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
        // KEY_NAME_FORMAT (the regular display format), not _EXPORT — the
        // table endpoint feeds an in-app GUI (Swing reservations view, SPA),
        // not a CSV / iCal export. Some installations (e.g. DHBW) override
        // {@code nameformat_export} for persons with a privacy guard that
        // returns empty unless the {@code internal_request} thread-context
        // flag is set; using KEY_NAME_FORMAT here matches what the legacy
        // RaplaTableModel.getValueAt does for the in-process table path.
        CellExtractor<T> extractor = row ->
                col.getValue(row, DynamicTypeAnnotations.KEY_NAME_FORMAT);
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

}
