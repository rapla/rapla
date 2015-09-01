package org.rapla.client.gwt.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;

public class TreeComponent extends Div
{
    public interface SelectionChangeHandler
    {
        void selectionChanged(final Collection<Allocatable> selected);
    }
    
    private static int counter = 0;
    private Allocatable[] allocatables;
    private final SelectionChangeHandler selectionChangeHandler;
    private final String id = "tree-"+(counter++);
    private final Locale locale;
    private boolean updatingData = false;
    public TreeComponent(Locale locale, SelectionChangeHandler selectionChangeHandler)
    {
        this.locale = locale;
        this.allocatables = null;
        this.selectionChangeHandler = selectionChangeHandler;
        this.setId(id);
        Scheduler.get().scheduleFinally(new ScheduledCommand()
        {
            @Override
            public void execute()
            {
                initJs(id, TreeComponent.this);
            }
        });
    }
    
    public void updateData(Allocatable[] entries, Collection<Allocatable>selected)
    {
        this.allocatables = entries;
        Map<String, JSONArray> dynTypes = new HashMap<String, JSONArray>();
        final JSONArray data = new JSONArray();
        for (int i = 0; i < allocatables.length; i++)
        {
            final Allocatable allocatable = allocatables[i];
            final DynamicType type = allocatable.getClassification().getType();
            final String key = type.getKey();
            JSONArray dynTypeArray = dynTypes.get(key);
            if (dynTypeArray == null)
            {
                dynTypeArray = new JSONArray();
                dynTypes.put(key, dynTypeArray);
                JSONObject dynTypeWrapper = new JSONObject();
                JSONObject state = new JSONObject();
                state.put("opened", new JSONString(Boolean.TRUE.toString()));
                dynTypeWrapper.put("state", state);
                dynTypeWrapper.put("icon", new JSONString("Rapla/big_folder.png"));
                String name = type.getName(locale);
                dynTypeWrapper.put("text", new JSONString(name));
                dynTypeWrapper.put("children", dynTypeArray);
//                dynTypeWrapper.put("icon", dynTypeArray);
                data.set(data.size(), dynTypeWrapper);
            }
            final JSONObject obj = new JSONObject();
            dynTypeArray.set(dynTypeArray.size(), obj);
            obj.put("id", new JSONNumber(i + 1));
            obj.put("text", new JSONString(allocatable.getName(locale)));
            if(selected.contains(allocatable))
            {
                JSONObject state = new JSONObject();
                state.put("selected", new JSONString(Boolean.TRUE.toString()));
                obj.put("state", state);
            }
        }
        // load data
        Scheduler.get().scheduleFinally(new ScheduledCommand()
        {
            @Override
            public void execute()
            {
                updatingData = true;
                fillData(id, data.getJavaScriptObject());
            }
        });
    }
    
    private void selectionChanged(Object selected)
    {
        if (updatingData)
        {
            return;
        }
        if (selected instanceof JsArrayInteger)
        {
            JsArrayInteger selectedPositions = ((JsArrayInteger) selected);
            final ArrayList<Allocatable> selectedAllocatables = new ArrayList<Allocatable>();
            for (int i = 0; i < selectedPositions.length(); i++)
            {
                final int selectedPosition = selectedPositions.get(i);
                selectedAllocatables.add(allocatables[selectedPosition-1]);
            }
            selectionChangeHandler.selectionChanged(selectedAllocatables);
        }
    }
    
    private void refreshCompleted()
    {
        updatingData = false;
    }
    
    /*
     * dblclick.jstree
     */
    private native void fillData(final String id, final JavaScriptObject newData)/*-{
        var jstree = $wnd.$('#'+id).jstree(true);
        jstree.settings.core.data = newData;
        jstree.deselect_all(true);
        jstree.refresh(true, true);
    }-*/;
    
    private native void initJs(final String id, final TreeComponent tc)/*-{
        $wnd.$('#'+id).jstree({
        'plugins': ["wholerow", "checkbox"],
        'core': {
            'dataType': 'JSON',
            'themes': {
                'name': 'proton',
                'responsive': true
                }
            }
        });
        $wnd.$('#'+id).on("changed.jstree", function (e, data) {
            tc.@org.rapla.client.gwt.components.TreeComponent::selectionChanged(Ljava/lang/Object;)(data.selected);
        });
        $wnd.$('#'+id).on("refresh.jstree", function (e, data) {
            tc.@org.rapla.client.gwt.components.TreeComponent::refreshCompleted()();
        });
    }-*/;
}
