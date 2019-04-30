package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.util.MultiMap;

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
		this(new MultiMap<>(), new MultiMap<>(), new HashSet<>());
	}

	SimplePointsToGraph(MultiMap<Node, Node> graph, MultiMap<Node, HeapItem> pointsTo, Set<Node> nodes) {
		this.graph = graph;
		this.pointsTo = pointsTo;
		this.nodes = nodes;
	}
	
	/**
	 * Add directed edge from "from" to "to", returning whether the edge was
	 * already present. Just used internally when constructing the graph.
	 */
	@Override
	public boolean addEdge(Node from, Node to) {
		nodes.add(from);
		nodes.add(to);
		return graph.getSet(from).add(to);
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
			
	public interface DfsIterator<Node> extends Iterator<Node> {
		void abandonBranch();
	}

	// Iterator that allows for a dfs branch to be abandoned
	public DfsIterator<Node> dfs(Node from) {
		Set<Node> seen = new HashSet<>();
		Deque<Node> toVisit = new ArrayDeque<>();
		toVisit.add(from);
		seen.add(from);
		return new DfsIterator<>() {
			Node curr = null;
			@Override public boolean hasNext() {
				if (!toVisit.isEmpty()) {
					return true;
				}
				if (curr == null) {
					return false;
				}
				for (Node to : edges(curr)) {
					if (!seen.contains(to)) {
						return true;
					}
				}
				return false;
			}
			@Override public Node next() {
				if (curr != null) {
					for (Node to : edges(curr)) {
						if (seen.add(to)) {
							toVisit.push(to);
						}
					}
				}
				curr = toVisit.pop();
				return curr;
			}
			@Override public void abandonBranch() {
				curr = null;
			}
		};
	}
	
	/**
	 * Clone. Don't care about Cloneable.
	 */
	public SimplePointsToGraph<Node, HeapItem> clone() {
		return new SimplePointsToGraph<>(new MultiMap<>(graph), new MultiMap<>(pointsTo), new HashSet<>(nodes));
	}
}
