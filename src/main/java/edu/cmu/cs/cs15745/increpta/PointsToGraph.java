package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.cmu.cs.cs15745.increpta.PointsToGraph.Node.HeapItem;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.util.MultiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Points-to-graph.
 * @param <C> The context associated with each node.
 */
public class PointsToGraph<C> {
	
	// Disallow outside instantiation
	private PointsToGraph() { }
	
	private final MultiMap<Pair<Node, C>, Pair<Node, C>> graph = new MultiMap<>();
	private final MultiMap<Pair<Node, C>, Pair<HeapItem, C>> pointsTo = new MultiMap<>();
	
	/**
	 * Add directed edge from "from" to "to", returning whether the edge was
	 * already present.
	 */
	public boolean addEdge(Pair<Node, C> from, Pair<Node, C> to) {
		return graph.getSet(from).add(to);
	}
	
	public Set<Pair<HeapItem, C>> pointsTo(Pair<Node, C> key) {
		return pointsTo.getSet(key);
	}
			
	public interface DfsIterator<C> extends Iterator<Pair<Node, C>> {
		void abandonBranch();
	}

	// Iterator that allows for a dfs branch to be abandoned
	public DfsIterator<C> dfs(Pair<Node, C> from) {
		Set<Pair<Node, C>> seen = new HashSet<>();
		Deque<Pair<Node, C>> toVisit = new ArrayDeque<>();
		toVisit.add(from);
		seen.add(from);
		return new DfsIterator<>() {
			Pair<Node, C> curr = null;
			@Override public boolean hasNext() {
				if (!toVisit.isEmpty()) {
					return true;
				}
				if (curr == null) {
					return false;
				}
				for (Pair<Node, C> to : edges(curr)) {
					if (!seen.contains(to)) {
						return true;
					}
				}
				return false;
			}
			@Override public Pair<Node, C> next() {
				if (curr != null) {
					for (Pair<Node, C> to : edges(curr)) {
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
	
	public Iterable<Pair<Node, C>> edges(Pair<Node, C> from) {
		return graph.getOrDefault(from, Collections.emptySet());
	}

	public static <C> PointsToGraph<C> fromAst(Ast ast, ContextBuilder<C> contextBuilder) {
		return new PointsToGraphBuilder<C>(ast, new PointsToGraph<>(), contextBuilder).build();
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
		
		/**
		 * Visitor for the different kinds of nodes.
		 * @param <T>
		 */
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
