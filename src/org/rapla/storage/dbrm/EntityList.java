package org.rapla.storage.dbrm;

import java.util.ArrayList;
import java.util.Collection;

import org.rapla.entities.storage.RefEntity;

public class EntityList extends ArrayList<RefEntity<?>>  {

	private static final long serialVersionUID = 1L;
	long repositoryVersion;
	public EntityList(Collection<? extends RefEntity<?>> list, long repositoryVersion) {
		super(list);
		this.repositoryVersion = repositoryVersion;
	}

	public long getRepositoryVersion() {
        return repositoryVersion;
    }

}
