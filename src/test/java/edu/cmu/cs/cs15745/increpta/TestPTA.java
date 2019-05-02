package edu.cmu.cs.cs15745.increpta;

import java.util.HashMap;
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
	}
}
