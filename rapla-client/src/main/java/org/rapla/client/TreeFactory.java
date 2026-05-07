package org.rapla.client;

import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface TreeFactory {

	AllocatableNodes createAllocatableModel(ClassificationFilter[] filter) throws RaplaException;

	RaplaTreeNode createClassifiableModel(Allocatable[] classifiables, boolean useCategorizations);

	RaplaTreeNode createConflictModel(Collection<Conflict> conflicts ) throws RaplaException;
	RaplaTreeNode createResourceRequestModel(Collection<Reservation> requests ) throws RaplaException;

    RaplaTreeNode newNamedNode(Named element);

	RaplaTreeNode newStringNode(String s);

	RaplaTreeNode newRootNode();

   RaplaTreeNode newResourceNode();

   RaplaTreeNode newUsersNode() throws RaplaException;

		RaplaTreeNode createModel(Category rootCategory,  Predicate<Category> pattern);

	static Stream<Conflict> getConflicts(RaplaTreeNode treeNode)
	{
		Object userObject = treeNode.getUserObject();
		if (userObject != null && userObject instanceof Conflict)
		{
			return Stream.of( (Conflict) userObject);
		}
		int children = treeNode.getChildCount();
		if (children == 0)
		{
			return Stream.empty();
		}
		final Stream<Conflict> conflictStream = IntStream.range(0, children)
				.mapToObj(treeNode::getChild)
				.flatMap(TreeFactory::getConflicts)
				.distinct()
				;
		return conflictStream;
	}

	static Stream<Reservation> getRequests(RaplaTreeNode treeNode)
	{
		Object userObject = treeNode.getUserObject();
		if (userObject != null && userObject instanceof Reservation)
		{
			return Stream.of( (Reservation) userObject);
		}
		int children = treeNode.getChildCount();
		if (children == 0)
		{
			return Stream.empty();
		}
		final Stream<Reservation> conflictStream = IntStream.range(0, children)
				.mapToObj(treeNode::getChild)
				.flatMap(TreeFactory::getRequests)
				.distinct()
				;
		return conflictStream;
	}

	class AllocatableNodes
	{
		public final RaplaTreeNode allocatableNode;
		public final boolean filtered;

		public AllocatableNodes(RaplaTreeNode allocatableNode, boolean filtered)
		{
			this.allocatableNode = allocatableNode;
			this.filtered = filtered;
		}

		public RaplaTreeNode getAllocatableNode()
		{
			return allocatableNode;
		}

		public boolean isFiltered()
		{
			return filtered;
		}
	}

}