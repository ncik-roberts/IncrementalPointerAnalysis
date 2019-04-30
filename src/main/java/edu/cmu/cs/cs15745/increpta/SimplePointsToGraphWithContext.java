package edu.cmu.cs.cs15745.increpta;

import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node.HeapItem;
import edu.cmu.cs.cs15745.increpta.SimplePointsToGraphWithContext.Node;
import edu.cmu.cs.cs15745.increpta.ast.Ast;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Points-to graph with fixed node type (Node) + context C.
 * @param <C>
 */
public class SimplePointsToGraphWithContext<C> extends SimplePointsToGraph<Pair<Node, C>, Pair<HeapItem, C>> {
	
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