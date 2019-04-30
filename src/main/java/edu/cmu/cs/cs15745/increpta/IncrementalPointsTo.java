package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.util.MultiMap;

/**
 * Class for building and incrementally updating SCCs.
 *
 * @param <Node> The type of node in the points-to graph.
 */
public class IncrementalPointsTo<Node, HeapItem> {

	private final PointsToGraph<Node, HeapItem> graph;
	private final Map<Node, SCC> sccs = new HashMap<>();
	private final MultiMap<SCC, SCC> edgesForSCC = new MultiMap<>();
	private final MultiMap<SCC, SCC> reverseEdgesForSCC = new MultiMap<>();
	public IncrementalPointsTo(PointsToGraph<Node, HeapItem> graph) {
		this.graph = graph;
	}
	
	private boolean alreadyRun = false;
	/**
	 * Runs Tarjan's algorithm, collecting strongly-connected components.
	 */
	public Graph build() {
		if (alreadyRun) {
			throw new IllegalStateException("Already run.");
		}
		alreadyRun = true;
		// Update which scc each elem belongs to
		var elems = tarjan(graph.nodes());
		for (SCC scc : elems) {
			for (var elem : scc.elems) {
				sccs.put(elem, scc);
			}
		}
		calculateEdgesForSCCs(elems);
		return new Graph();
	}
	
	// Wrapper class for object identity :)
	private class SCC {
		private final Node rep; // equiv class representative
		private final Set<Node> elems;
		private SCC(Node rep, Set<Node> elems) {
			this.rep = rep;
			this.elems = elems;
		}
	}
	
	/**
	 * Points-to-graph supporting incremental add and delete
	 */
	public class Graph implements PointsToGraph<Node, HeapItem> {

		@Override
		public boolean addEdge(Node from, Node to) {
			updateSCCsAdd(from, to);
			return graph.addEdge(from, to);
		}

		@Override
		public Set<Node> nodes() {
			return graph.nodes();
		}

		@Override
		public Set<Node> edges(Node node) {
			Set<Node> result = new HashSet<>();
			var edges = edgesForSCC.getSet(sccs.get(node));
			for (var e : edges) {
				result.add(e.rep);
			}
			return result;
		}

		@Override
		public Set<HeapItem> pointsTo(Node key) {
			return graph.pointsTo(key);
		}
	}
	
	/** Incrementally update SCC based on add of edge. */
	void updateSCCsAdd(Node from, Node to) {
		SCC sccFrom = Objects.requireNonNull(sccs.get(from));
		SCC sccTo = Objects.requireNonNull(sccs.get(to));
		if (sccFrom.equals(sccTo)) { // Same instance?
			return; // We don't need to update anything
		} else if (edgesForSCC.getSet(sccFrom).contains(sccTo)){ // If we're already pointing to,
			return;
		} else {
			// Add new edge
			edgesForSCC.getSet(sccFrom).add(sccTo);
			
			// DFS from To to see if we can get to From.
			var path = path(sccTo, sccFrom, new HashSet<>());
			if (path != null) {
				// Then we should merge the SCCs.
				var superSCC = sccs.get(from); // Merge into "from"
				for (var scc : path) {
					superSCC.elems.addAll(scc.elems);
					
					// Update edges and reverse edges for scc
					// (scc, a)
					for (var a : edgesForSCC.remove(scc)) {
						var edgesA = reverseEdgesForSCC.getSet(a);
						edgesA.remove(scc);
						edgesA.add(superSCC);
					}

					// (a, scc)
					for (var a : reverseEdgesForSCC.remove(scc)) {
						var edgesA = edgesForSCC.getSet(a);
						edgesA.remove(scc);
						edgesA.add(superSCC);
					}
				}
			}
		}
	}
	
	/** Incrementally update SCC based on delete of edge. */
	void updateSCCsDelete(Node from, Node to) {
		SCC sccFrom = Objects.requireNonNull(sccs.get(from));
		SCC sccTo = Objects.requireNonNull(sccs.get(to));
		if (!sccFrom.equals(sccTo)) { // Different SCC, deletion does nothing.
			edgesForSCC.getSet(sccFrom).remove(sccTo);
			return;
		} else {
			SCC scc = sccTo; // or sccFrom, they're the same
			List<SCC> afterDelete = tarjan(scc.elems);
			if (afterDelete.size() == 1) {
				// We're good, no need to update anything.
				return;
			} else { // Otherwise, we broke into multiple sccs.
				for (SCC newScc : afterDelete) {
					for (var elem : scc.elems) {
						// Create "updated" as well so we can calculate edges for only the right sccs
						sccs.put(elem, newScc);
					}
				}
				calculateEdgesForSCCs(afterDelete);

				// Now we just need to: update stale references in edgesForSCCs and reverseEdgesForSCCs
				// For each edge (scc, A) removed from edges, remove (A, scc) from reverseEdges.
				for (var toRemove : edgesForSCC.remove(scc)) {
					reverseEdgesForSCC.getSet(toRemove).remove(scc);
				}
				
				// For each edge (scc, A) removed from reversed edges, remove (A, scc) from edges.
				var as = reverseEdgesForSCC.remove(scc);
				for (var a : as) {
					edgesForSCC.getSet(a).remove(scc);
				}
				
				// Recalculate edges for each node A, in case they go to the newly-created SCCs
				calculateEdgesForSCCs(as);
			}
		}
	}
	// For each rep, find all edges in SCC to other SCCs.
	// Remove old values at those keys.
	private void calculateEdgesForSCCs(Iterable<SCC> newSCCs) {
		for (var scc : newSCCs) {
			var toAddTo = edgesForSCC.getSet(scc);
			for (var node : scc.elems) { 
				for (var to : graph.edges(node)) {
					// Only add edges that go outside of scc
					if (!scc.elems.contains(to)) {
						var rep = sccs.get(to);
						toAddTo.add(rep);
						reverseEdgesForSCC.getSet(rep).add(scc);
					}
				}
			}
		}
	}
	
	private class TarjanVertex {
		int index = -1;
		int lowlink = -1;
		boolean onStack = false;
		final Node data;
		final Set<Node> edgesTo;
		TarjanVertex(Node data, Set<Node> edgesTo) {
			this.data = data;
			this.edgesTo = edgesTo;
		}
	}

	// See https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
	private final Deque<TarjanVertex> S = new ArrayDeque<>();
	private int index = 0;
	private List<SCC> tarjan(Iterable<Node> vs) {
		List<SCC> out = new ArrayList<>();
		Map<Node, TarjanVertex> V = new HashMap<>();
		for (var v : vs) {
			var edges = graph.edges(v);
			V.put(v, new TarjanVertex(v, edges));
		}
		for (var v : V.values()) {
			if (v.index < 0) { // if v.index is undefined,
				strongconnect(V, v).ifPresent(out::add);
			}
		}
		return out;
	}
	
	private Optional<SCC> strongconnect(Map<Node, TarjanVertex> V, TarjanVertex v) {
		v.index = index;
		v.lowlink = index;
		index++;
		S.push(v);
		v.onStack = true;
		
		// Consider successors of v
		for (var wVtx : v.edgesTo) {
			var w = V.get(wVtx);
			if (w.index < 0) {
				// Successor w has not yet been visited; recurse on it
				strongconnect(V, w);
				v.lowlink = Math.min(v.lowlink, w.lowlink);
			} else if (w.onStack) { 
				// Successor w is in stack S and hence in the current SCC
				// If w is not on stack, then (v, w) is a cross-edge in the DFS tree and must be ignored
				// Note: The next line may look odd - but is correct.
				// It says w.index not w.lowlink; that is deliberate and from the original paper
				v.lowlink = Math.min(v.lowlink, w.index);
			}
		}
		
		// If v is a root node, pop the stack and generate an SCC
		if (v.lowlink == v.index) {
			Set<Node> set = new HashSet<>();
			SCC scc = new SCC(v.data, set); // v is representative
			TarjanVertex w;
			do {
				w = S.pop();
				w.onStack = false;
				set.add(w.data);
				sccs.put(w.data, scc);
			} while (w != v);
			return Optional.of(scc);
		}
		return Optional.empty();
	}
	
	// DFS, returning path between nodes if present, or null if not.
	private Set<SCC> path(SCC from, SCC to, Set<SCC> seen) {
		if (from.equals(to)) {
			Set<SCC> result = new HashSet<>();
			result.add(from);
			return result;
		}

		for (SCC next : edgesForSCC.getSet(from)) {
			if (!seen.contains(next)) {
				seen.add(next);
				var path = path(next, to, seen);
				if (path != null) {
					path.add(from);
					return path;
				}
			}
		}

		return null;
	}
}
