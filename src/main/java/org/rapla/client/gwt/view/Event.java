package org.rapla.client.gwt.view;

import java.util.List;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.TimeWithoutTimezone;
import org.rapla.entities.domain.Allocatable;
import org.rapla.plugin.abstractcalendar.server.HTMLRaplaBlock;

import com.google.gwt.dom.client.ParagraphElement;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

public class Event extends FlowPanel
{
    private HTMLRaplaBlock htmlBlock;

    public Event(HTMLRaplaBlock htmlBlock)
    {
        super();
        this.htmlBlock = htmlBlock;
        addStyleName("event");
        TimeWithoutTimezone endtime = DateTools.toTime(htmlBlock.getEnd().getTime());
        final int endminute = Math.min(22, endtime.hour)*60 + endtime.minute ;
        TimeWithoutTimezone starttime = DateTools.toTime(htmlBlock.getStart().getTime());
        final int startminute = Math.max(5, starttime.hour)*60 + starttime.minute ;
        final int rows = (int) Math.ceil(((endminute - startminute) / ( 30.0)));
        final int size = Math.max(1, rows);
        setHeight((size * 15) + "px");
        add(createTitleBlock());
        add(createPersonBlock());
        add(createResourcesBlock());
        //        getElement().setTitle(htmlBlock.getContext().getTooltip());
        //        getElement().getStyle().setBackgroundColor(htmlBlock.getBackgroundColor());
        //        getElement().setDraggable(Element.DRAGGABLE_TRUE);
    }

    private Widget createResourcesBlock()
    {
        final StringBuilder sb = new StringBuilder();
        final Allocatable[] resources = htmlBlock.getReservation().getResources();
        for (int i = 0; i < resources.length; i++)
        {
            if (!htmlBlock.getContext().isVisible(resources[i]))
                continue;
            sb.append(htmlBlock.getName(resources[i]));
            sb.append(", ");
        }
        final String string;
        if (sb.length() > 0)
        {
            string = sb.substring(0, sb.length() - 2);
        }
        else
        {
            string = "";
        }
        final FlowPanel p = new FlowPanel(ParagraphElement.TAG);
        p.getElement().setInnerText(string);
        return p;
    }

    private Widget createTitleBlock()
    {
        final String name = htmlBlock.getName();
        final HTML p = new HTML(name);
        return p;
    }

    private Widget createPersonBlock()
    {
        final StringBuilder sb = new StringBuilder();
        List<Allocatable> persons = htmlBlock.getContext().getAllocatables();

        for (Allocatable person : persons)
        {
            if (!htmlBlock.getContext().isVisible(person) || !person.isPerson())
                continue;
            sb.append(htmlBlock.getName(person));
            sb.append(", ");
        }
        final String string;
        if (sb.length() > 0)
        {
            string = sb.substring(0, sb.length() - 2);
        }
        else
        {
            string = "";
        }
        final FlowPanel p = new FlowPanel(ParagraphElement.TAG);
        p.getElement().setInnerText(string);
        return p;
    }

    public HTMLRaplaBlock getHtmlBlock()
    {
        return htmlBlock;
    }

}
