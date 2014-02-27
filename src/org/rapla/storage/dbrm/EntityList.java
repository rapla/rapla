package org.rapla.storage.dbrm;

import java.util.ArrayList;
import java.util.Collection;

import org.rapla.entities.Entity;

public class EntityList extends ArrayList<Entity> {

	private static final long serialVersionUID = 1L;
	long repositoryVersion;
	public EntityList(Collection<? extends Entity> list, long repositoryVersion) {
		super(list);
		this.repositoryVersion = repositoryVersion;
	}

	public long getRepositoryVersion() {
        return repositoryVersion;
    }

}
