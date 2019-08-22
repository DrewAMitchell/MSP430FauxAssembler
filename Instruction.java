package edu.ttu.drewmitchell;

import java.util.regex.Pattern;

public class Instruction {
	private String name, operator, opcode;
	private boolean byteOpPossible = false;
	private Pattern validRegex;
	
	public Instruction(String name, String operator, String opcode, String validFormatsRegex, boolean byteOpPossible) {
		this.name = name;
		this.operator = operator;
		this.opcode = opcode;
		this.validRegex = Pattern.compile(validFormatsRegex);
		this.byteOpPossible = byteOpPossible;
	}
	
	public String assemble(boolean byteOp, String sourceParam, String destParam) {
		return InstructionFactory.assemble(this, byteOp, sourceParam, destParam);
	}
	
	public String getName() {
		return name;
	}
	
	public String getOperator() {
		return operator;
	}
	
	public String getOpCode() {
		return opcode;
	}
	
	public Pattern getValidRegex() {
		return validRegex;
	}
	
	public boolean hasByteOp() {
		return byteOpPossible;
	}
}