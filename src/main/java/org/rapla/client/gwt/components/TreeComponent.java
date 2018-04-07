package org.rapla.client.gwt.components;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.client.gwt.components.util.JQueryElement;
import org.rapla.client.gwt.components.util.JS;
import org.rapla.client.gwt.test.Event;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TreeComponent extends Div
{
    /**
     * Interface for callback after selection changed in tree
     */
    public interface SelectionChangeHandler
    {
        void selectionChanged(final Collection<Allocatable> selected);
    }

    @JsType(isNative=true)
    public interface JsTreeJquery extends JQueryElement
    {
        JsTreeElement jstree(JsTreeOptions options);

        void on(String event, JsTreeEventListener eventListener);
    }

    @JsType(isNative=true)
    public interface JsTreeElement extends JQueryElement
    {
        JsTree data(String key);
    }

    @JsType(isNative=true)
    public interface JsTree extends JQueryElement
    {
        void deselect_all(boolean supressEvent);

        void refresh(boolean skipLoading, boolean forgetState);

        @JsProperty
        JsTreeSettings getSettings();

        void show_contextmenu(JsTreeContextMenu menuFunction);
    }

    @JsType(isNative=true)
    public interface JsTreeSettings
    {

        @JsProperty
        JsTreeCore getCore();

        @JsProperty
        void setCore(JsTreeCore core);

    }

    @JsType(isNative=true)
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

        @JsProperty
        void setContextmenu(JsTreeContextMenu core);

        @JsProperty
        JsTreeContextMenu getContextmenu();

    }

    @JsType(isNative=true)
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

        @JsProperty
        void setCheck_callback(boolean themes);

        @JsProperty
        boolean isCheck_callback();
    }

    @JsType(isNative=true)
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

    @JsType(isNative=true)
    public interface JsTreeDataChange
    {
        @JsProperty
        void setSelected(JsArrayInteger selected);

        @JsProperty
        JsArrayInteger getSelected();
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
        Map<String, JSONArray> dynTypes = new HashMap<>();
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
            Scheduler.get().scheduleFinally(() -> initTree());
        }
        // load data
        Scheduler.get().scheduleFinally(() -> {
            updatingData = true;
            jstree.getSettings().getCore().setData(data.getJavaScriptObject());
            jstree.deselect_all(true);
            jstree.refresh(true, true);
        });
    }

    @JsType(isNative=true)
    public interface JsTreeContextMenu
    {
        @JsProperty
        void setItems(JsTreeContextMenuFunction function);

        @JsProperty
        void setSelect_node(boolean select_node);
    }

    @JsFunction
    public interface JsTreeContextMenuFunction
    {
        void show(Object node, Object e);
    }

    private void selectionChanged(JsArrayInteger selected)
    {
        if (updatingData)
        {
            return;
        }
        JsArrayInteger selectedPositions = selected;
        final ArrayList<Allocatable> selectedAllocatables = new ArrayList<>();
        for (int i = 0; i < selectedPositions.length(); i++)
        {
            final int selectedPosition = selectedPositions.get(i);
            selectedAllocatables.add(allocatables[selectedPosition - 1]);
        }
        selectionChangeHandler.selectionChanged(selectedAllocatables);
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
        plugins.set(2, new JSONString("contextmenu"));
        options.setPlugins(plugins.getJavaScriptObject());
        options.setCore((JsTreeCore)JS.createObject());
        options.setContextmenu((JsTreeContextMenu)JS.createObject());
        options.getContextmenu().setItems((node, e) -> Window.alert("Context menu, I am coming: " + node + ", " + e));
        options.getContextmenu().setSelect_node(false);
        JsTreeCore core = options.getCore();
        core.setDataType("JSON");
        core.setThemes((JsTreeThemes)JS.createObject());
        core.setCheck_callback(true);
        JsTreeThemes themes = core.getThemes();
        themes.setName("proton");
        themes.setResponsive(true);
        JsTreeJquery jsTreeJquery = (JsTreeJquery) JQueryElement.Static.$(getElement());
        JsTreeElement jstreeElement = jsTreeJquery.jstree(options);
        jstree = jstreeElement.data("jstree");
        jsTreeJquery.on("changed.jstree", (e, data) -> {
            JsArrayInteger selected = ((JsTreeDataChange) data).getSelected();
            selectionChanged(selected);
        });
        jsTreeJquery.on("refresh.jstree", (e, data) -> {
            refreshCompleted();
        });
    }
}
