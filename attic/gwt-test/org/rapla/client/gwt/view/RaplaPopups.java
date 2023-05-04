package org.rapla.client.gwt.view;

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.gwtbootstrap3.client.ui.ProgressBar;
import org.gwtbootstrap3.client.ui.html.Div;

public class RaplaPopups
{

    public static class ProgressBarWrapper 
    {
        private final Div wrapper = new Div();
        private final ProgressBar progressBar = new ProgressBar();

        private ProgressBarWrapper()
        {
            wrapper.setId("progressWrapper");
            final Div asd = new Div();
            asd.setStyleName("progress");
            wrapper.add(asd);
            asd.add(progressBar);
            progressBar.addStyleName("progress-bar-striped");
        }

        public void setPercent(double percent)
        {
            if (percent < 100)
            {
                wrapper.setVisible(true);
                wrapper.getElement().getStyle().setTop(Window.getScrollTop(), Unit.PX);
                Document.get().getBody().getStyle().setOverflow(Overflow.HIDDEN);
            }
            else
            {
                wrapper.setVisible(false);
                Document.get().getBody().getStyle().setOverflow(Overflow.AUTO);
            }
            progressBar.setPercent(percent);
        }
    }

    private static final int MAX_WIDTH_POPUP = 1200;
    private static final ProgressBarWrapper progressBarWrapper = new ProgressBarWrapper();

    static
    {
        Window.addResizeHandler(event -> {
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
        });
        RootPanel.get().add(progressBarWrapper.wrapper);
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

    public static ProgressBarWrapper getProgressBar()
    {
        return progressBarWrapper;
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
