package org.rapla.storage;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

import javax.jws.WebParam;
import java.util.Map;
import java.util.Set;

@RemoteJsonMethod
public interface RemoteLocaleService 
{
	FutureResult<LocalePackage> locale(@WebParam(name = "id") String id, @WebParam(name = "locale") String locale);

	FutureResult<Map<String, Set<String>>> countries(Set<String> languages);
}
