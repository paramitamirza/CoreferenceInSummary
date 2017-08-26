package mpiinf.mpg.de.CoreferenceInSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.simple.*;

import org.apache.commons.lang3.StringUtils;  

public class StoryEntity {
	
	private String id;
	private String name;
	private Gender gender;
	private List<String> aliases;
	private List<Fact> facts;
	
	private Map<Integer, List<EntityMention>> mentions;
	
	public StoryEntity() {
		aliases = new ArrayList<String>();
		facts = new ArrayList<Fact>();
		mentions = new HashMap<Integer, List<EntityMention>>();
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
			
		} else if (compareStrings(firstName, firstMention) >= 0.9
				|| firstName.toLowerCase().contains(firstMention.toLowerCase())) {	//first name is similar
			return true;
			
		} else if (compareStrings(lastName, lastMention) >= 0.9) {	//only last name is similar
			if (gender == getGenderTitle(firstMention))
				return true;
			else
				return false;
			
		} else if (aliases.contains(mention)) {
			return true;
		
		} else {
			return false;
		}
	}
	
	public boolean containedInMention(int sent, int start, int end) {
		if (mentions.containsKey(sent)) {
			for (EntityMention em : mentions.get(sent)) {
				if (em.getStartIdx() >= start 
						&& em.getEndIdx() <= end) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean containMention(int sent, int start, int end) {
		if (mentions.containsKey(sent)) {
			for (EntityMention em : mentions.get(sent)) {
				if (em.getStartIdx() == start 
						&& em.getEndIdx() == end) {
					return true;
				}
			}
		}
		return false;
	}
	
	public void removeMention(int sent, int start, int end) {
		if (mentions.containsKey(sent)) {
			int removeIdx = -1;
			for (int i=0; i<mentions.get(sent).size(); i++) {
				System.out.println(mentions.get(sent) + ":" + start + "-" + end);
				if (mentions.get(sent).get(i).getStartIdx() == start 
						&& mentions.get(sent).get(i).getEndIdx() == end) {
					removeIdx = i;
					break;
				}
			}
			if (removeIdx > 0) mentions.get(sent).remove(removeIdx);
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
	
	public boolean isBelongToAGroup(String group, int sentIdx, int startIdx, int endIdx) {
		String groupStr = "";
		boolean found = false;
		Sentence sent = new Sentence(group);
		for (int i=0; i<sent.words().size(); i++) {
			if (sent.word(i).equals(id)) found = true;
			else if (containedInMention(sentIdx, startIdx, endIdx)) found = true;
			if (!sent.posTag(i).equals("NNP") 
					&& !sent.word(i).equals("and") 
					&& !sent.word(i).equals(",")) {
				groupStr += sent.word(i);
			}
		}
		if (found && groupStr.trim().isEmpty()) return true;
		else return false;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public void setAliases(List<String> aliases) {
		this.aliases = aliases;
	}

	public Map<Integer, List<EntityMention>> getMentions() {
		return mentions;
	}

	public void setMentions(Map<Integer, List<EntityMention>> mentions) {
		this.mentions = mentions;
	}

	public List<Fact> getFacts() {
		return facts;
	}

	public void setFacts(List<Fact> facts) {
		this.facts = facts;
	}
	
	public String toString() {
		String entity = id + ": " + name + " (" + gender + ") also known as ";
		for (String alias : aliases) entity += alias + "; ";
		entity += "\nFacts: ";
		for (Fact fact : facts) entity += fact.toString() + "; ";
		return entity;
	}
	
	public double compareStrings(String stringA, String stringB) {
	    return StringUtils.getJaroWinklerDistance(stringA, stringB);
	}
	
	public Gender getGenderTitle(String name) {
		if (name.equalsIgnoreCase("Mr.")) {
			return Gender.MALE;
		} else if (name.equalsIgnoreCase("Mrs.")
				|| name.equalsIgnoreCase("Miss.")
				|| name.equalsIgnoreCase("Ms.")) {
			return Gender.FEMALE;
		} else {
			return null;
		}
	}

}
