package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node;
import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Build and incrementally update SCCs for a graph.
 */
public final class SCCUpdater<C> {
    private final MultiMap<Pair<Node, C>, Pair<Node, C>> sccs = new MultiMap<>();
	public SCCUpdater(PointsToGraph<C> graph) {
		for (var node : graph.nodes()) {
			var edges = graph.edges(node);
			V.put(node, new TarjanVertex(node, edges));
		}
	}
	
	// TODO: Incremental updates.
	
	private boolean alreadyRun = false;
	/**
	 * Runs Tarjan's algorithm, collecting strongly-connected components.
	 */
	public SCCs<C> sccs() {
		if (alreadyRun) {
			throw new IllegalStateException("Already run.");
		}
		alreadyRun = true;
		tarjan();
		return () -> sccs;
	}
	
	// Type abbreviation
	public interface SCCs<C> {
		MultiMap<Pair<Node, C>, Pair<Node, C>> value();
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
	private final Map<Pair<Node, C>, TarjanVertex> V = new HashMap<>();
	private final Deque<TarjanVertex> S = new ArrayDeque<>();
	private int index = 0;
	private void tarjan() {
		for (var v : V.values()) {
			if (v.index < 0) { // if v.index is undefined,
				strongconnect(v);
			}
		}
	}
	
	private void strongconnect(TarjanVertex v) {
		v.index = index;
		v.lowlink = index;
		index++;
		S.push(v);
		v.onStack = true;
		
		// Consider successors of v
		for (var wVtx : v.edgesTo) {
			var w = Objects.requireNonNull(V.get(wVtx), wVtx.toString());
			if (w.index < 0) {
				// Successor w has not yet been visited; recurse on it
				strongconnect(w);
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
			Set<Pair<Node, C>> scc = new HashSet<>();
			TarjanVertex w;
			do {
				w = S.pop();
				w.onStack = false;
				scc.add(w.data);
				sccs.put(w.data, scc);
			} while (w != v);
		}
	}
}