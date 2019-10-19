package de.drazil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MicroMovesConverter {
	private String format = null;
	private String comment = null;
	private String definition = null;
	private boolean hasLineNumbers = false;
	private boolean convertToHexString = true;
	private int maxLineLength = 9999;

	public void run(CommandLine cl) {

		try {
			List<Integer> borderList = new ArrayList<>();
			List<Integer> targetPosList = new ArrayList<>();
			List<Integer> boxPosList = new ArrayList<>();
			List<String> lineList = new ArrayList<>();
			List<Integer> levelList = new ArrayList<>();
			Integer player = new Integer((byte) 0);

			String inFileName = cl.getOptionValue("in");
			int dotIndex = inFileName.indexOf('.');
			String outDefault = inFileName.substring(0, dotIndex);
			String suffix = null;
			format = cl.getOptionValue("of", "basic");
			switch (format) {
			case "basic": {
				suffix = "bas";
				comment = ":REM ";
				definition = "DATA";
				convertToHexString = false;
				hasLineNumbers = true;
				maxLineLength = 255;
				break;
			}
			case "acme": {
				suffix = "a";
				comment = "; ";
				definition = "!byte";
				break;
			}
			case "kickass": {
				suffix = "asm";
				comment = "// ";
				definition = ".byte";
				break;
			}
			case "bin": {
				suffix = "obj";
				break;
			}
			default: {
				throw new RuntimeException("?unkown format [ " + format + " ]");
			}
			}
			String outFileName = cl.getOptionValue("out", outDefault + "_data." + suffix);
			File inFile = new File(inFileName);
			File outFile = new File(outFileName);
			BufferedReader br = new BufferedReader(new FileReader(inFile));
			BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
			String line = null;
			int width = 0;
			int height = 0;
			int ln = Integer.parseInt(cl.getOptionValue("lo", "1000"));
			int ls = Integer.parseInt(cl.getOptionValue("ls", "1"));
			System.out.println("Start converting process...");

			while ((line = br.readLine()) != null) {
				if (line.equals(""))
					line = br.readLine();
				if (line == null)
					break;
				if (!line.startsWith(";")) {
					lineList.add(line);
					// determine maximum width of a playfield
					width = (width < line.length() ? line.length() : width);
					height++;

				} else {
					String description = line.substring(line.indexOf(";") + 1).replace('_', '-').toUpperCase();
					if (width <= 16 && height <= 16) {
						height = 0;
						for (String s : lineList) {

							s = getFiller((int) Math.ceil((16 - width) / 2)) + s;
							s = s + getFiller(16 - s.length());

							int offset = 0;
							// find target
							while ((offset = s.indexOf('.', offset)) != -1) {
								int b = ((offset << 4) | height);
								targetPosList.add(new Integer(b));
								offset++;
							}
							offset = 0;
							// find box on target
							while ((offset = s.indexOf('*', offset)) != -1) {
								int b = ((offset << 4) | height);
								boxPosList.add(new Integer(b));
								targetPosList.add(new Integer(b));
								offset++;
							}
							offset = 0;
							// find box
							while ((offset = s.indexOf('$', offset)) != -1) {
								int b = ((offset << 4) | height);
								boxPosList.add(new Integer(b));
								offset++;
							}

							// find player
							if ((offset = s.indexOf('@')) != -1) {
								int b = ((offset << 4) | height);
								player = new Integer(b);
							}
							// find player on target
							if ((offset = s.indexOf('+')) != -1) {
								int b = ((offset << 4) | height);
								player = new Integer(b);
								targetPosList.add(new Integer(b));
							}

							int b = 0;
							int v = 128;
							for (int i = 0; i < s.length(); i++) {
								if (i > 0 && i % 8 == 0) {
									borderList.add(b);
									b = 0;
									v = 128;
								}
								b |= (s.charAt(i) == '#') ? v : 0;
								v >>= 1;
							}
							borderList.add(b);
							height++;
						}

						StringBuilder sb = new StringBuilder();
						if (hasLineNumbers) {
							sb.append(ln);
						}

						if (isBinary()) {
							levelList.add(borderList.size());
						} else {
							sb.append(definition + " " + getValue(borderList.size(), convertToHexString) + ",");
						}
						if (isBinary()) {
							for (int i = 0; i < borderList.size(); i++) {
								levelList.add(borderList.get(i));
							}
						} else {
							for (int i = 0; i < borderList.size(); i++) {
								sb.append(getValue(borderList.get(i), convertToHexString) + ",");
							}
							sb.append(targetPosList.size() + ",");
						}
						if (isBinary()) {
							for (int i = 0; i < targetPosList.size(); i++) {
								levelList.add(boxPosList.get(i));
								levelList.add(targetPosList.get(i));
							}
						} else {
							for (int i = 0; i < targetPosList.size(); i++) {
								sb.append(getValue(boxPosList.get(i), convertToHexString) + ",");
								sb.append(getValue(targetPosList.get(i), convertToHexString) + ",");
							}
						}

						if (isBinary()) {
							levelList.add(player);
						} else {
							sb.append(player);
							if (cl.hasOption("rem")) {
								sb.append(comment + " " + description);
							}
							sb.append("\n");
						}

						int lineLength = sb.toString().substring(sb.toString().indexOf(' ') + 1).length() + 3;
						if (lineLength > maxLineLength) {
							System.out.println("skip level [ " + description + " ] due to line length exceedance (max. "
									+ maxLineLength + "). Maybe restart without '-rem' option");
						} else {
							System.out.println("[ " + description + " ] successfully converted.");
							if (isBinary()) {
								for (int i = 0; i < levelList.size(); i++) {
									bw.write(new char[] { (char) (levelList.get(i) & 0xff) });
								}
							} else {
								bw.write(sb.toString());
							}
						}
						ln += ls;

					} else {
						System.out.println("skip level [ " + description + " ] due to playfield bounds exceedance w:"
								+ width + " h:" + height + " (max. 16 x 16)");
					}

					borderList.clear();
					targetPosList.clear();
					boxPosList.clear();
					lineList.clear();
					levelList.clear();
					width = 0;
					height = 0;
				}

			}
			if (isBasic()) {
				bw.write(ln + "DATA -1");
			}
			
			System.out.println("write file : " + outFileName);
			System.out.println("ready.");
			br.close();
			bw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	private boolean isBinary() {
		return format.equalsIgnoreCase("bin");
	}

	private boolean isBasic() {
		return format.equalsIgnoreCase("basic");
	}

	private String getValue(Integer value, boolean toHex) {

		String result = null;
		if (toHex) {
			result = Integer.toHexString(value);
			result = "$" + ((result.length() == 1) ? "0" + result : result);
		} else {
			return value.toString();
		}

		return result;
	}

	private String getFiller(int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			sb.append(' ');
		}
		return sb.toString();
	}

	public static void main(String argv[]) {

		Options options = new Options();
		options.addOption(Option.builder("help").desc("Help").build());
		options.addOption(Option.builder("rem").desc("Add level description to end of DATA line").build());
		options.addOption(Option.builder("in").hasArg().argName("file")
				.desc("Sokoban Level Text File (http://http://www.sourcecode.se/sokoban/levels)").build());
		options.addOption(Option.builder("out").hasArg().argName("file")
				.desc("Outputfile, when <out> is empty, inputfilename is used to create outputfile").build());
		options.addOption(Option.builder("of").hasArg().argName("format [basic*,kickass,acme,bin]")
				.desc("Set output format").build());
		options.addOption(Option.builder("w").hasArg().argName("width [1,2*,3,4]")
				.desc("Set maximum width in bytes, max. 2 for use in the BASIC Version, due to screen build slowness")
				.build());
		options.addOption(Option.builder("h").hasArg().argName("height [3-20]")
				.desc("Set maximum height in rows, max. 16 for use in the BASIC Version, due to screen build slowness")
				.build());

		options.addOption(Option.builder("lo").hasArg().argName("number 1000*")
				.desc("Linenumber offset. Only relevant for output format BASIC").build());
		options.addOption(Option.builder("ls").hasArg().argName("number 1* ")
				.desc("Linenumber count step. Only relevant for output format BASIC").build());

		MicroMovesConverter sc;
		try {
			CommandLineParser clp = new DefaultParser();
			CommandLine cl = clp.parse(options, argv);
			if (cl.hasOption("help") || cl.getOptions().length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.setWidth(400);
				formatter.printHelp("java -jar MicroMovesConverter.jar -in level.txt\n\n",
						"MicroMoves - Sokoban Level Converter Tool\n\nOptions:\n\n", options,
						"\n\nhttp://www.drazil.de");
				System.exit(0);
			} else {
				sc = new MicroMovesConverter();
				if (cl.hasOption("in")) {
					sc.run(cl);
				}
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}

	}
}
