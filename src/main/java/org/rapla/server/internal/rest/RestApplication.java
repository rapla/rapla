package org.rapla.server.internal.rest;

import org.rapla.rest.server.ServiceInfLoader;
import org.rapla.rest.server.provider.exception.RestExceptionMapper;
import org.rapla.rest.server.provider.filter.HttpMethodOverride;
import org.rapla.rest.server.provider.json.JsonParamConverterProvider;
import org.rapla.rest.server.provider.json.JsonReader;
import org.rapla.rest.server.provider.json.JsonStringWriter;
import org.rapla.rest.server.provider.json.JsonWriter;
import org.rapla.rest.server.provider.xml.XmlReader;
import org.rapla.rest.server.provider.xml.XmlWriter;

import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RestApplication extends Application
{
    private Set<Class<?>> classes;

    public RestApplication() throws IOException
    {
        final HashSet<Class<?>> classes = new HashSet<>();
        {
            final ClassLoader classLoader = getClass().getClassLoader();
            final ServiceInfLoader.LoadingResult loadingResult = ServiceInfLoader.loadClassesFromMetaInfo(classLoader, Provider.class.getCanonicalName(),Path.class.getCanonicalName());
            classes.addAll(loadingResult.getClasses());
            for (Throwable error : loadingResult.getErrors())
            {
                throw new RuntimeException("Error loading Meta-INF" + error);
            }
            /*
            classes.add(RestExceptionMapper.class);
            classes.add(HttpMethodOverride.class);
            classes.add(JsonParamConverterProvider.class);
            classes.add(JsonReader.class);
            classes.add(JsonWriter.class);
            classes.add(JsonStringWriter.class);
            classes.add(XmlWriter.class);
            classes.add(XmlReader.class);
            classes.add(RaplaRestDaggerContextProvider.class);

            classes.add(MyRestPage.class);
            classes.add(ExampleServiceImpl.class);
            classes.add(ExampleSimpleServiceImpl.class);
*/
        }
        this.classes = Collections.unmodifiableSet(classes);
    }

    @Override
    public Set<Class<?>> getClasses()
    {
        return classes;
    }
}
