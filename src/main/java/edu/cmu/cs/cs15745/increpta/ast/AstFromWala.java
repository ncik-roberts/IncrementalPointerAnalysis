package edu.cmu.cs.cs15745.increpta.ast;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstruction.IVisitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.Atom;

/**
 * Utility class from converting a WALA representation of a Java program into
 * the simplified Ast for our pointer analysis.
 */
public final class AstFromWala {
	// field for array loads/stores
	private static final Ast.Variable ARRAY_FIELD = new Ast.Variable("array");

	private final Ast ast;

	// Uniquely identify method signatures
	private final Map<Atom, Ast.Variable> selectorToMethodName = new HashMap<>();
	private final Ast.Variable methodName(Selector selector) {
		return selectorToMethodName.computeIfAbsent(selector.getName(), atom -> new Ast.Variable(atom.toString()));
	}

	// Uniquely identify classes
	private final Map<TypeName, Ast.Type> classToTypeName = new HashMap<>();
	private final Ast.Type type(IClass klass) {
		return classToTypeName.computeIfAbsent(klass.getName(), name -> new Ast.Type(name.toString()));
	}
	
	// Uniquely identify fields
	private final Map<Map.Entry<TypeName, Atom>, Ast.Variable> fieldToName = new HashMap<>();
	private final Ast.Variable field(FieldReference field) {
		return fieldToName.computeIfAbsent(
			new AbstractMap.SimpleEntry<>(field.getDeclaringClass().getName(), field.getName()),
			entry -> new Ast.Variable(entry.getKey() + "::"  + entry.getValue()));
	}

	public AstFromWala(CallGraph graph, Predicate<CGNode> isEntryPoint) {
		List<Ast.FunctionBody> entryPoints = new ArrayList<>();
		List<Ast.Function> functions = new ArrayList<>();
		for (CGNode node : graph) {
			IR ir = node.getIR();
			if (ir == null) continue;
			IMethod method = node.getMethod();
			Optional<Ast.Type> type = method.isStatic() ? Optional.empty()
					: Optional.of(type(method.getDeclaringClass()));
			Ast.Variable name = methodName(method.getSelector());
			List<Ast.Variable> params = new ArrayList<>();
			for (int i = 0; i < method.getNumberOfParameters(); i++) {
				params.add(new Ast.Variable("param" + i));
			}
			InstructionVisitor visitor = new InstructionVisitor(params);
			ir.visitAllInstructions(visitor);

			// Create function body based on the return of the function.
			Ast.FunctionBody body = new Ast.FunctionBody(visitor.instructions);
			if (isEntryPoint.test(node)) {
				entryPoints.add(body);
			}
			functions.add(new Ast.Function(name, type, params, body));
		}
		ast = new Ast(functions, entryPoints);
	}

	public Ast ast() {
		return ast;
	}

	// Visitor that builds a list of instructions.
	private final class InstructionVisitor implements IVisitor {

		final List<Ast.Instruction> instructions = new ArrayList<>();

		private final Map<Integer, Ast.Variable> variables = new HashMap<>();
		private Ast.Variable variable(int value) {
			return variables.computeIfAbsent(value, i -> new Ast.Variable(Integer.toString(i)));
		}
		private Optional<Ast.Variable> optionalDefVariable(SSAInstruction instruction) {
			return instruction.hasDef() ? Optional.of(variable(instruction.getDef())) : Optional.empty();
		}

		InstructionVisitor(List<Ast.Variable> params) {
			// Parameters numbered from 1 in Java bytecode
			for (int i = 0; i < params.size(); i++) {
				variables.put(i + 1, params.get(i));
			}
		}

		public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			Ast.Variable source = variable(instruction.getArrayRef());
			instructions.add(new Ast.Instruction.FieldRead(target, source, ARRAY_FIELD));
		}

		public void visitArrayStore(SSAArrayStoreInstruction instruction) {
			Ast.Variable target = variable(instruction.getArrayRef());
			Ast.Variable source = variable(instruction.getValue());
			instructions.add(new Ast.Instruction.FieldWrite(target, ARRAY_FIELD, source));
		}

		public void visitReturn(SSAReturnInstruction instruction) {
			// skip returns of primitive type
			if (instruction.returnsPrimitiveType() || instruction.returnsVoid()) {
				return;
			} else {
				instructions.add(new Ast.Instruction.Return(variable(instruction.getResult())));
			}
		}

		public void visitInvoke(SSAInvokeInstruction instruction) {
			Optional<Ast.Variable> target = optionalDefVariable(instruction);
			Ast.Variable method = methodName(instruction.getCallSite().getDeclaredTarget().getSelector());
			if (instruction.isDispatch()) { // It's virtual
				Ast.Variable source = variable(instruction.getReceiver());
				List<Ast.Variable> arguments = new ArrayList<>();
				for (int i = 1; i < instruction.getNumberOfPositionalParameters(); i++) { // Start at 1 to exclude receiver
					arguments.add(variable(instruction.getUse(i)));
				}
				instructions.add(new Ast.Instruction.Invocation(target, source, method, arguments));
			} else { // It's static
				List<Ast.Variable> arguments = new ArrayList<>();
				for (int i = 0; i < instruction.getNumberOfPositionalParameters(); i++) { // Start at 1 to exclude receiver
					arguments.add(variable(instruction.getUse(i)));
				}
				instructions.add(new Ast.Instruction.StaticInvocation(target, method, arguments));
			}
		}

		public void visitGet(SSAGetInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			Ast.Variable field = field(instruction.getDeclaredField());
			if (instruction.isStatic()) {
				instructions.add(new Ast.Instruction.Assignment(target, field));
			} else {
				Ast.Variable source = variable(instruction.getRef());
				instructions.add(new Ast.Instruction.FieldRead(target, source, field));
			}
		}

		public void visitPut(SSAPutInstruction instruction) {
			Ast.Variable field = field(instruction.getDeclaredField());
			Ast.Variable source = variable(instruction.getVal());
			if (instruction.isStatic()) {
				instructions.add(new Ast.Instruction.Assignment(field, source));
			} else {
				Ast.Variable target = variable(instruction.getDef());
				instructions.add(new Ast.Instruction.FieldWrite(target, field, source));
			}
		}

		/*
		 * void visitConversion(SSAConversionInstruction instruction) {} void
		 * visitComparison(SSAComparisonInstruction instruction) {} void
		 * visitSwitch(SSASwitchInstruction instruction) {} void
		 * instruction) {} void visitNew(SSANewInstruction instruction) {} void
		 * visitThrow(SSAThrowInstruction instruction) {} void
		 * visitMonitor(SSAMonitorInstruction instruction) {} void
		 * visitCheckCast(SSACheckCastInstruction instruction) {} void
		 * visitInstanceof(SSAInstanceofInstruction instruction) {} void
		 * visitPhi(SSAPhiInstruction instruction) {} void visitPi(SSAPiInstruction
		 * instruction) {} void visitGetCaughtException(SSAGetCaughtExceptionInstruction
		 * instruction) {} void visitLoadMetadata(SSALoadMetadataInstruction
		 * instruction) {}
		 */
	}
}
