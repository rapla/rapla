package org.rapla.client.gwt.components;

import java.util.ArrayList;
import java.util.Locale;

import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.entities.domain.Allocatable;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Window;

public class TreeComponent extends Div
{
    private static int counter = 0;
    private Allocatable[] allocatables;
    public TreeComponent(final Allocatable[] allocatables, Locale locale)
    {
        this.allocatables = allocatables;
        final String id = "tree-"+counter;
        this.setId(id);
        final JSONArray data = new JSONArray();
        for (int i = 0; i < allocatables.length; i++)
        {
            final JSONObject obj = new JSONObject();
            data.set(i, obj);
            obj.put("id", new JSONNumber(i+1));
            obj.put("text", new JSONString(allocatables[i].getName(locale)));
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
        if (selected instanceof JsArrayInteger)
        {
            JsArrayInteger selectedPositions = ((JsArrayInteger) selected);
            final ArrayList<Allocatable> selectedAllocatables = new ArrayList<Allocatable>();
            for (int i = 0; i < selectedPositions.length(); i++)
            {
                final int selectedPosition = selectedPositions.get(i);
                selectedAllocatables.add(allocatables[selectedPosition-1]);
            }
            Window.alert(selectedAllocatables.toString());
        }
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
