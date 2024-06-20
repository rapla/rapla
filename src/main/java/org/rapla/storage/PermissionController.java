package org.rapla.storage;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
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
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Set;


@Singleton
public class PermissionController
{
    private final Set<PermissionExtension> permissionExtensions;
    StorageOperator operator;

    @Inject
    public PermissionController(Set<PermissionExtension> permissionExtensions, StorageOperator operator)
    {
        super();
        this.permissionExtensions = permissionExtensions;
        this.operator = operator;
    }

    public static boolean isOwner(Ownable classifiable, User user)
    {
        ReferenceInfo<User> ownerId = classifiable.getOwnerRef();
        return ownerId != null && ownerId.isSame(user.getReference());
    }

    public static Collection<Category> getGroupsToAdmin(User user)
    {
        return getGroupsToAdmin( user, true);
    }

    public static Collection<Category> getAdminGroups(User user)
    {
        return getGroupsToAdmin( user, false);
    }

    private static Collection<Category> getGroupsToAdmin(User user, boolean addParent)
    {
        final Collection<Category> result = new ArrayList<>();
        final Collection<Category> groupList = user.getGroupList();
        for ( Category group:groupList)
        {
            final String annotation = group.getAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT);
            if ( annotation != null && annotation.equals("true"))
            {
                final Category adminGroup;
                if (addParent)
                {
                    adminGroup = group.getParent();
                }
                else
                {
                    adminGroup = group;
                }
                Assert.notNull(adminGroup);
                result.add( adminGroup );
            }
        }
        return result;
    }

    public static boolean canAdminGroups(Collection<Category> groups, User user)
    {
        final Collection<Category> adminGroups = getGroupsToAdmin(user);
        final boolean result = groups.stream().allMatch(group -> PermissionController.canAdminGroup(adminGroups, group));
        return result;
    }

    public static boolean canAdminGroup(Collection<Category> adminGroups, Category group)
    {
        for (Category adminGroup : adminGroups)
        {
            if (group.equals(adminGroup) || adminGroup.isAncestorOf(group))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Access method
     */
    private boolean hasAccess(Entity entity, User user, Permission.AccessLevel accessLevel)
    {
        for (PermissionExtension permissionExtension : permissionExtensions)
        {
            final Date start = null;
            final Date end = null;
            final Date today = null;
            if (!permissionExtension.hasAccess(entity, user, accessLevel, start, end, today,false))
            {
                return false;
            }
        }
        return true;
    }

    private boolean hasAccess(Entity entity, User user, final AccessLevel permission, final Date start, final Date end, final Date today,
            final boolean checkOnlyToday)
    {
        for (PermissionExtension permissionExtension : permissionExtensions)
        {
            if (!permissionExtension.hasAccess(entity, user, permission, start, end, today, checkOnlyToday))
            {
                return false;
            }
        }
        return true;
    }


    public boolean canDelete(Entity<?> object, User user)
    {
        return canAdmin( object, user);
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
            if ( isOwner( ownable, user))
            {
                return true;
            }
            // fallback for old entities that dont an owner
            //            if (ownerId == null && object instanceof Classifiable)
            //            {
            //                if (canCreate((Classifiable) object, user))
            //                {
            //                    return true;
            //                }
            //            }
        }
        Class<? extends Entity> type = object.getTypeClass();
        if ( type == Conflict.class)
        {
            return canModifyConflict((Conflict) object, user);
        }

        if (hasAccess(object, user, Permission.EDIT))
        {
            return true;
        }
        return canWriteTemplate(object, user);
    }


    public boolean canAdmin(Entity<?> object, User user)
    {
        if (user.isAdmin())
            return true;
        if (!canModify(object, user))
        {
            return false;
        }
        if (object instanceof Ownable)
        {
            Ownable ownable = (Ownable) object;
            ReferenceInfo<User> ownerId = ownable.getOwnerRef();
            if (ownerId != null && user.getReference().isSame(ownerId))
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
        if (canWriteTemplate( object, user))
        {
            return true;
        }

        if (object instanceof DynamicType)
        {
            return user.isAdmin();
        }

        if (object instanceof Entity)
        {

            return hasAccess(object, user, Permission.ADMIN);
        }

        return false;
    }


    /**
     * Will check all its permission extensions and if one of the extensions do not allow
     * to read the container <code>false</code> is returned.
     */
    private boolean canReadPrivate(Entity container, User user)
    {
        if ( user.isAdmin())
        {
            return true;
        }
        if (container instanceof Ownable && isOwner((Ownable)container, user))
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
            Collection<Permission> permissionList = ((DynamicType)container).getPermissionList();
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
        if (!canReadType(container, user))
        {
            return false;
        }
        final Date start = null;
        final Date end = null;
        final Date today = null;
        final AccessLevel permission = Permission.ALLOCATE_CONFLICTS;
        final boolean checkOnlyToday = false;
        return hasAccess(container, user, permission, start, end, today, checkOnlyToday);
    }

    public boolean hasPermissionToAllocate(User user, Allocatable a)
    {
        Collection<String> groups = UserImpl.getGroupsIncludingParents(user);
        final ReferenceInfo<User> ownerRef = a.getOwnerRef();
        if ( user != null && ownerRef != null && user.getReference().equals(ownerRef))
        {
            return true;
        }
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

    public boolean canReadOnlyInformation(Classifiable entity, User user)
    {
        if (entity instanceof  Ownable && isOwner((Ownable) entity, user))
        {
            return true;
        }
        if (!canReadType( entity, user))
        {
            return false;
        }
        return entity instanceof Entity && hasAccess((Entity) entity, user, Permission.READ_NO_ALLOCATION);
    }

    /**
     *  Checks if the user is allowed to make an allocation in the passed time.
     * @return
     */
    public boolean canAllocate(Allocatable container, User user, Date start, Date end, Date today)
    {
        return hasAccess(container, user, Permission.ALLOCATE, start, end, today, false);
    }

    public boolean canRequest(Allocatable alloc, User user) {
        final Classification classification = alloc.getClassification();
        final Attribute emailAtt = classification.getType().getAttribute("Email");
        if (canRead(alloc, user)  && emailAtt!= null)
        {
            final Object email = classification.getValue("Email");
            if (email != null) {
                final boolean emailSet = !email.toString().trim().isEmpty();
                return emailSet;
            }
        }
        return  false;
    }

    public boolean isRequestOnly(Allocatable alloc, User user, Date today) {
        if (canAllocate( alloc, user, today)) {
            return false;
        }
        return canRequest(alloc, user);
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
        if (!canReadType(container, user))
        {
            return false;
        }
        boolean hasAccess = hasAccess(container, user, Permission.ALLOCATE, null, null, today, true);
        return hasAccess;

    }

    public boolean hasPermissionToAllocate(User user, Appointment appointment, Allocatable allocatable, Reservation original, Date today)
    {
        if (user.isAdmin())
        {
            return true;
        }
        Collection<String> groups = UserImpl.getGroupsIncludingParents(user);

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
            if (originalAppointment == null || !original.hasAllocatedOn(allocatable, originalAppointment))
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

    private boolean hasAccess(Classification objectList, Attribute attribute, User user, AccessLevel edit)
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

    private boolean affectsUser(User user, Permission p, Collection<String> groups)
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

        Collection<String> groups = UserImpl.getGroupsIncludingParents(user);
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
        Date today = operator.today();
        return canAllocate(allocatables, user, start, end, today);
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
            return canModifyEvent(conflict.getReservation2(), user);
        }
        return false;
    }

    private boolean canModifyEvent(ReferenceInfo<Reservation> reservationId, User user)
    {
        EntityResolver resolver = operator;
        Reservation reservation = resolver.tryResolve(reservationId);
        boolean canModify = reservation != null && canModify(reservation, user);
        return canModify;
    }

    private boolean canWriteTemplate(Entity entity, User user)
    {
        EntityResolver resolver = this.operator;
        Class<? extends Entity> type  = entity.getTypeClass();
        if (type == Allocatable.class ||  type == Reservation.class)
        {
            Annotatable annotatable = (Annotatable) entity;
            String templateId = annotatable.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
            if (templateId != null)
            {
                Allocatable template = resolver.tryResolve(templateId, Allocatable.class);
                if (template != null)
                {
                    return canModify(template, user);
                }
            }
        }
        return false;
    }

    public boolean canReadTemplate(Annotatable entity, User user)
    {
        String templateId = entity.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
        EntityResolver resolver = this.operator;
        if (templateId != null)
        {
            Allocatable template = resolver.tryResolve(templateId, Allocatable.class);
            if (template != null)
            {
                return canRead(template, user);
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
        // canRead includes canModify
//        if (canModify(reservation, user))
//        {
//            return true;
//        }
        if (canReadTemplate(reservation, user))
        {
            return true;
        }
        return canReadPrivate(reservation, user);
    }

    public boolean canRead(Allocatable allocatable, User user)
    {
        // canRead includes canModify
//        if (canModify(allocatable, user))
//        {
//            return true;
//        }
        return canReadPrivate(allocatable, user);
    }

    public boolean canWrite(Classification object, Attribute attribute, User user)
    {
        if(user.isAdmin())
        {
            return true;
        }
        return hasAccess(object, attribute, user, Permission.AccessLevel.EDIT);
    }
    
    public boolean canRead(Classification object, Attribute attribute, User user)
    {
        if(user.isAdmin())
        {
            return true;
        }
        return hasAccess(object, attribute, user, Permission.AccessLevel.READ);
    }

    public static boolean canAdminUsers(User workingUser)
    {
        final boolean isAdmin = workingUser.isAdmin();
        final boolean localGroupAdmin = isAdmin || PermissionController.getAdminGroups(workingUser).size() > 0;
        return localGroupAdmin;
    }

    public static boolean canAdminUser(User adminUser, User userToAdmin)
    {
        final boolean isAdmin = adminUser.isAdmin();
        if (isAdmin)
        {
            return true;
        }
        if ( PermissionController.getAdminGroups(adminUser).isEmpty())
        {
            return false;
        }
        if (userToAdmin.isAdmin())
        {
            return false;
        }
        final Collection<Category> adminGroups = PermissionController.getGroupsToAdmin(adminUser);

        if ( adminGroups.size() > 0)
        {
            boolean belongsTo = false;
            for (Category group : adminGroups)
            {
                if (userToAdmin.belongsTo(group))
                {
                    belongsTo = true;
                    break;
                }
            }
            return belongsTo;
        }
        return false;
        // belongsto Could be replaced with a real membership
        //final Collection<Category> groupList = userToAdmin.getGroupList();
        //boolean disjoint =Collections.disjoint( groupList, adminGroups);
        //return !disjoint;
    }
}
