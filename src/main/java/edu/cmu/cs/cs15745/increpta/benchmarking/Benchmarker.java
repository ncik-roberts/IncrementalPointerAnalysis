package edu.cmu.cs.cs15745.increpta.benchmarking;

import com.ibm.wala.ipa.callgraph.impl.Util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;

import edu.cmu.cs.cs15745.increpta.ContextBuilders;
import edu.cmu.cs.cs15745.increpta.IncrementalPointsTo;
import edu.cmu.cs.cs15745.increpta.PointsToGraph;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node.HeapItem;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContextBuilder;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.ast.AstFromWala;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/** Run some benchmarks. */
public final class Benchmarker {

	private final AnalysisScope scope;
	private final ClassHierarchy cha;
	
	public Benchmarker(String scopeFile, String exclusionsFile) {
		scope = swallow(() -> CallGraphTestUtil.makeJ2SEAnalysisScope(scopeFile, exclusionsFile));
		cha = swallow(() -> ClassHierarchyFactory.make(scope));
	}
	
	// Build initial call graph.
	public CallGraph makeCallGraph(Iterable<Entrypoint> entrypoints) {
		var options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
		var cache = new AnalysisCacheImpl();
		var builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);
		try {
			return builder.makeCallGraph(options, null);
		} catch (CallGraphBuilderCancelException cancel) {
			throw new IllegalStateException(cancel);
		}
	}

	public void test(String mainClassName) {
		System.out.println("Starting analysis on " + mainClassName);
		long pointStart = System.currentTimeMillis();

		var entrypoints = Util.makeMainEntrypoints(scope, cha, mainClassName);
		var cfg = makeCallGraph(entrypoints);

		long pointCFG = System.currentTimeMillis();
		long timeCFG = pointCFG - pointStart;
		System.out.println(String.format("\tCFG construction: %.3fs", timeCFG / 1000D));

		// Starting building astFromWala. A node is an entrypoint if its method is an entry method.
		var entryMethods =
			StreamSupport.stream(entrypoints.spliterator(), false)
				.map(Entrypoint::getMethod)
				.collect(Collectors.toSet());
		Ast ast = new AstFromWala(cfg, cha, node -> entryMethods.contains(node.getMethod())).ast();

		long pointAST = System.currentTimeMillis();
		long timeAST = pointAST - pointCFG;
		System.out.println(String.format("\tAST conversion: %.3fs", timeAST / 1000D));

		// Starting building pointsToGraph
		var builder = new SimplePointsToGraphWithContextBuilder<>(
				ast,
				new SimplePointsToGraphWithContext<>(),
				ContextBuilders.NO_CONTEXT);

		var simplePag = builder.build(); // This graph does not find SCCs / does not incrementally update points-to sets.
		var pag = new IncrementalPointsTo<>(simplePag).build(); // This graph finds SCCs / incrementally updates points-to sets.

		long pointPAG = System.currentTimeMillis();
		long timePAG = pointPAG - pointAST;
		System.out.println(String.format("\tPAG construction: %.3fs", timePAG / 1000D));
		
		TestState state = new TestState();

		// Test static methods
		var pagCopy = pag.clone();
		for (var f : ast.staticFunctions().values()) {
			testNode(f.body(), pag, pagCopy, builder, state);
		}
		
		// Test instance methods
		for (var f : ast.instanceMethods().values()) {
			testNode(f.body(), pag, pagCopy, builder, state);
		}

		System.out.println("===== Total statistics: =====");
		System.out.printf("  Total additions/deletions: %d\n", state.totalInstructions);
		System.out.printf("  Total deletion time: %.3fs\n", state.totalDeleteTimeMS / 1000D);
		System.out.printf("  Mean deletion time:  %.3fs\n", state.totalDeleteTimeMS / 1000D / state.totalInstructions);
		System.out.printf("  Total add time: %.3fs\n", state.totalAddTimeMS / 1000D);
		System.out.printf("  Mean add time:  %.3fs\n", state.totalAddTimeMS / 1000D / state.totalInstructions);
	}
	
	// Test adding and removing each instruction in the node, updating the state
	// based on the run.
	private <C> void testNode(
			Ast.FunctionBody body,
			PointsToGraph<Pair<Node, C>, Pair<Ast.Instruction.Allocation, C>> pag, // Run incremental add / delete
			PointsToGraph<Pair<Node, C>, Pair<Ast.Instruction.Allocation, C>> pagCopy, // Check correctness
			SimplePointsToGraphWithContextBuilder<C> builder, // Convert ast instruction to graph nodes
			TestState state) {
		for (var inst : body.instructions()) {
			if (inst == null) {
				continue;
			}

			state.totalInstructions++;
			
			var edges = builder.affectedEdges(inst);
			Set<Pair<Node, C>> affectedNodes = new HashSet<>();

			/******** DELETION CITY ********/
			System.out.println("Deleting SSA Instruction: " + inst);
			long deletePointMS = System.currentTimeMillis();
			for (var edge : edges) {
				// affectedNodes.addAll(pag.deleteEdge(edge.fst(), edge.snd()));
			}
			long deleteTimeMS = System.currentTimeMillis() - deletePointMS;

			/******** ADDITION CITY ********/
			System.out.println("Adding SSA Instruction: " + inst);
			long addPointMS = System.currentTimeMillis();
			for (var edge : edges) {
				// affectedNodes.addAll(pag.addEdge(edge.fst(), edge.snd()));
			}
			long addTimeMS = System.currentTimeMillis() - addPointMS;
			
			// Verify correctness by checking old pag vs. current pag
			for (var node : affectedNodes) {
				var oldPTS = pagCopy.pointsTo(node);
				var newPTS = pag.pointsTo(node);
				if (!oldPTS.equals(newPTS)) {
					System.err.println("Old PTS: " + oldPTS);
					System.err.println("New PTS: " + newPTS);
					System.err.println("For node: " + node);
					System.err.println("For instruction: " + inst);
					throw new IllegalStateException("It is illegal to be wrong.");
				}
			}

			System.out.println("Verified correctness! :)");

			// We done
			System.out.printf("Delete time: %.3fs\nAdd time: %.3fs\n", deleteTimeMS / 1000D, addTimeMS / 1000D);
			state.totalDeleteTimeMS += deleteTimeMS;
			state.totalAddTimeMS += addTimeMS;
		}
	}
	
	private static class TestState {
		int totalInstructions = 0;
		long totalDeleteTimeMS = 0;
		long totalAddTimeMS = 0;
	}

	private static <T> T swallow(Callable<T> f) {
		try {
			return f.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
