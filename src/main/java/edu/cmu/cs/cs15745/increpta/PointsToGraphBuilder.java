package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.PointsToGraph.DfsIterator;
import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node;
import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node.HeapItem;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Function;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Allocation;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Assignment;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.FieldRead;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.FieldWrite;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Invocation;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.StaticInvocation;
import edu.cmu.cs.cs15745.increpta.util.BiMap;
import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

class PointsToGraphBuilder<C> {
	private final Ast ast;
	private final PointsToGraph<C> result; // The graph we are building
	private final ContextBuilder<C> contextBuilder; // Strategy for merging contexts and creating new contexts.
	public PointsToGraphBuilder(Ast ast, PointsToGraph<C> result, ContextBuilder<C> contextBuilder) {
		this.ast = Objects.requireNonNull(ast);
		this.result = Objects.requireNonNull(result);
		this.contextBuilder = Objects.requireNonNull(contextBuilder);
	}

	private Map<Pair<Ast.Variable, C>, Pair<Node, C>> variables = new HashMap<>();
	private BiMap<Pair<Ast.Variable, Ast.Variable>, C, Pair<Node, C>> varFields = new BiMap<>();
	private MultiMap<Pair<Node, C>, Pair<Pair<Ast.Instruction.Invocation, Ast.Variable>, C>> invocationMethodPairs = new MultiMap<>();

	private Pair<Node, C> var(Ast.Variable in, C ctx) {
		return variables.computeIfAbsent(Pair.of(in, ctx), unused -> Pair.of(Node.variable(in), ctx));
	}

	private Pair<Node, C> varFields(Ast.Variable var, Ast.Variable field, C ctx) {
		return varFields.computeIfAbsent(
			Pair.of(Pair.of(var, field), ctx),
			unused -> Pair.of(Node.varFields(var, field), ctx));
	}
	
	private Set<Pair<Pair<Ast.Instruction.Invocation, Ast.Variable>, C>> invocationMethodPairs(Pair<Node, C> key) {
		return invocationMethodPairs.getSet(key);
	}
	
	private boolean alreadyBuilt = false;
	public PointsToGraph<C> build() {
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

		// Now we use the on-the-fly alg to process the rest.
		while (!workList.isEmpty()) {
			var curr = workList.remove();
			var roots = new GraphConstructor(curr).calculateRoots();
			System.out.println("Starting dfs from " + roots);
			for (var pair : roots.entrySet()) {
				var root = pair.getKey();
				var patch = pair.getValue();
				for (DfsIterator<C> dfs = result.dfs(root); dfs.hasNext(); ) {
					var node = dfs.next();
					if (!result.pointsTo(node).addAll(patch)) {
						dfs.abandonBranch();
					}
				}
			}
			
			// Visit the instructions in the function body.
			onTheFly.process(curr);
		}
		
		return result;
	}
	
	// See paper: "ECHO: Instantaneous In Situ Race Detection in the IDE"
	// p.780
	// https://parasol.tamu.edu/~jeff/academic/echo.pdf
	private final class GraphConstructor {
		private final List<Ast.Instruction> instructions; 
		private final C context; // The context where we're constructing the graph.
		public GraphConstructor(Pair<Ast.Function, C> job) {
			instructions = job.fst().body().instructions();
			context = job.snd();
		}

		private final MultiMap<Pair<Node, C>, Pair<HeapItem, C>> roots = new MultiMap<>();

		// Added this round.
		private final Set<Pair<Node, C>> added = new HashSet<>();

		// Add the node to the added set if it was added this round.
		Pair<Node, C> lookup(Ast.Variable var) {
			boolean addedThisRound = !variables.containsKey(Pair.of(var, context));
			Pair<Node, C> result = var(var, context);
			if (addedThisRound) {
				added.add(result);
			}
			return result;
		}

		// Add the node to the added set if it was added this round.
		Pair<Node, C> lookup(Ast.Variable var, Ast.Variable field) {
			boolean addedThisRound = varFields.get(Pair.of(var, field), context) == null;
			Pair<Node, C> result = varFields(var, field, context);
			if (addedThisRound) {
				added.add(result);
			}
			return result;
		}
		
		void addEdge(Pair<Node, C> from, Pair<Node, C> to) {
			result.addEdge(from, to);
			if (!added.contains(from) && added.contains(to)) {
				roots.getSet(to).addAll(result.pointsTo(from)); // To propagate
			}
		}
		
		/**
		 * Calculate the root nodes and the heapItems to add at those root nodes.
		 * The client of this function should likely use DFS to add heapItems to
		 * all nodes reachable from the root nodes.
		 */
		MultiMap<Pair<Node, C>, Pair<HeapItem, C>> calculateRoots() {
			var visitor = new Ast.Instruction.StatefulVisitor() {
				// Rule 2
				@Override
				public void iterAssignment(Assignment a) {
					addEdge(lookup(a.source()), lookup(a.target()));
				}
				// Rule 1
				@Override
				public void iterAllocation(Allocation a) {
					var heapItem = new HeapItem(a.type());
					var heapItemWithCtx = Pair.of(heapItem, context);
					var node = Pair.of(Node.heapItem(heapItem), context);
					var target = lookup(a.target());
					result.pointsTo(node).add(heapItemWithCtx);
				  // Don't call "addEdge" here as an optimization, since we know this adds a single element
				  // to the points-to set of "target".
					roots.getSet(target).add(heapItemWithCtx); // To propagate
					result.addEdge(node, target);
				}

				// Rule 4; x.f = y
				@Override
				public void iterFieldWrite(FieldWrite fw) {
					addEdge(lookup(fw.source()), lookup(fw.target(), fw.field()));
				}

				// Rule 3; x = y.f
				@Override
				public void iterFieldRead(FieldRead fr) {
					addEdge(lookup(fr.source(), fr.field()), lookup(fr.target()));
				}
				
				// Rule 5 specialized for static functions
				@Override
				public void iterStaticInvocation(StaticInvocation s) {
					Ast.Function f = ast.staticFunction(s.method());
					var params = f.params();
					var args = s.arguments();
					for (int i = 0; i < params.size(); i++) {
						addEdge(lookup(args.get(i)), lookup(params.get(i)));
					}
					
					// Add edge from return z to target x
					s.target().ifPresent(target -> {
						var x = lookup(target);
						for (var ret : f.body().returns()) {
							var z = lookup(ret.returned());
							addEdge(x, z);
						}
					});
				}
			}.visitor();
			instructions.forEach(i -> i.accept(visitor));
			return roots;
		}
	}
	
	private final class OnTheFlyWorkGenerator extends Ast.Instruction.StatefulVisitor {
		
		// This is where you put work.
		private final Queue<Pair<Function, C>> workList = new ArrayDeque<>();
		private final Set<Pair<Function, C>> seen = new HashSet<>();
		private final Ast.Instruction.Visitor<?> me = visitor();

		// I hate communicating to the visitor methods through mutable state,
		// but I might as well save the allocation of the closure around the
		// parameter to "process".
		private C currentContext;
		
		// Processing is visiting each of the instructions in a function.
		void process(Pair<Function, C> job) {
			currentContext = job.snd();
			job.fst().body().instructions().forEach(i -> i.accept(me));
		}
		
		// Might need to add a new static function to the worklist
		@Override
		public void iterStaticInvocation(StaticInvocation i) {
			var f = ast.staticFunction(i.method());
			var job = Pair.of(f, contextBuilder.merge(currentContext, f));
			if (seen.add(job)) {
				workList.add(job);
			}
		}

		// Connect two nodes.
		// Also propagate points-to set through dfs.
		private void connect(Pair<Node, C> from, Pair<Node, C> to) {
			if (result.addEdge(from, to)) { // This short-circuiting prevents infinite recursion
				var pointsTo = result.pointsTo(from);
				for (DfsIterator<C> dfs = result.dfs(to); dfs.hasNext(); ) {
					var node = dfs.next();
					if (!result.pointsTo(node).addAll(pointsTo)) {
						dfs.abandonBranch();
					}
					
					// Propagate to called methods, potentially.
					for (var pair : invocationMethodPairs(node)) {
						var inv = pair.fst().fst();
						var method = pair.fst().snd();
						var ctx = pair.snd();
						for (var heapItem : pointsTo) {
							Ast.Function f = ast.instanceMethods(heapItem.fst().type(), method);
							connectInvocationToFunction(inv, f, ctx);
						}
					}
				}
			}
		}
		
		private void connectInvocationToFunction(Invocation inv, Function f, C invContext) {
			var fContext = contextBuilder.merge(invContext, f);
			var params = f.params();
			// Arguments includes both o and all of y
			List<Ast.Variable> args = new ArrayList<>();
			args.add(inv.source());
			args.addAll(inv.arguments());

			for (int i = 0; i < params.size(); i++) {
				// Edge goes from y to y'
				var y = var(args.get(i), invContext);
				var yPrime = var(params.get(i), fContext);
				connect(y, yPrime);
			}
			
			// Add edge from return z to target x
			var target = inv.target();
			if (target.isPresent()) {
				var x = var(target.get(), invContext);
				for (var ret : f.body().returns()) {
					var z = var(ret.returned(), fContext);
					connect(z, x);
				}
			}
			
			// Add to worklist if needed
			var job = Pair.of(f, fContext);
			if (seen.add(job)) {
				workList.add(job);
			}
		}

		// Rule 5: x = o.m(y), calling m(y'){ return z }
		@Override
		public void iterInvocation(Invocation inv) {
			var o = var(inv.source(), currentContext);
			var m = inv.method();
			
			// Keep track of methods called on this variable so we can properly
			// add new calls if we encounter it during a future dfs.
			invocationMethodPairs(o).add(Pair.of(Pair.of(inv, m), currentContext));
			for (var pair : result.pointsTo(o)) {
				var heapItem = pair.fst();
				Ast.Function f = ast.instanceMethods(heapItem.type(), m);
				connectInvocationToFunction(inv, f, currentContext);
			}
		}
	}
}
