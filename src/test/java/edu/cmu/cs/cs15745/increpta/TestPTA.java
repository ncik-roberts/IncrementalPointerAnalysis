package edu.cmu.cs.cs15745.increpta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import edu.cmu.cs.cs15745.increpta.TestSCCs.Node;
import edu.cmu.cs.cs15745.increpta.TestSCCs.HeapItem;

// Test points-to analysis
public class TestPTA {

	private static <N, H> void check(PointsToGraph<N, H> pag, Map<N, Set<H>> map) {
		var result = new HashMap<N, Set<H>>();
		for (var n : pag.nodes()) {
			var pts = pag.pointsTo(n);
			result.put(n, pts);
		}
		Assert.assertEquals(map, result);
	}

	@Test
	public void test1() {
		var builder = TestSCCs.of(
			Set.of(Node.A, Node.B, Node.C, Node.D, Node.E, Node.F),
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(Node.D),
				Node.D, Set.of(Node.F)),
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.A, HeapItem.C),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));
		
		var pag = builder.build();
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(Node.D),
				Node.D, Set.of(Node.F),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.A, HeapItem.C),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));
		
		pag.addEdge(Node.F, Node.B);
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.F),
				Node.C, Set.of(Node.F),
				Node.E, Set.of(Node.F),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A, HeapItem.C, HeapItem.E),
				Node.D, Set.of(HeapItem.A, HeapItem.C, HeapItem.E),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));

		// Back to where we started
		pag.deleteEdge(Node.F, Node.B);
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(Node.D),
				Node.D, Set.of(Node.F),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.A, HeapItem.C),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));

		// Delete new edge
		pag.deleteEdge(Node.B, Node.D);
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(),
				Node.D, Set.of(Node.F),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.C),
				Node.F, Set.of(HeapItem.C, HeapItem.E)));

		// Add back in
		pag.addEdge(Node.B, Node.D);
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(Node.D),
				Node.D, Set.of(Node.F),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.A, HeapItem.C),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));

		// Add new edge
		pag.addEdge(Node.F, Node.D);
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(Node.D),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.A, HeapItem.C, HeapItem.E),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));

		// Delete back out
		pag.deleteEdge(Node.F, Node.D);
		TestSCCs.check(builder,
			Map.of(
				Node.A, Set.of(Node.B),
				Node.C, Set.of(Node.D),
				Node.E, Set.of(Node.F),
				Node.B, Set.of(Node.D),
				Node.D, Set.of(Node.F),
				Node.F, Set.of()));
		check(pag,
			Map.of(
				Node.A, Set.of(HeapItem.A),
				Node.C, Set.of(HeapItem.C),
				Node.E, Set.of(HeapItem.E),
				Node.B, Set.of(HeapItem.A),
				Node.D, Set.of(HeapItem.A, HeapItem.C),
				Node.F, Set.of(HeapItem.A, HeapItem.C, HeapItem.E)));
	}
	
	@Test
	// Based on TestFields from wala.core.testdata.demandpa
	public void test2() {
		var builder = TestSCCs.of(
			Set.of(Node.A, Node.B, Node.C, Node.D, Node.E, Node.F, Node.G, Node.H, Node.I, Node.J, Node.K, Node.L),
			Map.of(
				Node.A, Set.of(Node.E),
				Node.B, Set.of(Node.F),
				Node.C, Set.of(Node.G),
				Node.D, Set.of(Node.H),
				Node.E, Set.of(Node.I),
				Node.F, Set.of(Node.J),
				Node.I, Set.of(Node.K),
				Node.J, Set.of(Node.L)),
			Map.ofEntries(
				Map.entry(Node.A, Set.of(HeapItem.A)),
				Map.entry(Node.B, Set.of(HeapItem.B)),
				Map.entry(Node.C, Set.of(HeapItem.C)),
				Map.entry(Node.D, Set.of(HeapItem.D)),
				Map.entry(Node.E, Set.of(HeapItem.A)),
				Map.entry(Node.F, Set.of(HeapItem.B)),
				Map.entry(Node.G, Set.of(HeapItem.C)),
				Map.entry(Node.H, Set.of(HeapItem.D)),
				Map.entry(Node.I, Set.of(HeapItem.A)),
				Map.entry(Node.J, Set.of(HeapItem.B)),
				Map.entry(Node.K, Set.of(HeapItem.A)),
				Map.entry(Node.L, Set.of(HeapItem.B))));
		
		var pag = builder.build();
		TestSCCs.check(builder,
			Map.ofEntries(
				Map.entry(Node.A, Set.of(Node.E)),
				Map.entry(Node.B, Set.of(Node.F)),
				Map.entry(Node.C, Set.of(Node.G)),
				Map.entry(Node.D, Set.of(Node.H)),
				Map.entry(Node.E, Set.of(Node.I)),
				Map.entry(Node.F, Set.of(Node.J)),
				Map.entry(Node.G, Set.of()),
				Map.entry(Node.H, Set.of()),
				Map.entry(Node.I, Set.of(Node.K)),
				Map.entry(Node.J, Set.of(Node.L)),
				Map.entry(Node.K, Set.of()),
				Map.entry(Node.L, Set.of())));
		check(pag,
			Map.ofEntries(
				Map.entry(Node.A, Set.of(HeapItem.A)),
				Map.entry(Node.B, Set.of(HeapItem.B)),
				Map.entry(Node.C, Set.of(HeapItem.C)),
				Map.entry(Node.D, Set.of(HeapItem.D)),
				Map.entry(Node.E, Set.of(HeapItem.A)),
				Map.entry(Node.F, Set.of(HeapItem.B)),
				Map.entry(Node.G, Set.of(HeapItem.C)),
				Map.entry(Node.H, Set.of(HeapItem.D)),
				Map.entry(Node.I, Set.of(HeapItem.A)),
				Map.entry(Node.J, Set.of(HeapItem.B)),
				Map.entry(Node.K, Set.of(HeapItem.A)),
				Map.entry(Node.L, Set.of(HeapItem.B))));
		
		for (var edge :
			List.of(
				Map.entry(Node.A, Node.E),
				Map.entry(Node.B, Node.F),
				Map.entry(Node.C, Node.G),
				Map.entry(Node.D, Node.H),
				Map.entry(Node.E, Node.I),
				Map.entry(Node.F, Node.J),
				Map.entry(Node.I, Node.K),
				Map.entry(Node.J, Node.L))) {
			var from = edge.getKey();
			var to = edge.getValue();
			pag.deleteEdge(from, to);
			pag.addEdge(from, to);
			TestSCCs.check(builder,
				Map.ofEntries(
					Map.entry(Node.A, Set.of(Node.E)),
					Map.entry(Node.B, Set.of(Node.F)),
					Map.entry(Node.C, Set.of(Node.G)),
					Map.entry(Node.D, Set.of(Node.H)),
					Map.entry(Node.E, Set.of(Node.I)),
					Map.entry(Node.F, Set.of(Node.J)),
					Map.entry(Node.G, Set.of()),
					Map.entry(Node.H, Set.of()),
					Map.entry(Node.I, Set.of(Node.K)),
					Map.entry(Node.J, Set.of(Node.L)),
					Map.entry(Node.K, Set.of()),
					Map.entry(Node.L, Set.of())));
			check(pag,
				Map.ofEntries(
					Map.entry(Node.A, Set.of(HeapItem.A)),
					Map.entry(Node.B, Set.of(HeapItem.B)),
					Map.entry(Node.C, Set.of(HeapItem.C)),
					Map.entry(Node.D, Set.of(HeapItem.D)),
					Map.entry(Node.E, Set.of(HeapItem.A)),
					Map.entry(Node.F, Set.of(HeapItem.B)),
					Map.entry(Node.G, Set.of(HeapItem.C)),
					Map.entry(Node.H, Set.of(HeapItem.D)),
					Map.entry(Node.I, Set.of(HeapItem.A)),
					Map.entry(Node.J, Set.of(HeapItem.B)),
					Map.entry(Node.K, Set.of(HeapItem.A)),
					Map.entry(Node.L, Set.of(HeapItem.B))));
		}
	}
}
