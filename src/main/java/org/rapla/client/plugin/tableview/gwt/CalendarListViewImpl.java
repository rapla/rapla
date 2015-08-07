package org.rapla.client.plugin.tableview.gwt;

import java.util.Collection;
import java.util.List;

import org.rapla.client.base.AbstractView;
import org.rapla.client.plugin.tableview.CalendarTableView;
import org.rapla.entities.domain.Reservation;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SingleSelectionModel;

public class CalendarListViewImpl extends AbstractView<org.rapla.client.plugin.tableview.CalendarTableView.Presenter> implements CalendarTableView<IsWidget> {

    final ListDataProvider<Reservation> data = new ListDataProvider<>();
    final CellList<Reservation> list = new CellList<Reservation>(new ReservationCell());

    public CalendarListViewImpl() {
        list.setStyleName("RaplaListDrawerList");
        data.addDataDisplay(list);
        final SingleSelectionModel<Reservation> singleSelectionModel = new SingleSelectionModel<>();
        list.setSelectionModel(singleSelectionModel);
        singleSelectionModel.addSelectionChangeHandler(new Handler() {

            @Override
            public void onSelectionChange(SelectionChangeEvent event) {
                Reservation selectedObject = singleSelectionModel.getSelectedObject();
                getPresenter().selectReservation(selectedObject);
            }
        });
    }

    private class ReservationCell extends AbstractCell<Reservation>
    {
        @Override
        public void render(com.google.gwt.cell.client.Cell.Context context, Reservation value, SafeHtmlBuilder sb) {
            String name = value.getName(getRaplaLocale().getLocale());
            sb.appendEscaped(name);
        }
    }

    @Override
    public void update(Collection<Reservation> result) {
    	final List<Reservation> list = data.getList();
        list.clear();
        for (Reservation event : result)
        {
            list.add(event);
        }
        this.list.setPageSize(data.getList().size());
    }

    @Override
    public IsWidget provideContent() {
        return list;
    }
}
