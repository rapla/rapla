package org.rapla.server.internal.rest.validator;

import dagger.MembersInjector;

public interface Injector
{
    <T> MembersInjector<T> getMembersInjector(Class<T> t);
}
