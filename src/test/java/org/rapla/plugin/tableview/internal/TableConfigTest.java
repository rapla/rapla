package org.rapla.plugin.tableview.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.plugin.tableview.internal.TableConfig.ViewDefinition;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@RunWith(JUnit4.class)
public class TableConfigTest
{
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static class Transport
    {
        private Transport()
        {

        }

        public Transport(TableConfig config)
        {
            this.config = config;
        }

        public TableConfig getConfig()
        {
            return config;
        }

        TableConfig config;
    }

    @Test
    public void serializationDesirialization() throws JAXBException, ConfigurationException
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
        System.out.println(json);
        final TableConfig fromJson = gson.fromJson(json, TableConfig.class);
        //System.out.println(fromJson.toString());
        JAXBContext context = JAXBContext.newInstance(Transport.class);
        final Marshaller marshaller = context.createMarshaller();
        //        final TableConfig.DateAdapter dateAdapter = new TableConfig.DateAdapter();
        //dateAdapter.setCutDate( true);
        //marshaller.setAdapter(dateAdapter);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(new Transport(config), out);
        //System.out.println(out.toString());
        final Unmarshaller unmarshaller = context.createUnmarshaller();
        //unmarshaller.setAdapter(dateAdapter);
        final Transport fromXml = (Transport) unmarshaller.unmarshal(new ByteArrayInputStream(out.toByteArray()));

        final RaplaConfiguration raplaConfig = TableConfig.print(fromXml.config);
        final TableConfig test = TableConfig.read(raplaConfig, new RaplaLocaleImpl(new DefaultBundleManager()));
        final String json2 = gson.toJson(test);
        //System.out.println(json2);
        Assert.assertEquals(json, json2);
    }
}
