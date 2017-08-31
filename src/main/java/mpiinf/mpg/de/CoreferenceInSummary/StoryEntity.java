package mpiinf.mpg.de.CoreferenceInSummary;

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

public class StoryEntity {
	
	private String id;
	private String name;
	private String type;
	private Gender gender;
	private String url;
	private Set<String> aliases;
	private List<Fact> facts;
	
	private Map<Integer, Map<Integer, List<EntityMention>>> mentions;
	
	public StoryEntity() {
		aliases = new HashSet<String>();
		facts = new ArrayList<Fact>();
		mentions = new HashMap<Integer, Map<Integer, List<EntityMention>>>();
	}
	
	public StoryEntity(String id, String name, Set<String> aliases) {
		this();
		this.setId(id); this.setName(name); this.getAliases().addAll(aliases);
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
			
		} else if (compareStrings(firstName, firstMention) >= 0.9
				|| firstName.toLowerCase().contains(firstMention.toLowerCase())) {	//first name is similar
			return true;
			
		} else if (compareStrings(lastName, lastMention) >= 0.9) {	//only last name is similar
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

}
