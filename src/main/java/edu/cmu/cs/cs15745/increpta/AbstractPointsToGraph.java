package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.util.MultiMap;

/**
 * Points-to-graph.
 * @param <Node> The node type.
 * @param <HeapItem> The heap item type.
 */
public class AbstractPointsToGraph<Node, HeapItem> {
	
	// Disallow outside instantiation
	AbstractPointsToGraph() { }
	
	private final MultiMap<Node, Node> graph = new MultiMap<>();
	private final MultiMap<Node, HeapItem> pointsTo = new MultiMap<>();
	
	/**
	 * Add directed edge from "from" to "to", returning whether the edge was
	 * already present.
	 */
	public boolean addEdge(Node from, Node to) {
		return graph.getSet(from).add(to);
	}
	
	public Set<Node> nodes() {
		return graph.keySet();
	}

	public Set<Node> edges(Node from) {
		return graph.getOrDefault(from, Collections.emptySet());
	}

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
}
