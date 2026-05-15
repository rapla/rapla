package org.rapla.server.spring.web;

import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.endpoints.server.RaplaResourcesRestPage;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/api/resources", produces = "application/json")
public class RaplaResourcesController
{
    private final RaplaResourcesRestPage page;

    public RaplaResourcesController(RaplaResourcesRestPage page)
    {
        this.page = page;
    }

    @GetMapping
    public List<AllocatableImpl> list(@RequestParam(value = "resourceTypes", required = false) List<String> resourceTypes,
                                       @RequestParam(value = "attributeFilter", required = false) Map<String, String> attributeFilter) throws RaplaException
    {
        return page.list(resourceTypes != null ? resourceTypes : Collections.emptyList(),
                attributeFilter != null ? attributeFilter : Collections.emptyMap());
    }

    @GetMapping("/{id}")
    public AllocatableImpl get(@PathVariable("id") String id) throws RaplaException
    {
        return page.get(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) throws RaplaException
    {
        page.delete(id);
    }

    @PutMapping
    public AllocatableImpl update(@RequestBody AllocatableImpl resource) throws RaplaException
    {
        return page.update(resource);
    }

    @PostMapping
    public AllocatableImpl create(@RequestBody AllocatableImpl resource) throws RaplaException
    {
        return page.create(resource);
    }
}
