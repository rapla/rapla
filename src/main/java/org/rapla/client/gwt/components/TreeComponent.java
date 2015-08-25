package org.rapla.client.gwt.components;

import java.util.Locale;

import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.entities.domain.Allocatable;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.Window;

public class TreeComponent extends Div
{
    private static int counter = 0;
    public TreeComponent(Allocatable[] allocatables, Locale locale)
    {
        final String id = "tree-"+counter;
        this.setId(id);
        // switch the layout after loading 
        Scheduler.get().scheduleFinally(new ScheduledCommand()
        {
            @Override
            public void execute()
            {
                initJs(id, TreeComponent.this);
            }
        });
    }
    
    private void selectionChanged(Object selected)
    {
        Window.alert("Selected:"+selected);
    }
    
    public native void initJs(final String id, final TreeComponent tc)/*-{
        $wnd.$('#'+id).jstree({
        'plugins': ["wholerow", "checkbox"],
        'core': {
            'data' : [
                {"id" : 1, "text" : "Node 1"},
                {"id" : 2, "text" : "Node 2"},
              ],
            'themes': {
                'name': 'proton',
                'responsive': true
                }
            }
        });
        $wnd.$('#'+id).on("changed.jstree", function (e, data) {
            tc.@org.rapla.client.gwt.components.TreeComponent::selectionChanged(Ljava/lang/Object;)(data.selected);
        });
    }-*/;
}
