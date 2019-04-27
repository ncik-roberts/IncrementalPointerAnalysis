package edu.cmu.cs.cs15745.increpta;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.PointsToGraph.AndersenNode.HeapItem;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Allocation;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Assignment;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.FieldRead;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.FieldWrite;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.Invocation;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Instruction.StaticInvocation;

public class PointsToGraph<Node> {
	
	// Disallow outside instantiation
	private PointsToGraph() { }
	
	private final Map<Node, Set<Node>> graph = new HashMap<>();
	
	/**
	 * Add directed edge from "from" to "to", returning whether the edge was
	 * already present.
	 */
	public boolean addEdge(Node from, Node to) {
		return graph.computeIfAbsent(from, unused -> new HashSet<>()).add(to);
	}
	
	public Set<Node> reachable(Node from) {
		Set<Node> seen = new HashSet<>();
		Deque<Node> toVisit = new ArrayDeque<>();
		toVisit.add(from);
		seen.add(from);
		while (!toVisit.isEmpty()) {
			Node curr = toVisit.pop();
			for (Node to : edges(curr)) {
				if (seen.add(to)) {
					toVisit.push(to);
				}
			}
		}
		return seen;
	}
	
	public Iterable<Node> edges(Node from) {
		Objects.requireNonNull(from, "from");
		return graph.getOrDefault(from, Collections.emptySet());
	}

	public static PointsToGraph<AndersenNode> fromAst(Ast ast) {
		PointsToGraph<AndersenNode> result = new PointsToGraph<>();
		
		// Most convenient way to have inner functions.
		var state = new Object() {
			Map<Ast.Variable, AndersenNode> variables = new HashMap<>();
			AndersenNode var(Ast.Variable in) {
				return variables.computeIfAbsent(in, AndersenNode::variable);
			}

			Map<Map.Entry<HeapItem, Ast.Variable>, AndersenNode> heapItemFields = new HashMap<>();
			AndersenNode heapItemField(HeapItem type, Ast.Variable field) {
				return heapItemFields.computeIfAbsent(
					new SimpleEntry<>(type, field),
					entry -> AndersenNode.heapItemField(type, field));
			}
			
			Map<AndersenNode, Set<HeapItem>> pointsTo = new HashMap<>();
			// Return true if the mapping wasn't already present.
			boolean pointTo(AndersenNode key, HeapItem val) {
				return pointsTo.computeIfAbsent(key, unused -> new HashSet<>()).add(val);
			}
			boolean pointToAll(AndersenNode key, Collection<HeapItem> vals) {
				return pointsTo.computeIfAbsent(key, unused -> new HashSet<>()).addAll(vals);
			}
			Set<HeapItem> pointsTo(AndersenNode key) {
				return pointsTo.getOrDefault(key, new HashSet<>());
			}
		};

		// See paper: "ECHO: Instantaneous In Situ Race Detection in the IDE"
		// p.780
		// https://parasol.tamu.edu/~jeff/academic/echo.pdf
		
		// First, construct the base graph
		var graphConstructor = new Object() {
			Ast.Instruction.Visitor<?> storingRootsIn(Set<AndersenNode> roots) {
				return new Ast.Instruction.StatefulVisitor() {
					// Rule 2
					@Override
					public void iterAssignment(Assignment a) {
						result.addEdge(state.var(a.source()), state.var(a.target()));
					}
					// Rule 1
					@Override
					public void iterAllocation(Allocation a) {
						var heapItem = new HeapItem(a.type());
						var node = AndersenNode.heapItem(heapItem);
						state.pointTo(node, heapItem);
						roots.add(node);
						result.addEdge(node, state.var(a.target()));
					}
					
					// Rule 5 specialized for static functions
					@Override
					public void iterStaticInvocation(StaticInvocation s) {
						Ast.Function f = ast.staticFunction(s.method());
						var params = f.params();
						var args = s.arguments();
						for (int i = 0; i < params.size(); i++) {
							// Edge goes from arg to param
							result.addEdge(state.var(args.get(i)), state.var(params.get(i)));
						}
						
						// Add edge from return z to target x
						s.target().ifPresent(target -> {
							var x = state.var(target);
							for (var ret : f.body().returns()) {
								var z = state.var(ret.returned());
								result.addEdge(x, z);
							}
						});
					}
				}.visitor();
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

			// Connect two nodes, returning whether points-to sets were updated.
			// Also propagate points-to set through dfs.
			private boolean connect(AndersenNode from, AndersenNode to) {
				if (result.addEdge(from, to)) {
					boolean anyUpdated = true;
					var pointsTo = state.pointsTo(from);
					for (var node : result.reachable(to)) {
						anyUpdated |= state.pointToAll(node, pointsTo);
					}
					return anyUpdated;
				} else {
					return false;
				}
			}

			// Rule 4; x.f = y
			@Override
			public void iterFieldWrite(FieldWrite fw) {
				var x = state.var(fw.target());
				var f = fw.field();
				var y = state.var(fw.source());
				for (var O : state.pointsTo(x)) {
					var Of = state.heapItemField(O, f);
					connect(y, Of);
				}
			}

			// Rule 3; x = y.f
			@Override
			public void iterFieldRead(FieldRead fr) {
				var x = state.var(fr.target());
				var y = state.var(fr.source());
				var f = fr.field();
				for (var O : state.pointsTo(y)) {
					var Of = state.heapItemField(O, f);
					connect(Of, x);
				}
			}

			// Rule 5: x = o.m(y), calling m(y'){ return z }
			@Override
			public void iterInvocation(Invocation inv) {
				var o = state.var(inv.source());
				for (var C : state.pointsTo(o)) {
					Ast.Function f = ast.instanceMethods(C.type(), inv.method());
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
					if (inv.target().isPresent()) {
						var x = state.var(inv.target().get());
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
			}
		}.visitor();
		
		// Now we use the on-the-fly alg to process the rest.
		while (!workList.isEmpty()) {
			var curr = workList.remove();
			Set<AndersenNode> roots = new HashSet<>();
			for (var param : curr.params()) {
				roots.add(state.var(param));
			}
			for (var i : curr.body().instructions()) {
				i.accept(graphConstructor.storingRootsIn(roots));
			}
			for (var root : roots) {
				var rootPointsTo = state.pointsTo(root);
				for (var reachable : result.reachable(root)) {
					state.pointToAll(reachable, rootPointsTo);
				}
			}
			for (var i : curr.body().instructions()) {
				i.accept(onTheFly);
			}
		}
		
		System.out.println(result.graph);
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
	public static abstract class AndersenNode {
		// Disallow external subclassing
		private AndersenNode() {}
		
		public static final class HeapItem {
			private final Ast.Type type;
			public HeapItem(Ast.Type type) {
				this.type = type;
			}
			public Ast.Type type() { return type; }
			public String toString() {
				return String.format("%s%x", type, hashCode());
			}
		}
		
		public interface Visitor<T> {
			T visitHeapItem(HeapItem item);
			T visitHeapItemField(HeapItem item, Ast.Variable field);
			T visitVariable(Ast.Variable item);
		}

		public abstract <T> T accept(Visitor<T> visitor);
		
		public static AndersenNode variable(Ast.Variable item) {
			return new AndersenNode() {
				@Override
				public <S> S accept(Visitor<S> visitor) {
					return visitor.visitVariable(item);
				}
				
				public String toString() {
					return item.toString();
				}
			};
		}

		public static AndersenNode heapItemField(HeapItem item, Ast.Variable field) {
			return new AndersenNode() {
				@Override
				public <S> S accept(Visitor<S> visitor) {
					return visitor.visitHeapItemField(item, field);
				}

				public String toString() {
					return item.toString() + "." + field.toString();
				}
			};
		}

		public static AndersenNode heapItem(HeapItem item) {
			return new AndersenNode() {
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
