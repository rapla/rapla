package org.rapla.client.gwt.view;

import java.util.Date;

import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.constants.ButtonType;
import org.rapla.client.gwt.components.DateComponent;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;

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

    public NavigatorView(final String parentStyle, final NavigatorAction navigatorAction, final RaplaLocale raplaLocale)
    {
        super();
        setStyleName(parentStyle);
        addStyleName("navigator");
        dateComponent = new DateComponent(new Date(), raplaLocale, new DateValueChanged()
        {
            @Override
            public void valueChanged(Date newValue)
            {
                navigatorAction.selectedDate(newValue);
            }
        });
        this.add(dateComponent);
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
