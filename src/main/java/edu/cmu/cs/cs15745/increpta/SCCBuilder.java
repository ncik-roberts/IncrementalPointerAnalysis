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

import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node;
import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Build and incrementally update SCCs for a graph.
 */
public final class SCCBuilder<C> {
	private final PointsToGraph<C> graph;
    private final Map<Pair<Node, C>, SCC> sccs = new HashMap<>();
    private final MultiMap<SCC, SCC> edgesForSCC = new MultiMap<>();
    private final MultiMap<SCC, SCC> reverseEdgesForSCC = new MultiMap<>();
	public SCCBuilder(PointsToGraph<C> graph) {
		this.graph = graph;
	}
	
	// TODO: Incremental updates.
	
	private boolean alreadyRun = false;
	/**
	 * Runs Tarjan's algorithm, collecting strongly-connected components.
	 */
	public SCCs build() {
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
		return new SCCs();
	}
	
	// Wrapper class for object identity :)
	private class SCC {
		private final Pair<Node, C> rep; // equiv class representative
		private final Set<Pair<Node, C>> elems;
		private SCC(Pair<Node, C> rep, Set<Pair<Node, C>> elems) {
			this.rep = rep;
			this.elems = elems;
		}
	}
	
	// Publicly-visible API
	public class SCCs {
		public Set<Pair<Node, C>> edgesForSCC(Pair<Node, C> node) {
			Set<Pair<Node, C>> result = new HashSet<>();
			var edges = edgesForSCC.get(sccs.get(node));
			for (var e : edges) {
				result.add(e.rep);
			}
			return result;
		}
		
		/** Incrementally update SCC based on add of edge. */
		public void addEdge(Pair<Node, C> from, Pair<Node, C> to) {
			SCC sccFrom = Objects.requireNonNull(sccs.get(from));
			SCC sccTo = Objects.requireNonNull(sccs.get(to));
			if (sccFrom.equals(sccTo)) { // Same instance?
				return; // We don't need to update anything
			} else if (edgesForSCC.get(sccFrom).contains(sccTo)){ // If we're already pointing to,
				return;
			} else if (edgesForSCC.get(sccTo).contains(sccFrom)) { // Need to merge!
				edgesForSCC.get(sccFrom).addAll(edgesForSCC.get(sccTo)); // Arbitrarily add to "from"
				edgesForSCC.get(sccFrom).remove(sccFrom); // Remove self that was just added on previous line.
				for (var toElem : sccTo.elems) { // Update SCC for each of to's elems
					sccs.put(toElem, sccFrom);
				}
				return;
			} else { // Otherwise, "from" will point to "to", but don't merge SCCs.
				edgesForSCC.get(sccFrom).add(sccTo);
				return;
			}
		}
		
		/** Incrementally update SCC based on delete of edge. */
		public void deleteEdge(Pair<Node, C> from, Pair<Node, C> to) {
			SCC sccFrom = Objects.requireNonNull(sccs.get(from));
			SCC sccTo = Objects.requireNonNull(sccs.get(to));
			if (!sccFrom.equals(sccTo)) { // Different SCC, deletion does nothing.
				edgesForSCC.get(sccFrom).remove(sccTo);
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
					// For edges (scc, ?), we just remove (?, scc) directly from reverseEdges.
					for (var toRemove : edgesForSCC.remove(scc)) {
						reverseEdgesForSCC.get(toRemove).remove(scc);
					}
					
					// For edges (?, src), we have to figure out which newSCC it points to :(
					calculateEdgesForSCCs(reverseEdgesForSCC.remove(scc));
				}
			}
		}
	}
	
	// For each rep, find all edges in SCC to other SCCs.
	private void calculateEdgesForSCCs(Iterable<SCC> newSCCs) {
		for (var scc : newSCCs) {
			for (var node : scc.elems) { 
				var toAddTo = edgesForSCC.getSet(scc);
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
		final Pair<Node, C> data;
		final Set<Pair<Node, C>> edgesTo;
		TarjanVertex(Pair<Node, C> data, Set<Pair<Node, C>> edgesTo) {
			this.data = data;
			this.edgesTo = edgesTo;
		}
	}

	// See https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
	private final Deque<TarjanVertex> S = new ArrayDeque<>();
	private int index = 0;
	private List<SCC> tarjan(Iterable<Pair<Node, C>> vs) {
		List<SCC> out = new ArrayList<>();
		Map<Pair<Node, C>, TarjanVertex> V = new HashMap<>();
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
	
	private Optional<SCC> strongconnect(Map<Pair<Node, C>, TarjanVertex> V, TarjanVertex v) {
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
			Set<Pair<Node, C>> set = new HashSet<>();
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
}