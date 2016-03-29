package org.rapla.server.internal.rest.validator;

import java.lang.reflect.Method;
import java.util.Map;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.validation.GeneralValidator;

import dagger.MembersInjector;

@Provider
public class RaplaRestDaggerContextProvider implements ContextResolver<GeneralValidator>
{
    public static final String RAPLA_CONTEXT = "raplaContext";
    final RestDaggerValidator raplaRestDaggerListener = new RestDaggerValidator();

    @Override
    public GeneralValidator getContext(Class<?> type)
    {
        return raplaRestDaggerListener;
    }

    public static class RestDaggerValidator implements GeneralValidator
    {

        @Override
        public void validate(HttpRequest request, Object object, Class<?>... groups)
        {
            final Object context = request.getAttribute(RAPLA_CONTEXT);
            if (context != null)
            {
                try
                {
                    Map<String, MembersInjector> membersInjector = (Map<String, MembersInjector>) context;
                    final MembersInjector memInj = membersInjector.get(object.getClass().getCanonicalName());
                    memInj.injectMembers(object);
                    //                final Method membersInjectMethod = context.getClass().getDeclaredMethod("");
                    //                final Object membersInjector = membersInjectMethod.invoke(context);
                    //                ((MembersInjector) membersInjector).injectMembers(object);
                }
                catch (Exception e)
                {
                    IllegalStateException newEx = new IllegalStateException("Could not inject dependencies for " + object + ": " + e.getMessage(), e);
                    newEx.printStackTrace();
                    throw newEx;
                }
            }
            //        attribute.getRestExample().injectMembers((RestExample) object);
        }

        @Override
        public void validateAllParameters(HttpRequest request, Object object, Method method, Object[] parameterValues, Class<?>... groups)
        {

        }

        @Override
        public void validateReturnValue(HttpRequest request, Object object, Method method, Object returnValue, Class<?>... groups)
        {

        }

        @Override
        public boolean isValidatable(Class<?> clazz)
        {
            return true;
        }

        @Override
        public boolean isMethodValidatable(Method method)
        {
            return false;
        }

        @Override
        public void checkViolations(HttpRequest request)
        {

        }
    }

}
