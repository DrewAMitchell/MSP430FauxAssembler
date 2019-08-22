package edu.ttu.drewmitchell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

public class AssemblerPhase3 {
	static Map<String, Integer> symbolTable = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
	public static int addressCounter = 0x0200; // Default value
	public static int prevAddrCount = addressCounter;
	public static int checkSum = 0;
	static boolean debugEnabled = true;
	static boolean errorOccurred = false;
	
	static final String addrSeg = "FF00AA55";
	static String obj = "CaseyMitchell00";
	static List<String> objBuffer = new ArrayList<String>();

	////// BEGIN PATTERNS
	static final Pattern blankLine = Pattern.compile("^\\s*?$");
	static Matcher blankMatch = null;
	
	static final Pattern commentEnd = Pattern.compile("^\\s*?(?:end|;).*$", Pattern.CASE_INSENSITIVE);
	static Matcher comMatch = null;
	
	static final Pattern constantDecl = Pattern.compile("^(.+?)" + "\\s+?" + "EQU" + "\\s+?" + "(%[01]{8}|0x[a-fA-F\\d]+|\\$\\s+?-\\s+?[\\w\\d]+)" + "(?:\\s*?$|\\s*?;.*?$)");
	static Matcher constMatch = null;
	
	static final Pattern defDir = Pattern.compile("^(?:(\\s*[\\w\\d?*&^%$#@!()]+?):?\\s*)?" + "\\s*" + "(DB|DS|DW)" + "\\s+" + "('[\\w\\d-]'|\"[\\w\\d-]+?\"|\\d|[\\w\\d-]+)" + "(?:\\s*?$|\\s*?;.*?$)");
	static Matcher defMatch = null;
	
	static final Pattern orgDir = Pattern.compile("^(?:\\s+)?([\\w]+)?:?(?:\\s+)?" + "ORG" + "\\s+" + "(0x[\\dA-Fa-f]{1,4})" + "(?:\\s*?$|\\s*?;.*?$)");
	static Matcher orgMatch = null;
	
	static final Pattern labelOnly = Pattern.compile("^(?:\\s*([\\w\\d?*&^%$#@!()]+?):?\\s*)?" + "(?:\\s*?$|\\s*?;.*?$)");
	static Matcher labelMatch = null;
	
	static final Pattern doubleOp = Pattern.compile("^(?:(\\s*[\\w\\d?*&^%$#@!()]+?):?\\s*)?" + "\\s+?" + "([a-zA-Z]{2,4}(?:\\.\\w)?)" + "\\s+?" + "((?:@R\\d{1,2}\\+?)|(?:\\d+?\\(R\\d{1,2}\\))|(?:#?(?:0x)?[\\dA-Fa-f]{1,4})|(?:[#&]?[\\w\\d\\+]+))?" + ",\\s*?" + "((?:R[\\d]{1,2})|(?:\\d+?\\(R\\d{1,2}\\))|(?:0x[\\da-fA-F]{1,4})|(?:&?[\\w\\+]+))?" + "(?:\\s*?$|\\s*?;.*?$)");
	static Matcher doubleOpMatch = null;
	
	static final Pattern singleOp = Pattern.compile("^(?:(\\s*[\\w\\d?*&^%$#@!()]+?):?\\s*)?" + "\\s+?" + "([a-zA-Z]{2,4}(?:\\.\\w)?)" + "\\s+?" + "((?:@?R\\d{1,2}\\+?)|(?:\\d+?\\(R`\\d{1,2}\\))|(?:#?0x[\\dA-Fa-f]{1,4})|(?:[#&]?[\\w\\d\\+]+))?" + "(?:\\s*?$|\\s*?;.*?$)");
	static Matcher singleOpMatch = null;
	////// END PATTERNS
	
	public static void main(String[] args) throws IOException {
		File source = new File("TestSourceCodeA.s43");
		File sourceERR = new File("TestSourceCodeA_E.s43");
		File sourceB = new File("TestSourceCodeB.s43");
		File sourceC = new File("TestSourceCodeC.s43");
		File sourceD = new File("TestSourceCodeD.s43");
		File sourceE = new File("TestSourceCodeE.s43");
		//File sourceG = new File("TestSourceCodeG.s43");
		
		outputList(source);
		resetInitialAssyConditions();
		outputList(sourceERR);
		resetInitialAssyConditions();
		outputList(sourceB);
		resetInitialAssyConditions();
		outputList(sourceC);
		resetInitialAssyConditions();
		outputList(sourceD);
		resetInitialAssyConditions();
		outputList(sourceE);
		//resetInitialAssyConditions();
		//outputList(sourceG);
	}
	
	public static void resetInitialAssyConditions() {
		obj = "CaseyMitchell00";
		errorOccurred = false;
		addressCounter = 0x200;
		prevAddrCount = addressCounter;
		checkSum = 0;
		objBuffer.clear();
		symbolTable.clear();  // Must reset this so we don't receive duplicate error labels everywhere running the 2nd file through
	}
	
	public static void outputList(File source) throws IOException {
		debug("\nBeginning output of " + source.getName() + "!\n");
		Scanner input = new Scanner(new FileInputStream(source));
		
		File output = new File(source.getName().substring(0, source.getName().lastIndexOf(".")) +".lst");
		File object = new File(source.getName().substring(0, source.getName().lastIndexOf(".")) +".txt");
		
		FileOutputStream fos = new FileOutputStream(output);
		String fullWrite = "";
		
		// Object file will be handled in a global scope per-file, reset upon each new file.
		
		// BEGIN FIRST PASS
		for(int lineNum = 1; input.hasNextLine(); lineNum++) { // Output the file, for the most part.
			debug(lineNum);
			String line = input.nextLine();
			String toWrite = String.format("%4d", lineNum) + "  " + parseLine(line) + "\n";
			fullWrite += toWrite; // Compile all the lines into one String object.
		}

		int maxLen = Integer.MIN_VALUE; // We have to loop through once to format the symbol table with uniform formatting
		for(String s : symbolTable.keySet()) {
			if(s.length() > maxLen) maxLen = s.length();
		}
		// Write out our symbol table at the very bottom
		fullWrite += String.format("%-" + (maxLen + 4) + "s", "\nLabel:") + "     Value:\n";
		for(String symbol : symbolTable.keySet()) {
			fullWrite += String.format("%-" + (maxLen + 4) + "s", symbol) + "     " + hexForm(symbolTable.get(symbol)) + "\n";
		}

		debug("First pass done");
		// END FIRST PASS
		
		// BEGIN SECOND PASS
		for(String key : symbolTable.keySet()) { // TODO Single ops with placeholders have extra spaces
			String replace = InstructionFactory.listByteOrder(hexForm(symbolTable.get(key))); // We need to calculate differences
			while(replace.length() < ("%'"+key+"'%").length() - 2) {
				replace += " ";
			}
			fullWrite = fullWrite.replace("%'"+key+"'%", replace);
		}
		
		fullWrite = fullWrite.replaceAll("^.*%'([A-Za-z][\\w\\d]+?)'%.*$", "ERROR - UNDEFINED LABEL '$1'"); // This covers the error case where we encountered a symbol without a value after first pass
		
		debug("Second pass done");
		// END SECOND PASS
		fos.write(fullWrite.getBytes());
		fos.close();
		
		// Write (hopefully) fully compiled object file if no error occurred
		if(!errorOccurred) {
			fos = new FileOutputStream(object);
			obj += "FFAA5500"; // Terminator Sequence, I'll be back.
			checkSum = checkSum % (2*Math.abs(Short.MIN_VALUE));
			obj += hexForm(checkSum);
			for(String key : symbolTable.keySet()) { // TODO Single ops with placeholders have extra spaces
				String replace = InstructionFactory.listByteOrder(hexForm(symbolTable.get(key))); // We need to calculate differences
				while(replace.length() < ("%'"+key+"'%").length() - 2) {
					replace += " ";
				}
				obj = obj.replace("%'"+key+"'%", replace);
			}
			fos.write(obj.getBytes());
			fos.close();
		}
		
		input.close();
	}
	
	/**
	 * @param line - A line of assembly language input
	 * @return A properly formatted line complete with address counting, errors, values, encoded instructions as memory would view them, and the original input line
	 */
	public static String parseLine(String line) {
		String parsedLine = hexForm(addressCounter) + " "; // Centralized baseline format
		
		blankMatch = blankLine.matcher(line);
		
		if(blankMatch.find()) { // Blank line, give a space to write.
			return parsedLine.trim();
		}
		
		comMatch = commentEnd.matcher(line);
		if(comMatch.find()) { // We're dealing with a comment/end statement at its root.
			return parsedLine += formatAssy(null) + line;
		}
		// Not a comment, take it seriously.
		
		// BEGIN CONSTANT HANDLING / DIRECTIVES
		constMatch = constantDecl.matcher(line);
		if(constMatch.find()) { // We're dealing with a constant.
			String label = constMatch.group(1);
			String value = constMatch.group(2);
			String isInvalid = checkValidLabelCase(label, addressCounter);
			if(isInvalid != null) return isInvalid;
			
			int hexValue = 0;
			if(value.startsWith("$")) { // Handle addressCounter-relative declaration
				String relativeVar = value.replaceAll("\\$\\s+-\\s+", ""); // Strip the $ -
				if(symbolTable.containsKey(relativeVar)) { // We can define the address value now
					hexValue = addressCounter - symbolTable.get(relativeVar);
				}
				else {
					return "%'"+label+"'%" + "    " + line; // Must save symbolTable and const value for 2nd pass, no way to lookahead
				}
			}
			else if(value.startsWith("%")) { // Parse out binary
				hexValue = Integer.parseUnsignedInt(value.replace("%", ""), 2); // Parse out bits
			}
			else hexValue = Integer.decode(value).intValue(); // Parse out hex values
			symbolTable.put(label, hexValue);
			return hexForm(hexValue) + " " + formatAssy(null) + line;
		}

		defMatch = defDir.matcher(line);
		if(defMatch.find()) {
			String label = defMatch.group(1);
			String dir = defMatch.group(2);
			String data = defMatch.group(3);
			String snapshot = parsedLine + formatAssy(null) + line;

			debug("Directive: " + addressCounter + " " + label + " " + dir + " " + data);  // DEBUG
			String isInvalid = checkValidLabelCase(label, addressCounter);
			if(isInvalid != null) return isInvalid;
			
			if(dir.equals("DB")) {
				if(data.startsWith("\"")) { //Handling defining bytespace for multiple characters
					addressCounter += data.replace("\"", "").length() + 1; // Add one to compensate for 0-base
					for(char c : data.replace("\"", "").toCharArray()) {
						objBuffer.add(String.format("%02X", (int) c));
					}
				}
				else if(data.matches("^\\d$")) {
					addressCounter++;
					objBuffer.add(String.format("%02X", Integer.parseInt(data)));
				}
				else if(data.startsWith("'")) {
					addressCounter++;
					objBuffer.add(String.format("%02X", (int) data.replace("'", "").charAt(0)));
				}
			}
			else if(dir.equals("DW")) {
				addressCounter += 2;
				objBuffer.add("00");
			}
			else if(dir.equals("DS")) {
				addressCounter += Integer.parseInt(data);
				for(int i = 0; i < Integer.parseInt(data); i++) {
					objBuffer.add("00");
				}
			}
			return snapshot;
		}

		orgMatch = orgDir.matcher(line);
		if(orgMatch.find()) {
			//TODO Symbol table for labeled ORG? - unlikely so far
			String hexAddr = orgMatch.group(2);
			if(prevAddrCount != addressCounter) { // We must write the data we've collected if this isn't the initial run.
				obj += String.format("%04d", (addressCounter-prevAddrCount)); // Must be bytes that are encoded as BCD for some reason
				for(String buff : objBuffer) {
					obj += buff;
				}
				objBuffer.clear();
				prevAddrCount = addressCounter;
			}
			addressCounter = Integer.decode(hexAddr).intValue();
			obj += addrSeg + hexForm(addressCounter);
			return parsedLine += formatAssy(null) + line;
		}
		// END CONSTANT HANDLING / DIRECTIVES

		// Label-only case, followed by potential label cases
		labelMatch = labelOnly.matcher(line);
		if(labelMatch.find()) {
			String label = labelMatch.group(1);
			debug("Label: " + label);
			
			// Single-op no source special case
			boolean byteOp = label.toLowerCase().contains(".b");
			String baseInstruction = label.substring(0, label.contains(".") ? label.lastIndexOf('.') : label.length());
			Instruction i = InstructionFactory.getInstruction(baseInstruction);
			if(i != null) { // We're parsing something such as 'ret' or 'reti'
				String assembled = InstructionFactory.assemble(i, byteOp, null); // Pass in null to trip the edge case in the assemble for single-ops
				
				if(assembled.startsWith("ERROR")) { // ERROR Invalid format, the assemble method will give us the output we need
					//We can remove the symbolTable key here if we failed if necessary
					return error(assembled);
				}
				
				String correction = assembled;
				if(correction.matches(".*%'[A-Za-z][\\w\\d]+'%.*")) { // Needed to have sensible values for addressCounter
					//debug("matched!");
					correction = correction.replaceAll("%'[A-Za-z][\\w\\d]+'%", "0000");
				}
				//debug(assembled + "    " + correction);
				objBuffer.add(assembled);
				String snapshot = parsedLine + formatAssy(assembled) + line;
				addressCounter += correction.length() / 2;
				return snapshot;
			}
			// End single-op special case
			
			String isInvalid = checkValidLabelCase(label, addressCounter);
			if(isInvalid != null) return isInvalid;
			
			return parsedLine += formatAssy(null) + line;
		}
		
		doubleOpMatch = doubleOp.matcher(line);
		if(doubleOpMatch.find()) {
			String label = doubleOpMatch.group(1);
			String operator = doubleOpMatch.group(2).toLowerCase();
			String source = doubleOpMatch.group(3);
			String dst = doubleOpMatch.group(4);
			
			debug("DoubleOp: " + addressCounter + " " + label + " " + operator + " " + source + " " + dst);  // DEBUG

			if(operator == null) return error("ERROR - opcode is missing");
			else if(!operator.matches("^[a-zA-Z]{2,4}(?:\\.[wbWB])?$")) {
				return error("ERROR - invalid opcode format, perhaps incorrect word/byte operation?");
			}
			if(source == null) return error("ERROR - source parameter is missing");
			if(dst == null) return error("ERROR - destination parameter is missing");
			
			String isInvalid = checkValidLabelCase(label, addressCounter);
			if(isInvalid != null) return isInvalid;
			
			boolean byteOp = operator.toLowerCase().contains(".b");
			String baseInstruction = operator.substring(0, operator.contains(".") ? operator.lastIndexOf('.') : operator.length());
			Instruction i = InstructionFactory.getInstruction(baseInstruction);
			if(i == null) { // ERROR Undefined instruction
				return error("ERROR - unknown instruction '" + baseInstruction + "'");
			}
			
			String assembled = InstructionFactory.assemble(i, byteOp, source, dst);
					
			if(assembled.startsWith("ERROR")) { // ERROR Invalid format, the assemble method will give us the output we need
				//We can remove the symbolTable key here if we failed if necessary
				return error(assembled);
			}
			
			String correction = assembled;
			if(correction.matches(".*%'[A-Za-z][\\w\\d]+'%.*")) { // Needed to have sensible values for addressCounter
				//debug("matched!");
				correction = correction.replaceAll("%'[A-Za-z][\\w\\d]+'%", "0000");
			}
			objBuffer.add(assembled);
			//debug(assembled + "    " + correction);
			String snapshot = parsedLine + formatAssy(assembled) + line;
			addressCounter += correction.length() / 2;
			return snapshot;
		}
		
		singleOpMatch = singleOp.matcher(line);
		if(singleOpMatch.find()) {
			String label = singleOpMatch.group(1);
			String operator = singleOpMatch.group(2).toLowerCase();
			String source = singleOpMatch.group(3);
			
			debug("Single OP: " + hexForm(addressCounter) + " " + label + " " + operator + " " + source);
			
			if(operator == null) return error("ERROR - opcode is missing");
			else if(!operator.matches("^[a-zA-Z]{2,4}(?:\\.[wbWB])?$")) {
				return error("ERROR - invalid opcode format, perhaps incorrect word/byte operation?");
			}
			
			
			if(source == null) {
				return error("ERROR - source parameter is missing");
			}
			
			String isInvalid = checkValidLabelCase(label, addressCounter);
			if(isInvalid != null) return isInvalid;
			
			boolean byteOp = operator.contains(".b");
			String baseInstruction = operator.substring(0, operator.contains(".") ? operator.lastIndexOf('.') : operator.length());
			Instruction i = InstructionFactory.getInstruction(baseInstruction);
			if(i == null) { // ERROR Undefined instruction
				return error("ERROR - unknown instruction '" + baseInstruction + "'");
			}
			
			String assembled = InstructionFactory.assemble(i, byteOp, source);
					
			if(assembled.startsWith("ERROR")) { // ERROR Invalid format, the assemble method will give us the output we need
				//We can remove the symbolTable key here if we failed if necessary
				return error(assembled);
			}
			objBuffer.add(assembled);
			String correction = assembled;
			if(correction.matches(".*%'[A-Za-z][\\w\\d]+?'%.*")) { // Needed to have sensible values for addressCounter
				correction = correction.replaceAll("%'[A-Za-z][\\w\\d]+?'%", "0000");
			}
			String snapshot = parsedLine + formatAssy(assembled) + line;
			addressCounter += correction.length() / 2;
			return snapshot;
		}
//		if(line.matches("^\\s+?(?:([A-Za-z][\\w\\d]+)(?:\\.[wb])?:?)\\s+(?:[&#]?[\\w\\d\\+]+\\s*|[&#]?[\\w\\d\\+]+\\s*,\\s*[&#]?[\\w\\d\\+]+)[^,]*$")) { // ERROR Label not in leftmost column
//			// TODO Edge case: Verify it's not a direct instruction.
//			Instruction test = InstructionFactory.getInstruction(line.replaceAll("^\\s+?(?:([A-Za-z][\\w\\d]+):?).*$", "$1"));
//			if(test == null) {
//				return error("ERROR - labels can only start in the leftmost column (first character of line)");
//			}
//			debug("Instruction found: " + test.getOperator());
//			return parsedLine += formatAssy(null) + line;
//		}
		return error("ERROR - Unrecognized syntax; check your formatting");
	}
	
	/**
	 * @param label - Label passed in to check for validity.
	 * @param addressCount - Value by assocation for taking care of symbolTable.
	 * @return null if valid, ERROR message if invalid.
	 */
	public static String checkValidLabelCase(String label, int addressCount) {
		if(label == null) return null;
		if(label.matches("^([A-Za-z][\\w\\d]+)$")) { // Valid label, return null.
			if(symbolTable.containsKey(label)) { // ERROR DUPLICATE LABEL
				return error("ERROR - duplicate definition of label '" + label + "'.");
			}
			symbolTable.put(label, addressCounter); // Will be inserted regardless of further failure, could remove it though
			return null;
		}
		else if(label.matches("^\\s+?([A-Za-z][\\w\\d]+).*$")) {
			return error("ERROR - labels can only start in the leftmost column (first character of line)");
		}
		else if(label.matches("^[^A-Za-z].*$")) { // ERROR Line/label started with a non-alphabetic character
			return error("ERROR - labels can only start with alphabetic characters");
		}
		else if(label.matches("^([a-zA-Z]{2,4}(?:\\.[wbWB])?\\s+.*$")) {
			return error("ERROR - opcodes must not be in the leftmost column.");
		}
		else if(label.matches("^.*[^\\w\\d]+.*$")) {
			return error("ERROR - Invalid label format (must be alphanumeric with underscores)");
		}
		return null;
	}
	
	public static String formatAssy(String assembled) {
		if(assembled == null) return String.format("%-13s", " ");
		return String.format("%-13s", assembled);
	}
	
	public static String error(String errMsg) {
		errorOccurred = true;
		return errMsg;
	}

	public static void debug(Object o) {
		if(!debugEnabled) return;
		System.out.println(o);
	}
	
	public static String hexForm(int input) {
		return String.format("%04X", input);
	}
}