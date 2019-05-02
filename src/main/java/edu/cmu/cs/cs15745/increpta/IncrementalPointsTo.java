package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Class for building and incrementally updating SCCs.
 *
 * @param <Node> The type of node in the points-to graph.
 */
public class IncrementalPointsTo<Node, HeapItem> {

	private final PointsToGraph<Node, HeapItem> graph;
	private final Map<Node, SCC> sccs = new LinkedHashMap<>();
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
	class SCC {
		final Node rep; // equiv class representative
		final Set<Node> elems;
		private SCC(Node rep, Set<Node> elems) {
			this.rep = rep;
			this.elems = elems;
		}
		@Override
		public boolean equals(Object o) {
			return o instanceof IncrementalPointsTo<?, ?>.SCC && Objects.equals(rep, ((IncrementalPointsTo<?, ?>.SCC) o).rep);
		}
		@Override
		public int hashCode() {
			return rep.hashCode();
		}
		@Override
		public String toString() {
			return rep.toString();
		}
	}
	
	Node rep(Node node) {
		return sccs.get(node).rep;
	}
	
	/**
	 * Points-to-graph supporting incremental add and delete
	 */
	public class Graph implements PointsToGraph<Node, HeapItem> {

		/**
		 * Incrementally add edge.
		 */
		@Override
		public Set<Node> addEdge(Node from, Node to) {
			// If we already have this edge, don't do anything.
			if (edgesForSCC.getSet(sccs.get(from)).contains(sccs.get(to))) {
				return Set.of();
			}

			var affectedNodes = new LinkedHashSet<Node>();
			var worklist = new ArrayDeque<Pair<Node, Node>>();
			worklist.add(Pair.of(from, to));
			
			while (!worklist.isEmpty()) {
				var edge = worklist.remove();
				var x = edge.fst();
				var y = edge.snd();

				graph.addEdge(x, y);
				updateSCCsAdd(x, y);
				
				// Be careful to call pointsTo (and not graph.pointsTo) to
				// ensure we are grabbing the pts for the representative for the scc
				// (which is where we are maintaining all updates).
				// We also must make a copy since "propagateAddchange" destructively
				// modifies this set.
				var delta = new LinkedHashSet<>(pointsTo(x));
				propagateAddChange(delta, rep(y), worklist, affectedNodes);
			}
			
			// We added (some) new edge
			return affectedNodes;
		}
		
		/**
		 * Incrementally delete edge.
		 */
		@Override
		public Set<Node> deleteEdge(Node from, Node to) {
			// The structure of this is very similar to addEdge;
			// but we don't need a worklist because:
			//   (1) each method node will be re-deleted one at a time (by client calls to deleteEdge),
			//   (2) we handle fields differently than the B. Liu et al. paper.
			var affectedNodes = new LinkedHashSet<Node>();
			
			graph.deleteEdge(from, to);
			updateSCCsDelete(from, to);
			
			var delta = new LinkedHashSet<>(pointsTo(from));
			propagateDeleteChange(delta, rep(to), affectedNodes);
			
			// We added (some) new edge
			return affectedNodes;
		}

		@Override
		public Set<Node> nodes() {
			return graph.nodes();
		}

		@Override
		public Set<Node> edges(Node node) {
			return graph.edges(node);
		}

		@Override
		public Set<HeapItem> pointsTo(Node key) {
			// We only update for the rep anymore, anyway.
			return graph.pointsTo(rep(key));
		}
		
		@Override
		public PointsToGraph<Node, HeapItem> clone() {
			var clone = graph.clone();
			for (var node : clone.nodes()) {
				var rep = rep(node);
				var pts = clone.pointsTo(node);
				pts.clear();
				pts.addAll(clone.pointsTo(rep));
			}
			return clone;
		}
	}
	
	private void propagateDeleteChange(Set<HeapItem> delta, Node y, Set<Node> affected) {
		for (var zSCC : reverseEdgesForSCC.getSet(sccs.get(y))) {
			var z = zSCC.rep;
			delta.removeAll(graph.pointsTo(z));
			if (delta.isEmpty()) {
				return;
			}
		}
		affected.add(y);
		graph.pointsTo(y).removeAll(delta);
		for (var wSCC : edgesForSCC.getSet(sccs.get(y))) {
			var w = wSCC.rep;
			propagateDeleteChange(new LinkedHashSet<>(delta), w, affected);
		}
	}
	
	// See "Rethinking Incremental and Parallel Pointer Analysis", p. 6.13, for var names
	/** Incrementally update change. */
	private void propagateAddChange(Set<HeapItem> delta, Node y, Deque<Pair<Node, Node>> worklist, Set<Node> affected) {
		// Invariant: y is a rep.
		delta.removeAll(graph.pointsTo(y));
		if (!delta.isEmpty()) {
			affected.add(y);
			graph.pointsTo(y).addAll(delta);
			for (var wSCC : edgesForSCC.get(sccs.get(y))) {
				var w = wSCC.rep;
				// We really do gotta make a copy here.
				propagateAddChange(new LinkedHashSet<>(delta), w, worklist, affected);
			}

			// Complex added statements
			// We don't need to separately handle field-loads and -writes, because
			// we only store one copy of each field.
			
			// TODO: Add method call processing.
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
			reverseEdgesForSCC.getSet(sccTo).add(sccFrom);
			
			// DFS from To to see if we can get to From.
			var path = path(sccTo, sccFrom, new LinkedHashSet<>());
			if (path != null) {
				// Then we should merge the SCCs.
				var superSCC = sccTo; // Merge into "to"
				var pts = graph.pointsTo(superSCC.rep);
				for (var scc : path) {
					superSCC.elems.addAll(scc.elems);
					scc.elems.forEach(e -> sccs.replace(e, superSCC));
					pts.addAll(graph.pointsTo(scc.rep));
					
					// Update edges and reverse edges for scc
					// (scc, a)
					for (var a : edgesForSCC.remove(scc)) {
						var edgesA = reverseEdgesForSCC.getSet(a);
						edgesA.remove(scc);
						if (!a.equals(superSCC)) {
							edgesA.add(superSCC);
							edgesForSCC.getSet(superSCC).add(a);
						}
					}

					// (a, scc)
					for (var a : reverseEdgesForSCC.remove(scc)) {
						var edgesA = edgesForSCC.getSet(a);
						edgesA.remove(scc);
						if (!a.equals(superSCC)) {
							edgesA.add(superSCC);
							reverseEdgesForSCC.getSet(superSCC).add(a);
						}
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
			reverseEdgesForSCC.getSet(sccTo).remove(sccFrom);
			return;
		} else {
			SCC scc = sccTo; // or sccFrom, they're the same
			List<SCC> afterDelete = tarjan(scc.elems);
			if (afterDelete.size() == 1) {
				// We're good, no need to update anything.
				return;
			} else { // Otherwise, we broke into multiple sccs.
				// Now we just need to: update stale references in edgesForSCCs and reverseEdgesForSCCs
				// For each edge (scc, A) removed from edges, remove (A, scc) from reverseEdges.
				for (var toRemove : edgesForSCC.removeSet(scc)) {
					reverseEdgesForSCC.getSet(toRemove).remove(scc);
				}
				
				// For each edge (scc, A) removed from reversed edges, remove (A, scc) from edges.
				var as = reverseEdgesForSCC.removeSet(scc);
				for (var a : as) {
					edgesForSCC.getSet(a).remove(scc);
				}
				
				for (SCC newScc : afterDelete) {
					for (var elem : newScc.elems) {
						// Create "updated" as well so we can calculate edges for only the right sccs
						sccs.put(elem, newScc);
					}
				}
				calculateEdgesForSCCs(afterDelete);

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
			reverseEdgesForSCC.getSet(scc); // add empty set
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
		
		@Override
		public String toString() {
			return data + "-->" + edgesTo;
		}
	}

	// See https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
	private List<SCC> tarjan(Iterable<Node> vs) {
		Deque<TarjanVertex> S = new ArrayDeque<>();
		int[] index = { 0 };
		List<SCC> out = new ArrayList<>();
		Map<Node, TarjanVertex> V = new LinkedHashMap<>();
		for (var v : graph.nodes()) {
			var edges = graph.edges(v);
			V.put(v, new TarjanVertex(v, edges));
		}
		for (var v : vs) {
			var tv = V.get(v);
			if (tv.index < 0) { // if v.index is undefined,
				out.addAll(strongconnect(S, index, V, tv));
			}
		}
		return out;
	}
	
	private List<SCC> strongconnect(Deque<TarjanVertex> S, int[] index, Map<Node, TarjanVertex> V, TarjanVertex v) {
		v.index = index[0];
		v.lowlink = index[0];
		index[0]++;
		S.push(v);
		v.onStack = true;
		
		// Consider successors of v
		List<SCC> result = new ArrayList<>();
		for (var wVtx : v.edgesTo) {
			var w = Objects.requireNonNull(V.get(wVtx), wVtx.toString());
			if (w.index < 0) {
				// Successor w has not yet been visited; recurse on it
				result.addAll(strongconnect(S, index, V, w));
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
			Set<Node> set = new LinkedHashSet<>();
			SCC scc = new SCC(v.data, set); // v is representative
			TarjanVertex w;
			do {
				w = S.pop();
				w.onStack = false;
				set.add(w.data);
				sccs.put(w.data, scc);
			} while (w != v);
			result.add(scc);
		}
		return result;
	}
	
	// DFS, returning path between nodes if present, or null if not.
	private Set<SCC> path(SCC from, SCC to, Set<SCC> seen) {
		if (from.equals(to)) {
			Set<SCC> result = new LinkedHashSet<>();
			return result;
		}

		for (SCC next : edgesForSCC.getSet(from)) {
			if (!seen.contains(next)) {
				seen.add(next);
				var path = path(next, to, seen);
				if (path != null) {
					path.add(next);
					return path;
				}
			}
		}

		return null;
	}
	
	/** For testing only */
	Map<Node, SCC> sccs() {
		return sccs;
	}
	
	/** For testing only */
	MultiMap<SCC, SCC> edgesForSCC() {
		return edgesForSCC;
	}

	/** For testing only */
	MultiMap<SCC, SCC> reverseEdgesForSCC() {
		return reverseEdgesForSCC;
	}
}
