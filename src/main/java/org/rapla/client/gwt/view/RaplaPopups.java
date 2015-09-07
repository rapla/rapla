package org.rapla.client.gwt.view;

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

public class RaplaPopups
{

    private static final int MAX_WIDTH_POPUP = 1200;

    static
    {
        Window.addResizeHandler(new ResizeHandler()
        {
            @Override
            public void onResize(ResizeEvent event)
            {
                int clientWidth = event.getWidth();
                final int width = Math.min(MAX_WIDTH_POPUP, clientWidth);
                BodyElement body = Document.get().getBody();
                NodeList<Node> childNodes = body.getChildNodes();
                int length = childNodes.getLength();
                for (int index = 0; index < length; index++)
                {
                    Node child = childNodes.getItem(index);
                    if (child instanceof Element)
                    {
                        Element element = (Element) child;
                        String styleClass = element.getClassName();
                        if (styleClass.contains("raplaPopup"))
                        {
                            element.getStyle().setWidth(width, Unit.PX);
                        }
                    }
                }
            }
        });
    }

    public static void showWarning(String title, String text)
    {
        final PopupPanel warningPopup = new PopupPanel(true, true);
        final VerticalPanel layout = new VerticalPanel();
        layout.add(new HTML(title));
        layout.add(new HTML(text));
        warningPopup.add(layout);
        warningPopup.center();
        warningPopup.show();
    }

    public static RootPanel getPopupPanel()
    {
        return RootPanel.get("raplaPopup");
    }

    public static PopupPanel createNewPopupPanel()
    {
        final DialogBox raplaPopup = new DialogBox(false, false);
        final int width = Math.min(MAX_WIDTH_POPUP, Window.getClientWidth());
        raplaPopup.getElement().getStyle().setWidth(width, Unit.PX);
        raplaPopup.setAutoHideOnHistoryEventsEnabled(true);
        raplaPopup.addStyleName("raplaPopup");
        return raplaPopup;
    }

}
