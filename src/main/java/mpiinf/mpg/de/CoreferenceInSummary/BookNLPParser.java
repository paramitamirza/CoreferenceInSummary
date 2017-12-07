package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.Characters;

public class BookNLPParser {
	
	public List<String> parseBookNLPTokens(String filepath) throws IOException {
		List<String> sentences = new ArrayList<String>(); 
		Map<String, Set<String>> characters = new HashMap<String, Set<String>>();
		
		String line;
		BufferedReader br = new BufferedReader(new FileReader(filepath));
		line = br.readLine();	//header
		line = br.readLine();
		
		String paraId = "", sentId = "", sentText = "";
		boolean sentDialogStart = false, sentDialogEnd = false;
		boolean charStarted = false;
		String charId = "", charName = "", charSpace = "", charPos = "";
		while (line != null) {
			String[] cols = line.split("\t");
			
			if (!paraId.equals("")
					&& !sentId.equals("")) {
				if (cols[0].equals(paraId)) {
					if (!cols[1].equals(sentId)) {
						if (sentDialogStart && sentDialogEnd) sentences.add("###DIALOGUE-ONLY###" + sentText);
						else if (sentDialogStart && !sentDialogEnd) sentences.add("###DIALOGUE###" + sentText);
						else if (!sentDialogStart && sentDialogEnd) sentences.add("###DIALOGUE###" + sentText);
						else sentences.add(sentText);
						
						sentText = "";
						paraId = cols[0];
						sentId = cols[1];
						if (cols[13].equals("true") || cols[10].equals("``")) sentDialogStart = true;
						else sentDialogStart = false;
						
					}
				} else {
					if (sentDialogStart && sentDialogEnd) sentences.add("###DIALOGUE-ONLY###" + sentText);
					else if (sentDialogStart && !sentDialogEnd) sentences.add("###DIALOGUE###" + sentText);
					else if (!sentDialogStart && sentDialogEnd) sentences.add("###DIALOGUE###" + sentText);
					else sentences.add(sentText);
					
					sentText = "";
					paraId = cols[0];
					sentId = cols[1];
					if (cols[13].equals("true") || cols[10].equals("``")) sentDialogStart = true;
					else sentDialogStart = false;
					
				}
			} else {
				paraId = cols[0];
				sentId = cols[1];
				if (cols[13].equals("true")) sentDialogStart = true;
				else sentDialogStart = false;
			}
			
			if (!charStarted) {
				if (!cols[14].equals("-1")) {
					charStarted = true;
					charId = cols[14];
					charSpace = cols[5];
					charPos = cols[10];
					
					if (charSpace.equals("S")) charName = cols[8] + " ";
					else charName = cols[8];
				
				} else {
					if (cols[5].equals("S")) sentText += cols[8] + " ";
					else if (cols[5].equals("")) sentText += cols[8];
					else sentText += cols[8];
					
					if (cols[13].equals("true") || cols[10].equals("''")) sentDialogEnd = true;
					else sentDialogEnd = false;
				}
				
			} else {
				if (cols[14].equals("-1")) {
					if (charPos.equals("PRP$")) {
						sentText += "###CHAR" + charId + "###'s ";
					} else {
						if (charSpace.equals("S")) sentText += "###CHAR" + charId + "### ";
						else sentText += "###CHAR" + charId + "###";
					}					
					
					if (!characters.containsKey(charId)) characters.put(charId, new HashSet<String>());
					if (!charPos.equals("PRP$") && !charPos.equals("PRP")) characters.get(charId).add(charName.trim());
					charStarted = false;
					charSpace = "";
					charPos = "";
					
					if (cols[5].equals("S")) sentText += cols[8] + " ";
//					else if (cols[5].equals("")) sentText += cols[8];
					else sentText += cols[8];
					
				} else {
					if (cols[14].equals(charId)) {
						if (cols[5].equals("S")) charName += cols[8] + " ";
//						else if (cols[5].equals("")) charName += cols[8];
						else charName += cols[8];
						
					} else {
						if (cols[5].equals("S")) sentText += "###CHAR" + charId + "### ";
						else sentText += "###CHAR" + charId + "###";
						
						if (!characters.containsKey(charId)) characters.put(charId, new HashSet<String>());
						if (!charPos.equals("PRP$") && !charPos.equals("PRP")) characters.get(charId).add(charName.trim());
						
						charStarted = true;
						charId = cols[14];
						charSpace = cols[5];
						charPos = cols[10];
						
						if (charSpace.equals("S")) charName = cols[8] + " ";
						else charName = cols[8];
					}
				}
			}
			
			line = br.readLine();
		}
		br.close();
		
		for (String key : characters.keySet()) {
			System.out.print(key + "---");
			System.out.println(characters.get(key));
		}
		
		return sentences;
	}
	
	public static void main(String[] args) throws IOException {
		BookNLPParser parser = new BookNLPParser();
		List<String> sentences = parser.parseBookNLPTokens("./data/harry_potter/2_chamber_of_secrets.tokens");
		
		BufferedWriter bw = new BufferedWriter(new FileWriter("./data/harry_potter/2_chamber_of_secrets.sentences"));
		for (String sent : sentences) {
			bw.write(sent + "\n");
		}
		bw.close();
	}

}
