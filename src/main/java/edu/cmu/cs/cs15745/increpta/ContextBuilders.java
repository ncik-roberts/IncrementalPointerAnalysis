package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.cs.cs15745.increpta.ast.Ast.Function;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Variable;

// Static utility class consisting of different context builders.
public final class ContextBuilders {
	private enum Singleton {
		SINGLETON;
	}
	
	// I don't want ANY context on the nodes. Just always return the same value.
	public static final ContextBuilder<?> NO_CONTEXT = new ContextBuilder<Singleton>() {

		@Override
		public Singleton initial(Function entryPoint) {
			return Singleton.SINGLETON;
		}

		@Override
		public Singleton merge(Singleton originalContext, Function call) {
			return Singleton.SINGLETON;
		}
	};
	
	// Give me a finite calling context, capped at n:
	public static ContextBuilder<List<Variable>> nCallContext(int n) {
		return new ContextBuilder<>() {
			@Override
			public List<Variable> initial(Function entryPoint) {
				List<Variable> result = new ArrayList<>();
				result.add(entryPoint.name());
				return result;
			}

			@Override
			public List<Variable> merge(List<Variable> originalContext, Function call) {
				List<Variable> result =  new ArrayList<>(originalContext);
				if (result.size() >= n) {
					result.remove(0);
				}
				result.add(call.name());
				return result;
			}
			
		};
	}
}
