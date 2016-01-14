package org.rapla.storage;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class PermissionController
{
    private final Set<PermissionExtension> permissionExtensions;
    StorageOperator operator;

    public PermissionController(Set<PermissionExtension> permissionExtensions, StorageOperator operator)
    {
        super();
        this.permissionExtensions = permissionExtensions;
        this.operator = operator;
    }

    public static boolean isOwner(Ownable classifiable, User user)
    {
        String ownerId = classifiable.getOwnerId();
        return ownerId != null && ownerId.equals(user.getId());
    }

    /**
     * Access method
     */
    public boolean hasAccess(PermissionContainer container, User user, Permission.AccessLevel accessLevel)
    {
        for (PermissionExtension permissionExtension : permissionExtensions)
        {
            if (!permissionExtension.hasAccess(container, user, accessLevel))
            {
                return false;
            }
        }
        return true;
    }

    private boolean canModifyPrivate(PermissionContainer container, User user)
    {
        if (container instanceof DynamicType)
        {
            return user.isAdmin();
        }
        return hasAccess(container, user, Permission.EDIT);
    }

    public boolean canAdmin(Entity<?> object, User user)
    {
        if (!canModify(object, user))
        {
            return false;
        }
        if (user.isAdmin())
            return true;
        if (object instanceof Ownable)
        {
            Ownable ownable = (Ownable) object;
            String ownerId = ownable.getOwnerId();
            if (ownerId != null && user.getId().equals(ownerId))
            {
                return true;
            }
            if (ownerId == null && object instanceof Allocatable)
            {
                if (canCreate((Allocatable) object, user))
                {
                    return true;
                }
            }
        }
        if (object instanceof PermissionContainer)
        {
            PermissionContainer permissionContainer = (PermissionContainer) object;
            if (permissionContainer instanceof DynamicType)
            {
                return user.isAdmin();
            }
            return hasAccess(permissionContainer, user, Permission.ADMIN);
        }
        return object instanceof Annotatable && canWriteTemplate((Annotatable) object, user);
    }


    /**
     * Will check all its permission extensions and if one of the extensions do not allow
     * to read the container <code>false</code> is returned.
     */
    private boolean canReadPrivate(PermissionContainer container, User user)
    {
        if (isOwner(container, user))
        {
            return true;
        }
        if (container instanceof Classifiable)
        {
            if (!canReadType((Classifiable) container, user))
            {
                return false;
            }
        }
        if (container instanceof DynamicType)
        {
            Collection<Permission> permissionList = container.getPermissionList();
            boolean result = matchesAccessLevel(permissionList, user, Permission.READ_TYPE, Permission.CREATE, Permission.ADMIN);
            return result;
        }
        else
        {
            return hasAccess(container, user, Permission.READ);
        }
    }

    public boolean canCreate(Classifiable classifiable, User user)
    {
        DynamicType type = classifiable.getClassification().getType();
        return canCreate(type, user);
    }

    public boolean canCreate(DynamicType type, User user)
    {
        Collection<Permission> permissionList = type.getPermissionList();
        boolean result = matchesAccessLevel(permissionList, user, Permission.CREATE, Permission.ADMIN);
        return result;
    }

    public boolean canRead(DynamicType type, User user)
    {
        return canReadPrivate( type, user);
    }

    public boolean canCreateConflicts(Allocatable container, User user)
    {
        Collection<Permission> permissions = container.getPermissionList();
        if (!canReadType(container, user))
        {
            return false;
        }
        final Date start = null;
        final Date end = null;
        final Date today = null;
        final AccessLevel permission = Permission.ALLOCATE_CONFLICTS;
        final boolean checkOnlyToday = false;
        return hasAccess(permissions, user, permission, start, end, today, checkOnlyToday);
    }

    public boolean hasPermissionToAllocate(User user, Allocatable a)
    {
        Collection<Category> groups = getGroupsIncludingParents(user);
        for (Permission p : a.getPermissionList())
        {
            if (!affectsUser(user, p, groups))
            {
                continue;
            }
            if (p.getAccessLevel().includes(Permission.ALLOCATE))
            {
                return true;
            }
        }
        return false;
    }

    public boolean canReadOnlyInformation(Allocatable classifiable, User user)
    {
        if (!canReadType(classifiable, user))
        {
            return false;
        }
        if (isOwner(classifiable, user))
        {
            return true;
        }
        return hasAccess(classifiable, user, Permission.READ_NO_ALLOCATION);
    }

    /**
     *  Checks if the user is allowed to make an allocation in the passed time.
     * @return
     */
    public boolean canAllocate(Allocatable container, User user, Date start, Date end, Date today)
    {
        Collection<Permission> permissions = container.getPermissionList();
        return hasAccess(permissions, user, Permission.ALLOCATE, start, end, today, false);
    }

    /**
     *  Checks if the user is allowed to make an allocation in the future (starting with date today)
     * @param container
     * @param user
     * @param today
     * @return
     */
    public boolean canAllocate(Allocatable container, User user, Date today)
    {
        Collection<Permission> permissions = container.getPermissionList();
        if (!canReadType(container, user))
        {
            return false;
        }
        boolean hasAccess = hasAccess(permissions, user, Permission.ALLOCATE, null, null, today, true);
        return hasAccess;

    }

    public boolean hasPermissionToAllocate(User user, Appointment appointment, Allocatable allocatable, Reservation original, Date today)
    {
        if (user.isAdmin())
        {
            return true;
        }
        Collection<Category> groups = getGroupsIncludingParents(user);

        Date start = appointment.getStart();
        Date end = appointment.getMaxEnd();

        for (Permission p : allocatable.getPermissionList())
        {
            Permission.AccessLevel accessLevel = p.getAccessLevel();
            if ((!affectsUser(user, p, groups)) || accessLevel.excludes(Permission.READ))
            {
                continue;
            }

            if (accessLevel == Permission.ADMIN)
            {
                // user has the right to allocate
                return true;
            }

            if (accessLevel.includes(Permission.ALLOCATE) && p.covers(start, end, today))
            {
                return true;
            }
            if (original == null)
            {
                continue;
            }

            // We must check if the changes of the existing appointment
            // are in a permisable timeframe (That should be allowed)

            // 1. check if appointment is old,
            // 2. check if allocatable was already assigned to the appointment
            Appointment originalAppointment = original.findAppointment(appointment);
            if (originalAppointment == null || !original.hasAllocated(allocatable, originalAppointment))
            {
                continue;
            }

            // 3. check if the appointment has changed during
            // that time
            if (appointment.matches(originalAppointment))
            {
                return true;
            }
            if (accessLevel.includes(Permission.ALLOCATE))
            {
                Date maxTime = DateTools.max(appointment.getMaxEnd(), originalAppointment.getMaxEnd());
                if (maxTime == null)
                {
                    maxTime = DateTools.addYears(today, 4);
                }

                Date minChange = appointment.getFirstDifference(originalAppointment, maxTime);
                Date maxChange = appointment.getLastDifference(originalAppointment, maxTime);
                //System.out.println ( "minChange: " + minChange + ", maxChange: " + maxChange );

                if (p.covers(minChange, maxChange, today))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAccess(Collection<? extends Classification> objectList, Attribute attribute, User user, AccessLevel edit)
    {
        for (PermissionExtension permissionExtension : permissionExtensions)
        {
            if (!permissionExtension.hasAccess(objectList, attribute, user, edit))
            {
                return false;
            }
        }
        return true;
    }

    private boolean hasAccess(Collection<Permission> permissions, User user, final AccessLevel permission, final Date start, final Date end, final Date today,
            final boolean checkOnlyToday)
    {
        for (PermissionExtension permissionExtension : permissionExtensions)
        {
            if (!permissionExtension.hasAccess(permissions, user, permission, start, end, today, checkOnlyToday))
            {
                return false;
            }
        }
        return true;
    }

    private boolean affectsUser(User user, Permission p, Collection<Category> groups)
    {
        int userEffect = PermissionContainer.Util.getUserEffect(user, p, groups);
        return userEffect > PermissionImpl.NO_PERMISSION;
    }

    private boolean canReadType(Classifiable classifiable, User user)
    {
        Classification classification = classifiable.getClassification();
        if (classification != null)
        {
            DynamicType type = classification.getType();
            return canRead(type, user);
        }
        return true;
    }

    private boolean matchesAccessLevel(Iterable<? extends Permission> permissions, User user, AccessLevel... accessLevels)
    {
        if (user == null || user.isAdmin())
            return true;

        Collection<Category> groups = getGroupsIncludingParents(user);
        for (Permission p : permissions)
        {
            for (AccessLevel accessLevel : accessLevels)
            {
                if (p.getAccessLevel() == accessLevel)
                {
                    int effectLevel = PermissionContainer.Util.getUserEffect(user, p, groups);
                    if (effectLevel > PermissionImpl.NO_PERMISSION)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Collection<Category> getGroupsIncludingParents(User user)
    {
        Collection<Category> groups = new HashSet<Category>();
        for (Category group : user.getGroupList())
        {
            groups.add(group);
            Category parent = group.getParent();
            while (parent != null)
            {
                if (parent == group)
                {
                    throw new IllegalStateException("Parent added to own child");
                }
                if (parent == null || parent.getParent() == null || parent.getKey().equals("user-groups"))
                {
                    break;
                }
                if (!groups.contains(parent))
                {
                    groups.add(parent);
                }
                parent = parent.getParent();

            }
        }
        return groups;
    }

    /** returns if the session user is a registerer */
    final public boolean isRegisterer(DynamicType type, User user)
    {
        if (user.isAdmin())
        {
            return true;
        }
        try
        {
            if (type == null)
            {
                for (DynamicType type1 : operator.getDynamicTypes())
                {
                    final String annotation = type1.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                    if ((annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)  || annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)) && canCreate(type1, user))
                    {
                        return true;
                    }
                }
                return false;
            }
            else
            {
                boolean result = canCreate(type, user);
                return result;
            }
        }
        catch (RaplaException ex)
        {
            return false;
        }
    }

    /** returns if the user has allocation rights for one or more resource */
    final public boolean canUserAllocateSomething(User user) throws RaplaException
    {
        if (user.isAdmin())
            return true;
        if (!canCreateReservation(user))
        {
            return false;
        }
        Collection<Allocatable> allocatables = operator.getAllocatables(null);
        for (Allocatable a : allocatables)
        {
            if (hasPermissionToAllocate(user, a))
            {
                return true;
            }
        }
        return false;
    }

    final public boolean canCreateReservation(User user)
    {
        try
        {
            for (DynamicType type1 : operator.getDynamicTypes())
            {
                final String annotation = type1.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                if (annotation.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION) && canCreate(type1, user))
                {
                    return true;
                }
            }
        }
        catch (RaplaException e)
        {
            return false;
        }
        return false;
    }

    public boolean canAllocate(Date start, Date end, Allocatable allocatables, User user)
    {
        if (allocatables == null)
        {
            return true;
        }
        try
        {
            Date today = operator.today();
            return canAllocate(allocatables, user, start, end, today);
        }
        catch (RaplaException ex)
        {
            return false;
        }
    }

    private boolean canModifyConflict(Conflict conflict, User user)
    {
        Allocatable allocatable = conflict.getAllocatable();
        if (user == null || user.isAdmin())
        {
            return true;
        }
        if (canRead(allocatable, user))
        {
            if (canModifyEvent(conflict.getReservation1(), user))
            {
                return true;
            }
            if (canModifyEvent(conflict.getReservation2(), user))
            {
                return true;
            }
        }
        return false;
    }

    private boolean canModifyEvent(String reservationId, User user)
    {
        EntityResolver resolver = operator;
        Reservation reservation = resolver.tryResolve(reservationId, Reservation.class);
        boolean canModify = reservation != null && canModify(reservation, user);
        return canModify;
    }

    public boolean canModify(Entity<?> object, User user)
    {
        if (object == null )
        {
            return false;
        }
        if (user == null)
        {
            return false;
        }
        if (user.isAdmin())
            return true;
        if (object instanceof Ownable)
        {
            Ownable ownable = (Ownable) object;
            String ownerId = ownable.getOwnerId();
            if (ownerId != null && user.getId().equals(ownerId))
            {
                return true;
            }
            if (ownerId == null && object instanceof Classifiable)
            {
                if (canCreate((Classifiable) object, user))
                {
                    return true;
                }
            }
        }
        if ( object instanceof Conflict)
        {
            return canModifyConflict((Conflict) object, user);
        }
        if (object instanceof PermissionContainer)
        {
            PermissionContainer permissionContainer = (PermissionContainer) object;
            if (canModifyPrivate(permissionContainer, user))
            {
                return true;
            }
        }
        return object instanceof Annotatable && canWriteTemplate((Annotatable) object, user);
    }

    public boolean canWriteTemplate(Annotatable entity, User user)
    {
        EntityResolver resolver = this.operator;
        String templateId = entity.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, null);
        if (templateId != null)
        {
            Allocatable template = resolver.tryResolve(templateId, Allocatable.class);
            if (template != null)
            {
                if (canModify(template, user))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canReadTemplate(Annotatable entity, User user)
    {
        String templateId = entity.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, null);
        EntityResolver resolver = this.operator;
        if (templateId != null)
        {
            Allocatable template = resolver.tryResolve(templateId, Allocatable.class);
            if (template != null)
            {
                if (canRead(template, user))
                {
                    return true;
                }
            }
        }
        return false;
    }


    public boolean canRead(Appointment appointment, User user)
    {
        Reservation reservation = appointment.getReservation();
        boolean result = canRead(reservation, user);
        return result;
    }

    public boolean canRead(Reservation reservation, User user)
    {
        if (user == null)
        {
            return true;
        }
        if (canModify(reservation, user))
        {
            return true;
        }
        if (canReadTemplate(reservation, user))
        {
            return true;
        }
        return canReadPrivate(reservation, user);
    }

    public boolean canRead(Allocatable allocatable, User user)
    {
        if (canModify(allocatable, user))
        {
            return true;
        }
        return canReadPrivate(allocatable, user);
    }

    public boolean canWrite(Collection<? extends Classification> objectList, Attribute attribute, User user)
    {
        if(user.isAdmin())
        {
            return true;
        }
        return hasAccess(objectList, attribute, user, Permission.AccessLevel.EDIT);
    }
    
    public boolean canRead(Collection<? extends Classification> objectList, Attribute attribute, User user)
    {
        if(user.isAdmin())
        {
            return true;
        }
        return hasAccess(objectList, attribute, user, Permission.AccessLevel.READ);
    }

}
