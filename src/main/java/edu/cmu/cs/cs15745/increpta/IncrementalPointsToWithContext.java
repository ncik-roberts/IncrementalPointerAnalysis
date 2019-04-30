package edu.cmu.cs.cs15745.increpta;

import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node.HeapItem;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Build and incrementally update SCCs for a graph.
 * Type alias for SCCBuilder.Abstract<Pair<Node, C>>.
 */
public final class IncrementalPointsToWithContext<C> extends IncrementalPointsTo<Pair<Node, C>, Pair<HeapItem, C>> {
	
	public IncrementalPointsToWithContext(PointsToGraph<Pair<Node, C>, Pair<HeapItem, C>> graph) {
		super(graph);
	}
	
}