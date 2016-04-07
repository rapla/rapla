package org.rapla.client.gwt.view;

import java.util.Date;

import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Column;
import org.gwtbootstrap3.client.ui.Container;
import org.gwtbootstrap3.client.ui.Row;
import org.gwtbootstrap3.client.ui.constants.ButtonType;
import org.gwtbootstrap3.client.ui.constants.ColumnSize;
import org.gwtbootstrap3.client.ui.constants.DeviceSize;
import org.rapla.client.gwt.components.DateComponent;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.IsWidget;

public class NavigatorView extends Container
{
    public interface NavigatorAction
    {
        void selectedDate(Date selectedDate);

        void next();

        void previous();
    }

    private final DateComponent dateComponent;

    public NavigatorView(final NavigatorAction navigatorAction, BundleManager bundleManager)
    {
        super();
        final Row row = new Row();
        add(row);
        setVisibleOn(DeviceSize.SM_MD_LG);
        dateComponent = new DateComponent(new Date(), new DateValueChanged()
        {
            @Override
            public void valueChanged(Date newValue)
            {
                navigatorAction.selectedDate(newValue);
            }
        }, bundleManager);
        final Column col = new Column(ColumnSize.LG_3, ColumnSize.MD_4, ColumnSize.SM_3);
        col.add(dateComponent);
        row.add(col);
        final Button today = new Button("today");
        today.setType(ButtonType.PRIMARY);
        today.setBlock(true);
        today.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                final Date today = DateTools.cutDate(new Date());
                navigatorAction.selectedDate(today);
            }
        });
        addColumn(row, today);
        final Button previousButton = new Button("previous");
        previousButton.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                navigatorAction.previous();
            }
        });
        previousButton.setBlock(true);
        addColumn(row, previousButton);
        final Button nextButton = new Button("next");
        nextButton.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                navigatorAction.next();
            }
        });
        nextButton.setBlock(true);
        addColumn(row, nextButton);
    }

    private void addColumn(final Row row, final IsWidget componentToAdd)
    {
        final Column col = new Column(ColumnSize.LG_2, ColumnSize.MD_2, ColumnSize.SM_3);
        col.add(componentToAdd);
        row.add(col);
    }

    public void setDate(Date date)
    {
        this.dateComponent.setDate(date);
    }
}
