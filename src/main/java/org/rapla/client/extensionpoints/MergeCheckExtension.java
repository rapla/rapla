package org.rapla.client.extensionpoints;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.util.Collection;

@ExtensionPoint(context=InjectionContext.client, id=MergeCheckExtension.ID)
public interface MergeCheckExtension
{
    String ID = "org.rapla.extension.merge.check";
    <T extends Allocatable> void precheckAllocatableSelection(Collection<T> allocatablesSelected) throws RaplaException;
    
    <T extends Allocatable> void postcheckAllocatableSelection(T allocatable, Collection<ReferenceInfo<Allocatable>> otherIds) throws RaplaException;
}
