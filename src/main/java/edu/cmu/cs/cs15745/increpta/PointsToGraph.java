package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

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
import edu.cmu.cs.cs15745.increpta.util.Util;

public class PointsToGraph {
	
	// Disallow outside instantiation
	private PointsToGraph() { }
	
	private final MultiMap<Node, Node> graph = new MultiMap<>();
	private final MultiMap<Node, HeapItem> pointsTo = new MultiMap<>();
	
	/**
	 * Add directed edge from "from" to "to", returning whether the edge was
	 * already present.
	 */
	public boolean addEdge(Node from, Node to) {
		return graph.getSet(from).add(to);
	}
	
	Set<HeapItem> pointsTo(Node key) {
		return pointsTo.getSet(key);
	}
			
	public interface DfsIterator extends Iterator<Node> {
		void abandonBranch();
	}

	// Iterator that allows for a dfs branch to be abandoned
	public DfsIterator dfs(Node from) {
		Set<Node> seen = new HashSet<>();
		Deque<Node> toVisit = new ArrayDeque<>();
		toVisit.add(from);
		seen.add(from);
		return new DfsIterator() {
			Node curr = null;
			@Override public boolean hasNext() {
				if (!toVisit.isEmpty()) {
					return true;
				}
				if (curr == null) {
					return false;
				}
				for (Node to : edges(curr)) {
					if (!seen.contains(to)) {
						return true;
					}
				}
				return false;
			}
			@Override public Node next() {
				if (curr != null) {
					for (Node to : edges(curr)) {
						if (seen.add(to)) {
							toVisit.push(to);
						}
					}
				}
				curr = toVisit.pop();
				return curr;
			}
			@Override public void abandonBranch() {
				curr = null;
			}
		};
	}
	
	public Iterable<Node> edges(Node from) {
		Objects.requireNonNull(from, "from");
		return graph.getOrDefault(from, Collections.emptySet());
	}

	public static PointsToGraph fromAst(Ast ast) {
		PointsToGraph result = new PointsToGraph();
		
		// Most convenient way to have inner functions.
		var state = new Object() {
			Map<Ast.Variable, Node> variables = new HashMap<>();
			BiMap<Ast.Variable, Ast.Variable, Node> varFields = new BiMap<>();
			MultiMap<Node, Pair<Ast.Instruction.Invocation, Ast.Variable>> invocationMethodPairs = new MultiMap<>();

			Node var(Ast.Variable in) {
				return variables.computeIfAbsent(in, Node::variable);
			}

			Node varFields(Ast.Variable var, Ast.Variable field) {
				return varFields.computeIfAbsent(
					Pair.of(var, field),
					entry -> Node.varFields(var, field));
			}
			
			Set<Pair<Ast.Instruction.Invocation, Ast.Variable>> invocationMethodPairs(Node key) {
				return invocationMethodPairs.getSet(key);
			}
		};

		// See paper: "ECHO: Instantaneous In Situ Race Detection in the IDE"
		// p.780
		// https://parasol.tamu.edu/~jeff/academic/echo.pdf
		
		// First, construct the base graph
		var graphConstructor = new Object() {
			
			// Add the node to the added set if it was added this round.
			Node lookup(Set<Node> added, Ast.Variable var) {
				boolean addedThisRound = !state.variables.containsKey(var);
				Node result = state.var(var);
				if (addedThisRound) {
					added.add(result);
				}
				return result;
			}

			// Add the node to the added set if it was added this round.
			Node lookup(Set<Node> added, Ast.Variable var, Ast.Variable field) {
				boolean addedThisRound = state.varFields.get(var, field) == null;
				Node result = state.varFields(var, field);
				if (addedThisRound) {
					added.add(result);
				}
				return result;
			}
			
			void addEdge(MultiMap<Node, HeapItem> roots, Set<Node> added, Node from, Node to) {
				result.addEdge(from, to);
				if (!added.contains(from) && added.contains(to)) {
					roots.getSet(to).addAll(result.pointsTo(from)); // To propagate
				}
			}
			
			MultiMap<Node, HeapItem> rootsOf(List<Ast.Instruction> instructions) {
				MultiMap<Node, HeapItem> roots = new MultiMap<>();

				// Added this round.
				Set<Node> added = new HashSet<>();

				var visitor = new Ast.Instruction.StatefulVisitor() {
					// Rule 2
					@Override
					public void iterAssignment(Assignment a) {
						var source = lookup(added, a.source());
						var target = lookup(added, a.target());
						addEdge(roots, added, source, target);
					}
					// Rule 1
					@Override
					public void iterAllocation(Allocation a) {
						var heapItem = new HeapItem(a.type());
						var node = Node.heapItem(heapItem);
						var target = lookup(added, a.target());
						result.pointsTo(node).add(heapItem);
					  // Don't call "addEdge" here as an optimization, since we know this adds a single element
					  // to the points-to set of "target".
						roots.getSet(target).add(heapItem); // To propagate
						result.addEdge(node, target);
					}

					// Rule 4; x.f = y
					@Override
					public void iterFieldWrite(FieldWrite fw) {
						var x = fw.target();
						var f = fw.field();
						var y = lookup(added, fw.source());
						var xf = lookup(added, x, f);
						addEdge(roots, added, y, xf);
					}

					// Rule 3; x = y.f
					@Override
					public void iterFieldRead(FieldRead fr) {
						var x = lookup(added, fr.target());
						var y = fr.source();
						var f = fr.field();
						var yf = lookup(added, y, f);
						addEdge(roots, added, yf, x);
					}
					
					// Rule 5 specialized for static functions
					@Override
					public void iterStaticInvocation(StaticInvocation s) {
						Ast.Function f = ast.staticFunction(s.method());
						var params = f.params();
						var args = s.arguments();
						for (int i = 0; i < params.size(); i++) {
							var arg = lookup(added, args.get(i));
							var param = lookup(added, params.get(i));
							addEdge(roots, added, arg, param);
						}
						
						// Add edge from return z to target x
						s.target().ifPresent(target -> {
							var x = state.var(target);
							for (var ret : f.body().returns()) {
								var z = state.var(ret.returned());
								addEdge(roots, added, x, z);
							}
						});
					}
				}.visitor();
				instructions.forEach(i -> i.accept(visitor));
				return roots;
			}
		};
		
		// Functions we've already processed.
		Set<Ast.Function> seen = new HashSet<>(); 
		Queue<Ast.Function> workList = new ArrayDeque<>(ast.entryPoints());
		
		// Visitor for adding on-the-fly edges.
		var onTheFly = new Ast.Instruction.StatefulVisitor() {

			// Might need to add a new static function to the worklist
			@Override
			public void iterStaticInvocation(StaticInvocation i) {
				var f = ast.staticFunction(i.method());
				if (seen.add(f)) {
					workList.add(f);
				}
			}

			// Connect two nodes.
			// Also propagate points-to set through dfs.
			private void connect(Node from, Node to) {
				if (result.addEdge(from, to)) { // This short-circuiting prevents infinite recursion
					var pointsTo = result.pointsTo(from);
					for (DfsIterator dfs = result.dfs(to); dfs.hasNext(); ) {
						var node = dfs.next();
						if (!result.pointsTo(node).addAll(pointsTo)) {
							dfs.abandonBranch();
						}
						
						// Propagate to called methods, potentially.
						for (var pair : state.invocationMethodPairs(node)) {
							var inv = pair.fst();
							var method = pair.snd();
							for (var heapItem : pointsTo) {
								Ast.Function f = ast.instanceMethods(heapItem.type(), method);
								connectInvocationToFunction(inv, f);
							}
						}
					}
				}
			}
			
			private void connectInvocationToFunction(Invocation inv, Function f) {
				var params = f.params();
				// Arguments includes both o and all of y
				List<Ast.Variable> args = new ArrayList<>();
				args.add(inv.source());
				args.addAll(inv.arguments());

				for (int i = 0; i < params.size(); i++) {
					// Edge goes from y to y'
					var y = state.var(args.get(i));
					var yPrime = state.var(params.get(i));
					connect(y, yPrime);
				}
				
				// Add edge from return z to target x
				var target = inv.target();
				if (target.isPresent()) {
					var x = state.var(target.get());
					for (var ret : f.body().returns()) {
						var z = state.var(ret.returned());
						connect(z, x);
					}
				}
				
				// Add to worklist if needed
				if (seen.add(f)) {
					workList.add(f);
				}
			}

			// Rule 5: x = o.m(y), calling m(y'){ return z }
			@Override
			public void iterInvocation(Invocation inv) {
				var o = state.var(inv.source());
				var m = inv.method();
				
				// Keep track of methods called on this variable so we can properly
				// add new calls if we encounter it during a future dfs.
				state.invocationMethodPairs(o).add(Pair.of(inv, m));
				for (var C : result.pointsTo(o)) {
					Ast.Function f = ast.instanceMethods(C.type(), m);
					
					connectInvocationToFunction(inv, f);
				}
			}
		}.visitor();
		
		// Now we use the on-the-fly alg to process the rest.
		while (!workList.isEmpty()) {
			var curr = workList.remove();
			var roots = graphConstructor.rootsOf(curr.body().instructions());
			System.out.println("Starting dfs from " + roots);
			for (var pair : roots.entrySet()) {
				var root = pair.getKey();
				var patch = pair.getValue();
				for (DfsIterator dfs = result.dfs(root); dfs.hasNext(); ) {
					var node = dfs.next();
					if (!result.pointsTo(node).addAll(patch)) {
						dfs.abandonBranch();
					}
				}
			}
			for (var i : curr.body().instructions()) {
				i.accept(onTheFly);
			}
		}
		
		System.out.println("\nPOINTS TO");
		System.out.println(Util.join("\n\t", result.pointsTo.entrySet()));
		System.out.println("\nGRAPH");
		System.out.println(Util.join("\n\t", result.graph.entrySet()));
		return result;
	}
	
	/**
	 * One of:
	 * <ul>
	 * <li>Abstract heap item</li>
	 * <li>Field of abstract heap item</li>
	 * <li>Variable</li>
	 * </ul>
	 * The type argument is the heap item type.
	 */
	public static abstract class Node {
		// Disallow external subclassing
		private Node() {}
		
		public static final class HeapItem {
			private final Ast.Type type;
			public HeapItem(Ast.Type type) {
				this.type = type;
			}
			public Ast.Type type() { return type; }
			public String toString() {
				return String.format("%s#%x", type, hashCode());
			}
		}
		
		public interface Visitor<T> {
			T visitHeapItem(HeapItem item);
			T visitField(Ast.Variable item, Ast.Variable field);
			T visitVariable(Ast.Variable item);
		}

		public abstract <T> T accept(Visitor<T> visitor);
		
		public static Node variable(Ast.Variable item) {
			return new Node() {
				@Override
				public <S> S accept(Visitor<S> visitor) {
					return visitor.visitVariable(item);
				}
				
				public String toString() {
					return item.toString();
				}
			};
		}

		public static Node varFields(Ast.Variable item, Ast.Variable field) {
			return new Node() {
				@Override
				public <S> S accept(Visitor<S> visitor) {
					return visitor.visitField(item, field);
				}

				public String toString() {
					return item.toString() + "." + field.toString();
				}
			};
		}

		public static Node heapItem(HeapItem item) {
			return new Node() {
				@Override
				public <S> S accept(Visitor<S> visitor) {
					return visitor.visitHeapItem(item);
				}

				public String toString() {
					return item.toString();
				}
			};
		}
	}
}
