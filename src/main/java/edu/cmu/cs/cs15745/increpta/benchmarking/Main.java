package edu.cmu.cs.cs15745.increpta.benchmarking;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.cmu.cs.cs15745.increpta.ContextBuilders;
import edu.cmu.cs.cs15745.increpta.benchmarking.Benchmarker.TestState;

public final class Main {
  
  private final static Map<String, List<String>> ALL =
    Map.of
      (TestInfo.SCOPE_FILE, List.of(
        TestInfo.TEST_FIELDS,
        TestInfo.TEST_DUMB,
        TestInfo.TEST_FIELDS_HARDER,
        TestInfo.TEST_GETTER_SETTER,
        TestInfo.TEST_ARRAYS,
        TestInfo.TEST_EXCEPTION,
        TestInfo.TEST_FACTORY,
        TestInfo.TEST_ARRAY_SET,
        TestInfo.TEST_ARRAY_SET_ITER,
        TestInfo.TEST_MULTI_DIM,
        TestInfo.TEST_GLOBAL,
        TestInfo.TEST_ID,
        TestInfo.TEST_LOCALS,
        TestInfo.TEST_ONTHEFLY_SIMPLE,
        TestInfo.TEST_ONTHEFLY_CS,
        TestInfo.TEST_COND,
        TestInfo.TEST_METHOD_RECURSION,
        TestInfo.TEST_HASH_SET,
        TestInfo.TEST_HASH_MAP_GET,
        TestInfo.TEST_LINKED_LIST,
        TestInfo.TEST_LINKEDLIST_ITER,
        TestInfo.TEST_ARRAY_LIST,
        TestInfo.TEST_HASHTABLE_ENUM,
        TestInfo.TEST_NASTY_PTRS,
        TestInfo.TEST_WITHIN_METHOD_CALL,
        TestInfo.TEST_CLONE,
        TestInfo.FLOWSTO_TEST_LOCALS,
        TestInfo.FLOWSTO_TEST_ID,
        TestInfo.FLOWSTO_TEST_FIELDS,
        TestInfo.FLOWSTO_TEST_FIELDS_HARDER,
        TestInfo.FLOWSTO_TEST_ARRAYSET_ITER,
        TestInfo.FLOWSTO_TEST_HASH_SET),
      "wala.testdata_sunflow.txt", List.of("Lorg/sunflow/Benchmark"),
      "wala.testdata_eclipse.txt", List.of("Lorg/eclipse/core/runtime/adaptor/EclipseStarter"),
      "wala.testdata_jython.txt",  List.of("Lorg/python/util/jython"),
      "wala.testdata_h2.txt", List.of("Lorg/h2/tools/Shell"),
      "wala.testdata_tsp.txt", List.of("Ltsp/Tsp"),
      "wala.testdata_scctest.txt", List.of("Lscctest/SCCTest"));
    
  
  public static void main(String[] args) {
    benchmarkAll();
  }
  
  private static final void benchmarkAll() {
    ALL.forEach(Main::benchmark);
  }
  
  @SuppressWarnings("unused")
  private static final void benchmarkWala() {
    benchmark(TestInfo.SCOPE_FILE, ALL.get(TestInfo.SCOPE_FILE));
  }
  
  private static final void benchmark(String scopeFile, List<String> mainClasses) {
    System.out.println("==============");
    System.out.println("Testing " + scopeFile);
    System.out.println("==============");
    var benchmarker = new Benchmarker(scopeFile, "exclusions.txt");
    var asts = mainClasses.stream().map(benchmarker::getAst).collect(Collectors.toList());
    for (var ctxBuilder : List.of(
        ContextBuilders.NO_CONTEXT,
        ContextBuilders.nCallContext(1),
        ContextBuilders.nCallContext(2),
        ContextBuilders.nCallContext(3))) {
      var state = new TestState();
      asts.forEach(ast -> benchmarker.test(ast, ctxBuilder, state));
      System.out.printf("===== Total statistics (%s): =====\n", ctxBuilder);
      System.out.printf("  Total nodes:   \t%d\n", state.totalNodes);
      System.out.printf("  Total pts:     \t%d\n", state.totalPointsTo);
      System.out.printf("  Total add/dels:\t%d\n", state.totalInstructions);
      System.out.printf("  Total del time:\t%.3fs\n", state.totalDeleteTimeMS / 1000D);
      System.out.printf("  Mean del time: \t%.3fs\n", state.totalDeleteTimeMS / 1000D / state.totalInstructions);
      System.out.printf("  Total add time:\t%.3fs\n", state.totalAddTimeMS / 1000D);
      System.out.printf("  Mean add time: \t%.3fs\n", state.totalAddTimeMS / 1000D / state.totalInstructions);
    }
  }
}
