package edu.ttu.drewmitchell;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Drew Mitchell, Junior, Texas Tech University
 * Created: 07/15/2019 (MM/DD/YYYY) @ 18:45
 * Revisions:
 *   1: 07/22/2019 @ 12:38pm
 * 
 * The objective of this program is to take an .s43 file as input and output
 *  a primitive result of a list file.
 */

/**
 * 
 * 1. Provide line number of the source code for all source code lines including comments and blank lines - DONE
 * 2. Detect any ORG statements and use those to update an address counter - DONE
 * 3. Detect any labels that start in column 1 and put those in a symbol table along with their address if the _ DONE
 *     label is for a RAM variable or ROM constant. _ DONE
 * 4. Detect any labels that start in column 1 and put those in a symbol table along with their fixed value if - DONE
 *     the label is for a constant. - DONE
 * 5. Calculate the encoded instruction in list file byte order, update the address counter accordingly.
 * 6. Ultimately, the symbol table must be sorted alphabetically before being written to the LIST file - DONE
 */

public class AssemblerPhase2 {
	static Map<String, Integer> symbolTable = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
	static int addressCounter = 0x0200; // Default value
	
	static final List<Instruction> inst = new ArrayList<Instruction>();
	static final int P1OUT = 0x0021;
	static final int WDTCTL = 0x0120;

	////// BEGIN PATTERNS
	static final Pattern blankLine = Pattern.compile("^\\s*?$");
	static Matcher blankMatch = null;
	
	static final Pattern commentEnd = Pattern.compile("^\\s*?(?:end|;).*$", Pattern.CASE_INSENSITIVE);
	static Matcher comMatch = null;
	
	static final Pattern defDir = Pattern.compile("^([\\w]+)?:?(?:\\s+)?(?:(DB|DS|DW)\\s+('[\\w\\d-]'|\"[\\w\\d-]+?\"|\\d|[\\w\\d-]+))(?:\\s*?$|\\s*?;.*?$)");
	static Matcher defMatch = null;
	
	static final Pattern orgDir = Pattern.compile("^(?:\\s+)?([\\w]+)?:?(?:\\s+)?ORG\\s+(0x[\\dA-Fa-f]{1,4})(?:\\s*?$|\\s*?;(.*)?$)");
	static Matcher orgMatch = null;
	
	static final Pattern labelOnly = Pattern.compile("^([\\w\\d]+?):?(?:\\s*?$|\\s*?;.*?$)");
	static Matcher labelMatch = null;
	
	static final Pattern constantDecl = Pattern.compile("^([\\w\\d]+)\\s+?EQU\\s+?(%([01]{8})|0x([a-fA-F\\d])+)(?:\\s*?$|\\s*?;.*?$)");
	static Matcher constMatch = null;
	
	static final Pattern doubleOp = Pattern.compile("^(?:(?:(\\w+):?\\s+)\\b)?\\s*?([a-zA-Z]{2,4}\\.?[wbWB]?){1}\\s+?((?:@?R[\\d]{1,2}\\+?)|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:#?0x[\\dA-Fa-f]{1,4})|(?:[#&]?[\\w\\+]+)),\\s*?((?:R[\\d]{1,2})|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:0x[\\da-fA-F]{1,4})|(?:&?[\\w\\+]+))(?:\\s*?$|\\s*?;.*?$)");
	static Matcher doubleOpMatch = null;
	
	static final Pattern singleOp = Pattern.compile("^(?:(?:(\\w+):?\\s+)\\b)?\\s*?([a-zA-Z]{2,4}\\.?[wbWB]?){1}\\s+?((?:@?R[\\d]{1,2}\\+?)|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:#?0x[\\dA-Fa-f]{1,4})|(?:[#&]?[\\w\\+]+))(?:\\s*?$|\\s*?;.*?$)");
	static Matcher singleOpMatch = null;
	////// END PATTERNS
	
	
	public static void main(String[] args) throws IOException {
		initializeInstructionSet();
		
		File source = new File("TestSourceCodeA.s43");
		File sourceERR = new File("TestSourceCodeA_E.s43");
		
		outputList(source);
		symbolTable.clear();  // Must reset this so we don't receive duplicate error labels everywhere running the 2nd file through
		outputList(sourceERR);
	}
	
	private static void initializeInstructionSet() {
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
		inst.add(new Instruction("SXT", "sxt", "1180", "to be added", false));
		inst.add(new Instruction("TST", "tst", "5s-d", "to be added", true));
		inst.add(new Instruction("XOR", "xor", "Es-d", "to be added", true));
	}
	
	public static void outputList(File source) throws IOException {
		Scanner scan = new Scanner(new FileInputStream(source));
		
		File output = new File(source.getName().substring(0, source.getName().lastIndexOf(".")) +".lst");
		
		FileOutputStream fos = new FileOutputStream(output);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
		
		// BEGIN FIRST PASS
		for(int lineNum = 1; scan.hasNextLine(); lineNum++) { //Output the file, for the most part.
			System.out.println(lineNum);
			bw.write(String.format("%4d", lineNum) + "  " + parseLine(scan.nextLine()));
			bw.newLine();
		}

		int maxLen = Integer.MIN_VALUE; // We have to loop through once to format the symbol table with uniform formatting
		for(String s : symbolTable.keySet()) {
			if(s.length() > maxLen) maxLen = s.length();
		}
		for(String symbol : symbolTable.keySet()) {
			bw.write(String.format("%-" + (maxLen + 4) + "s", symbol) + "     " + hexForm(symbolTable.get(symbol)));
			bw.newLine();
		}
		// END FIRST PASS
		System.out.println("First pass done");
		
		bw.close();
		scan.close();
	}
	
	/**
	 * @param line - The line of input
	 * @return A formatted and address-counted line of output to be written to a list file.
	 */
	public static String parseLine(String line) {
		blankMatch = blankLine.matcher(line);
		
		if(blankMatch.find()) { // Blank line, give a space to write.
			return " ";
		}
		
		comMatch = commentEnd.matcher(line);
		if(comMatch.find()) { // We're dealing with a comment/end statement at its root.
			return hexForm(addressCounter) + "    " + line;
		}
		// Not a comment, take it seriously.
		
		// BEGIN CONSTANT HANDLING / DIRECTIVES
		constMatch = constantDecl.matcher(line);
		if(constMatch.find()) { // We're dealing with a constant.
			//
			//return System.out.println(constMatch.group(0) + "\n" + constMatch.group(1) + "\n" + constMatch.group(2));
			String label = constMatch.group(1);
			String hexValue = constMatch.group(2);
			if(symbolTable.containsKey(label)) {
				return "ERROR - duplicate definition of constant '" + label + "'.";
			}
			int value = Integer.decode(hexValue).intValue();
			symbolTable.put(label, value); //TODO Binary to hex to Integer conversion
			return hexForm(value) + "    " + line;
		}
		
		defMatch = defDir.matcher(line);
		if(defMatch.find()) {
			String label = defMatch.group(1);
			String dir = defMatch.group(2);
			String data = defMatch.group(3);
			String snapshot = hexForm(addressCounter) + "    " + line;
			
			System.out.println("Directive: " + addressCounter + " " + label + " " + dir + " " + data);  // DEBUG
			if(label != null) {
				if(symbolTable.containsKey(label)) {
					return "ERROR - duplicate definition of label '" + label + "'.";
				}
				
				symbolTable.put(label, addressCounter);
				
				if(dir.equalsIgnoreCase("DB")) {
					if(data.startsWith("\"")) { //Handling defining bytespace for multiple characters
						addressCounter += data.replace("\"", "").length();
					}
					else if(data.matches("^\\d$")) {
						addressCounter++;
					}
					else if(data.startsWith("'")) {
						addressCounter++;
					}
				}
				else if(dir.equalsIgnoreCase("DW")) {
					addressCounter += 2;
				}
				else if(dir.equalsIgnoreCase("DS")) {
					addressCounter += Integer.parseInt(data);
				}
			}
			return snapshot;
		}
		
		orgMatch = orgDir.matcher(line);
		if(orgMatch.find()) {
			//TODO Symbol table for labeled ORG? - unlikely so far
			String hexAddr = orgMatch.group(2);
			addressCounter = Integer.decode(hexAddr).intValue();
			return hexForm(addressCounter) + "    " + line;
		}
		// END CONSTANT HANDLING / DIRECTIVES
		
		// Label-only case.
		labelMatch = labelOnly.matcher(line);
		if(labelMatch.find()) {
			String label = labelMatch.group(1);
			if(symbolTable.containsKey(label)) {
				return "ERROR - duplicate definition of label '" + label + "'.";
			}
			symbolTable.put(label, addressCounter);
			return hexForm(addressCounter) + "    " + line;
		}
		
		doubleOpMatch = doubleOp.matcher(line);
		if(doubleOpMatch.find()) {
			String label = doubleOpMatch.group(1);
			String operator = doubleOpMatch.group(2).toLowerCase();
			String source = doubleOpMatch.group(3);
			String dst = doubleOpMatch.group(4);
			
			System.out.println("DoubleOp: " + addressCounter + " " + label + " " + operator + " " + source + " " + dst);  // DEBUG
			if(label != null) {
				if(symbolTable.containsKey(label)) {
					return "ERROR - duplicate definition of label '" + label + "'.";
				}
				symbolTable.put(label, addressCounter); // Will be inserted regardless of further failure, could remove it though
			}
				
			boolean byteOp = operator.toLowerCase().contains(".b");
			String baseInstruction = operator.substring(0, operator.contains(".") ? operator.lastIndexOf('.') : operator.length());
			Instruction i = getInstruction(baseInstruction);
			if(i == null) {
				return "ERROR - unknown instruction '" + baseInstruction + "'";
			}
			
			String assembled = InstructionFactory.assemble(i, byteOp, source, dst);
					
			if(assembled.startsWith("ERROR")) { //Invalid format, the assemble method will give us the output we need
				//We can remove the symbolTable key here if we failed if necessary
				return assembled;
			}
			
			String snapshot = hexForm(addressCounter) + "    " + line;
			addressCounter += assembled.length() / 2;
			return snapshot;
		}
		
		singleOpMatch = singleOp.matcher(line);
		if(singleOpMatch.find()) {
			String label = singleOpMatch.group(1);
			String operator = singleOpMatch.group(2).toLowerCase();
			String source = singleOpMatch.group(3);
			
			System.out.println("Single OP: " + addressCounter + " " + label + " " + operator + " " + source);  // DEBUG
			if(label != null) {
				if(symbolTable.containsKey(label)) {
					return "ERROR - duplicate definition of label '" + label + "'.";
				}
				symbolTable.put(label, addressCounter); // Will be inserted regardless of further failure, could remove it though
			}
			
			boolean byteOp = operator.contains(".b");
			String baseInstruction = operator.substring(0, operator.contains(".") ? operator.lastIndexOf('.') : operator.length());
			Instruction i = getInstruction(baseInstruction);
			if(i == null) {
				return "ERROR - unknown instruction '" + baseInstruction + "'";
			}
			String assembled = InstructionFactory.assemble(i, byteOp, source);
					
			if(assembled.startsWith("ERROR")) { //Invalid format, the assemble method will give us the output we need
				//We can remove the symbolTable key here if we failed if necessary
				return assembled;
			}
			
			
			String snapshot = hexForm(addressCounter) + "    " + line;
			addressCounter += assembled.length() / 2;
			return snapshot;
		}
		return "||WARNING|| Unrecognized syntax";
	}
	
	private static Instruction getInstruction(String operator) {
		for(Instruction i : inst) {
			if(i.getOperator().equalsIgnoreCase(operator)) {
				return i;
			}
		}
		return null;
	}
	
	private static String hexForm(int input) {
		return String.format("%04X", input);
	}
}

class Instruction {
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

class InstructionFactory {
	static Pattern dopSource = Pattern.compile("^$((?:@?R[\\d]{1,2}\\+?)|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:#?0x[\\dA-Fa-f]{1,4})|(?:[#&]?[\\w\\+]+))$");
	static Pattern dopDest = Pattern.compile("^((?:R[\\d]{1,2})|(?:\\d+?\\(R[\\d]{1,2}\\))|(?:0x[\\da-fA-F]{1,4})|(?:&?[\\w\\+]+))$");
	
	static Pattern registerMode = Pattern.compile("^R(\\d{1,2})$");
	static Pattern indexedMode = Pattern.compile("^(\\d{2,4})\\(?:R(\\d{1,2})+\\)$");
	static Pattern symbolicMode = Pattern.compile("^(\\w+)$");
	static Pattern absoluteMode = Pattern.compile("^&(\\w+)$");
	static Pattern indirectRegisterMode = Pattern.compile("^@R(\\d{1,2})$");
	static Pattern indirectAutoInc = Pattern.compile("^@R(\\d{1,2})\\+$");
	static Pattern immediateMode = Pattern.compile("^#(?:0x)?([\\da-f]+|\\w+)$", Pattern.CASE_INSENSITIVE);
	
	//BEGIN CONSTANTS/REGISTERS (avoiding parsing header file by selectively adding undefined ones from the given source files manually, could make a separate & easily parsable file to dynamically add more)
	
	
	public static String assemble(Instruction in, boolean byteOp, String sourceParam, String destParam) { //MOV, ADD, ADDC, AND, SUB, SUBC, CMP, DADD, BIT, BIC, BIS, XOR
		String correctAssembled = in.getOpCode();
		int AdBWAs = byteOp ? 4 : 0;
		
		if(dopSource.matcher(sourceParam).find()) {
			// Legitimate addressing mode so far
			Matcher reg = registerMode.matcher(sourceParam);
			Matcher index = indexedMode.matcher(sourceParam);
			Matcher symbol = symbolicMode.matcher(sourceParam);
			Matcher abs = absoluteMode.matcher(sourceParam);
			Matcher indReg = indirectRegisterMode.matcher(sourceParam);
			Matcher indAuto = indirectAutoInc.matcher(sourceParam);
			Matcher immed = immediateMode.matcher(sourceParam);
			if(reg.find()) {
				correctAssembled = correctAssembled.replace("s", hexFromDec(reg.group(1)));
				AdBWAs += 0;
			}
			else if (index.find()) {
				String offset = index.group(1);
				String sReg = index.group(2);
				correctAssembled = correctAssembled.replace("s", hexFromDec(sReg)) + String.format("%04X", Integer.parseInt(offset));
				AdBWAs += 1;
			}
			else if (symbol.find()) {
				String name = symbol.group(1);
				AdBWAs += 1;
			}
			else if (abs.find()) {
				AdBWAs += 1;
			}
			else if (indReg.find()) {
				AdBWAs += 2;
			}
			else if (indAuto.find()) {
				AdBWAs += 3;
			}
			else if (immed.find()) {
				String src = immed.group(1);
				System.out.println("immed: " + src);
				//if()
				correctAssembled = correctAssembled.replace("s", "0") + "0000"; ///String.format("%04X", Integer.decode("#" + immed.group(1)));
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
			if(reg.find()) {
				String dReg = reg.group(1);
				correctAssembled = correctAssembled.replace("d", hexFromDec(dReg));
				AdBWAs += 0;
			}
			else if (index.find()) {
				String offset = index.group(1);
				String dReg = index.group(2);
				correctAssembled = correctAssembled.replace("d", hexFromDec(dReg));
				AdBWAs += 8;
			}
			else if (symbol.find()) {
				String name = symbol.group(1);
				if(!name.equalsIgnoreCase("SP")) {
					correctAssembled += "0000";
				}
				AdBWAs += 8;
			}
			else if (abs.find()) {
				correctAssembled += "0000";
				AdBWAs += 8;
			}
			else {
				return "ERROR - Invalid destination parameter syntax.";
			}
		}
		
		correctAssembled = correctAssembled.replace("-", hexFromDec(""+AdBWAs));
		System.out.println(correctAssembled);
		return listByteOrder(correctAssembled);
	}
	
	public static String assemble(Instruction in, boolean byteOp, String sourceParam) { //RRC, RRA, PUSH, SWPB, CALL, RETI, SXT, jumps (15 total)
		String correctAssembled = in.getOpCode();
		if(in.getOpCode().startsWith("1")) { // SPECIAL CASE: We have to deal with the case-specific byteOp splits
			if(in.hasByteOp()) {
				String op = in.getOperator().toLowerCase(); // possibly extra redundant
				if(op.equals("rrc")) correctAssembled = correctAssembled.replace("-", byteOp ? "4" : "0");
				else if(op.equals("rra")) correctAssembled = correctAssembled.replace("-", byteOp ? "4" : "0");
				else if(op.equals("push")) correctAssembled = correctAssembled.replace("-", byteOp ? "4" : "0");
			}
			
			return correctAssembled;
		}
		
		//Begin encoding Ad/BW/As
		
		return correctAssembled;
		//return listByteOrder(correctAssembled);
	}
	
	public static String listByteOrder(String hexInput) {
		String ret = "";
		for(int i = 0; i < hexInput.length(); i+= 4) {
			ret += hexInput.substring(i+2, i+4) + hexInput.substring(i, i+2);
		}
		return ret;
	}
	
	private static String hexFromDec(String input) {
		int i = 0;
		try {
			Integer.parseInt(input);
		} catch (Exception e) {
			return "$";
		}
		return String.format("%1X", i);
	}
}