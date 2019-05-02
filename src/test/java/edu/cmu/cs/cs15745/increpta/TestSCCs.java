package edu.cmu.cs.cs15745.increpta;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test the creation and maintainment of SCCs.
 */
public class TestSCCs {
	
	// Distinct nodes.
	enum Node {
		A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z;
	}
	
	enum HeapItem {
		A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z;
	}
	
	static <N, H> IncrementalPointsTo<N, H> of(Set<N> nodes, Map<N, Set<N>> graph, Map<N, Set<H>> pointsTo) {
		return new IncrementalPointsTo<>(new SimplePointsToGraph<>(new MultiMap<>(graph), new MultiMap<>(pointsTo), new LinkedHashSet<>(nodes)));
	}
	
	static void check(IncrementalPointsTo<Node, ?> builder, Map<Node, Set<Node>> map) {
		var withReps = new HashMap<Node, Set<Node>>();
		for (var entry : map.entrySet()) {
			var rep = builder.rep(entry.getKey());
			var set = entry.getValue().stream().map(builder::rep).collect(Collectors.toSet());
			withReps.put(rep, set);
		}
		
		var other = new HashMap<Node, Set<Node>>();
		for (var entry : builder.edgesForSCC().entrySet()) {
			other.put(entry.getKey().rep, entry.getValue().stream().map(scc -> scc.rep).collect(Collectors.toSet()));
		}
		
		Assert.assertEquals(withReps, other);
		Assert.assertEquals(withReps.size(), map.size());
	}
	
	@Test
	public void test1() {
		var builder = of(
			Set.of(Node.A, Node.B, Node.C),
			Map.of(
				Node.A, Set.of(Node.B),
				Node.B, Set.of(Node.C),
				Node.C, Set.of(Node.A)),
			Map.of());

		var pag = builder.build(); // a -> b -> c -> a
		check(builder, Map.of(Node.A, Set.of()));

		pag.addEdge(Node.A, Node.B); // a -> b -> c -> a
		check(builder, Map.of(Node.A, Set.of()));

		pag.deleteEdge(Node.C, Node.A); // a -> b -> c
		check(builder, Map.of(Node.A, Set.of(Node.B),
				              Node.B, Set.of(Node.C),
				              Node.C, Set.of()));
	}

	@Test
	public void test2() {
		var builder = of(
			Set.of(Node.A, Node.B, Node.C),
			Map.of(
				Node.A, Set.of(Node.B),
				Node.B, Set.of(Node.C)),
			Map.of());

		var pag = builder.build(); // a -> b -> c
		check(builder,
				Map.of(
					Node.A, Set.of(Node.B),
					Node.B, Set.of(Node.C),
					Node.C, Set.of()));

		pag.addEdge(Node.A, Node.B); // a -> b -> c
		check(builder,
				Map.of(
					Node.A, Set.of(Node.B),
					Node.B, Set.of(Node.C),
					Node.C, Set.of()));

		pag.addEdge(Node.C, Node.A); // a -> b -> c -> a
		check(builder,
				Map.of(
					Node.A, Set.of()));
	}

	@Test
	public void test3() {
		var builder = of(
			Set.of(Node.A, Node.B, Node.C),
			Map.of(
				Node.A, Set.of(Node.B),
				Node.B, Set.of(Node.C)),
			Map.of());

		var pag = builder.build(); // a -> b -> c
		check(builder,
				Map.of(
					Node.A, Set.of(Node.B),
					Node.B, Set.of(Node.C),
					Node.C, Set.of()));

		pag.addEdge(Node.A, Node.B); // a -> b -> c
		check(builder,
				Map.of(
					Node.A, Set.of(Node.B),
					Node.B, Set.of(Node.C),
					Node.C, Set.of()));

		pag.addEdge(Node.C, Node.B); // a -> b <-> c
		check(builder,
				Map.of(
					Node.A, Set.of(Node.B),
					Node.B, Set.of()));

		pag.addEdge(Node.C, Node.A); // a -> b <-> c -> a
		check(builder,
				Map.of(
					Node.A, Set.of()));
	}

	@Test
	public void test4() {
		var builder = of(
			Set.of(Node.A, Node.B, Node.C, Node.D, Node.E, Node.F, Node.G, Node.H),
			Map.of(
				Node.A, Set.of(Node.B),
				Node.B, Set.of(Node.C),
				Node.C, Set.of(Node.A, Node.D),
				Node.D, Set.of(Node.E, Node.G),
				Node.E, Set.of(Node.F),
				Node.F, Set.of(Node.E),
				Node.G, Set.of(Node.H),
				Node.H, Set.of(Node.G)
			),
			Map.of());

		var pag = builder.build(); // abc  ->  d (-> ef, -> gh)
		check(builder,
				Map.of(
					Node.A, Set.of(Node.D),
					Node.D, Set.of(Node.E, Node.G),
					Node.E, Set.of(),
					Node.G, Set.of()));
		
		// add an edge to collapse sccs
		pag.addEdge(Node.F, Node.B);
		check(builder,
				Map.of(
					Node.A, Set.of(Node.G),
					Node.G, Set.of()));

		// make sure it's invertible
		pag.deleteEdge(Node.F, Node.B);
		check(builder,
				Map.of(
					Node.A, Set.of(Node.D),
					Node.D, Set.of(Node.E, Node.G),
					Node.E, Set.of(),
					Node.G, Set.of()));

		// add back the edge
		pag.addEdge(Node.F, Node.B);
		check(builder,
				Map.of(
					Node.A, Set.of(Node.G),
					Node.G, Set.of()));

		// add another edge to make one scc
		pag.addEdge(Node.H, Node.C);
		check(builder,
				Map.of(
					Node.A, Set.of()));

		// delete back out the other of the two added edges
		pag.deleteEdge(Node.F, Node.B);
		check(builder,
				Map.of(
					Node.A, Set.of(Node.F),
					Node.F, Set.of()));

		// round trip
		pag.deleteEdge(Node.H, Node.C);
		check(builder,
				Map.of(
					Node.A, Set.of(Node.D),
					Node.D, Set.of(Node.E, Node.G),
					Node.E, Set.of(),
					Node.G, Set.of()));
	}
}
