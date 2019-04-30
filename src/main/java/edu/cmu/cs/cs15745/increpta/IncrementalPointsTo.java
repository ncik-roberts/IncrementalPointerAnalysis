package edu.cmu.cs.cs15745.increpta;

import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node;
import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node.HeapItem;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Build and incrementally update SCCs for a graph.
 * Type alias for SCCBuilder.Abstract<Pair<Node, C>>.
 */
public final class IncrementalPointsTo<C> extends AbstractIncrementalPointsTo<Pair<Node, C>, Pair<HeapItem, C>> {
	
	public IncrementalPointsTo(PointsToGraph<C> graph) {
		super(graph);
	}
	
}