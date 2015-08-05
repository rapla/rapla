package org.rapla.client.plugin.tableview.gwt;

import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.plugin.tableview.CalendarTableView;
import org.rapla.client.plugin.tableview.CalendarTableViewPresenter;

import com.google.gwt.inject.client.GinModule;
import com.google.gwt.inject.client.binder.GinBinder;
import com.google.gwt.inject.client.multibindings.GinMultibinder;

public class ListViewPluginModule implements GinModule {

    @Override
    public void configure(GinBinder binder) {
        GinMultibinder<CalendarPlugin> uriBinder = GinMultibinder.newSetBinder(binder, CalendarPlugin.class);
        uriBinder.addBinding().to(CalendarTableViewPresenter.class);
        binder.bind(CalendarTableView.class).to(CalendarListViewImpl.class);
    }

}
