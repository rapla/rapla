package org.rapla.client.gwt.view;

import java.util.Date;

import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.constants.ButtonType;
import org.rapla.client.gwt.components.DateComponent;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;

import com.google.gwt.dom.client.Style.Float;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlowPanel;

public class NavigatorView extends FlowPanel
{
    public static interface NavigatorAction
    {
        void selectedDate(Date selectedDate);

        void next();

        void previous();
    }

    private final DateComponent dateComponent;

    public NavigatorView(final String parentStyle, final NavigatorAction navigatorAction, BundleManager bundleManager)
    {
        super();
        setStyleName(parentStyle);
        dateComponent = new DateComponent(new Date(), new DateValueChanged()
        {
            @Override
            public void valueChanged(Date newValue)
            {
                navigatorAction.selectedDate(newValue);
            }
        }, bundleManager);
        this.add(dateComponent);
        dateComponent.getElement().getStyle().setFloat(Float.LEFT);
        final Button today = new Button("today");
        today.setType(ButtonType.PRIMARY);
        today.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                final Date today = DateTools.cutDate(new Date());
                navigatorAction.selectedDate(today);
            }
        });
        this.add(today);
        final Button previousButton = new Button("previous");
        previousButton.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                navigatorAction.previous();
            }
        });
        this.add(previousButton);
        final Button nextButton = new Button("next");
        nextButton.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                // callback
                navigatorAction.next();
            }
        });
        this.add(nextButton);
    }

    public void setDate(Date date)
    {
        this.dateComponent.setDate(date);
    }
}
