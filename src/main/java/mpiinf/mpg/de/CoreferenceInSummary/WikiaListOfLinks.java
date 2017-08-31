package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
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

public class WikiaListOfLinks {
	
	public StoryEntity createEntity(String wikiaUrl, String name, Set<String> mentions) throws MalformedURLException, UnsupportedEncodingException, IOException {
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
//    	System.out.println(infoboxStr);
    	
    	Pattern p;
    	Matcher m;
    	
    	String pattern3 = "\\{\\{([\\w\\d\\s]+)infobox";
    	p = Pattern.compile(pattern3);
    	m = p.matcher(infoboxStr);
    	if(m.find()) {	//There is an infobox!
    		String infoboxName = m.group(1).replaceAll("_", "").trim();            		
    		return extractEntity(wikiaUrl, name, id, mentions, infoboxStr, infoboxName);
        }
    	return null;
	}
	
	public StoryEntity extractEntity(String wikiaUrl, String name, String pageId, Set<String> aliases, String infoboxStr, String infoboxName) {
		String entityUrl = wikiaUrl + "/" + name;
//		System.out.println(entityUrl);
		
		Pattern p;
    	Matcher m;
		
		String fullname = name;
		String namePattern = "\\|name=([\\w\\d\\s]+)";
		p = Pattern.compile(namePattern);
    	m = p.matcher(infoboxStr);
    	if (m.find()) {
    		fullname = m.group(1).trim();
    	}
    	if (!fullname.equals(name)) aliases.add(name);
    	
    	Gender gender = Gender.UNKNOWN;
    	String genderPattern = "\\|gender=(\\w+)";
		p = Pattern.compile(genderPattern);
    	m = p.matcher(infoboxStr);
    	if (m.find()) {
    		if (m.group(1).equals("Male")) gender = Gender.MALE;
    		else if (m.group(1).equals("Female")) gender = Gender.FEMALE;
    	}
    	
    	String familyPattern = "\\|family=(.*?)\\\n\\|\\w";
    	p = Pattern.compile(familyPattern, Pattern.DOTALL);
    	m = p.matcher(infoboxStr);
    	if (m.find()) {
    		String familyMember = m.group(1);
    		familyMember = familyMember.replaceAll("\\*", "");
    		String[] members = familyMember.split("\\\n");
    		for (String s : members) System.out.println(s);
    	}
    	
		StoryEntity ent = new StoryEntity(fullname.split(" ")[0] + pageId, fullname, aliases);
		ent.setUrl(entityUrl);
		ent.setType(infoboxName);
		ent.setGender(gender);
		
		return ent;
	}
	
	public List<StoryEntity> listOfLinks(String wikiaUrl, String url) throws IOException {
		
		List<StoryEntity> entities = new ArrayList<StoryEntity>();
		
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
        	
        	String pattern1 = "\\[\\[([\\w\\d\\s]+)\\|([\\w\\d\\s]+)\\]\\]";
        	p = Pattern.compile(pattern1);
        	m = p.matcher(plotStr);        	
        	
        	while(m.find()) {
        		if (!links.containsKey(m.group(1))) links.put(m.group(1), new HashSet<String>());
        		links.get(m.group(1)).add(m.group(2));
            }
        	
        	String pattern2 = "\\[\\[([\\w\\d\\s]+)\\]\\]";
        	p = Pattern.compile(pattern2);
        	m = p.matcher(plotStr);
        	while(m.find()) {
        		if (!links.containsKey(m.group(1))) links.put(m.group(1), new HashSet<String>());
            }
        	
        	
        	for (String name : links.keySet()) {
        		
        		StoryEntity ent = createEntity(wikiaUrl, name, links.get(name));
        		if (ent != null) {
        			entities.add(ent);
        		}
        		
        	}
        }
		
		return entities;
	}
	
	public static void main(String[] args) throws IOException {
		
		String title = "Harry_Potter_and_the_Philosopher%27s_Stone";
		String summaryUrl = "http://harrypotter.wikia.com/api.php?action=query&prop=revisions&rvprop=content&format=json&rvsection=3&titles=" + title;
		
		WikiaListOfLinks wl = new WikiaListOfLinks();
		
		System.out.println(wl.listOfLinks("http://harrypotter.wikia.com", summaryUrl));
		
//		BufferedWriter bw = new BufferedWriter(new FileWriter("data/wikia.harry.potter.female.character.csv"));
//		bw.write(wc.listOfCharacters("http://harrypotter.wikia.com", femaleUrl, Gender.FEMALE));
//		bw.close();

		
	}

}
