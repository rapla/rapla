package org.rapla.rest;

import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * REST contract for {@code /api/dynamictypes}. Single source of truth for
 * path/verb/params; the matching {@code RaplaDynamicTypesController} in
 * rapla-server implements it.
 */
@HttpExchange("/api/dynamictypes")
public interface RaplaDynamicTypesService
{
    @GetExchange
    List<DynamicTypeImpl> list(@RequestParam(value = "classificationType", required = false) String classificationType) throws RaplaException;
}
