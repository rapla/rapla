package org.rapla.client.gwt.components;

import java.util.Locale;

import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.entities.domain.Allocatable;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;

public class TreeComponent extends Div
{
    private static int counter = 0;
    public TreeComponent(Allocatable[] allocatables, Locale locale)
    {
        final String id = "tree-"+counter;
        this.setId(id);
        final JSONArray data = new JSONArray();
        int i = 0;
        for (Allocatable allocatable : allocatables)
        {
            final JSONObject obj = new JSONObject();
            data.set(i++, obj);
            obj.put("id", new JSONString(allocatable.getId()));
            obj.put("text", new JSONString(allocatable.getName(locale)));
        }
        // switch the layout after loading 
        Scheduler.get().scheduleFinally(new ScheduledCommand()
        {
            @Override
            public void execute()
            {
                initJs(id, data.getJavaScriptObject(), TreeComponent.this);
            }
        });
    }
    
    private void selectionChanged(Object selected)
    {
        Window.alert("Selected:"+selected);
    }
    
    public native void initJs(final String id, final JavaScriptObject data, final TreeComponent tc)/*-{
        $wnd.$('#'+id).jstree({
        'plugins': ["wholerow", "checkbox"],
        'core': {
            'dataType': 'JSON',
            'data' : data,
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
