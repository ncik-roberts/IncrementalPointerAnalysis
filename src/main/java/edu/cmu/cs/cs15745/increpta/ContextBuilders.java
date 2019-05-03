package edu.cmu.cs.cs15745.increpta;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.cs.cs15745.increpta.ast.Ast.Function;
import edu.cmu.cs.cs15745.increpta.ast.Ast.Variable;
import edu.cmu.cs.cs15745.increpta.util.Util.Unit;

// Static utility class consisting of different context builders.
public final class ContextBuilders {
  // I don't want ANY context on the nodes. Just always return the same value.
  public static final ContextBuilder<Unit> NO_CONTEXT = new ContextBuilder<>() {
    @Override
    public Unit initial(Function entryPoint) {
      return Unit.UNIT;
    }

    @Override
    public Unit merge(Unit originalContext, Function call) {
      return Unit.UNIT;
    }
    
    @Override
    public String toString() {
      return "No context";
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
        List<Variable> result = new ArrayList<>(originalContext);
        if (result.size() >= n) {
          result.remove(0);
        }
        result.add(call.name());
        return result;
      }

      @Override
      public String toString() {
        return String.format("%d-context", n);
      }
    };
  }
}
