package org.rapla.storage;

import java.util.Map;
import java.util.Set;

import javax.jws.WebParam;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.ResultType;

@RemoteJsonMethod
public interface RemoteLocaleService 
{
	@ResultType(LocalePackage.class) FutureResult<LocalePackage> locale(@WebParam(name = "id") String id, @WebParam(name = "locale") String locale);

	@ResultType(Map.class) FutureResult<Map<String, Set<String>>> countries(Set<String> languages);
}
