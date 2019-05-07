package edu.cmu.cs.cs15745.increpta.benchmarking;

import com.ibm.wala.ipa.callgraph.impl.Util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

import edu.cmu.cs.cs15745.increpta.ContextBuilder;
import edu.cmu.cs.cs15745.increpta.IncrementalPointsTo;
import edu.cmu.cs.cs15745.increpta.PointsToGraph;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node;
import edu.cmu.cs.cs15745.increpta.IncrementalPointsToGraphBuilder;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.ast.AstFromWala;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/** Run some benchmarks. */
public final class Benchmarker {

  private static final int DEBUG = swallow(() -> {
    var debugLevel = System.getenv("IPA_DEBUG");
    if (debugLevel != null) {
      try {
        return Integer.parseInt(debugLevel);
      } catch (Exception e) {
        return 0;
      }
    }
    return 0;
  });
  
  private final AnalysisScope scope;
  private final ClassHierarchy cha;

  public Benchmarker(String scopeFile, String exclusionsFile) {
    scope = swallow(() -> AnalysisScopeReader.readJavaScope(scopeFile, new FileProvider().getFile(exclusionsFile),
        Benchmarker.class.getClassLoader()));
    cha = swallow(() -> ClassHierarchyFactory.make(scope));
  }

  // Build initial call graph.
  public CallGraph makeCallGraph(Iterable<Entrypoint> entrypoints) {
    var options = new AnalysisOptions(scope, entrypoints);
    var cache = new AnalysisCacheImpl();
    var builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);
    try {
      return builder.makeCallGraph(options, null);
    } catch (CallGraphBuilderCancelException cancel) {
      throw new IllegalStateException(cancel);
    }
  }
  
  public Ast getAst(String mainClassName) {
    System.out.println("Starting analysis on " + mainClassName);
    long pointStart = System.currentTimeMillis();

    var entrypoints = Util.makeMainEntrypoints(scope, cha, mainClassName);
    var cfg = makeCallGraph(entrypoints);

    long pointCFG = System.currentTimeMillis();
    long timeCFG = pointCFG - pointStart;
    System.out.println(String.format("\tCFG construction: %.3fs", timeCFG / 1_000D));

    // Starting building astFromWala. A node is an entrypoint if its method is an
    // entry method.
    var entryMethods = StreamSupport.stream(entrypoints.spliterator(), false).map(Entrypoint::getMethod)
        .collect(Collectors.toSet());
    Ast ast = new AstFromWala(cfg, cha, node -> entryMethods.contains(node.getMethod())).ast();

    long pointAST = System.currentTimeMillis();
    long timeAST = pointAST - pointCFG;
    System.out.println(String.format("\tAST conversion: %.3fs", timeAST / 1_000D));

    return ast;
  }
  
  public <C> void test(Ast ast, ContextBuilder<C> ctxBuilder, TestState state) {
    long pointStart = System.currentTimeMillis();

    // Starting building pointsToGraph
    var builder = new IncrementalPointsToGraphBuilder<>(ast, new SimplePointsToGraphWithContext<>(),
        ctxBuilder);

    var pag = builder.build();

    long pointPAG = System.currentTimeMillis();
    long timePAG = pointPAG - pointStart;
    state.pagConstructionMS += timePAG;

    pag.checkInvariant(); // make sure it was correctly constructed

    // Accumulate the number of nodes and the size of points-to sets.
    var nonEmptyNodeSummary =
      pag.nodes().stream()
         .mapToInt(n -> pag.pointsTo(n).size())
         .filter(i -> i > 0)
         .summaryStatistics();
    state.totalNodes += nonEmptyNodeSummary.getCount();
    state.totalPointsTo += nonEmptyNodeSummary.getSum();

    // Test static methods
    var pagCopy = pag.clone();
    for (var f : ast.staticFunctions().values()) {
      testNode(f.body(), pag, pagCopy, builder, state);
    }

    // Test instance methods
    for (var f : ast.instanceMethods().values()) {
      testNode(f.body(), pag, pagCopy, builder, state);
    }
  }

  // Test adding and removing each instruction in the node, updating the state
  // based on the run.
  private <C> void testNode(Ast.FunctionBody body,
      IncrementalPointsTo<Pair<Node, C>, Pair<Ast.Instruction.Allocation, C>>.Graph pag, // Run incremental add / delete
      PointsToGraph<Pair<Node, C>, Pair<Ast.Instruction.Allocation, C>> pagCopy, // Check correctness
      IncrementalPointsToGraphBuilder<C> builder, // Convert ast instruction to graph nodes
      TestState state) {
    for (var inst : body.instructions()) {
      if (inst == null) {
        continue;
      }

      var edges = builder.affectedEdges(inst);
      Set<Pair<Node, C>> affectedNodes = new LinkedHashSet<>();

      /******** DELETION CITY ********/
      if (DEBUG >= 3) {
        System.err.println("Deleting SSA Instruction: " + inst + " (" + edges + ")");
      }
      long deletePointNS = System.nanoTime();
      for (var edge : edges) {
        affectedNodes.addAll(pag.deleteEdge(edge.fst(), edge.snd()));
      }
      long deleteTimeNS = System.nanoTime() - deletePointNS;
      if (DEBUG >= 2 && affectedNodes.size() > 0) {
        pag.checkInvariant();
      }

      /******** ADDITION CITY ********/
      if (DEBUG >= 3)
        System.err.println("Adding SSA Instruction: " + inst + " (" + edges + ")");
      long addPointNS = System.nanoTime();
      for (var edge : edges) {
        affectedNodes.addAll(pag.addEdge(edge.fst(), edge.snd()));
      }
      long addTimeNS = System.nanoTime() - addPointNS;
      if (DEBUG >= 2 && affectedNodes.size() > 0) {
        pag.checkInvariant();
      }

      if (affectedNodes.size() > 0) {
        if (DEBUG >= 1) {
          if (DEBUG >= 3) {
            System.err.println("Checking correctness on " + affectedNodes.size() + " nodes...");
          }

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

          if (DEBUG >= 3) {
            System.err.println("Verified correctness! :)");
          }
        }

        // We done
        if (DEBUG >= 3) {
          System.err.printf("Delete time: %.3fms\nAdd time: %.3fms\n", deleteTimeNS / 1_000_000D, addTimeNS / 1_000_000D);
        }

        state.totalInstructions++;
        state.totalDeleteTimeNS += deleteTimeNS;
        state.totalAddTimeNS += addTimeNS;
        state.minAddTimeNS = Math.min(state.minAddTimeNS, addTimeNS);
        state.maxAddTimeNS = Math.max(state.maxAddTimeNS, addTimeNS);
        state.minDeleteTimeNS = Math.min(state.minDeleteTimeNS, deleteTimeNS);
        state.maxDeleteTimeNS = Math.max(state.maxDeleteTimeNS, deleteTimeNS);
      }
    }
  }

  static class TestState {
    int totalInstructions = 0;
    long totalDeleteTimeNS = 0;
    long maxDeleteTimeNS = 0;
    long minDeleteTimeNS = Long.MAX_VALUE;
    long totalAddTimeNS = 0;
    long maxAddTimeNS = 0;
    long minAddTimeNS = Long.MAX_VALUE;
    long totalNodes = 0;
    long totalPointsTo = 0;
    long pagConstructionMS = 0;
  }

  private static <T> T swallow(Callable<T> f) {
    try {
      return f.call();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
