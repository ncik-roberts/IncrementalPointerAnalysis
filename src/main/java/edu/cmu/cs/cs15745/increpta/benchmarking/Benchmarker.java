package edu.cmu.cs.cs15745.increpta.benchmarking;

import com.ibm.wala.ipa.callgraph.impl.Util;

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
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.ast.AstFromWala;

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
		var pag =
			new IncrementalPointsTo<>(SimplePointsToGraphWithContext.fromAst(ast, ContextBuilders.NO_CONTEXT))
			  .build();

		long pointPAG = System.currentTimeMillis();
		long timePAG = pointPAG - pointAST;
		System.out.println(String.format("\tPAG construction: %.3fs", timePAG / 1000D));
	}

	private static <T> T swallow(Callable<T> f) {
		try {
			return f.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
