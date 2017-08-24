package mpiinf.mpg.de.CoreferenceInSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.simple.*;

public class StoryEntity {
	
	private String id;
	private String name;
	private Gender gender;
	private List<String> aliases;
	
	private Map<Integer, List<EntityMention>> mentions;
	
	public StoryEntity() {
		aliases = new ArrayList<String>();
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
		if (mention.equals(name)) {
			return true;
			
		} else if (mention.equals(name.split(" ")[0])) {
			return true;
			
		} else if (mention.split(" ")[0].equals(name.split(" ")[0])) {
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
	
	public boolean isBelongToFamilyMention(String family) {
		String[] names = name.split(" ");
		String familyName = names[names.length-1];
		
		if (family.endsWith("family")
				&& family.contains(familyName)) {
			return true;
		
		} else if (family.contains(familyName + "s")) {
			return true;
		}
		return false;
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
	
	public String toString() {
		String entity = id + ": " + name + " (" + gender + ") also known as ";
		for (String alias : aliases) entity += alias + ", ";
		return entity;
	}

}
