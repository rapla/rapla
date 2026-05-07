package org.rapla.facade.internal;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ModificationEvent;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ModificationEventImpl implements ModificationEvent
{
    private TimeInterval timeInterval;
    private boolean switchTemplateMode = false;
    private final Set<ReferenceInfo> removedReferences = new LinkedHashSet<>();
    private final Set<Entity> added = new LinkedHashSet<>();
    private final Set<Entity> changed = new LinkedHashSet<>();
    private final Set<Class<? extends Entity>> modified = new LinkedHashSet<>();
    public ModificationEventImpl()
    {

    }

    public ModificationEventImpl(UpdateResult updateResult, TimeInterval timeInterval)
    {
        this.timeInterval = timeInterval;
        final Iterable<UpdateOperation> operations = updateResult.getOperations();
        for (UpdateOperation op : operations)
        {
            final Class<? extends Entity> typeClass = op.getType();
            modified.add(typeClass);
            if(op instanceof UpdateResult.Remove)
            {
                removedReferences.add(op.getReference());
            }
            else if(op instanceof UpdateResult.Change)
            {
                changed.add(updateResult.getLastKnown(op.getReference()));
            }
            else if(op instanceof UpdateResult.Add)
            {
                added.add(updateResult.getLastKnown(op.getReference()));
            }
        }
    }

    public boolean hasChanged(Entity object) {
        return getChanged().contains(object);
    }

    public boolean isRemoved(Entity object) {
        final ReferenceInfo referenceInfo = new ReferenceInfo(object);
        return getRemovedReferences().contains( referenceInfo);
    }

    public boolean isModified(Entity object)
    {
        return hasChanged(object) || isRemoved( object);
    }

    /** returns the modified objects from a given set.
     * @deprecated use the retainObjects instead in combination with getChanged*/
    public <T extends RaplaObject> Set<T> getChanged(Collection<T> col) {
        return RaplaType.retainObjects(getChanged(),col);
    }

    //    /** returns the modified objects from a given set.
    //     * @deprecated use the retainObjects instead in combination with getChanged*/
    //    public <T extends RaplaObject> Set<T> getRemoved(Collection<T> col) {
    //        return RaplaType.retainObjects(getRemoved(),col);
    //    }

    public Set<Entity> getChanged() {
        Set<Entity> result  = new HashSet<>(getAddObjects());
        result.addAll(getChangeObjects());
        return result;
    }


//    protected <T extends UpdateOperation> Set<Entity> getObject( final Class<T> operationClass ) {
//        Set<Entity> set = new HashSet<Entity>();
//        if ( operationClass == null)
//            throw new IllegalStateException( "OperationClass can't be null" );
//        Collection<? extends UpdateOperation> it= getOperations( operationClass);
//        for (UpdateOperation next:it ) {
//            String currentId =next.getCurrentId();
//            final Entity current = getLastKnown(currentId);
//            set.add( current);
//        }
//        return set;
//    }

    public Set<ReferenceInfo> getRemovedReferences()
    {
        return removedReferences;
    }

    public Set<Entity> getChangeObjects() {
        return changed;
    }

    public Set<Entity> getAddObjects() {
        return added;
    }


    public boolean isModified(Class<? extends Entity> raplaType)
    {
        return modified.contains( raplaType) ;
    }

    public boolean isModified() {
        return !removedReferences.isEmpty() || !changed.isEmpty() || !added.isEmpty() || switchTemplateMode;
    }

    public boolean isEmpty() {
        return !isModified() && timeInterval == null;
    }


    public void setInvalidateInterval(TimeInterval timeInterval)
    {
        this.timeInterval = timeInterval;
    }

    public TimeInterval getInvalidateInterval()
    {
        return timeInterval;
    }


    public void setSwitchTemplateMode(boolean b)
    {
        switchTemplateMode = b;
    }

    public boolean isSwitchTemplateMode() {
        return switchTemplateMode;
    }

    public void addChanged(Entity changed)
    {
        this.changed.add(changed);
        modified.add( changed.getTypeClass());
    }
}
