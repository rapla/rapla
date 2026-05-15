package org.rapla.server.spring.web;

import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.endpoints.server.RaplaDynamicTypesRestPage;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/api/dynamictypes", produces = "application/json")
public class RaplaDynamicTypesController
{
    private final RaplaDynamicTypesRestPage page;

    public RaplaDynamicTypesController(RaplaDynamicTypesRestPage page)
    {
        this.page = page;
    }

    @GetMapping
    public List<DynamicTypeImpl> list(@RequestParam(value = "classificationType", required = false) String classificationType) throws RaplaException
    {
        return page.list(classificationType);
    }
}
