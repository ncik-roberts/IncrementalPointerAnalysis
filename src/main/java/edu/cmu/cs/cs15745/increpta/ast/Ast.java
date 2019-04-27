package edu.cmu.cs.cs15745.increpta.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Instances of this class represent a full Ast for a program in Java0. An "Ast"
 * consists of a sequence of:
 * <ul>
 * <li>Java functions. (parameters + function bodies)</li>
 * <li>Entry points. (function bodies)</li>
 * </ul>
 */
public final class Ast {
	private final List<AstFunction> functions;
	private final List<AstFunctionBody> entryPoints;

	public Ast(List<AstFunction> functions, List<AstFunctionBody> entryPoints) {
		this.functions = new ArrayList<>(functions);
		this.entryPoints = new ArrayList<>(entryPoints);
	}

	public List<AstFunction> functions() { return functions; }
	public List<AstFunctionBody> entryPoints() { return entryPoints; }
	
	@Override
	public String toString() {
		return join("\n", functions) + join("\n", entryPoints);
	}

	/**
	 * An AstFunction is a pair of:
	 * <ul>
	 * <li>The parameters to the function.</li>
	 * <li>The body of the function.</li>
	 * </ul>
	 */
	public static final class AstFunction {
		private final List<AstVariable> params;
		private final AstFunctionBody body;

		public AstFunction(List<AstVariable> params, AstFunctionBody body) {
			this.params = new ArrayList<>(params);
			this.body = body;
		}

		public List<AstVariable> params() { return params; }
		public AstFunctionBody body() { return body; }
		
		@Override
		public String toString() {
			return String.format("function %s(%s) {\n%s}\n");
		}
	}

	/**
	 * A type is just a string.
	 */
	public static final class AstType {
		private final String name;
		public AstType(String name) { this.name = name; }
		public String name() { return name; }
		public String toString() { return name; }
	}

	/**
	 * A variable is just a String.
	 */
	public static final class AstVariable {
		private final String name;
		public AstVariable(String name) { this.name = name; }
		public String name() { return name; }
		public String toString() { return name; }
	}

	/**
	 * An AstFunctionBody is just a list of instructions.
	 */
	public static final class AstFunctionBody {
		private final List<AstInstruction> instructions;

		public AstFunctionBody(List<AstInstruction> instructions) {
			this.instructions = new ArrayList<>(instructions);
		}

		public List<AstInstruction> instructions() {
			return instructions;
		}
		
		public String toString() {
			return "\t" + join("\n\t", instructions) + "\n";
		}
	}

	/**
	 * An AstInstruction is one of the following:
	 * <ul>
	 * <li>x = y (simple assignment)</li>
	 * <li>x = new O (allocation of heap object)</li>
	 * <li>x.f = y (field write)</li>
	 * <li>x = y.f (field read)</li>
	 * <li>x = y.f(x1, ..., xn) (method invocation)</li>
	 * </ul>
	 */
	public static abstract class AstInstruction {
		// Disallow external subclassing.
		private AstInstruction() {}
		
		/**
		 * Use the visitor pattern to return a result based on node type.
		 */
		public <T> T accept(Visitor<T> visitor) {
			if (this instanceof Assignment) return visitor.visitAssignment((Assignment) this);
			if (this instanceof Allocation) return visitor.visitAllocation((Allocation) this);
			if (this instanceof FieldWrite) return visitor.visitFieldWrite((FieldWrite) this);
			if (this instanceof FieldRead)  return visitor.visitFieldRead((FieldRead) this);
			if (this instanceof Invocation) return visitor.visitInvocation((Invocation) this);
			throw new IllegalStateException("Impossible: unknown subclass of AstInstruction.");
		}

		/**
		 * Visitor for AstInstruction's fixed set of subclasses.
		 */
		public interface Visitor<T> {
			T visitAssignment(Assignment a);
			T visitAllocation(Allocation a);
			T visitFieldWrite(FieldWrite fw);
			T visitFieldRead(FieldRead fr);
			T visitInvocation(Invocation i);
		}

		/**
		 * x = y (simple assignment)
		 */
		public static final class Assignment extends AstInstruction {
			private final AstVariable target;
			private final AstVariable source;

			/**
			 * target = source
			 */
			public Assignment(AstVariable target, AstVariable source) {
				this.target = target;
				this.source = source;
			}

			public AstVariable target() { return target; }
			public AstVariable source() { return source; }

			@Override
			public String toString() {
				return String.format("%s = %s", target, source);
			}
		}

		/**
		 * x = new O (allocation of heap object)
		 */
		public static final class Allocation extends AstInstruction {
			private final AstVariable target;
			private final AstType type;

			/**
			 * target = new type
			 */
			public Allocation(AstVariable target, AstType type) {
				this.target = target;
				this.type = type;
			}

			public AstVariable target() { return target; }
			public AstType type() { return type; }

			@Override
			public String toString() {
				return String.format("%s = new %s", target, type);
			}
		}

		/**
		 * x.f = y (field write)
		 */
		public static final class FieldWrite extends AstInstruction {
			private final AstVariable target;
			private final AstVariable field;
			private final AstVariable source;

			/**
			 * target.field = source
			 */
			public FieldWrite(AstVariable target, AstVariable field, AstVariable source) {
				this.target = target;
				this.field = field;
				this.source = source;
			}

			public AstVariable target() { return target; }
			public AstVariable field() { return field; }
			public AstVariable source() { return source; }

			@Override
			public String toString() {
				return String.format("%s.%s = %s", target, field, source);
			}
		}

		/**
		 * x = y.f (field read)
		 */
		public static final class FieldRead extends AstInstruction {
			private final AstVariable target;
			private final AstVariable source;
			private final AstVariable field;

			/**
			 * target.field = source
			 */
			public FieldRead(AstVariable target, AstVariable source, AstVariable field) {
				this.target = target;
				this.source = source;
				this.field = field;
			}

			public AstVariable target() { return target; }
			public AstVariable source() { return source; }
			public AstVariable field() { return field; }
			
			@Override
			public String toString() {
				return String.format("%s = %s.%s", target, source, field);
			}
		}

		/**
		 * x = y.f(x1, ..., xn) (method invocation
		 */
		public static class Invocation extends AstInstruction {
			private final AstVariable target;
			private final AstVariable source;
			private final AstVariable method;
			private final List<AstVariable> arguments;

			/**
			 * target = source.method(...arguments...)
			 */
			public Invocation(AstVariable target, AstVariable source, AstVariable method, List<AstVariable> arguments) {
				this.target = target;
				this.source = source;
				this.method = method;
				this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
			}

			public AstVariable target() { return target; }
			public AstVariable source() { return source; }
			public AstVariable method() { return method; }
			public List<AstVariable> arguments() { return arguments; }
			
			@Override
			public String toString() {
				return String.format("%s = %s.%s(%s)", target, source, method, join(",", arguments));
			}
		}
	}

	// String.join calling "toString" on each constituent element of the iterable.
	private static String join(CharSequence delimiter, Iterable<?> iter) {
		StringBuilder result = new StringBuilder();
		for (Iterator<?> it = iter.iterator(); it.hasNext(); ) {
			result.append(it.next());
			if (it.hasNext()) {
				result.append(delimiter);
			}
		}
		return result.toString();
	}
}
