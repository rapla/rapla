package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.rest.RaplaDynamicTypesService;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class RaplaDynamicTypesController implements RaplaDynamicTypesService
{
    private final RemoteSession session;
    private final RaplaFacade facade;
    private final HttpServletRequest request;

    public RaplaDynamicTypesController(RemoteSession session, RaplaFacade facade, HttpServletRequest request)
    {
        this.session = session;
        this.facade = facade;
        this.request = request;
    }

    @Override
    public List<DynamicTypeImpl> list(String classificationType) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        DynamicType[] types = facade.getDynamicTypes(classificationType);
        List<DynamicTypeImpl> result = new ArrayList<>();
        final PermissionController controller = facade.getPermissionController();
        for (DynamicType type : types)
        {
            if (controller.canRead(type, user))
            {
                result.add((DynamicTypeImpl) type);
            }
        }
        return result;
    }
}
