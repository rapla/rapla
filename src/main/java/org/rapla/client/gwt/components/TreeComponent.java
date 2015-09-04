package org.rapla.client.gwt.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.client.gwt.components.util.JQueryElement;
import org.rapla.client.gwt.components.util.JS;
import org.rapla.client.gwt.test.Event;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.client.js.JsFunction;
import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;

public class TreeComponent extends Div
{
    /**
     * Interface for callback after selection changed in tree
     */
    public interface SelectionChangeHandler
    {
        void selectionChanged(final Collection<Allocatable> selected);
    }

    @JsType(prototype = "jQuery")
    public interface JsTreeJquery extends JQueryElement
    {
        JsTreeElement jstree(JsTreeOptions options);

    }

    @JsType(prototype = "jQuery")
    public interface JsTreeElement extends JQueryElement
    {
        JsTree data(String key);
        
        void on(String event, JsTreeEventListener eventListener);
    }

    @JsType(prototype = "DateRangePicker")
    public interface JsTree extends JQueryElement
    {
        void deselect_all(boolean supressEvent);

        void refresh(boolean skipLoading, boolean forgetState);

        @JsProperty
        JsTreeSettings getSettings();
    }

    @JsType
    public interface JsTreeSettings
    {

        @JsProperty
        JsTreeCore getCore();

        @JsProperty
        void setCore(JsTreeCore core);

    }

    @JsType
    public interface JsTreeOptions
    {
        @JsProperty
        void setPlugins(JavaScriptObject javaScriptObject);

        @JsProperty
        JavaScriptObject getPlugins();

        @JsProperty
        void setCore(JsTreeCore core);

        @JsProperty
        JsTreeCore getCore();
    }

    @JsType
    public interface JsTreeCore
    {
        @JsProperty
        void setDataType(String dataType);

        @JsProperty
        String getDataType();

        @JsProperty
        void setData(JavaScriptObject data);

        @JsProperty
        JavaScriptObject getData();

        @JsProperty
        void setThemes(JsTreeThemes themes);

        @JsProperty
        JsTreeThemes getThemes();
    }

    @JsType
    public interface JsTreeThemes
    {
        @JsProperty
        void setName(String name);

        @JsProperty
        String getName();

        @JsProperty
        void setResponsive(Boolean resposive);

        @JsProperty
        Boolean getResponsive();
    }

    @JsFunction
    public interface JsTreeEventListener
    {
        void handle(Event e, Object data);
    }

    private Allocatable[] allocatables;
    private final SelectionChangeHandler selectionChangeHandler;
    private final Locale locale;
    private boolean updatingData = false;
    private JsTree jstree = null;

    public TreeComponent(Locale locale, SelectionChangeHandler selectionChangeHandler)
    {
        this.locale = locale;
        this.allocatables = null;
        this.selectionChangeHandler = selectionChangeHandler;
    }

    public void updateData(Allocatable[] entries, Collection<Allocatable> selected)
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
            if (selected.contains(allocatable))
            {
                JSONObject state = new JSONObject();
                state.put("selected", new JSONString(Boolean.TRUE.toString()));
                obj.put("state", state);
            }
        }
        if (jstree == null)
        {
            Scheduler.get().scheduleFinally(new ScheduledCommand()
            {
                @Override
                public void execute()
                {
                    initTree();
                }
            });
        }
        // load data
        Scheduler.get().scheduleFinally(new ScheduledCommand()
        {
            @Override
            public void execute()
            {
                updatingData = true;
                jstree.getSettings().getCore().setData(data.getJavaScriptObject());
                jstree.deselect_all(true);
                jstree.refresh(true, true);
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
                selectedAllocatables.add(allocatables[selectedPosition - 1]);
            }
            selectionChangeHandler.selectionChanged(selectedAllocatables);
        }
    }

    private void refreshCompleted()
    {
        updatingData = false;
    }

    private void initTree()
    {
        JsTreeOptions options = JS.createObject();
        // JSONArray ist doof... aber ohne geht nicht, wegen zusätzlicher Infos an dem Objekt...
        JSONArray plugins = new JSONArray();
        plugins.set(0, new JSONString("wholerow"));
        plugins.set(1, new JSONString("checkbox"));
        options.setPlugins(plugins.getJavaScriptObject());
        options.setCore(JS.createObject());
        JsTreeCore core = options.getCore();
        core.setDataType("JSON");
        core.setThemes(JS.createObject());
        JsTreeThemes themes = core.getThemes();
        themes.setName("proton");
        themes.setResponsive(true);
        JsTreeJquery jsTreeJquery = (JsTreeJquery) JQueryElement.Static.$(getElement());
        JsTreeElement jstreeElement = jsTreeJquery.jstree(options);
        jstree = jstreeElement.data("jstree");
        jstreeElement.on("changed.jstree", new JsTreeEventListener()
        {
            @Override
            public void handle(Event e, Object data)
            {
                selectionChanged(data);
            }
        });
        jstreeElement.on("refresh.jstree", new JsTreeEventListener()
        {
            @Override
            public void handle(Event e, Object data)
            {
                refreshCompleted();
            }
        });
    }
}
