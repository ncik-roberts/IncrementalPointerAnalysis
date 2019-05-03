package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Allocation;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Assignment;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.FieldRead;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.FieldWrite;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Invocation;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Return;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.StaticInvocation;
import edu.cmu.cs.cs15745.increpta.util.BiMap;
import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Create an incrementally-updating points-to-graph from an AST with context C.
 *
 * @param <C> The type of context to associate with each node.
 */
public class IncrementalPointsToGraphBuilder<C> {
  private final Ast ast;
  private final IncrementalPointsTo<Pair<Node, C>, Pair<Allocation, C>>.Graph result; // The graph we are building
  private final ContextBuilder<C> contextBuilder; // Strategy for merging contexts and creating new contexts.

  public IncrementalPointsToGraphBuilder(Ast ast, PointsToGraph<Pair<Node, C>, Pair<Allocation, C>> result,
      ContextBuilder<C> contextBuilder) {
    this.ast = Objects.requireNonNull(ast);
    this.result = new IncrementalPointsTo<>(result).build();
    this.contextBuilder = Objects.requireNonNull(contextBuilder);
  }

  private boolean alreadyBuilt = false; // Can only be built once

  public IncrementalPointsTo<Pair<Node, C>, Pair<Allocation, C>>.Graph build() {
    if (alreadyBuilt) {
      throw new IllegalStateException("Already called build.");
    }
    alreadyBuilt = true;

    // Visitor for adding on-the-fly edges.
    var onTheFly = new OnTheFlyWorkGenerator();
    var workList = onTheFly.workList;

    // Start with the entrypoints.
    for (var entryPoint : ast.entryPoints()) {
      C context = contextBuilder.initial(entryPoint);
      workList.add(Pair.of(entryPoint, context));
    }

    while (!workList.isEmpty()) {
      var curr = workList.remove();
      new GraphConstructor(curr).build();

      // Visit the instructions in the function body.
      onTheFly.process(curr);
    }

    return result;
  }

  // Correctly associating nodes with ast elements
  private Map<Ast.Variable, Node> variables = new LinkedHashMap<>();
  private Map<Ast.Instruction.Allocation, Node> heapItems = new LinkedHashMap<>();
  private BiMap<Ast.Variable, Ast.Variable, Node> varFields = new BiMap<>();
  private MultiMap<Pair<Node, C>, Pair<Pair<Ast.Instruction.Invocation, Ast.Variable>, C>> invocationMethodPairs = new MultiMap<>();

  private MultiMap<Node, C> contextsForNode = new MultiMap<>();

  private Pair<Node, C> heapItem(Ast.Instruction.Allocation item, C ctx) {
    var node = heapItems.computeIfAbsent(item, Node::heapItem);
    contextsForNode.getSet(node).add(ctx);
    return Pair.of(node, ctx);
  }

  private Pair<Node, C> var(Ast.Variable in, C ctx) {
    var node = variables.computeIfAbsent(in, Node::variable);
    contextsForNode.getSet(node).add(ctx);
    return Pair.of(node, ctx);
  }

  private Pair<Node, C> varFields(Ast.Variable var, Ast.Variable field, C ctx) {
    var node = varFields.computeIfAbsent(Pair.of(var, field), unused -> Node.varFields(var, field));
    contextsForNode.getSet(node).add(ctx);
    return Pair.of(node, ctx);
  }

  private Set<Pair<Pair<Ast.Instruction.Invocation, Ast.Variable>, C>> invocationMethodPairs(Pair<Node, C> key) {
    return invocationMethodPairs.getSet(key);
  }

  // Class that contains a visitor for constructing the graph for a list of
  // instructions.
  private final class GraphConstructor {
    private final List<Ast.Instruction> instructions;
    private final C context; // The context where we're constructing the graph.

    public GraphConstructor(Pair<Ast.Function, C> job) {
      instructions = job.fst().body().instructions();
      context = job.snd();
    }

    // Add the node to the added set if it was added this round.
    Pair<Node, C> lookup(Ast.Variable var) {
      return var(var, context);
    }

    // Add the node to the added set if it was added this round.
    Pair<Node, C> lookup(Ast.Variable var, Ast.Variable field) {
      return varFields(var, field, context);
    }

    // Calculate roots can only be run once.
    private boolean alreadyRun = false;

    /**
     * Calculate the root nodes and the heapItems to add at those root nodes. The
     * client of this function should likely use DFS to add heapItems to all nodes
     * reachable from the root nodes.
     */
    void build() {
      if (alreadyRun) {
        throw new IllegalStateException("already run calculateRoots");
      }
      alreadyRun = true;
      instructions.forEach(i -> i.accept(build));
    }

    // See paper: "ECHO: Instantaneous In Situ Race Detection in the IDE"
    // p.780
    // https://parasol.tamu.edu/~jeff/academic/echo.pdf
    private final Ast.Instruction.Visitor<?> build = new Ast.Instruction.StatefulVisitor() {
      // Rule 2
      @Override
      public void iterAssignment(Ast.Instruction.Assignment a) {
        result.addEdge(lookup(a.source()), lookup(a.target()));
      }

      // Rule 1
      @Override
      public void iterAllocation(Ast.Instruction.Allocation a) {
        var node = heapItem(a, context);
        var target = lookup(a.target());
        result.pointsTo(node).add(Pair.of(a, context));
        result.addEdge(node, target);
      }

      // Rule 4; x.f = y
      @Override
      public void iterFieldWrite(Ast.Instruction.FieldWrite fw) {
        result.addEdge(lookup(fw.source()), lookup(fw.target(), fw.field()));
      }

      // Rule 3; x = y.f
      @Override
      public void iterFieldRead(Ast.Instruction.FieldRead fr) {
        result.addEdge(lookup(fr.source(), fr.field()), lookup(fr.target()));
      }

      // Rule 5 specialized for static functions
      @Override
      public void iterStaticInvocation(Ast.Instruction.StaticInvocation s) {
        var optF = ast.staticFunction(s.method());
        if (optF.isEmpty())
          return;
        var f = optF.get();
        var params = f.params();
        var args = s.arguments();
        int n = Math.min(args.size(), params.size());
        for (int i = 0; i < n; i++) {
          result.addEdge(lookup(args.get(i)), lookup(params.get(i)));
        }

        // Add edge from return z to target x
        s.target().ifPresent(target -> {
          var x = lookup(target);
          for (var ret : f.body().returns()) {
            var z = lookup(ret.returned());
            result.addEdge(x, z);
          }
        });
      }
    }.visitor();
  }

  private final class OnTheFlyWorkGenerator {

    // This is where you put work.
    private final Queue<Pair<Ast.Function, C>> workList = new ArrayDeque<>();
    private final Set<Pair<Ast.Function, C>> seen = new LinkedHashSet<>();

    // Processing is visiting each of the instructions in a function.
    void process(Pair<Ast.Function, C> job) {
      C currentContext = job.snd();
      var visitor = new Visitor(currentContext).visitor();
      job.fst().body().instructions().forEach(i -> i.accept(visitor));
    }

    private void connectInvocationToFunction(Ast.Instruction.Invocation inv, Ast.Function f, C invContext) {
      var fContext = contextBuilder.merge(invContext, f);
      var params = f.params();
      // Arguments includes both o and all of y
      List<Ast.Variable> args = new ArrayList<>();
      args.add(inv.source());
      args.addAll(inv.arguments());

      // This must be varargs.
      if (args.size() != params.size()) {
        // It's possible to pass 0 varargs to a function.
        boolean validDifference = args.size() >= params.size() - 1;
        if (!validDifference) {
          throw new IllegalStateException(String.format("Illegal call to: %s; expected %d params, got %d args",
              f.name(), params.size(), args.size()));
        }
      }

      int n = Math.min(args.size(), params.size());
      for (int i = 0; i < n; i++) {
        // Edge goes from y to y'
        var y = var(args.get(i), invContext);
        var yPrime = var(params.get(i), fContext);
        result.addEdge(y, yPrime);
      }

      // Add edge from return z to target x
      var target = inv.target();
      if (target.isPresent()) {
        var x = var(target.get(), invContext);
        for (var ret : f.body().returns()) {
          var z = var(ret.returned(), fContext);
          result.addEdge(z, x);
        }
      }

      // Add to worklist if needed
      var job = Pair.of(f, fContext);
      if (seen.add(job)) {
        workList.add(job);
      }
    }

    // Visitor for the current context that adds to the worklist.
    private final class Visitor extends Ast.Instruction.StatefulVisitor {
      private final C currentContext;

      private Visitor(C context) {
        currentContext = context;
      }

      // Might need to add a new static function to the worklist
      @Override
      public void iterStaticInvocation(Ast.Instruction.StaticInvocation i) {
        var optF = ast.staticFunction(i.method());
        if (optF.isEmpty())
          return;
        var f = optF.get();
        var job = Pair.of(f, contextBuilder.merge(currentContext, f));
        if (seen.add(job)) {
          workList.add(job);
        }
      }

      // Rule 5: x = o.m(y), calling m(y'){ return z }
      @Override
      public void iterInvocation(Ast.Instruction.Invocation inv) {
        var o = var(inv.source(), currentContext);
        var m = inv.method();

        // Keep track of methods called on this variable so we can properly
        // add new calls if we encounter it during a future dfs.
        invocationMethodPairs(o).add(Pair.of(Pair.of(inv, m), currentContext));
        for (var pair : List.copyOf(result.pointsTo(o))) {
          var heapItem = pair.fst();
          ast.instanceMethod(heapItem.type(), m).ifPresent(f -> connectInvocationToFunction(inv, f, currentContext));
        }
      }
    }
  }

  /**
   * Get the edges involved in the instruction.
   */
  public Set<Pair<Pair<Node, C>, Pair<Node, C>>> affectedEdges(Instruction inst) {
    var answer = inst.accept(new Instruction.Visitor<Set<Pair<Node, Node>>>() {
      @Override
      public Set<Pair<Node, Node>> visitAssignment(Assignment a) {
        var result = new LinkedHashSet<Pair<Node, Node>>();
        Optional.ofNullable(variables.get(a.source())).ifPresent(
            n1 -> Optional.ofNullable(variables.get(a.target())).ifPresent(n2 -> result.add(Pair.of(n1, n2))));
        return result;
      }

      @Override
      public Set<Pair<Node, Node>> visitAllocation(Allocation a) {
        var result = new LinkedHashSet<Pair<Node, Node>>();
        Optional.ofNullable(heapItems.get(a)).ifPresent(
            n1 -> Optional.ofNullable(variables.get(a.target())).ifPresent(n2 -> result.add(Pair.of(n1, n2))));
        return result;
      }

      @Override
      public Set<Pair<Node, Node>> visitFieldWrite(FieldWrite fw) {
        var result = new LinkedHashSet<Pair<Node, Node>>();
        Optional.ofNullable(variables.get(fw.source())).ifPresent(n1 -> Optional
            .ofNullable(varFields.get(fw.target(), fw.field())).ifPresent(n2 -> result.add(Pair.of(n1, n2))));
        return result;
      }

      @Override
      public Set<Pair<Node, Node>> visitFieldRead(FieldRead fr) {
        var result = new LinkedHashSet<Pair<Node, Node>>();
        Optional.ofNullable(varFields.get(fr.source(), fr.field())).ifPresent(
            n1 -> Optional.ofNullable(variables.get(fr.target())).ifPresent(n2 -> result.add(Pair.of(n1, n2))));
        return result;
      }

      // TODO: add functions
      @Override
      public Set<Pair<Node, Node>> visitStaticInvocation(StaticInvocation i) {
        return Set.of();
      }

      @Override
      public Set<Pair<Node, Node>> visitInvocation(Invocation i) {
        return Set.of();
      }

      @Override
      public Set<Pair<Node, Node>> visitReturn(Return i) {
        return Set.of();
      }
    });

    // Flatmap over all contexts for each returned node.
    return answer.stream().flatMap(pair -> {
      var n1 = pair.fst();
      var n2 = pair.snd();
      return contextsForNode.get(n1).stream().flatMap(c1 -> {
        var v1 = Pair.of(n1, c1);
        return contextsForNode.get(n2).stream().map(c2 -> Pair.of(v1, Pair.of(n2, c2)))
            .filter(p -> result.edges(p.fst()).contains(p.snd()));
      });
    }).collect(Collectors.toSet());
  }
}