package edu.cmu.cs.cs15745.increpta;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Util;

/**
 * Points-to-graph that also maintains points-to information at each node.
 * This points-to graph is not "smart", i.e. it does not automatically update
 * HeapItems when a new edge is added. For an implementation of PointsToGraph
 * that does this, 
 * @param <Node> The node type.
 * @param <HeapItem> The heap item type.
 */
public class SimplePointsToGraph<Node, HeapItem> implements PointsToGraph<Node, HeapItem> {
	
	private final MultiMap<Node, Node> graph;
	private final MultiMap<Node, HeapItem> pointsTo;
	private final Set<Node> nodes;

	// Disallow outside instantiation
	SimplePointsToGraph() {
		this(new MultiMap<>(), new MultiMap<>(), new LinkedHashSet<>());
	}

	SimplePointsToGraph(MultiMap<Node, Node> graph, MultiMap<Node, HeapItem> pointsTo, Set<Node> nodes) {
		this.graph = graph;
		this.pointsTo = pointsTo;
		this.nodes = nodes;
	}
	
	/**
	 * Add directed edge from "from" to "to".
	 */
	@Override
	public Set<Node> addEdge(Node from, Node to) {
		nodes.add(from);
		nodes.add(to);
		if (graph.getSet(from).add(to)) {
			return Set.of(to);
		} else {
			return Set.of();
		}
	}
	
	@Override
	public Set<Node> deleteEdge(Node from, Node to) {
		if (graph.getSet(from).remove(to)) {
			return Set.of(to);
		} else {
			return Set.of();
		}
	}
	
	/** Returns unmodifiable set. */
	@Override
	public Set<Node> nodes() {
		return Collections.unmodifiableSet(nodes);
	}

	/** Returns unmodifiable set */
	@Override
	public Set<Node> edges(Node from) {
		return Collections.unmodifiableSet(graph.getOrDefault(from, Collections.emptySet()));
	}

	@Override
	public Set<HeapItem> pointsTo(Node key) {
		return pointsTo.getSet(key);
	}
			
	/**
	 * Clone. Don't care about Cloneable.
	 */
	public SimplePointsToGraph<Node, HeapItem> clone() {
		return new SimplePointsToGraph<>(new MultiMap<>(graph), new MultiMap<>(pointsTo), new LinkedHashSet<>(nodes));
	}
	
	@Override
	public String toString() {
		return String.format("Graph:\n\t%s\n\nPoints-to:\n\t%s", Util.join("\n\t", graph.entrySet()), Util.join("\n\t", pointsTo.entrySet()));
	}
}
