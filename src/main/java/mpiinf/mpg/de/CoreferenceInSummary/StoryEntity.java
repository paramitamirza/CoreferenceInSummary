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
		if (mention.equals(name)) {
			return true;
			
		} else if (mention.equals(name.split(" ")[0])) {
			return true;
			
		} else if (mention.split(" ")[0].equals(name.split(" ")[0])) {
			return true;
			
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
				if (mentions.get(sent).get(i).getStartIdx() == start 
						&& mentions.get(sent).get(i).getEndIdx() == end) {
					removeIdx = i;
					break;
				}
			}
			mentions.get(sent).remove(removeIdx);
		}
	}
	
	public boolean isBelongToFamilyMention(String family) {
		String[] names = name.split(" ");
		String familyName = names[names.length-1];
		
		if (family.endsWith("family")
				&& family.contains(familyName)) {
			return true;
		
		} else if (family.equalsIgnoreCase("the " + familyName + "s")
				|| family.equalsIgnoreCase("the " + familyName + "s '")) {
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

}
