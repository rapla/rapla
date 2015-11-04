package org.rapla.storage;

import java.util.Map;
import java.util.Set;

import javax.jws.WebParam;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface RemoteLocaleService 
{
	FutureResult<LocalePackage> locale(@WebParam(name = "id") String id, @WebParam(name = "locale") String locale);

	FutureResult<Map<String, Set<String>>> countries(Set<String> languages);
}
