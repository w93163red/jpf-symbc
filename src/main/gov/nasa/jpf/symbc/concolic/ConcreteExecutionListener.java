/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * Symbolic Pathfinder (jpf-symbc) is licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package gov.nasa.jpf.symbc.concolic;

import gov.nasa.jpf.symbc.SymbolicInstructionFactory;
import gov.nasa.jpf.symbc.numeric.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.EXECUTENATIVE;
import gov.nasa.jpf.jvm.bytecode.JVMInvokeInstruction;
import gov.nasa.jpf.report.ConsolePublisher;
import gov.nasa.jpf.symbc.string.StringConstant;
import gov.nasa.jpf.vm.*;

public class ConcreteExecutionListener extends PropertyListenerAdapter {

	Config config;
	public static boolean debug = false;
	long ret;
	Object resultAttr;
	String[] partitions;

	public enum type {
		INT, DOUBLE, FLOAT, BYTE,
		SHORT, LONG, BOOLEAN, CHAR
	}

	public ConcreteExecutionListener(Config conf, JPF jpf) {
		jpf.addPublisherExtension(ConsolePublisher.class, this);
		this.config = conf;
	}

	@Override
	public void instructionExecuted(VM vm, ThreadInfo currentThread, Instruction nextInstruction, Instruction executedInstruction) {
		Instruction lastInsn =  executedInstruction;
		MethodInfo mi = executedInstruction.getMethodInfo();
//		System.out.println("Instruction: " + lastInsn);
//		System.out.println("mi: " + mi);
		if(lastInsn != null && lastInsn instanceof JVMInvokeInstruction) {

			boolean foundAnote = checkConcreteAnnotation(mi);
//			boolean foundFunction = ((JVMInvokeInstruction) lastInsn).getInvokedMethodName().contains("println");
			// if the function is annotated with concolic, it would run all the way here?
			// Need to skip System.out.println, seems VM did not handle println correctly
			if(foundAnote) {
				ThreadInfo ti = vm.getCurrentThread();
				StackFrame sf = ti.popAndGetModifiableTopFrame();
				FunctionExpression result =
					generateFunctionExpression(mi, (JVMInvokeInstruction)
													lastInsn, ti);
				vm.updatePath();
//				System.out.println("current cg: " + ((PCChoiceGenerator) vm.getSystemState().getChoiceGenerator()).getCurrentPC().stringPC());
				PathCondition pc = PathCondition.getPC(vm);
				System.out.println("current cg: " + pc.stringPC());
				checkReturnType(ti, mi, result);
				Instruction nextIns = sf.getPC().getNext();
				System.out.println("nextIns: " + nextIns);
				// TODO: LX: This might be helpful to add another check here to inject the result
				// LX: Get the current path constraint
				vm.getCurrentThread().skipInstruction(nextIns);
			}
		}
	}


	private boolean checkConcreteAnnotation(MethodInfo mi) {
		AnnotationInfo[] ai = mi.getAnnotations();
		boolean retVal = false;
		if(ai == null || ai.length == 0)  return retVal;
		for(int aIndex = 0; aIndex < ai.length; aIndex++) {
			AnnotationInfo annotation = ai[aIndex];
			if(annotation.getName().equals
							("gov.nasa.jpf.symbc.Concrete")) {
				if(annotation.valueAsString().
									equalsIgnoreCase("true"))
					retVal = true;
				else
					retVal = false;
			} else if (annotation.getName().equals
					("gov.nasa.jpf.symbc.Partition"))	 {

				partitions = annotation.getValueAsStringArray();
//				if (SymbolicInstructionFactory.debugMode)
//					for(int i = 0; i < partitions.length; i++)
//						System.out.println("discovered partition "+partitions[i]);
			}
		}
		return retVal;
	}

	private FunctionExpression generateFunctionExpression(MethodInfo mi,
			JVMInvokeInstruction ivk, ThreadInfo ti){

		System.out.println("ivk: " + ivk);
		System.out.println("mi: " + mi);

		// ivk is the statement within the method
		// If ivk is invokevirtual, the first attr will be the objectref, start from 1 is the actual arguments
		Object [] attrs = ivk.getArgumentAttrs(ti);
		System.out.println("attrs: " + attrs);
		Object [] values = ivk.getArgumentValues(ti);
		// means the function/method type
		String [] types = mi.getArgumentTypeNames();

		assert (attrs != null);
		// This assertion might have some problem that
		// ivk is the actual function and mi is the methodinfo

		assert (attrs.length == values.length &&
						values.length == types.length);
		int size = attrs.length;

		Class<?>[] args_type = new Class<?> [size];
		Expression[] sym_args = new Expression[size];

		Map<String,Expression> expressionMap =
			new HashMap<String, Expression>();
		LocalVarInfo[] localVars = mi.getLocalVars();
		System.out.println("values.size: " + values.length + " sym_args: " + sym_args.length);
		for(int argIndex = 0; argIndex < size && argIndex < types.length && argIndex < values.length; argIndex++) {
			Object attribute = attrs[argIndex];
			System.out.println(" values: " + values[argIndex] + " types: " + types[argIndex]);

			if(attribute == null) {
				sym_args[argIndex] = this.generateConstant(
								types[argIndex],
								values[argIndex]);
			} else {
				sym_args[argIndex] = (Expression) attribute;
				if(localVars.length > argIndex)
					expressionMap.put(localVars[argIndex].getName(),
						sym_args[argIndex]);


			}
			args_type[argIndex] = checkArgumentTypes(types[argIndex]);
		}

		ArrayList<PathCondition> conditions = Partition.
							createConditions(partitions, expressionMap);


		FunctionExpression result = new FunctionExpression(
				  mi.getClassName(),
				  mi.getName(), args_type, sym_args, conditions);

		return result;
	}


	private void checkReturnType(ThreadInfo ti, MethodInfo mi, Object resultAttr) {
		String retTypeName = mi.getReturnTypeName();
		StackFrame sf = ti.getModifiableTopFrame();
		sf.removeArguments(mi);
		if(retTypeName.equals("double") || retTypeName.equals("long")) {
			sf.pushLong(0);
			sf.setLongOperandAttr(resultAttr);
		} else {
			sf.push(0);
			sf.setOperandAttr(resultAttr);
		}
	}



	private Class<?> checkArgumentTypes(String typeVal) {
		if(typeVal.equals("int")) {
			return Integer.TYPE;
		} else if (typeVal.equals("double")) {
			return Double.TYPE;
		} else if (typeVal.equals("float")) {
			return Float.TYPE;
		} else if (typeVal.equals("long")) {
			return Long.TYPE;
		} else if (typeVal.equals("short")) {
			return Short.TYPE;
		}  else if (typeVal.equals("boolean")) {
			return Boolean.TYPE;
		} else {
			throw new RuntimeException("the type not handled :" + typeVal);
		}
	}

	private Expression generateConstant(String typeVal, Object value) {
		System.out.println(String.format("generate constant here: type: %s, value: %s", typeVal, value));
		if(typeVal.equals("int") && value.toString().contains("String")) {
			return new StringConstant("123456");
		}
		else if(typeVal.equals("int")) {
			return new IntegerConstant(Integer.parseInt
//					(value.toString()));
		("1"));
		} else if (typeVal.equals("double")) {
			return new RealConstant(Double.parseDouble
					(value.toString()));
//		("1.0"));
		} else if (typeVal.equals("float")) {
			return new RealConstant(Float.parseFloat
					(value.toString()));
		} else if (typeVal.equals("long")) {
			return new IntegerConstant((int) Long.parseLong
					(value.toString()));
		} else if (typeVal.equals("short")) {
			return new IntegerConstant((int) Short.parseShort
					(value.toString()));
		} else if (typeVal.equals("boolean")) {
			if(value.toString().equals("true")) {
				return new IntegerConstant(1);
			} else {
				return new IntegerConstant(0);
			}
		}
		// TODO: if it is String, might need to read from config
		else {
			throw new RuntimeException("the type not handled :" + typeVal);
		}
	}

}