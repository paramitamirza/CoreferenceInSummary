package mpiinf.mpg.de.CoreferenceInSummary.Parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.simple.Sentence;

public class TextToColumns {
	
	private static void writeToConllFile(String title, List<String> sentences) throws IOException {
		StringBuilder conllString = new StringBuilder();
		
		for (String s : sentences) {
			Sentence sent = new Sentence(s);
			conllString.append(MateToolsParser.toConllString(sent.words()));
		}
        
        BufferedWriter bw = new BufferedWriter(new FileWriter("./data/" + title + ".conll"));
        bw.write(conllString.toString());
        bw.close();
	}
	
	public static void main(String[] args) throws Exception {
		
		String title = "science-sentences";
		List<String> sentences = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader("./data/" + title + ".txt"));
		String line = br.readLine();
		while (line != null) {
			sentences.add(line);
			line = br.readLine();
		}
		br.close();
		
//		writeToConllFile(title, sentences);
//		MateToolsParser mateTools = new MateToolsParser(ParserConfig.mateLemmatizerModel, ParserConfig.mateTaggerModel, ParserConfig.mateParserModel, ParserConfig.mateSrlModel);
//		mateTools.runFullPipeline(new File("./data/" + title + ".conll"), new File("./data/" + title + ".srl"));
		
		br = new BufferedReader(new FileReader("./data/" + title + ".srl"));
        line = br.readLine();
        int sentIdx = 0;
        int eventIdx = 1;
//        int fileIdx = 0;
        int tIdx = 1;
        
//        BufferedWriter bw = new BufferedWriter(new FileWriter("./data/" + title + "_" + fileIdx + ".col"));
        BufferedWriter bw = new BufferedWriter(new FileWriter("./data/" + title + ".col"));
        bw.write("DCT_0000-00-00");
        for (int i=0; i<17; i++) {
        	bw.write("\tO");
        }
        bw.write("\n\n");
        
        List<String> tokens = new ArrayList<String>();
        List<String> tIdxs = new ArrayList<String>();
        List<String> lemmas = new ArrayList<String>();
        List<String> posTags = new ArrayList<String>();
        Map<String, String> tokIdxId = new HashMap<String, String>();  
        Map<String, String> dep = new HashMap<String, String>();
        List<String> events = new ArrayList<String>();
        List<String> mainVerbs = new ArrayList<String>();
        String token, tId, lemma, posTag, depId, depRel;
        while (line != null) {
        	if (line.trim().equals("")) {	//new sentence!
        		
//        		if ((sentIdx % 100) == 0) {
//        			bw.close();
//        			bw = new BufferedWriter(new FileWriter("./data/" + title + "_" + fileIdx + ".col"));
//        			fileIdx ++;
//        		}
        		
        		sentIdx ++;
        		
        		for (int t=0; t<tokens.size(); t++) {
        			bw.write(
        					tokens.get(t) 
        					+ "\t" + tIdxs.get(t)
        					+ "\t" + sentIdx
        					+ "\t" + lemmas.get(t)
        					+ "\t" + events.get(t)
        					+ "\t" + "OCCURRENCE"
        					+ "\t" + "O"
        					+ "\t" + "O"
        					+ "\t" + "O"
        					+ "\t" + "O"
        					+ "\t" + "O"
        					+ "\t" + "O"
        					+ "\t" + posTags.get(t)
//        					+ "\t" + "O"
        					);
        			if (posTags.get(t).startsWith("V")) {
        				bw.write("\t" + "VP");
        			} else {
        				bw.write("\t" + "O");
        			}
        			bw.write(
        					"\t" + lemmas.get(t)
        					+ "\t" + posTags.get(t)
//        					+ "\t" + dep.get(tIdxs.get(t)).substring(2)
//        					+ "\t" + mainVerbs.get(t)
        					);
        			if (dep.containsKey(t+1 + "")) {
        				bw.write("\t" + dep.get(t+1 + "").substring(2));
        			} else {
        				bw.write("\t" + "O");
        			}
        			bw.write("\t" + mainVerbs.get(t));
        			bw.write("\n");
        		}
        		bw.write("\n");
        		tokens.clear();
        		tIdxs.clear();
        		lemmas.clear();
        		posTags.clear();
        		tokIdxId.clear();
        		dep.clear();
        		events.clear();
        		mainVerbs.clear();
        		
        	} else {
	        	String[] cols = line.split("\t");
	        	token = cols[1]; tokens.add(token);
	        	tId = "t" + tIdx; tIdxs.add(tId); tIdx ++;
	        	tokIdxId.put(cols[0], tId);
	        	lemma = cols[2]; lemmas.add(lemma);
	        	posTag = cols[4]; posTags.add(posTag);
	        	depId = cols[8]; 
	        	depRel = cols[10];
	        	if (!dep.containsKey(depId)) dep.put(depId, "");
	        	dep.put(depId, dep.get(depId) + "||" + tId + ":" + depRel);
	        	if (cols[12].equals("Y")) {
	        		events.add("e" + eventIdx);
	        		eventIdx ++;
	        	} else {
	        		events.add("O");
	        	}
	        	if (cols[10].equals("ROOT")) {
	        		mainVerbs.add("mainVb");
	        	} else {
	        		mainVerbs.add("O");
	        	}
        	}
        	
        	line = br.readLine();
        }
        
        br.close();
        bw.close();
		
	}

}
