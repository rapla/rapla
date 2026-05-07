package org.rapla.plugin.tableview.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.plugin.tableview.internal.TableConfig.ViewDefinition;


@RunWith(JUnit4.class)
public class TableConfigTest
{
    @Test
    public void serializationDesirialization() throws ConfigurationException
    {
        TableConfig config = new TableConfig();
        final TableConfig.TableColumnConfig nameColumn;
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("name");
            columnConfig.setDefaultValue("{name()}");
            columnConfig.setType("string");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "name");
            name.setName("de", "Name");
            columnConfig.setName(name);
            config.addColumn(columnConfig);

            nameColumn = columnConfig;

        }
        final TableColumnConfig startColumn;
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("date");
            columnConfig.setDefaultValue("{context:beginn}");
            columnConfig.setType("date");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "start");
            name.setName("de", "Start");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            startColumn = columnConfig;
        }

        final ViewDefinition eventView = config.getOrCreateView("events");
        eventView.addColumn(nameColumn);

        final ViewDefinition appointmentView = config.getOrCreateView("appointments");
        appointmentView.addColumn(nameColumn);
        appointmentView.addColumn(startColumn);
        GsonBuilder builder = new GsonBuilder();
        final Gson gson = builder.setPrettyPrinting().create();
        final String json = gson.toJson(config);
        final RaplaConfiguration raplaConfig = TableConfig.print(config);
        final TableConfig test = TableConfig.read(raplaConfig, new RaplaLocaleImpl(new ServerBundleManager()));
        final String json2 = gson.toJson(test);
        Assert.assertEquals(json, json2);
    }
}
