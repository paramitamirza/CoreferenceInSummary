package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;
import mpiinf.mpg.de.CoreferenceInSummary.Parser.MateToolsParser;

public class WikiaListOfLinks {
	
	public static Set<String> extractedEntities = new HashSet<String>();
	
	public String getPageIdAndInfoboxStr(String wikiaUrl, String name) throws MalformedURLException, UnsupportedEncodingException, IOException {
		//First section in the entity page (usually contains infobox)
		BufferedReader in = new BufferedReader(new InputStreamReader(new URL(wikiaUrl + "/api.php?action=query&prop=revisions&rvprop=content&format=json&rvsection=0&titles=" + URLEncoder.encode(name, "UTF-8")).openStream()));
		String entityStr = "", entityStrLine;
        while ((entityStrLine = in.readLine()) != null)
        	entityStr += entityStrLine + "\n";
        in.close();
        
        //Get page ID
        JSONObject objEnt = new JSONObject(entityStr).getJSONObject("query").getJSONObject("pages");
        String id = objEnt.keySet().iterator().next();
        
        //Get content
        JSONArray revs = objEnt.getJSONObject(id).getJSONArray("revisions");
    	JSONObject infobox = revs.getJSONObject(revs.length()-1);
    	String infoboxStr = infobox.getString("*");
    	
    	Pattern p;
    	Matcher m;
    	if (infoboxStr.contains("#REDIRECT")) {
    		String pattern1 = "#REDIRECT \\[\\[([\\w\\d\\s\\.'-\\(\\)]+)\\|([\\w\\d\\s\\.'-]+)\\]\\]";
        	p = Pattern.compile(pattern1);
        	m = p.matcher(infoboxStr);
        	if (m.find()) {
        		return getPageIdAndInfoboxStr(wikiaUrl, m.group(1));
            
        	} else {
        	
	        	String pattern2 = "#REDIRECT \\[\\[([\\w\\d\\s\\.'-\\(\\)]+)\\]\\]";
	        	p = Pattern.compile(pattern2);
	        	m = p.matcher(infoboxStr);
	        	if (m.find()) {
	        		return getPageIdAndInfoboxStr(wikiaUrl, m.group(1));
	            }
        	}
    	}
        
        return id + "###" + infoboxStr;
	}
	
	public StoryEntity createEntity(String wikiaUrl, String name, Set<String> mentions, 
			String pageId, String infoboxStr, String filepath) throws Exception {
		
		Pattern p;
    	Matcher m;
    	
    	extractedEntities.add(pageId);
    	
    	String pattern3 = "\\{\\{([\\w\\d\\s]+)infobox";
    	p = Pattern.compile(pattern3);
    	m = p.matcher(infoboxStr);
    	if(m.find()) {	//There is an infobox!
    		String infoboxName = m.group(1).replaceAll("_", "").trim();    
    		String entityName = extractFullName(infoboxStr);
    		if (entityName != null && !name.equals(entityName)) {
    			mentions.add(name);
    			name = entityName;
    		}
    		
    		StoryEntity ent = new StoryEntity(pageId, name, mentions);
    		
			ent.setUrl(wikiaUrl + "/" + name);
        	ent.setGender(extractGender(infoboxStr));
        	ent.setType(infoboxName);
        	
        	String desc = getFirstSentDesc(wikiaUrl, pageId);
			if (!desc.trim().equals("###")) {
				ent.setPredDesc(desc.split("###")[0]);
				ent.setDesc(desc.split("###")[1]);
			} else {
	        	ent.setPredDesc("");
	    		ent.setDesc("");
			}
			
			Map<String, String> families = getFamilyMembers(wikiaUrl, pageId, infoboxStr, filepath);
			for (String rel : families.keySet()) {
				ent.getFacts().add(new Fact(ent.getId(), rel, families.get(rel)));
			}
        	
        	BufferedWriter bw = new BufferedWriter(new FileWriter(filepath, true));
        	PrintWriter out = new PrintWriter(bw);
        	out.println(ent.toTSV());
        	out.close();
        	bw.close();
        	
        	return ent;
        }
    	return null;
	}
	
	public String getFirstSentDesc(String wikiaUrl, String pageId) throws Exception {
		//Get first sentence
    	BufferedReader in = new BufferedReader(new InputStreamReader(new URL(wikiaUrl + "/api/v1/Articles/AsSimpleJson?id=" + pageId).openStream()));
    	String entityDesc = "", entityDescLine;
        while ((entityDescLine = in.readLine()) != null)
        	entityDesc += entityDescLine + "\n";
        in.close();
        JSONObject objEntDesc = new JSONObject(entityDesc).getJSONArray("sections").getJSONObject(0).getJSONArray("content").getJSONObject(0);
        String entDescStr = "";
        BufferedWriter bw = new BufferedWriter(new FileWriter("./data/" + pageId + ".txt"));
        if (objEntDesc.has("text")) {
        	Document doc = new Document(objEntDesc.getString("text"));
        	if (doc.sentences().size() >= 1) {
//        		entDescStr = doc.sentence(0).text();
//        		entDescStr = entDescStr.replaceAll("bloodwizard", "pure-blood wizard");
//        		entDescStr = entDescStr.replaceAll("bloodwitch", "pure-blood witch");
        	
        		entDescStr = objEntDesc.getString("text");
	        	entDescStr = entDescStr.replaceAll("bloodwizard", "blood wizard");
	    		entDescStr = entDescStr.replaceAll("bloodwitch", "blood witch");
	    		entDescStr = entDescStr.replaceAll("Darkwizard", "dark wizard");
	    		entDescStr = entDescStr.replaceAll("Darkwitch", "dark witch");
        		bw.write(MateToolsParser.toConllString(new Sentence(entDescStr).words()));
        		bw.close();
        	}
        }
        
        String predDesc = getPredDesc("./data/" + pageId + ".txt");
        
        System.out.println(predDesc + "###" + entDescStr);
        return predDesc + "###" + entDescStr;
	}
	
	public String getPredDesc(String sentFilePath) throws Exception {
		String mateLemmatizerModel = "./models/CoNLL2009-ST-English-ALL.anna-3.3.lemmatizer.model";
		String mateTaggerModel = "./models/CoNLL2009-ST-English-ALL.anna-3.3.postagger.model";
		String mateParserModel = "./models/CoNLL2009-ST-English-ALL.anna-3.3.parser.model";
		String mateSrlModel = "./models/CoNLL2009-ST-English-ALL.anna-3.3.srl-4.1.srl.model";
		
		MateToolsParser mateTools = new MateToolsParser(mateLemmatizerModel, mateTaggerModel, mateParserModel, mateSrlModel);
		List<String> parsedDesc = mateTools.run(new File(sentFilePath));
		Files.delete(new File(sentFilePath).toPath());
		
		String predDesc = "";
		for (String line : parsedDesc) {
			if (!line.trim().equals("")
					&& line.split("\t")[11].equals("PRD")) {
				predDesc = line.split("\t")[3];
				break;
			}
		}
		return predDesc;
	}
	
	public String extractFullName(String infoboxStr) {
		Pattern p;
    	Matcher m;
		String fullname;
		String namePattern = "\\|name=([\\w\\d\\s\\.'-]+)";
		p = Pattern.compile(namePattern);
    	m = p.matcher(infoboxStr);
    	if (m.find()) {
    		fullname = m.group(1).trim();
    		return fullname;
    	}
    	
    	return null;
	}
	
	public Gender extractGender(String infoboxStr) {
		Pattern p;
    	Matcher m;
		Gender gender = Gender.UNKNOWN;
    	String genderPattern = "\\|gender=(\\w+)";
		p = Pattern.compile(genderPattern);
    	m = p.matcher(infoboxStr);
    	if (m.find()) {
    		if (m.group(1).equals("Male")) gender = Gender.MALE;
    		else if (m.group(1).equals("Female")) gender = Gender.FEMALE;
    	}
    	
    	return gender; 
	}
	
	public StoryEntity updateDescription(StoryEntity ent, String wikiaUrl, String pageId) throws Exception {
		StoryEntity entity = ent;
		
		String desc = getFirstSentDesc(wikiaUrl, pageId);
		ent.setPredDesc(desc.split("###")[0]);
		ent.setDesc(desc.split("###")[1]);
		
		return entity;
	}
	
	public Map<String, String> getFamilyMembers(String wikiaUrl, String pageId, String infoboxStr, String filepath) throws Exception {
		
		Map<String, String> familyMembers = new HashMap<String, String>();
    	
    	Pattern p;
    	Matcher m;
    	String familyPattern = "\\|family=(.*?)\\\n\\|\\w";
    	p = Pattern.compile(familyPattern, Pattern.DOTALL);
    	m = p.matcher(infoboxStr);
    	if (m.find()) {
    		String familyMember = m.group(1);
    		familyMember = familyMember.replaceAll("\\*", "");
    		String[] members = familyMember.split("\\\n");
    		
    		for (String s : members) {
    			if (s.startsWith("[[") && s.contains("(") && s.contains("")) {
//	    				System.out.println(s);
    				
    				Pattern pp, ppp; Matcher mm, mmm;	    				
    				String familyRelation = "", familyName = "";
    				Set<String> familyAlias = new HashSet<String>();
    				
    				String originalFamily = "née[\\w\\s\\.'-\\[\\|]+?\\]+";
    				s = s.replaceAll(originalFamily, "");
    				
    				String relationPattern = "\\(([\\w\\s-]+)\\)";
    				pp = Pattern.compile(relationPattern);
    				mm = pp.matcher(s);
    				while (mm.find()) {
    					familyRelation = mm.group(1);
    					break;
    				}
    				
    				String familyNamePattern1 = "\\[\\[([\\w\\d\\s\\.'-]+)\\|([\\w\\d\\s\\.'-]+)\\]\\]";
    				String familyNamePattern2 = "\\[\\[([\\w\\d\\s\\.'-]+)\\]\\]";
    				pp = Pattern.compile(familyNamePattern1);
    				ppp = Pattern.compile(familyNamePattern2);
    				mm = pp.matcher(s);
    				mmm = ppp.matcher(s);
    				if (mm.find()) {
    					familyName = mm.group(1);
    					familyAlias.add(mm.group(2));
    				} else if (mmm.find()) {
    					familyName = mmm.group(1);
    					familyAlias.add(mmm.group(1));
    				}
    				
    				if (!familyName.isEmpty()) {
    					String idAndInfobox = getPageIdAndInfoboxStr(wikiaUrl, familyName);
    					String id = idAndInfobox.split("###")[0];
    					String infobox = idAndInfobox.split("###")[1];
    					
    					StoryEntity familyEnt = null; 
    					if (!extractedEntities.contains(id)) {
		    				familyEnt = createEntity(wikiaUrl, familyName, familyAlias, 
		    						id, infobox, filepath);
    					} else {
    						familyEnt = new StoryEntity(id, familyName, familyAlias);
    					}
	    				
	    				if (familyEnt != null) {
	    					familyMembers.put(familyRelation, familyEnt.getId());
	            		}
    				}
    				
    			}
    		}
    	}
    	
    	return familyMembers;
	}
	
	public void listOfLinks(String wikiaUrl, String url, boolean addFamilyMembers, String filepath) throws Exception {
		
//		Map<String, StoryEntity> entities = new HashMap<String, StoryEntity>();
		
        BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()));

        String input = "", inputLine;
        while ((inputLine = in.readLine()) != null)
            input += inputLine + "\n";
        in.close();
        
        StringBuilder output = new StringBuilder();
        
        JSONObject obj = new JSONObject(input).getJSONObject("query").getJSONObject("pages");
        for (String key : obj.keySet()) {
        	JSONArray rev = obj.getJSONObject(key).getJSONArray("revisions");
        	JSONObject plot = rev.getJSONObject(rev.length()-1);
        	String plotStr = plot.getString("*");
        	
//        	System.out.println(plotStr);
        	
        	Pattern p;
        	Matcher m;
        	Map<String, Set<String>> links = new HashMap<String, Set<String>>();
        	
        	String pattern1 = "\\[\\[([\\w\\d\\s\\.'-\\(\\)]+)\\|([\\w\\d\\s\\.'-]+)\\]\\]";
        	p = Pattern.compile(pattern1);
        	m = p.matcher(plotStr);        	
        	
        	while(m.find()) {
        		if (!links.containsKey(m.group(1))) links.put(m.group(1), new HashSet<String>());
        		links.get(m.group(1)).add(m.group(2));
            }
        	
        	String pattern2 = "\\[\\[([\\w\\d\\s\\.'-\\(\\)]+)\\]\\]";
        	p = Pattern.compile(pattern2);
        	m = p.matcher(plotStr);
        	while(m.find()) {
        		if (!links.containsKey(m.group(1))) links.put(m.group(1), new HashSet<String>());
        		links.get(m.group(1)).add(m.group(1));
            }
        	
        	for (String name : links.keySet()) {
        		
        		String idAndInfobox = getPageIdAndInfoboxStr(wikiaUrl, name);
        		String pageId = idAndInfobox.split("###")[0];
        		String infoboxStr = idAndInfobox.split("###")[1];
        		
        		if (!extractedEntities.contains(pageId)) {
					StoryEntity ent = createEntity(wikiaUrl, name, links.get(name), 
							pageId, infoboxStr, filepath);
        		}
				
//        		if (ent != null) {
//        			if (!entities.containsKey(ent.getId())) {
//        				if (ent.getDesc().equals("")) {
//        					String desc = getFirstSentDesc(wikiaUrl, pageId);
//        					if (!desc.trim().equals("###")) {
//	        					ent.setPredDesc(desc.split("###")[0]);
//	        					ent.setDesc(desc.split("###")[1]);
//        					}
//        				}
//        				entities.put(ent.getId(), ent);
//        				
//        				if (addFamilyMembers) {
//        					for (StoryEntity familyEnt : ent.getFamilyMembers()) {
//    	        				if (!entities.containsKey(familyEnt.getId())) {
//    	        					if (familyEnt.getDesc().equals("")) {
//    	            					String desc = getFirstSentDesc(wikiaUrl, familyEnt.getPageId());
//    	            					if (!desc.trim().equals("###")) {
//	    	            					familyEnt.setPredDesc(desc.split("###")[0]);
//	    	            					familyEnt.setDesc(desc.split("###")[1]);
//    	            					}
//    	            				}
//    	        					entities.put(familyEnt.getId(), familyEnt);
//    	        				} else {
//    	        					StoryEntity existingEnt = entities.get(familyEnt.getId());
//    	            				existingEnt.getAliases().addAll(familyEnt.getAliases());
//    	            				existingEnt.getFacts().addAll(familyEnt.getFacts());
//    	            				existingEnt.getFamilyMembers().addAll(familyEnt.getFamilyMembers());
//    	            				if (existingEnt.getDesc().equals("")) {
//    	            					String desc = getFirstSentDesc(wikiaUrl, existingEnt.getPageId());
//    	            					if (!desc.trim().equals("###")) {
//	    	            					existingEnt.setPredDesc(desc.split("###")[0]);
//	    	            					existingEnt.setDesc(desc.split("###")[1]);
//    	            					}
//    	            				}
//    	        				}
//    	        			}
//            			}
//        				
//        			} else {
//        				StoryEntity existingEnt = entities.get(ent.getId());
//        				existingEnt.getAliases().addAll(ent.getAliases());
//        				existingEnt.getFacts().addAll(ent.getFacts());
//        				existingEnt.getFamilyMembers().addAll(ent.getFamilyMembers());
//        				if (existingEnt.getDesc().equals("")) {
//        					String desc = getFirstSentDesc(wikiaUrl, existingEnt.getPageId());
//        					if (!desc.trim().equals("###")) {
//	        					existingEnt.setPredDesc(desc.split("###")[0]);
//	        					existingEnt.setDesc(desc.split("###")[1]);
//        					}
//        				}
//        			}
//        		}
        		
        	}
        }
	}
	
	public void getEntities(String wikia, String title, boolean addFamilyMembers, String filepath) throws Exception {
		
		String summaryUrl = wikia + "/api.php?action=query&prop=revisions&rvprop=content&format=json&rvsection=3&titles=" + title;
		
		WikiaListOfLinks wl = new WikiaListOfLinks();
		
		wl.listOfLinks("http://harrypotter.wikia.com", summaryUrl, addFamilyMembers, filepath);
	}
	
	public Map<String, String> getEntityLinkDict(Map<String, StoryEntity> entities) {
		Map<String, String> links = new HashMap<String, String>();
		for (String key : entities.keySet()) {
			String url = entities.get(key).getUrl();
			links.put(url.replace("http://harrypotter.wikia.com/", ""), key);
		}
		return links;
	}
	
	public static void main(String[] args) throws Exception {
		
		String title = "Harry_Potter_and_the_Philosopher%27s_Stone";
		String wikia = "http://harrypotter.wikia.com";
		
		WikiaListOfLinks wl = new WikiaListOfLinks();		
		wl.getEntities(title, wikia, true, "./output/" + wikia.replace("http://", "") + ".entities.tsv");
		
//		BufferedWriter bw = new BufferedWriter(new FileWriter("data/wikia.harry.potter.female.character.csv"));
//		bw.write(wc.listOfCharacters("http://harrypotter.wikia.com", femaleUrl, Gender.FEMALE));
//		bw.close();

		
	}

}
