package org.rapla.client.gwt.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rapla.client.gui.menu.gwt.context.ContextCreator;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.HTMLDaySlot;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.RowSlot;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.Slot;
import org.rapla.client.plugin.weekview.CalendarWeekViewPresenter.HTMLWeekViewPresenter.SpanAndMinute;
import org.rapla.components.calendarview.Block;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.PopupContext;
import org.rapla.plugin.abstractcalendar.server.HTMLRaplaBlock;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.ContextMenuHandler;
import com.google.gwt.event.dom.client.DragEndEvent;
import com.google.gwt.event.dom.client.DragEndHandler;
import com.google.gwt.event.dom.client.DragEnterEvent;
import com.google.gwt.event.dom.client.DragEnterHandler;
import com.google.gwt.event.dom.client.DragLeaveEvent;
import com.google.gwt.event.dom.client.DragLeaveHandler;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragOverHandler;
import com.google.gwt.event.dom.client.DragStartEvent;
import com.google.gwt.event.dom.client.DragStartHandler;
import com.google.gwt.event.dom.client.DropEvent;
import com.google.gwt.event.dom.client.DropHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.PopupPanel;

public class WeekviewGWT extends FlexTable
{

    public static interface Callback
    {
        void updateReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer rowSlot, PopupContext context);

        void selectReservation(HTMLRaplaBlock block, PopupContext context);

        void newReservation(HTMLDaySlot daySlot, Integer fromMinuteOfDay, Integer tillMinuteOfDay, PopupContext context);

    }

    private static final String BACKGROUND_COLOR_TARGET = "#54FFE5";//"#ffa";
    private final Map<Element, Event> events = new HashMap<Element, Event>();
    private final Logger logger;
    private final List<Integer> extraDayColumns = new ArrayList<Integer>();
    private final Callback callback;
    List<HandlerRegistration> currentDomHandlers = new ArrayList<HandlerRegistration>();
    private final ContextCreator contextCreator;

    public WeekviewGWT(String tableStylePrefix, Logger logger, Callback callback, ContextCreator contextCreator)
    {
        super();
        this.logger = logger;
        this.callback = callback;
        this.contextCreator = contextCreator;
        setStyleName(tableStylePrefix);
        addStyleName("table");
        this.sinkEvents(com.google.gwt.user.client.Event.getTypeInt(ContextMenuEvent.getType().getName()));
        this.addHandler(new ContextMenuHandler()
        {
            @Override
            public void onContextMenu(ContextMenuEvent event)
            {
                event.preventDefault();
                event.stopPropagation();
                final Element tc = WeekviewGWT.this.getEventTargetCell((com.google.gwt.user.client.Event) event.getNativeEvent());
                final Event myEvent = events.get(tc.getFirstChildElement());
                final StringBuilder sb = new StringBuilder();
                sb.append("This could be the context Menut :-)");
                if (myEvent != null)
                {
                    sb.append("... clicked on ");
                    sb.append(myEvent.getHtmlBlock().getName());
                }
                final PopupPanel menu = new PopupPanel(true, true);
                menu.add(new HTML(sb.toString()));
                final NativeEvent nativeEvent = event.getNativeEvent();
                final int clientX = nativeEvent.getClientX();
                final int scrollLeft = Window.getScrollLeft();
                final int clientY = nativeEvent.getClientY();
                final int scrollTop = Window.getScrollTop();
                menu.setPopupPosition(clientX + scrollLeft, clientY + scrollTop);
                menu.show();
            }
        }, ContextMenuEvent.getType());
    }

    public void update(final List<HTMLDaySlot> daylist, final List<RowSlot> timelist, final String weeknumber)
    {
        this.clear();
        this.removeAllRows();
        setupTable(daylist, timelist, weeknumber);
    }

    private void setupTable(final List<HTMLDaySlot> daylist, final List<RowSlot> timelist, final String weeknumber)
    {
        final FlexCellFormatter flexCellFormatter = this.getFlexCellFormatter();
        this.setText(0, 0, weeknumber);
        setStyleNameFor(0, 0, "header topleft");
        final int actualColumnCount = createXAchsis(daylist, flexCellFormatter);
        final int actualRowCount = createYAchsis(timelist, flexCellFormatter);
        final List<Integer> timeRows = calcTimeRows(timelist);
        final boolean[][] spanCells = new boolean[actualRowCount][actualColumnCount];
        initSpanCells(daylist, timelist, spanCells);
        createEvents(daylist, spanCells, flexCellFormatter, timeRows);
        createDragAndDropSupport(spanCells, actualColumnCount, actualRowCount, daylist, timelist);
    }

    private ArrayList<Integer> calcTimeRows(final List<RowSlot> timelist)
    {
        final ArrayList<Integer> timeRows = new ArrayList<Integer>();
        int row = 0;
        for (final RowSlot rowSlot : timelist)
        {
            final List<SpanAndMinute> rowTimes = rowSlot.getRowTimes();
            for (final SpanAndMinute rowSpan : rowTimes)
            {
                timeRows.add(row + 1);
                row += rowSpan.getRowspan();
            }
        }
        return timeRows;
    }

    /**
     */
    private static final class OriginSupport
    {
        private Event event;
        private Position point;
    }

    private static final class Position
    {
        private int column;
        private int row;

        @Override
        public String toString()
        {
            return "Column: " + column + ", Row: " + row;
        }
    }

    /**
     * Creates the needed drag and drop listener within the table.
     * 
     * @param spanCells the information which cells have a span (so are not within the table)
     * @param columnCount the column count 
     * @param rowCount the row count
     */
    private void createDragAndDropSupport(final boolean[][] spanCells, final int columnCount, final int rowCount, final List<HTMLDaySlot> daylist,
            final List<RowSlot> timelist)
    {
        final int rowCountFromWidget = getRowCount();
        final int rows = spanCells.length;
        final int columns = spanCells[0].length;
        for (int row = 1; row < rows; row++)
        {
            for (int column = 1; column < columns; column++)
            {
                if (!spanCells[row][column])
                {
                    final int cellCountFromWidget = getCellCount(row);
                    final int realColumn = calcColumn(spanCells, row, column);
                    if (getWidget(row, realColumn) == null)
                    {
                        if (row > rowCountFromWidget || realColumn > cellCountFromWidget)
                        {
                            logger.error("to many rows or columns " + row + "/" + rowCountFromWidget + "-" + realColumn + "/" + cellCountFromWidget);
                        }
                        try
                        {
                            getCellFormatter().getElement(row, realColumn).setDraggable(Element.DRAGGABLE_TRUE);
                        }
                        catch (Exception e)
                        {
                            logger.error("ASD-" + row + "/" + column);
                        }
                    }
                }
            }
        }

        for (HandlerRegistration old : currentDomHandlers)
        {
            old.removeHandler();
        }
        currentDomHandlers.clear();
        EventListener listener = new EventListener(spanCells, daylist, timelist, logger);
        // Drag and Drop support
        currentDomHandlers.add(addDomHandler(listener, DragEnterEvent.getType()));
        currentDomHandlers.add(addDomHandler(listener, DragOverEvent.getType()));
        currentDomHandlers.add(addDomHandler(listener, DragLeaveEvent.getType()));
        currentDomHandlers.add(addDomHandler(listener, DragEndEvent.getType()));
        currentDomHandlers.add(addDomHandler(listener, DragStartEvent.getType()));
        currentDomHandlers.add(addDomHandler(listener, DropEvent.getType()));
        currentDomHandlers.add(addDomHandler(listener, ClickEvent.getType()));
    }

    private int normalize(boolean[][] spanCells, int row, int column)
    {
        int col = -1;
        final boolean[] spans = spanCells[row];
        logger.info("spans: " + spans);
        for (int i = 0; i < spans.length; i++)
        {
            final boolean isSpan = spans[i];
            if (!isSpan)
            {
                col++;
            }
            if (col == column)
            {
                if (!extraDayColumns.contains(i))
                {
                    // update to the end of the day
                    final int index = Collections.binarySearch(extraDayColumns, i);
                    return extraDayColumns.get(-(index) - 1);
                }
                else
                {
                    return i;
                }
            }
        }
        return column;
    }

    class EventListener implements DragEnterHandler, DragOverHandler, DragLeaveHandler, DragEndHandler, DragStartHandler, DropHandler, ClickHandler
    {
        final private OriginSupport originSupport = new OriginSupport();

        final private boolean[][] spanCells;
        final private Collection<RowSlot> timelist;
        final private Collection<HTMLDaySlot> daylist;
        final private Logger logger;

        public EventListener(boolean[][] spanCells2, List<HTMLDaySlot> daylist2, List<RowSlot> timelist2, Logger logger)
        {
            this.spanCells = spanCells2;
            this.daylist = daylist2;
            this.timelist = timelist2;
            this.logger = logger;
        }

        @Override
        public void onClick(ClickEvent event)
        {
            final Element tc = WeekviewGWT.this.getEventTargetCell((com.google.gwt.user.client.Event) event.getNativeEvent());
            if (tc != null && tc.hasChildNodes() && events.containsKey(tc.getFirstChildElement()))
            {
                final HTMLRaplaBlock htmlBlock = events.get(tc.getFirstChildElement()).getHtmlBlock();
                final PopupContext context = contextCreator.createContext(event);
                callback.selectReservation(htmlBlock, context);
                event.stopPropagation();
            }
        }

        @Override
        public void onDragEnter(DragEnterEvent event)
        {
            event.stopPropagation();
            com.google.gwt.user.client.Event event2 = (com.google.gwt.user.client.Event) event.getNativeEvent();
            final Element tc = WeekviewGWT.this.getEventTargetCell(event2);
            if (tc != null)
            {
                if (originSupport.event != null)
                {
                    if (!events.containsKey(tc.getFirstChildElement()))
                    {
                        final Position position = calcPosition(tc);
                        mark(position, position);
                        tc.getStyle().setBackgroundColor(BACKGROUND_COLOR_TARGET);
                    }
                }
                else if (originSupport.point != null)
                {
                    final Position newPosition = calcPosition(tc);
                    logger.info("from: " + originSupport.point);
                    logger.info("to: " + newPosition);
                    clearAllDayMarks(spanCells, originSupport.point);
                    mark(originSupport.point, newPosition);
                }
            }
        }

        private void unmark(Position p1, Position p2)
        {
            logger.info("unmarking from " + p1 + " to " + p2);
            changeBackgroundColor(p1, p2, false);
        }

        private void mark(Position p1, Position p2)
        {
            logger.info("marking from " + p1 + " to " + p2);
            changeBackgroundColor(p1, p2, true);
        }

        private void changeBackgroundColor(Position p1, Position p2, boolean add)
        {
            final int column1Normalized = normalize(spanCells, p1.row, p1.column);
            final int column2Normalized = normalize(spanCells, p2.row, p2.column);
            if (column1Normalized == column2Normalized)
            {
                final int indexOfCurrentExtraDay = Collections.binarySearch(extraDayColumns, column1Normalized);
                final int startColumn;
                if (indexOfCurrentExtraDay > 0)
                {
                    startColumn = extraDayColumns.get(indexOfCurrentExtraDay - 1) + 1;
                }
                else
                {
                    startColumn = 1;
                }
                final int startRow = Math.max(1, Math.min(p1.row, p2.row));
                final int endRow = Math.max(p1.row, p2.row);
                for (int aRow = startRow; aRow <= endRow; aRow++)
                {
                    for (int aColumn = startColumn; aColumn <= column2Normalized; aColumn++)
                    {
                        if (!spanCells[aRow][aColumn])
                        {
                            final int column = calcColumn(spanCells, aRow, aColumn);
                            final Element element = getCellFormatter().getElement(aRow, column);
                            if (!element.hasChildNodes() || !events.containsKey(element.getFirstChildElement()))
                            {
                                if (add)
                                {
                                    element.getStyle().setBackgroundColor(BACKGROUND_COLOR_TARGET);
                                }
                                else
                                {
                                    element.getStyle().clearBackgroundColor();
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                // TODO: marking over more than one day
                // clear also think about
            }
        }

        @Override
        public void onDragLeave(DragLeaveEvent event)
        {
            if (originSupport.event != null)
            {
                com.google.gwt.user.client.Event event2 = (com.google.gwt.user.client.Event) event.getNativeEvent();
                final Element tc = WeekviewGWT.this.getEventTargetCell(event2);
                if (tc != null)
                {
                    final Position position = calcPosition(tc);
                    unmark(position, position);
                }
            }
            event.stopPropagation();
        }

        @Override
        public void onDragOver(DragOverEvent event)
        {
            event.stopPropagation();
        }

        @Override
        public void onDragEnd(DragEndEvent event)
        {
            WeekviewGWT.this.removeStyleName("dragging");
        }

        @Override
        public void onDragStart(final DragStartEvent event)
        {
            com.google.gwt.user.client.Event event2 = (com.google.gwt.user.client.Event) event.getNativeEvent();
            final Element tc = WeekviewGWT.this.getEventTargetCell(event2);
            final Event myEvent = events.get(tc.getFirstChildElement());
            if (myEvent != null)
            {
                WeekviewGWT.this.addStyleName("dragging");
                logger.info("event drag");
                originSupport.event = myEvent;
                originSupport.point = null;
            }
            else
            {
                logger.info("new event drag");
                originSupport.point = calcPosition(tc);
                originSupport.event = null;
            }
            try
            {// enable if needed
                if (originSupport.point != null)
                {
                    final ImageElement image = Document.get().createImageElement();
                    image.setSrc("");
                    event.getDataTransfer().setDragImage(image, 0, 0);
                }
                event.setData("dragging", "start");
            }
            catch (Exception e)
            {
            }
        }

        @Override
        public void onDrop(final DropEvent event)
        {
            if (originSupport.event != null)
            {
                com.google.gwt.user.client.Event event2 = (com.google.gwt.user.client.Event) event.getNativeEvent();
                final Element targetCell = WeekviewGWT.this.getEventTargetCell(event2);
                if (!isDroppedOnStart(targetCell))
                {
                    Position p = calcPosition(targetCell);
                    unmark(p, p);
                    final int column = normalize(spanCells, p.row, p.column);
                    final HTMLDaySlot daySlot = findDaySlot(column);
                    final Integer start = findRowSlot(p.row);
                    logger.info("day" + daySlot.getHeader() + " - " + start);
                    final PopupContext context = contextCreator.createContext(event);
                    callback.updateReservation(originSupport.event.getHtmlBlock(), daySlot, start, context);
                }
            }
            else if (originSupport.point != null)
            {
                clearAllDayMarks(spanCells, originSupport.point);
                com.google.gwt.user.client.Event event2 = (com.google.gwt.user.client.Event) event.getNativeEvent();
                final Element targetCell = WeekviewGWT.this.getEventTargetCell(event2);
                logger.info("dropping on " + targetCell);
                Position p = calcPosition(targetCell);
                final int column = normalize(spanCells, originSupport.point.row, originSupport.point.column);
                final HTMLDaySlot daySlot = findDaySlot(column);
                final Integer from = findRowSlot(Math.min(originSupport.point.row, p.row));
                final Integer till = findRowSlot(Math.max(originSupport.point.row, p.row));

                final PopupContext context = contextCreator.createContext(event);
                callback.newReservation(daySlot, from, till, context);
            }
            originSupport.event = null;
            originSupport.point = null;
            event.stopPropagation();
        }

        private boolean isDroppedOnStart(Element targetCell)
        {
            final boolean hasChildNodes = targetCell.hasChildNodes();
            if (hasChildNodes)
            {
                final Element firstChildElement = targetCell.getFirstChildElement();
                return events.get(firstChildElement) == originSupport.event;
            }
            return false;
        }

        private Integer findRowSlot(int row)
        {
            // header remove
            row--;
            for (RowSlot rowSlot : timelist)
            {
                if (row < rowSlot.getRowspan())
                {
                    for (SpanAndMinute rowTime : rowSlot.getRowTimes())
                    {
                        if (row < rowTime.getRowspan())
                        {
                            return rowTime.getMinute();
                        }
                        row -= rowTime.getRowspan();
                    }
                }
                row -= rowSlot.getRowspan();
            }
            return null;
        }

        private HTMLDaySlot findDaySlot(int column)
        {
            //header remove
            column--;
            for (final HTMLDaySlot day : daylist)
            {
                if (column < day.size() + 1)
                    return day;
                column -= (day.size() + 1);
            }
            return null;
        }

    }

    private static Position calcPosition(Element targetCell)
    {
        final Position position = new Position();
        final TableCellElement td = TableCellElement.as(targetCell);
        final Element tr = td.getParentElement();
        final int column = DOM.getChildIndex(tr, td);
        final Element tbody = tr.getParentElement();
        final int row = DOM.getChildIndex(tbody, tr);
        position.column = column;
        position.row = row;
        return position;
    }

    private void createEvents(final List<HTMLDaySlot> daylist, final boolean[][] spanCells, final FlexCellFormatter flexCellFormatter, List<Integer> timeRows)
    {
        events.clear();
        // create events
        int column = 1;
        for (final HTMLDaySlot daySlot : daylist)
        {
            if (daySlot.isEmpty())
            {
                fillWithEmptyCellsInInterval(spanCells, column, 1, spanCells.length, timeRows, "empty emptyDayRow");
                column++;
            }
            else
            {
                for (final Slot slot : daySlot)
                {
                    int lastEndRow = 1;
                    final Collection<Block> blocks = slot.getBlocks();
                    for (final Block block : blocks)
                    {
                        final HTMLRaplaBlock htmlBlock = (HTMLRaplaBlock) block;
                        final int blockRow = htmlBlock.getRow();
                        fillWithEmptyCellsInInterval(spanCells, column, lastEndRow, blockRow, timeRows, "empty");
                        final int blockColumn = calcColumn(spanCells, blockRow, column);
                        final Event event = new Event(htmlBlock);
                        this.setWidget(blockRow, blockColumn, event);
                        final Element element = event.getElement();
                        events.put(element, event);
                        final Element td = element.getParentElement();
                        td.getStyle().setBackgroundColor(htmlBlock.getBackgroundColor());
                        td.setClassName("eventTd");
                        td.setDraggable(Element.DRAGGABLE_TRUE);
                        final int rowCount = htmlBlock.getRowCount();
                        for (int i = 1; i < rowCount; i++)
                        {
                            spanCells[blockRow + i][column] = true;
                        }
                        flexCellFormatter.setVerticalAlignment(blockRow, blockColumn, HasVerticalAlignment.ALIGN_TOP);
                        flexCellFormatter.setRowSpan(blockRow, blockColumn, rowCount);
                        lastEndRow = blockRow + rowCount;
                    }
                    fillWithEmptyCellsInInterval(spanCells, column, lastEndRow, spanCells.length, timeRows, "empty");
                    column++;
                }
                fillWithEmptyCellsInInterval(spanCells, column, 1, spanCells.length, timeRows, "empty emptyDayRow");
                column++;
            }
        }
    }

    // 1. fuellt die spanCells
    // 2. fuellt die leere tds ein mit columns
    private void fillWithEmptyCellsInInterval(final boolean[][] spanCells, final int column, final int startRow, final int endRow, List<Integer> timeRows,
            final String styleName)
    {
        for (int row = startRow; row < endRow;)
        {
            final int nextTimeRow = getNextTimeRow(row, timeRows);
            final int rowToUse = nextTimeRow == -1 ? endRow : Math.min(nextTimeRow, endRow);
            final int rowSpan = rowToUse - row;
            final int realColumn = calcColumn(spanCells, row, column);
            setText(row, realColumn, "");// + rowSpan);
            getFlexCellFormatter().setRowSpan(row, realColumn, rowSpan);
            for (int blockRow = 1; blockRow < rowSpan; blockRow++)
            {
                final boolean[] bs = spanCells[row + blockRow];
                if (bs == null)
                {
                    logger.error("Error " + bs + " - " + (row + blockRow));
                }
                bs[column] = true;
            }
            setStyleNameFor(row, realColumn, styleName);
            row += rowSpan;
        }
    }

    // does not work
    //    private int getFirstElementThatIsGreater(int value,List<Integer> list )
    //    {
    //        int startIndex = 0;
    //        int endIndex = list.size()-1;
    //        int index=-1;
    //        while (true)
    //        {
    //            int halfIndex = startIndex + (endIndex-startIndex ) / 2;
    //            final Integer valueAt = list.get(halfIndex);
    //            if ( valueAt == value)
    //            {
    //                index = halfIndex+1 ;
    //                break;
    //            }
    //            if ( valueAt < value)
    //            {
    //                if (startIndex == halfIndex )
    //                {
    //                    break;
    //                }
    //                startIndex= halfIndex;
    //               
    //            }
    //            if ( valueAt > value)
    //            {
    //                if (endIndex == halfIndex )
    //                {
    //                    break;
    //                }
    //                endIndex = halfIndex;
    //                index = halfIndex;
    //            }
    //            if ( startIndex >= endIndex)
    //            {
    //                break;
    //            }
    //        }
    //        if ( index >= list.size())
    //        {
    //            return -1;
    //        }
    //        return list.get(index);
    //    }

    private int getNextTimeRow(int startRow, List<Integer> timeRows)
    {
        //  return getFirstElementThatIsGreater(startRow, timeRows);
        //        for (Integer integer : timeRows)
        //        {
        //            if (integer > startRow)
        //            {
        //                return integer;
        //            }
        //        }
        //        return -1;
        final int indexSearch = Collections.binarySearch(timeRows, startRow);
        final int index;
        // indexSearch >= 0 means theat startRow is found in timeRows list startRow is a value in timeRows 
        // indexSearch returns the index of the found entry
        if (indexSearch >= 0)
        {
            index = indexSearch + 1;
        }
        // indexSearch <0 means startRow is not found in timeRows -indexSearch-1 is the index at which startRow could be inserted to maintain the order 
        else
        {
            index = (-indexSearch - 1);// + 1;
        }
        if (index >= timeRows.size())
        {
            return -1;
        }
        return timeRows.get(index);
    }

    private void initSpanCells(final List<HTMLDaySlot> daylist, final List<RowSlot> timelist, final boolean[][] spanCells)
    {
        int header = 0;
        spanCells[0][header] = false;// left top corner
        for (final HTMLDaySlot daySlot : daylist)
        {
            header++;
            // headername is never one
            spanCells[0][header] = false;
            final int slotCount = Math.max(1, daySlot.size());
            for (int i = 1; i < slotCount; i++)
            {// span is one
                header++;
                spanCells[0][header] = true;
            }
        }
        int timeentry = 0;
        for (final RowSlot rowSlot : timelist)
        {
            for (SpanAndMinute time : rowSlot.getRowTimes())
            {
                final int span = time.getRowspan();
                timeentry++;
                spanCells[timeentry][0] = false;
                for (int i = 1; i < span; i++)
                {
                    timeentry++;
                    spanCells[timeentry][0] = true;
                }
            }
        }
    }

    private int createYAchsis(final List<RowSlot> timelist, final FlexCellFormatter flexCellFormatter)
    {
        int actualRowCount = 1;
        final ArrayList<Integer> fullHours = new ArrayList<Integer>();
        final ArrayList<Integer> subHours = new ArrayList<Integer>();
        for (final RowSlot timeEntry : timelist)
        {
            boolean first = true;
            for (SpanAndMinute rowTime : timeEntry.getRowTimes())
            {
                if (first)
                {
                    fullHours.add(actualRowCount);
                }
                else
                {
                    subHours.add(actualRowCount);
                }
                final String rowname = first ? timeEntry.getRowname() : "";
                this.setText(actualRowCount, 0, rowname);
                if (!first)
                {
                    getCellFormatter().getElement(actualRowCount, 0).setInnerHTML("&#160;");
                }
                first = false;
                setStyleNameFor(actualRowCount, 0, "header left");
                final int rowspan = rowTime.getRowspan();
                flexCellFormatter.setRowSpan(actualRowCount, 0, rowspan);
                flexCellFormatter.setAlignment(actualRowCount, 0, HasHorizontalAlignment.ALIGN_RIGHT, HasVerticalAlignment.ALIGN_TOP);
                actualRowCount += rowspan;
            }
        }
        final Element table = getCellFormatter().getElement(0, 0).getParentElement().getParentElement();
        final NodeList<Node> childNodes = table.getChildNodes();
        for (int i = 1; i < actualRowCount; i++)
        {
            final Node item = childNodes.getItem(i);
            if (item != null)
            {
                ((Element) item).addClassName(fullHours.contains(i) ? "full" : subHours.contains(i) ? "sub" : "stub");
            }
        }
        return actualRowCount;
    }

    private int createXAchsis(final List<HTMLDaySlot> daylist, final FlexCellFormatter flexCellFormatter)
    {
        extraDayColumns.clear();
        int actualColumnCount = 1;
        for (int i = 0; i < daylist.size(); i++)
        {
            final HTMLDaySlot htmlDaySlot = daylist.get(i);
            final int slotCount = Math.max(1, htmlDaySlot.size() + 1);
            final int column = i + 1;
            this.setText(0, column, htmlDaySlot.getHeader());
            setStyleNameFor(0, column, "header top");
            flexCellFormatter.setColSpan(0, column, slotCount);
            flexCellFormatter.setAlignment(0, column, HasHorizontalAlignment.ALIGN_CENTER, HasVerticalAlignment.ALIGN_TOP);
            actualColumnCount += slotCount;
            extraDayColumns.add(actualColumnCount - 1);
        }
        return actualColumnCount;
    }

    private void setStyleNameFor(final int row, final int column, final String styleName)
    {
        getCellFormatter().getElement(row, column).setClassName(styleName);
    }

    private void clearAllDayMarks(final boolean[][] spanCells, Position p1)
    {
        final int rows = spanCells.length;
        final int column1Normalized = normalize(spanCells, p1.row, p1.column);
        final int indexOfCurrentExtraDay = Collections.binarySearch(extraDayColumns, column1Normalized);
        final int startColumn;
        if (indexOfCurrentExtraDay > 0)
        {
            startColumn = extraDayColumns.get(indexOfCurrentExtraDay - 1) + 1;
        }
        else
        {
            startColumn = 1;
        }
        for (int row = 1; row < rows; row++)
        {
            for (int dayColumn = startColumn; dayColumn <= column1Normalized; dayColumn++)
            {
                if (!spanCells[row][dayColumn])
                {
                    final int column = calcColumn(spanCells, row, dayColumn);
                    final Element element = getCellFormatter().getElement(row, column);
                    if (!element.hasChildNodes() || !events.containsKey(element.getFirstChildElement()))
                    {
                        element.getStyle().clearBackgroundColor();
                    }
                }
            }
        }
    }

    private int calcColumn(final boolean[][] spanCells, final int row, final int column)
    {
        int numSpanCellsToRemove = 0;
        final boolean[] columns = spanCells[row];
        for (int i = 0; i < column; i++)
        {
            if (columns[i])
            {
                numSpanCellsToRemove++;
            }
        }
        return column - numSpanCellsToRemove;
    }
}
