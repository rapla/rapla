package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.CsvSerializer;
import org.rapla.plugin.tableview.EngineColumn;
import org.rapla.plugin.tableview.PageSpec;
import org.rapla.plugin.tableview.SortSpec;
import org.rapla.plugin.tableview.TablePage;
import org.rapla.plugin.tableview.TableViewEngine;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Promise;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST surface for the CSV export of the table view (PRD 030 Phase 5).
 *
 * <p>Implements {@link ExportService} for the wire contract; adds a thin
 * Spring controller layer that wraps the raw CSV body in a
 * {@code ResponseEntity} with the right {@code Content-Type} +
 * {@code Content-Disposition: attachment} so browsers offer a download.
 *
 * <p>Permission filter inherits from the facade — only reservations the
 * user can read flow through. Same column resolution + sort parsing as
 * {@link TableViewController}, just with a CSV serializer at the end
 * instead of returning a {@link TablePage}.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/api/export", produces = "application/json")
public class ExportController
{
    /** Same cap as the table endpoint — never exceed this even when
     *  serialising an "all rows" CSV. */
    private static final int DEFAULT_CAP = 50_000;

    private final RemoteSession session;
    private final HttpServletRequest request;
    private final RaplaFacade facade;
    private final RaplaLocale raplaLocale;
    private final TableConfig.TableConfigLoader tableConfigLoader;

    public ExportController(RemoteSession session,
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

    @GetMapping(value = "/csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> csvDownload(@RequestParam("tableName") String tableName,
                                              @RequestParam("from") String fromIso,
                                              @RequestParam("to") String toIso,
                                              @RequestParam(value = "columns", required = false) List<String> columnIds,
                                              @RequestParam(value = "sort", required = false) List<String> sortSpecs)
            throws RaplaException
    {
        String body = renderCsv(tableName, fromIso, toIso, columnIds, sortSpecs);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String filename = sanitizeFilename(tableName) + "-" + fromIso + "_" + toIso + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private String renderCsv(String tableName, String fromIso, String toIso,
                             List<String> columnIds, List<String> sortSpecs) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        LocalDate from = LocalDate.parse(fromIso);
        LocalDate to   = LocalDate.parse(toIso);
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atStartOfDay();

        boolean isAppointments = TableConfig.APPOINTMENTS_VIEW.equals(tableName)
                || TableConfig.APPOINTMENTS_PER_DAY_VIEW.equals(tableName);
        boolean isEvents = TableConfig.EVENTS_VIEW.equals(tableName);
        if (!isEvents && !isAppointments)
        {
            // Unknown table name — return an empty CSV (just an empty body).
            // AGENTS.md §12: don't differentiate via status code.
            return "";
        }

        Collection<Reservation> reservations = TableViewController.waitForCollection(
                facade.getReservations(user, fromDt, toDt, null), "csv export");

        TablePage page;
        SortSpec sort = TableViewController.parseSort(sortSpecs);
        PageSpec pageSpec = PageSpec.allWithCap(DEFAULT_CAP);

        if (isEvents)
        {
            List<EngineColumn<Reservation>> columns = TableViewController.resolveColumnsStatic(
                    tableConfigLoader, TableConfig.EVENTS_VIEW, user, columnIds);
            page = TableViewEngine.project(reservations, columns, sort, pageSpec, Reservation::getId);
        }
        else
        {
            List<AppointmentBlock> blocks = new ArrayList<>();
            for (Reservation r : reservations)
            {
                for (Appointment a : r.getAppointments())
                {
                    a.createBlocks(fromDt, toDt, blocks);
                }
            }
            List<EngineColumn<AppointmentBlock>> columns = TableViewController.resolveColumnsStatic(
                    tableConfigLoader, tableName, user, columnIds);
            page = TableViewEngine.project(blocks, columns, sort, pageSpec,
                    TableViewController::appointmentBlockIdPublic);
        }

        return CsvSerializer.serialize(page, raplaLocale.getLocale());
    }

    /** Drop path-traversal hazards from the user-supplied tableName before
     *  putting it in a header. */
    private static String sanitizeFilename(String tableName)
    {
        if (tableName == null) return "export";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tableName.length(); i++)
        {
            char c = tableName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') b.append(c);
        }
        return b.length() == 0 ? "export" : b.toString();
    }
}
