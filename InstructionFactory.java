package edu.ttu.drewmitchell;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstructionFactory {
	static Pattern sopParam = Pattern.compile("^(@?R[\\d]{1,2}\\+?|[#&]?[A-Za-z][\\w\\+\\d]+)$");
	static Pattern dopSource = Pattern.compile("^((?:@?R[\\d]{1,2}\\+?)|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:#?(?:0x)?[\\dA-Fa-f]{1,4})|(?:[#&]?[A-Za-z][\\w\\+\\d]+))$");
	static Pattern dopDest = Pattern.compile("^((?:R[\\d]{1,2})|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:0x[\\da-fA-F]{1,4})|(?:&?[A-Za-z][\\w\\+\\d]+))$");
	
	static Pattern registerMode = Pattern.compile("^R(\\d{1,2})$");
	static Pattern indexedMode = Pattern.compile("^(\\d{1,4})\\(R(\\d{1,2})\\)$");
	static Pattern symbolicMode = Pattern.compile("^([A-Za-z][\\w\\d]+)$");
	static Pattern absoluteMode = Pattern.compile("^&([A-Za-z][\\w\\d]+)$");
	static Pattern indirectRegisterMode = Pattern.compile("^@R(\\d{1,2})$"); // Graduate students only
	static Pattern indirectAutoInc = Pattern.compile("^@R(\\d{1,2})\\+$");
	static Pattern immediateMode = Pattern.compile("^#((?:0x)?[\\da-f]{1,4}|[a-z][\\w\\+\\d]+)$", Pattern.CASE_INSENSITIVE);
	
	//BEGIN CONSTANTS/REGISTERS (avoiding parsing header file by selectively adding undefined ones from the given source files manually, could make a separate & easily parsable file to dynamically add more)
	static final int R0 = 0x0000; // PC
	static final int R1 = 0x0001; // SP
	static final int R2 = 0x0002; // SR/CG1
	static final int R3 = 0x0003;
	static final int P1OUT = 0x0021;
	static final int P1REN = 0x0027;
	static final int P2OUT = 0x0029;
	static final int WDTCTL = 0x0120;
	static final int WDTPW = 0x5A00;
	static final int WDTHOLD = 0x0080;
	//END CONSTANTS/REGISTERS
	
	static final List<Instruction> inst = new ArrayList<Instruction>(); // Populated at bottom of class

	// Single operator handling
	public static String assemble(Instruction in, boolean byteOp, String sourceParam, String destParam) { //MOV, ADD, ADDC, AND, SUB, SUBC, CMP, DADD, BIT, BIC, BIS, XOR
		String src = "%SRC%", dst = "%DST%";
		String correctAssembled = in.getOpCode() + src+dst;
		int AdBWAs = byteOp ? 4 : 0;
		
		if(dopSource.matcher(sourceParam).find()) {
			// Legitimate addressing mode so far
			Matcher reg = registerMode.matcher(sourceParam);
			Matcher index = indexedMode.matcher(sourceParam);
			//Matcher symbol = symbolicMode.matcher(sourceParam);
			Matcher abs = absoluteMode.matcher(sourceParam);
			//Matcher indReg = indirectRegisterMode.matcher(sourceParam);
			//Matcher indAuto = indirectAutoInc.matcher(sourceParam);
			Matcher immed = immediateMode.matcher(sourceParam);
			if(reg.find()) {
				String sReg = reg.group(1);
				correctAssembled = correctAssembled.replace("s", hexFromDec(sReg)).replace(src, "");
				AdBWAs += 0;
			}
			else if (index.find()) {
				String offset = index.group(1);
				String sReg = index.group(2);
				// TODO Poor hardcoding, likely should create an aside table to remove chained logic
				if(sReg.equalsIgnoreCase("PC")) {
					sReg = "0";
				} else if(sReg.equalsIgnoreCase("SP")) {
					sReg = "1";
				} else if (sReg.equalsIgnoreCase("SR")) {
					sReg = "2";
				}
				correctAssembled = correctAssembled.replace("s", hexFromDec(sReg)).replace(src, String.format("%04X", Integer.parseInt(offset)));
				AdBWAs += 1;
			}
//			else if (symbol.find()) {
//				String name = symbol.group(1);
//				correctAssembled = correctAssembled.replace("s", newChar)
//				if(AssemblerPhase3.symbolTable.containsKey(name)) {
//					correctAssembled += AssemblerPhase3.hexForm(AssemblerPhase3.symbolTable.get(name));
//				}
//				else correctAssembled += "%'"+name+"'%";
//				AdBWAs += 1;
//			}
			else if (abs.find()) {
				String name = abs.group(1);
				String replace = "%'"+name+"'%";
				correctAssembled = correctAssembled.replace("s", "2"); // for status register
				if(AssemblerPhase3.symbolTable.containsKey(name)) {
					replace = AssemblerPhase3.hexForm(AssemblerPhase3.symbolTable.get(name));
				}
				
				correctAssembled = correctAssembled.replace(src, replace);
				AdBWAs += 1;
			}
//			else if (indReg.find()) {
//				AdBWAs += 2;
//			}
//			else if (indAuto.find()) {
//				AdBWAs += 3;
//			}
			else if (immed.find()) {
				String cap = immed.group(1); // No # prefix, we know it was there
				String replace = "%'"+cap+"'%";
				correctAssembled = correctAssembled.replace("s", "0"); // Set s-reg to 0 since it's a constant (not planning to use constant generator)
				if(cap.matches("^(?:0x)?[\\da-fA-F]+$")) { // Hex/decimal string
					if(cap.startsWith("0x")) {
						replace = String.format("%04X", Integer.decode(cap)); // Parse it as hexadecimal
					}
					else replace = String.format("%04X", Integer.parseInt(cap, 10)); // Parse it as decimal
				}
				else { // Using defined constants, check symbol table
					int immedValue = 0;
					if(cap.contains("+")) { // We must handle immediate summations
						String[] constants = cap.split("\\+");
						for(String c : constants) {
							if(AssemblerPhase3.symbolTable.containsKey(c)) {
								immedValue += AssemblerPhase3.symbolTable.get(c);
							}
							else immedValue += getHardcodedValue(c);
						}
					}
					else { // We're just using one value for immediate mode
						if(AssemblerPhase3.symbolTable.containsKey(cap)) {
							immedValue += AssemblerPhase3.symbolTable.get(cap);
						}
						else immedValue += getHardcodedValue(cap);
					}
					if(immedValue != 0) replace = String.format("%04X", immedValue); // Parse it as decimal, looking to see if it's changed
				}
				
				correctAssembled = correctAssembled.replace(src, replace);
				debug("simmed: " + cap);
				AdBWAs += 3;
			}
			else {
				return "ERROR - Invalid source parameter syntax.";
			}
		}
		if(dopDest.matcher(destParam).find()) {
			Matcher reg = registerMode.matcher(destParam);
			Matcher index = indexedMode.matcher(destParam);
			Matcher symbol = symbolicMode.matcher(destParam);
			Matcher abs = absoluteMode.matcher(destParam);
			debug(destParam);
			if(reg.find()) {
				String dReg = reg.group(1);
				correctAssembled = correctAssembled.replace("d", hexFromDec(dReg)).replace(dst, "");
				AdBWAs += 0;
			}
			else if (index.find()) {
				int offset = Integer.parseInt(index.group(1));
				String dReg = index.group(2);
				if(dReg.equalsIgnoreCase("PC")) {
					dReg = "0";
				} else if(dReg.equalsIgnoreCase("SP")) {
					dReg = "1";
				} else if (dReg.equalsIgnoreCase("SR")) {
					dReg = "2";
				}
				correctAssembled = correctAssembled.replace("d", hexFromDec(dReg)).replace(dst, (offset == 0 ? "" : AssemblerPhase3.hexForm(offset)));
				AdBWAs += 8;
			}
			else if (symbol.find()) { // TODO
				String name = symbol.group(1);
				String replace = "%'"+name+"'%";
				// TODO More hardcoded
				if(name.equalsIgnoreCase("SP")) {
					replace = "";
					correctAssembled = correctAssembled.replace("d", "1"); // For R1 = SP
				}
				
				correctAssembled = correctAssembled.replace(dst, replace);
				AdBWAs += replace.equals("") ? 0 : 8;
			}
			else if (abs.find()) {
				String name = abs.group(1);
				String replace = "%'"+name+"'%"; // default format in case we need a second pass
				if(AssemblerPhase3.symbolTable.containsKey(name)) { // TODO OFFSET INDEXED MODE X(SR)?
					replace = AssemblerPhase3.hexForm(AssemblerPhase3.symbolTable.get(name));
				}
				else { // Might be using defined constants, check symbol table
					int immedValue = getHardcodedValue(name);
					if(immedValue != 0) replace = String.format("%04X", immedValue); // Parse it as decimal, looking to see if it's changed
				}
				
				correctAssembled = correctAssembled.replace("d", "2").replace(dst, replace);
				AdBWAs += 8;
			}
			else {
				return "ERROR - Invalid destination parameter syntax.";
			}
		}
		
		correctAssembled = correctAssembled.replace("-", hexFromDec(""+AdBWAs));
		debug(correctAssembled);
		return listByteOrder(correctAssembled);
	}
	
	// Single operator handling
	public static String assemble(Instruction in, boolean byteOp, String sourceParam) { //RRC, RRA, PUSH, SWPB, CALL, RETI, SXT, jumps (15 total)
		//String src = "%SRC%";															// CALL, JNZ, INC, DEC, JZ, RET
		//String correctAssembled = in.getOpCode() + src;

		if(sourceParam == null) { // RET, RETI
			if(in.getOperator().equalsIgnoreCase("RET")) {
				return listByteOrder("4130"); // TODO BIG HARDCODE
			}
			else if(in.getOperator().equalsIgnoreCase("RETI")) {
				
			}
		}
		
		if(sopParam.matcher(sourceParam).find()) {
			Matcher reg = registerMode.matcher(sourceParam);
			Matcher symbol = symbolicMode.matcher(sourceParam);
			String toReturn = "";
			if(in.getOperator().equalsIgnoreCase("call")) {
				Matcher immed = immediateMode.matcher(sourceParam);
				if(immed.find()) {
					toReturn = "12B0%'"+immed.group(1)+"'%"; // TODO HARDCODED?
				}
				else toReturn = "12B0"+"0000"; // TODO BAD ERROR CASE
			}
			else if (in.getOperator().equalsIgnoreCase("jz")) {
//				if (symbol.find()) { TODO
//					String name = symbol.group(1);
//					if(AssemblerPhase3.symbolTable.containsKey(name)) {
//						int offset = Math.ma
//						toReturn = "2" + hexForm(AssemblerPhase3.addressCounter AssemblerPhase3.symbolTable.get(name);
//					}
//					//else correctAssembled += "%'"+name+"'%";
//				}
				toReturn = "2xxx";
			}
			else if(in.getOperator().equalsIgnoreCase("jnz")) {
				toReturn = "2xxx";
			}
			else if(in.getOperator().equalsIgnoreCase("inc")) {
				if(reg.find()) {
					toReturn = "531" + hexFromDec(reg.group(1));
				}
				else toReturn = "531d";
			}
			else if(in.getOperator().equalsIgnoreCase("dec")) {
				if(reg.find()) {
					toReturn = "831" + hexFromDec(reg.group(1));
				}
				else toReturn = "831d";
			}
			return listByteOrder(toReturn);
		}
		else {
			return "ERROR - Invalid single parameter syntax.";
		}

//		if(in.getOpCode().startsWith("1")) { // SPECIAL CASE: We have to deal with the case-specific byteOp splits
//			if(in.hasByteOp()) {
//				String op = in.getOperator().toLowerCase(); // possibly extra redundant
//				if(op.equals("rrc")) correctAssembled = correctAssembled.replace("-", byteOp ? "4" : "0");
//				else if(op.equals("rra")) correctAssembled = correctAssembled.replace("-", byteOp ? "4" : "0");
//				else if(op.equals("push")) correctAssembled = correctAssembled.replace("-", byteOp ? "4" : "0");
//			}
//
//			return correctAssembled;
//		}
		//return correctAssembled;
		//return listByteOrder(correctAssembled);
	}
	
	// BEGIN UTILITY METHODS
	
	public static int getHardcodedValue(String headerVal) {
		if(headerVal.equalsIgnoreCase("WDTCTL")) return WDTCTL;
		else if(headerVal.equalsIgnoreCase("WDTPW")) return WDTPW;
		else if(headerVal.equalsIgnoreCase("WDTHOLD")) return WDTHOLD;
		else if(headerVal.equalsIgnoreCase("P1OUT")) return P1OUT;
		else if(headerVal.equalsIgnoreCase("P1REN")) return P1REN;
		else if(headerVal.equalsIgnoreCase("P2OUT")) return P2OUT;
		return 0;
	}
	
	public static String listByteOrder(String hexInput) {
		String ret = "";
		for(int i = 0; i < hexInput.length(); i+= 4) {
			if(hexInput.charAt(i) == '%') { // Catch our second-pass replacements to leave them in and not mess up the loop
				ret += hexInput.substring(i, hexInput.indexOf("'%", i) + 2);
				i = hexInput.indexOf("'%", i) + 2 - 4;
				continue;
			}
			ret += hexInput.substring(i+2, i+4) + hexInput.substring(i, i+2);
		}
		return ret;
	}
	
	private static String hexFromDec(String input) {
		int i = 0;
		try {
			i = Integer.parseInt(input);
		} catch (Exception e) {
			return "$";
		}
		return String.format("%1X", i);
	}
	
	public static void debug(Object o) {
		if(!AssemblerPhase3.debugEnabled) return;
		System.out.println(o);
	}
	
	// END UTILITY METHODS
	
	// INITIALIZE INSTRUCTION SET
	static {
//		static final List<String> assyKeywords = new ArrayList<String>(
//		Arrays.asList(
//			"db", "ds", "dw", "org", // directives, below are all valid instructions
//			"adc", "add", "addc", "and", 
//			"bic", "bis", "bit", "br", 
//			"call", "clr", "clrc", "clrn", "clrz", "cmp", 
//			"dadc", "dadd", "dec", "decd", "dint",
//		    "eint",
//		    "inc", "incd", "inv", 
//			"jc", "jhs",  "jeq", "jz",  "jge", "jl", "jmp", "jn",  "jnc", "jlo",  "jne", "jnz",
//			"mov",
//			"nop",
//			"pop", "push",
//			"ret", "reti", "rla", "rlc", "rra", "rrc",
//			"sbc", "setc", "setn", "setz", "sub", "subc", "swpb", "sxt",
//			"tst",
//			"xor"));
		inst.add(new Instruction("DB", "db", "5s-d", "to be added", false));
		inst.add(new Instruction("DS", "ds", "5s-d", "to be added", false));
		inst.add(new Instruction("DW", "dw", "5s-d", "to be added", false));
		inst.add(new Instruction("ORG", "org", "5s-d", "to be added", false));
		inst.add(new Instruction("ADC", "adc", "50-d", "to be added", true)); //emulated add
		inst.add(new Instruction("ADD", "add", "5s-d", "to be added", true));
		inst.add(new Instruction("ADDC", "addc", "6s-d", "to be added", true));
		inst.add(new Instruction("AND", "and", "Fs-d", "to be added", true));
		inst.add(new Instruction("BIC", "bic", "Cs-d", "to be added", true));
		inst.add(new Instruction("BIS", "bis", "Ds-d", "to be added", true));
		inst.add(new Instruction("BIT", "bit", "Bs-d", "to be added", true));    //same as add
		inst.add(new Instruction("BRANCH", "br", "4s-d", "to be added", false)); //emulated mov
		inst.add(new Instruction("CALL", "call", "1280", "to be added", false)); //emulated tbd
		inst.add(new Instruction("CLR", "clr", "5s-d", "to be added", true));   //emulated bic
		inst.add(new Instruction("CLRC", "clrc", "5s-d", "to be added", false));  //emulated bic->c
		inst.add(new Instruction("CLRN", "clrn", "5s-d", "to be added", false));  //emulated bic->n
		inst.add(new Instruction("CLRZ", "clrz", "5s-d", "to be added", false));  //emulated bic->z
		inst.add(new Instruction("CMP", "cmp", "9s-d", "to be added", true));
		inst.add(new Instruction("DADC", "dadc", "As-d", "to be added", true));  //emulated dadd
		inst.add(new Instruction("DADD", "dadd", "As-d", "to be added", true));
		inst.add(new Instruction("DEC", "dec", "5s-d", "to be added", true));   //emulated sub
		inst.add(new Instruction("DECD", "decd", "5s-d", "to be added", true));  //emulated sub
		inst.add(new Instruction("DINT", "dint", "5s-d", "to be added", false));  //emulated bic
		inst.add(new Instruction("EINT", "eint", "5s-d", "to be added", false));  //emulated bis
		inst.add(new Instruction("INC", "inc", "5s-d", "to be added", true));   //emulated add
		inst.add(new Instruction("INCD", "incd", "5s-d", "to be added", true));  //emulated add
		inst.add(new Instruction("INV", "inv", "5s-d", "to be added", true));  //inversion
		inst.add(new Instruction("JC", "jc", "2C-d", "to be added", false));
		inst.add(new Instruction("JHS", "jhs", "5s-d", "to be added", false));
		inst.add(new Instruction("JEQ", "jeq", "24-d", "to be added", false));
		inst.add(new Instruction("JZ", "jz", "24-d", "to be added", false));
		inst.add(new Instruction("JL", "jl", "38-d", "to be added", false));
		inst.add(new Instruction("JMP", "jmp", "3C-d", "to be added", false));
		inst.add(new Instruction("JN", "jn", "30-d", "to be added", false));
		inst.add(new Instruction("JNC", "jnc", "28-d", "to be added", false));
		inst.add(new Instruction("JLO", "jlo", "5s-d", "to be added", false));
		inst.add(new Instruction("JNE", "jne", "20-d", "to be added", false));
		inst.add(new Instruction("JNZ", "jnz", "20-d", "to be added", false));
		inst.add(new Instruction("MOV", "mov", "4s-d", "to be added", true));
		inst.add(new Instruction("NOP", "nop", "5s-d", "to be added", false));
		inst.add(new Instruction("POP", "pop", "5s-d", "to be added", true));
		inst.add(new Instruction("PUSH", "push", "120-", "to be added", true));
		inst.add(new Instruction("RET", "ret", "5s-d", "to be added", false));
		inst.add(new Instruction("RETI", "reti", "1300", "to be added", false));
		inst.add(new Instruction("RLA", "rla", "5s-d", "to be added", true));
		inst.add(new Instruction("RLC", "rlc", "5s-d", "to be added", true));
		inst.add(new Instruction("RRA", "rra", "11-0", "to be added", true));
		inst.add(new Instruction("RRC", "rrc", "10-0", "to be added", true));
		inst.add(new Instruction("SBC", "sbc", "8s-d", "to be added", true));  //emulated sub
		inst.add(new Instruction("SETC", "setc", "5s-d", "to be added", false)); 
		inst.add(new Instruction("SETN", "setn", "5s-d", "to be added", false));
		inst.add(new Instruction("SETZ", "setz", "5s-d", "to be added", false));
		inst.add(new Instruction("SUB", "sub", "8s-d", "to be added", true));
		inst.add(new Instruction("SUBC", "subc", "7s-d", "to be added", true));
		inst.add(new Instruction("SWPB", "swpb", "1080", "to be added", false));
		inst.add(new Instruction("XOR", "xor", "Es-d", "to be added", true));
	}

	public static Instruction getInstruction(String operator) {
		for(Instruction i : inst) {
			if(i.getOperator().equalsIgnoreCase(operator)) {
				return i;
			}
		}
		return null;
	}
}