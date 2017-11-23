package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.simple.*;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;  

public class StoryEntity {
	
	private String id;
	private String name;
	private String type;
	private Gender gender;
	private String url;
	private String pageId;
	private String desc;
	private String predDesc;
	private Set<String> aliases;
	private List<StoryEntity> familyMembers;
	private List<Fact> facts;
	
	private Map<Integer, Map<Integer, List<EntityMention>>> mentions;
	
	public StoryEntity() {
		aliases = new HashSet<String>();
		facts = new ArrayList<Fact>();
		mentions = new HashMap<Integer, Map<Integer, List<EntityMention>>>();
	}
	
	public StoryEntity(String pageId, String name, Set<String> aliases) {
		this();
		// entity key
		String key = name.split(" ")[0].replaceAll("'", "");
    	if (key.toLowerCase().equals("mr")
    			|| key.toLowerCase().equals("mrs")
    			|| key.toLowerCase().equals("the")
    			) {
    		key = name.split(" ")[1].replaceAll("'", "");
    	}
    	key = key + pageId;
		this.setId(key); this.setPageId(pageId);
		this.setName(name); this.getAliases().addAll(aliases);
		this.setFacts(new ArrayList<Fact>());
	}
		
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public Gender getGender() {
		return gender;
	}
	
	public void setGender(Gender gender) {
		this.gender = gender;
	}
	
	public boolean isSimilar(String mention) {
		Sentence sent = new Sentence(mention);
		
		String firstName = "", lastName = "", firstMention = "", lastMention = ""; 
		firstName = name.split(" ")[0];
		if (name.split(" ").length > 1) lastName = name.split(" ")[1];
		firstMention = mention.split(" ")[0];
		if (mention.split(" ").length > 1) lastMention = mention.split(" ")[1];
		
		if (mention.equals(name)) {	//full name matches perfectly
			return true;
			
		} else if (aliases.contains(mention)) {	//contained in the aliases list
			return true;
			
		} else if (firstName.toLowerCase().equals(firstMention.toLowerCase())
//				|| compareStrings(firstName, firstMention) >= 0.9
				) {	//first name is similar
			return true;
			
		} else if (lastName.toLowerCase().equals(lastName.toLowerCase())) {	//only last name is similar
			if (gender == getGenderTitle(firstMention))
				return true;
			else
				return false;
			
		} else {
			return false;
		}
	}
	
	public boolean containedInMention(int para, int sent, int start, int end) {
		if (mentions.containsKey(para)) {
			if (mentions.get(para).containsKey(sent)) {
				for (EntityMention em : mentions.get(para).get(sent)) {
					if (em.getStartIdx() >= start 
							&& em.getEndIdx() <= end) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public boolean containMention(int para, int sent, int start, int end) {
		if (mentions.containsKey(para)) {
			if (mentions.get(para).containsKey(sent)) {
				for (EntityMention em : mentions.get(para).get(sent)) {
					if (em.getStartIdx() <= start 
							&& em.getEndIdx() >= end) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public void removeIncludedMention(int para, int sent, int start, int end) {
		if (mentions.containsKey(para)) {
			if (mentions.get(para).containsKey(sent)) {
				Iterator<EntityMention> it = mentions.get(para).get(sent).iterator();
				while (it.hasNext()) {
					EntityMention em = it.next();
					if (em.getStartIdx() >= start
				    		&& em.getEndIdx() <= end) {
				        it.remove();
				    }
				}
			}
		}
	}
	
	public void removeMention(int para, int sent, int start, int end) {
		if (mentions.containsKey(para)) {
			if (mentions.get(para).containsKey(sent)) {
				Iterator<EntityMention> it = mentions.get(para).get(sent).iterator();
				while (it.hasNext()) {
					EntityMention em = it.next();
					if (em.getStartIdx() == start
				    		&& em.getEndIdx() == end) {
				        it.remove();
				    }
				}
			}
		}
	}
	
	public boolean isBelongToFamilyMention(String family) {
		String[] names = name.split(" ");
		String familyName = names[names.length-1];
		
		if (family.endsWith("family")
				&& family.contains(familyName)) {
			return true;
		
		} else if ((family.toLowerCase().startsWith("the") || family.toLowerCase().startsWith("all"))
				&& (family.endsWith(familyName + "s") || family.endsWith(familyName + "s '"))
				) {
			return true;
		}
		return false;
	}
	
	public boolean isBelongToAGroup(String group, int paraIdx, int sentIdx, int startIdx, int endIdx) {
		String groupStr = "";
		boolean found = false;
		Sentence sent = new Sentence(group);
		for (int i=0; i<sent.words().size(); i++) {
			if (sent.word(i).equals(id)) found = true;
			else if (containedInMention(paraIdx, sentIdx, startIdx, endIdx)) found = true;
			if (!sent.posTag(i).equals("NNP") 
					&& !sent.word(i).equals("and") 
					&& !sent.word(i).equals(",")) {
				groupStr += sent.word(i);
			}
		}
		if (found && groupStr.trim().isEmpty()) return true;
		else return false;
	}

	public Set<String> getAliases() {
		return aliases;
	}

	public void setAliases(Set<String> aliases) {
		this.aliases = aliases;
	}

	public Map<Integer, Map<Integer, List<EntityMention>>> getMentions() {
		return mentions;
	}

	public void setMentions(Map<Integer, Map<Integer, List<EntityMention>>> mentions) {
		this.mentions = mentions;
	}

	public List<Fact> getFacts() {
		return facts;
	}

	public void setFacts(List<Fact> facts) {
		this.facts = facts;
	}
	
	public String toString() {
		String entity = id + ": " + name + " (" + type + ", " + gender + ") also known as ";
		for (String alias : aliases) entity += alias + "; ";
		entity += "\nWikia URL: " + url;
		entity += "\nMentioned in (" + countMention() + " times): " + mentions;
		entity += "\nFacts: ";
		for (Fact fact : facts) entity += fact.toString() + "; ";
		entity += "\n";
		return entity;
	}
	
	public double compareStrings(String stringA, String stringB) {
	    return StringUtils.getJaroWinklerDistance(stringA, stringB);
	}
	
	public Gender getGenderTitle(String name) {
		if (name.equalsIgnoreCase("Mr.")
				|| name.equalsIgnoreCase("Sir")) {
			return Gender.MALE;
		} else if (name.equalsIgnoreCase("Mrs.")
				|| name.equalsIgnoreCase("Miss.")
				|| name.equalsIgnoreCase("Ms.")
				|| name.equalsIgnoreCase("Madam")) {
			return Gender.FEMALE;
		} else {
			return null;
		}
	}
	
	public long countMention() {
		int count = 0;
		for (Integer paraIdx : mentions.keySet()) {
			for (Integer sentIdx : mentions.get(paraIdx).keySet()) {
				count += mentions.get(paraIdx).get(sentIdx).size();
			}
		}
		return count;
	}
	
	@Override
	public boolean equals(Object obj) {
		boolean retVal = false;

        if (obj instanceof StoryEntity){
        	StoryEntity ptr = (StoryEntity) obj;
            retVal = ptr.id == this.id;
        }

        return retVal;
	}

	@Override
	public int hashCode() {
		int hash = 7;
        hash = 17 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public List<StoryEntity> getFamilyMembers() {
		return familyMembers;
	}

	public void setFamilyMembers(List<StoryEntity> familyMembers) {
		this.familyMembers = familyMembers;
	}
	
	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		
		obj.put("id", this.id);
		obj.put("name", this.name);
		obj.put("type", this.type);
		obj.put("gender", this.gender);
		obj.put("wikia-url", this.url);
		
		JSONArray arrAliases = new JSONArray();
		int i = 0;
		for (String al : this.aliases) {
			arrAliases.put(i, al);
			i ++;
		}
		obj.put("aliases", arrAliases);
		
		
		
		return obj;
	}
	
	public String toCSV() {
		String csv = "";
		csv += "\"" + this.getId() + "\"";
		csv += ",\"" + this.getName() + "\"";
		csv += ",\"" + this.getType() + "\"";
		csv += ",\"" + this.getDesc() + "\"";
		csv += ",\"" + this.getPredDesc() + "\"";
		csv += ",\"" + this.getGender() + "\"";
		csv += ",\"" + this.getUrl() + "\"";
		csv += ",\"[";
		for (String al : this.aliases) {
			csv += al + ",";
		}
		csv += "]\"";
		csv += ",\"[";
		for (Fact fact : this.facts) {
			csv += fact.toString() + ",";
		}
		csv += "]\"";
		
		return csv;
	}
	
	public String toTSV() {
		String csv = "";
		csv += this.getId();
		csv += "\t" + this.getName().replaceAll("\t", " ");
		csv += "\t" + this.getType().replaceAll("\t", " ");
		csv += "\t" + this.getDesc().replaceAll("\t", " ");
		csv += "\t" + this.getPredDesc().replaceAll("\t", " ");
		csv += "\t" + this.getGender();
		csv += "\t" + this.getUrl().replaceAll("\t", " ");
		csv += "\t[";
		for (String al : this.aliases) {
			csv += al.replaceAll("\t", " ") + ",";
		}
		csv += "]";
		csv += "\t[";
		for (Fact fact : this.facts) {
			csv += fact.toString() + ",";
		}
		csv += "]";
		
		return csv;
	}
	
	public Map<String, StoryEntity> loadFromTSV(String tsvPath) throws IOException {
		Map<String, StoryEntity> entities = new HashMap<String, StoryEntity>();
		
		BufferedReader br = new BufferedReader(new FileReader(tsvPath));
		
		String line = br.readLine();
		while (line != null) {
			
			String[] cols = line.split("\t");
			StoryEntity ent = new StoryEntity();
			ent.setId(cols[0]);
			ent.setName(cols[1]);
			ent.setType(cols[2]);
			ent.setDesc(cols[3]);
			ent.setPredDesc(cols[4]);
			if (cols[5].equals("MALE")) {
				ent.setGender(Gender.MALE);
			} else if (cols[5].equals("FEMALE")) {
				ent.setGender(Gender.FEMALE);
			} else if (cols[5].equals("NEUTRAL")) {
				ent.setGender(Gender.NEUTRAL);
			} else if (cols[5].equals("UNKNOWN")) {
				ent.setGender(Gender.UNKNOWN);
			}
			ent.setUrl(cols[6]);
			String aliases = cols[7].substring(1, cols[7].length()-2);
			for (String alias : aliases.split(",")) {
				ent.getAliases().add(alias);
			}
			String facts = cols[8].substring(1, cols[8].length()-2);
			for (String fact : facts.split(",")) {
				ent.getFacts().add(Fact.loadFromString(fact));
			}
			
			entities.put(ent.getId(), ent);
			
			line = br.readLine();
		}
		
		br.close();
		
		return entities;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String getPredDesc() {
		return predDesc;
	}

	public void setPredDesc(String predDesc) {
		this.predDesc = predDesc;
	}

	public String getPageId() {
		return pageId;
	}

	public void setPageId(String pageId) {
		this.pageId = pageId;
	}

}
