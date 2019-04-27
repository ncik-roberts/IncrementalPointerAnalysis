package edu.cmu.cs.cs15745.increpta.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.AbstractMap.SimpleEntry;

/**
 * Instances of this class represent a full Ast for a program in Java0. An "Ast"
 * consists of a sequence of:
 * <ul>
 * <li>Functions. (A map from function names to function definitions.)
 * <li>Entry points.
 * </ul>
 */
public final class Ast {
	private final Map<Variable, Function> staticFunctions = new HashMap<>();
	private final Map<Map.Entry<Type, Variable>, Function> instanceMethods = new HashMap<>();
	private final List<Function> entryPoints;

	public Ast(List<Function> functions, List<Function> entryPoints) {
		for (Function f : functions) {
			if (f.staticness == Function.Staticness.STATIC) { 
				staticFunctions.put(f.name(), f);
			} else {
				instanceMethods.put(new SimpleEntry<>(f.type(), f.name()), f);
			}
		}
		this.entryPoints = new ArrayList<>(entryPoints);
	}

	public Function staticFunction(Variable name) {
		return Objects.requireNonNull(staticFunctions.get(name), name.name());
	}

	public Function instanceMethods(Type type, Variable name) {
		return Objects.requireNonNull(instanceMethods.get(new SimpleEntry<>(type, name)));
	}

	public List<Function> entryPoints() {
		return entryPoints;
	}

	@Override
	public String toString() {
		return join("\n", staticFunctions.values()) + join("\n", instanceMethods.values()) + join("\n", entryPoints);
	}

	/**
	 * A Function is a record of:
	 * <ul>
	 * <li>The name of the function.</li>
	 * <li>The type the method is associated with.</li>
	 * <li>The parameters to the function.</li>
	 * <li>The body of the function.</li>
	 * <li>Whether the function is static.</li>
	 * </ul>
	 */
	public static final class Function {
		private final Variable name;
		private final Type type;
		private final List<Variable> params;
		private final FunctionBody body;
		private final Staticness staticness;

		public Function(Variable name, Type type, List<Variable> params, FunctionBody body, Staticness staticness) {
			this.name = Objects.requireNonNull(name);
			this.type = Objects.requireNonNull(type);
			this.params = new ArrayList<>(params);
			this.body = Objects.requireNonNull(body);
			this.staticness = Objects.requireNonNull(staticness);
		}

		public Variable name() {
			return name;
		}

		public Type type() {
			return type;
		}

		public List<Variable> params() {
			return params;
		}

		public FunctionBody body() {
			return body;
		}
		
		public Staticness staticness() {
			return staticness;
		}

		@Override
		public String toString() {
			return String.format("%s::%s::%s(%s) {\n%s}\n", staticness, type, name, join(", ", params), body);
		}

		public enum Staticness {
			STATIC, VIRTUAL;
			public static Staticness fromBoolean(boolean isStatic) {
				return isStatic ? STATIC : VIRTUAL;
			}

			@Override
			public String toString() {
				return this == STATIC ? "static" : "virtual";
			}
		}
	}

	/**
	 * A type is just a string.
	 */
	public static final class Type {
		private final String name;

		public Type(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		public String toString() {
			return name;
		}
	}

	/**
	 * A variable is just a String.
	 */
	public static final class Variable {
		private final String name;

		public Variable(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		public String toString() {
			return name;
		}
	}

	/**
	 * A FunctionBody is just a list of instructions.
	 * We also track returns for convenience.
	 */
	public static final class FunctionBody {
		private final List<Instruction> instructions = new ArrayList<>();
		private final List<Instruction.Return> returns = new ArrayList<>();


		public FunctionBody(List<Instruction> instructions) {
			var visitor = new Instruction.StatefulVisitor() {
				@Override
				public void iterReturn(Instruction.Return ret) {
					returns.add(ret);
				}
			}.visitor();
			for (Instruction i : instructions) {
				this.instructions.add(i);
				i.accept(visitor);
			}
		}

		public List<Instruction> instructions() {
			return instructions;
		}
		
		public List<Instruction.Return> returns() {
			return returns;
		}

		public String toString() {
			return "\t" + join("\n\t", instructions) + "\n";
		}
	}

	/**
	 * An Instruction is one of the following:
	 * <ul>
	 * <li>x = y (simple assignment)</li>
	 * <li>x = new O (allocation of heap object)</li>
	 * <li>x.f = y (field write)</li>
	 * <li>x = y.f (field read)</li>
	 * <li>x = f(x1, ..., xn) (static method invocation)</li>
	 * <li>x = y.f(x1, ..., xn) (method invocation)</li>
	 * <li>return x (return)</li>
	 * </ul>
	 */
	public static abstract class Instruction {
		// Disallow external subclassing.
		private Instruction() {
		}

		/**
		 * Visitor for Instruction's fixed set of subclasses.
		 */
		public interface Visitor<T> {
			T visitAssignment(Assignment a);
			T visitAllocation(Allocation a);
			T visitFieldWrite(FieldWrite fw);
			T visitFieldRead(FieldRead fr);
			T visitStaticInvocation(StaticInvocation i);
			T visitInvocation(Invocation i);
			T visitReturn(Return i);
		}
		
		/**
		 * Convenience class for unit-returning visitor.
		 */
		public static abstract class StatefulVisitor {
			public void iterAssignment(Assignment a) { }
			public void iterAllocation(Allocation a) { }
			public void iterFieldWrite(FieldWrite fw) { }
			public void iterFieldRead(FieldRead fr) { }
			public void iterStaticInvocation(StaticInvocation i) { }
			public void iterInvocation(Invocation i) { }
			public void iterReturn(Return i) { }
			public Visitor<?> visitor() {
				return new Visitor<Object>() {
					public Object visitAssignment(Assignment a) {
						iterAssignment(a);
						return null;
					}

					@Override
					public Object visitAllocation(Allocation a) {
						iterAllocation(a);
						return null;
					}

					@Override
					public Object visitFieldWrite(FieldWrite fw) {
						iterFieldWrite(fw);
						return null;
					}

					@Override
					public Object visitFieldRead(FieldRead fr) {
						iterFieldRead(fr);
						return null;
					}

					@Override
					public Object visitStaticInvocation(StaticInvocation i) {
						iterStaticInvocation(i);
						return null;
					}

					@Override
					public Object visitInvocation(Invocation i) {
						iterInvocation(i);
						return null;
					}

					@Override
					public Object visitReturn(Return i) {
						iterReturn(i);
						return null;
					}
				};
			}
		}

		public abstract <T> T accept(Visitor<T> visitor);

		/**
		 * x = y (simple assignment)
		 */
		public static final class Assignment extends Instruction {
			private final Variable target;
			private final Variable source;

			/**
			 * target = source
			 */
			public Assignment(Variable target, Variable source) {
				this.target = Objects.requireNonNull(target);
				this.source = Objects.requireNonNull(source);
			}

			public Variable target() {
				return target;
			}

			public Variable source() {
				return source;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitAssignment(this);
			}

			@Override
			public String toString() {
				return String.format("%s = %s", target, source);
			}
		}

		/**
		 * x = new O (allocation of heap object)
		 */
		public static final class Allocation extends Instruction {
			private final Variable target;
			private final Type type;

			/**
			 * target = new type
			 */
			public Allocation(Variable target, Type type) {
				this.target = Objects.requireNonNull(target);
				this.type = Objects.requireNonNull(type);
			}

			public Variable target() {
				return target;
			}

			public Type type() {
				return type;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitAllocation(this);
			}

			@Override
			public String toString() {
				return String.format("%s = new %s", target, type);
			}
		}

		/**
		 * x.f = y (field write)
		 */
		public static final class FieldWrite extends Instruction {
			private final Variable target;
			private final Variable field;
			private final Variable source;

			/**
			 * target.field = source
			 */
			public FieldWrite(Variable target, Variable field, Variable source) {
				this.target = Objects.requireNonNull(target);
				this.field = Objects.requireNonNull(field);
				this.source = Objects.requireNonNull(source);
			}

			public Variable target() {
				return target;
			}

			public Variable field() {
				return field;
			}

			public Variable source() {
				return source;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitFieldWrite(this);
			}

			@Override
			public String toString() {
				return String.format("%s.%s = %s", target, field, source);
			}
		}

		/**
		 * x = y.f (field read)
		 */
		public static final class FieldRead extends Instruction {
			private final Variable target;
			private final Variable source;
			private final Variable field;

			/**
			 * target.field = source
			 */
			public FieldRead(Variable target, Variable source, Variable field) {
				this.target = Objects.requireNonNull(target);
				this.source = Objects.requireNonNull(source);
				this.field = Objects.requireNonNull(field);
			}

			public Variable target() {
				return target;
			}

			public Variable source() {
				return source;
			}

			public Variable field() {
				return field;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitFieldRead(this);
			}

			@Override
			public String toString() {
				return String.format("%s = %s.%s", target, source, field);
			}
		}

		/**
		 * x = f(x1, ..., xn) (static function invocation)
		 */
		public static class StaticInvocation extends Instruction {
			private final Optional<Variable> target;
			private final Variable method;
			private final List<Variable> arguments;

			/**
			 * target = source.method(...arguments...)
			 */
			public StaticInvocation(Optional<Variable> target, Variable method, List<Variable> arguments) {
				this.target = Objects.requireNonNull(target);
				this.method = Objects.requireNonNull(method);
				this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
			}

			public Optional<Variable> target() {
				return target;
			}

			public Variable method() {
				return method;
			}

			public List<Variable> arguments() {
				return arguments;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitStaticInvocation(this);
			}

			@Override
			public String toString() {
				String call = String.format("%s(%s)", method, join(",", arguments));
				return target.map(t -> String.format("%s = %s", t, call)).orElse(call);
			}
		}

		/**
		 * x = y.f(x1, ..., xn) (method invocation)
		 */
		public static class Invocation extends Instruction {
			private final Optional<Variable> target;
			private final Variable source;
			private final Variable method;
			private final List<Variable> arguments;

			/**
			 * target = source.method(...arguments...)
			 */
			public Invocation(Optional<Variable> target, Variable source, Variable method, List<Variable> arguments) {
				this.target = Objects.requireNonNull(target);
				this.source = Objects.requireNonNull(source);
				this.method = Objects.requireNonNull(method);
				this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
			}

			public Optional<Variable> target() {
				return target;
			}

			public Variable source() {
				return source;
			}

			public Variable method() {
				return method;
			}

			public List<Variable> arguments() {
				return arguments;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitInvocation(this);
			}

			@Override
			public String toString() {
				String call = String.format("%s.%s(%s)", source, method, join(",", arguments));
				return target.map(t -> String.format("%s = %s", t, call)).orElse(call);
			}
		}

		/**
		 * return x (return)
		 */
		public static class Return extends Instruction {
			private final Variable returned;

			/**
			 * return returned
			 */
			public Return(Variable returned) {
				this.returned = Objects.requireNonNull(returned);
			}

			public Variable returned() {
				return returned;
			}

			public <T> T accept(Visitor<T> visitor) {
				return visitor.visitReturn(this);
			}

			@Override
			public String toString() {
				return String.format("return %s", returned);
			}
		}
	}

	// String.join calling "toString" on each constituent element of the iterable.
	private static String join(CharSequence delimiter, Iterable<?> iter) {
		StringBuilder result = new StringBuilder();
		for (Iterator<?> it = iter.iterator(); it.hasNext();) {
			result.append(it.next());
			if (it.hasNext()) {
				result.append(delimiter);
			}
		}
		return result.toString();
	}
}
