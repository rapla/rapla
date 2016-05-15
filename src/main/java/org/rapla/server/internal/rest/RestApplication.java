package org.rapla.server.internal.rest;

import org.rapla.rest.server.ServiceInfLoader;
import org.rapla.rest.server.provider.exception.RestExceptionMapper;
import org.rapla.rest.server.provider.filter.HttpMethodOverride;
import org.rapla.rest.server.provider.json.JsonParamConverterProvider;
import org.rapla.rest.server.provider.json.JsonReader;
import org.rapla.rest.server.provider.json.JsonStringReader;
import org.rapla.rest.server.provider.json.JsonStringWriter;
import org.rapla.rest.server.provider.json.JsonWriter;
import org.rapla.rest.server.provider.json.OptionsAcceptHeader;
import org.rapla.rest.server.provider.json.PatchReader;
import org.rapla.rest.server.provider.resteasy.ResteasyMembersInjector;
import org.rapla.rest.server.provider.xml.XmlReader;
import org.rapla.rest.server.provider.xml.XmlWriter;
import org.scannotation.AnnotationDB;
import org.scannotation.WarUrlFinder;

import javax.servlet.ServletContext;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RestApplication extends Application
{
    private Set<Class<?>> classes;


    public static URL[] findWebInfLibClasspaths(ServletContext servletContext)
    {
        ArrayList<URL> list = new ArrayList<URL>();
        Set libJars = servletContext.getResourcePaths("/WEB-INF/lib");
        if (libJars == null)
        {
            URL[] empty = {};
            return empty;
        }
        for (Object jar : libJars)
        {
            try
            {
                list.add(servletContext.getResource((String) jar));
            }
            catch (MalformedURLException e)
            {
                throw new RuntimeException(e);
            }
        }
        return list.toArray(new URL[list.size()]);
    }

    public Collection<URL> getScanningUrls(ServletContext servletContext) throws MalformedURLException
    {
        Collection<URL> result = new ArrayList<URL>();
        URL[] urls = findWebInfLibClasspaths(servletContext);
        result.addAll(Arrays.asList( urls));
        URL url = WarUrlFinder.findWebInfClassesPath(servletContext);
        if (url != null)
        {
            result.add(url);
        }
        final URL e = new File("target/classes").getAbsoluteFile().toURI().toURL();
        result.add(e);
        return result;
    }

    public RestApplication(@Context ServletContext context) throws IOException
    {
        final Set<Class<?>> classes = scanWithAnnotation(context);
//        final Set<Class<?>> classes = getFromMetaInf();
        this.classes = Collections.unmodifiableSet(classes);
    }

    public Set<Class<?>> getFromMetaInf() throws IOException
    {
        final HashSet<Class<?>> classes = new HashSet<>();
        final ClassLoader classLoader = getClass().getClassLoader();
        final ServiceInfLoader.LoadingResult loadingResult = ServiceInfLoader.loadClassesFromMetaInfo(classLoader, Provider.class.getCanonicalName(),Path.class.getCanonicalName());
        classes.addAll(loadingResult.getClasses());
        for (Throwable error : loadingResult.getErrors())
        {
            throw new RuntimeException("Error loading Meta-INF" + error);
        }
        return classes;
    }

    public HashSet<Class<?>> scanWithAnnotation(@Context ServletContext context) throws MalformedURLException
    {
        final HashSet<Class<?>> classes = new HashSet<>();
        Collection<URL> urls = getScanningUrls(context);
        AnnotationDB db = new AnnotationDB();
        String[] ignoredPackages = {"org.jboss.resteasy.plugins", "org.jboss.resteasy.annotations", "org.jboss.resteasy.client", "org.jboss.resteasy.specimpl", "org.jboss.resteasy.core", "org.jboss.resteasy.spi", "org.jboss.resteasy.util", "org.jboss.resteasy.mock", "javax.ws.rs"};
        db.setIgnoredPackages(ignoredPackages);
        // only index class annotations as we don't want sub-resources being picked up in the scan
        db.setScanClassAnnotations(true);
        db.setScanFieldAnnotations(false);
        db.setScanMethodAnnotations(false);
        db.setScanParameterAnnotations(false);
        try
        {
            final URL[] urls1 = urls.toArray(new URL[] {});
            db.scanArchives(urls1);
            try
            {
                db.crossReferenceImplementedInterfaces();
                db.crossReferenceMetaAnnotations();
            }
            catch (AnnotationDB.CrossReferenceException ignored)
            {

            }

        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to scan WEB-INF for JAX-RS annotations, you must manually register your classes/resources", e);
        }

        boolean scanProviders= true;
        boolean scanResources= true;
        if (scanProviders) processScannedResources(db, classes, Provider.class.getName());
        if (scanResources) processScannedResources(db, classes, Path.class.getName());
        classes.add(ResteasyMembersInjector.class);
        classes.add(RestExceptionMapper.class);
        classes.add(HttpMethodOverride.class);
        classes.add(JsonParamConverterProvider.class);
        classes.add(JsonStringReader.class);
        classes.add(JsonReader.class);
        classes.add(JsonWriter.class);
        classes.add(PatchReader.class);
        classes.add(OptionsAcceptHeader.class);
        classes.add(JsonStringWriter.class);
        classes.add(XmlWriter.class);
        classes.add(XmlReader.class);
        return classes;
    }

    protected void processScannedResources(AnnotationDB db,  Set<Class<?>> classesToAdd,String name)
    {
        Set<String> classes = new HashSet<String>();
        Set<String> paths = db.getAnnotationIndex().get(name);
        if (paths != null) classes.addAll(paths);
        for (String clazz : classes)
        {
            if (clazz.endsWith("JavaJsonProxy") || clazz.endsWith("GwtJsonProxy"))
            {
                continue;
            }
            Class cls = null;
            try
            {
                // Ignore interfaces and subresource classes
                // Scanning is different than other deployment methods
                // in other deployment methods we don't want to ignore interfaces and subresources as they are
                // application errors
                cls = Thread.currentThread().getContextClassLoader().loadClass(clazz.trim());
                if (cls.isInterface()) continue;
            }
            catch (ClassNotFoundException e)
            {
                throw new RuntimeException(e);
            }

            classesToAdd.add( cls);
        }
    }

    @Override
    public Set<Class<?>> getClasses()
    {
        return classes;
    }
}
