package org.rapla.server.internal.rest;

import org.rapla.inject.scanning.RestEasyLoadingFilter;
import org.rapla.inject.scanning.ScanningClassLoader;
import org.rapla.inject.scanning.ServiceInfLoader;

import javax.servlet.ServletContext;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class RestApplication extends Application
{
    private Set<Class<?>> classes;

    public RestApplication(@Context ServletContext context) throws IOException
    {
        Collection<Class<? extends Annotation>> classList = new ArrayList<>();
        classList.add(Path.class);
        classList.add(Provider.class);
        ScanningClassLoader classLoader = new ServiceInfLoader();
        final RestEasyLoadingFilter filter = new RestEasyLoadingFilter();
        final ScanningClassLoader.LoadingResult loadingResult = classLoader.loadClasses(filter, classList);
        this.classes = Collections.unmodifiableSet(loadingResult.getClasses());
    }

    @Override
    public Set<Class<?>> getClasses()
    {
        return classes;
    }
}
