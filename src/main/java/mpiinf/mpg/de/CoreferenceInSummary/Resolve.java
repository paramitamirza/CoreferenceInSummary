package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;
import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.simple.*;
import edu.stanford.nlp.simple.Document;

import org.apache.commons.lang3.StringUtils;  

public class Resolve {
	
	private StanfordCoreNLP pipeline;
	private Map<String, String> characters;
	
	private List<String> currentPlural;
	private String currentMaleSingular;
	private String currentFemaleSingular;
	private String currentNeutralSingular;
	private List<String> nonReferentEntity;
	
	public Resolve() {
		Properties props = new Properties();
	    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,coref");
	    props.setProperty("coref.algorithm", "neural");
	    pipeline = new StanfordCoreNLP(props);
	}
	
	public StoryEntity getEntityById(String id, List<StoryEntity> corefEntities) {
		for (StoryEntity ent : corefEntities) {
			if (ent.getId().equals(id)) return ent;
		}
		return null;
	}
	
	public boolean matchGender(String pronoun, Gender gender) {
		if (gender == Gender.MALE 
				&& (pronoun.equalsIgnoreCase("he")
						|| pronoun.equalsIgnoreCase("him")
						|| pronoun.equalsIgnoreCase("his")
						|| pronoun.equalsIgnoreCase("himself"))) {
			return true;
		} else if (gender == Gender.FEMALE 
				&& (pronoun.equalsIgnoreCase("she")
						|| pronoun.equalsIgnoreCase("her")
						|| pronoun.equalsIgnoreCase("herself"))) {
			return true;
		
		} else if (gender == Gender.NEUTRAL 
				&& (pronoun.equalsIgnoreCase("it")
						|| pronoun.equalsIgnoreCase("its")
						|| pronoun.equalsIgnoreCase("itself"))) {
			return true;
		
		} else {
			return false;
		}
	}
	
	public void addMention(List<StoryEntity> entities, String entityId, int sentIdx, int startIdx, int endIdx) {
		StoryEntity entity = getEntityById(entityId, entities);
		if (!entity.getMentions().containsKey(sentIdx)) {
			entity.getMentions().put(sentIdx, new ArrayList<EntityMention>());
		}
		if (!entity.containMention(sentIdx, startIdx, endIdx)) {
			entity.getMentions().get(sentIdx).add(new EntityMention(startIdx, endIdx));
		}
	}
	
	public void addAlias(List<StoryEntity> entities, String entityId, String alias) {
		StoryEntity entity = getEntityById(entityId, entities);
		if (!entity.getAliases().contains(alias)
				&& !alias.equals(entity.getId() + " 's")) {
			entity.getAliases().add(alias);
		}
	} 
	
	public void inferFacts(StoryEntity subj, StoryEntity obj, String pred) {
		if (pred.equals("son") || pred.equals("daughter") || pred.equals("child") || pred.equals("baby")) {
			if (subj.getGender() == Gender.MALE) obj.getFacts().add(new Fact(obj.getId(), "father", subj.getId()));
			else if (subj.getGender() == Gender.FEMALE) obj.getFacts().add(new Fact(obj.getId(), "mother", subj.getId()));
		} else if (pred.equals("mother") || pred.equals("father") || pred.equals("parent")) {
			if (obj.getGender() == Gender.MALE) obj.getFacts().add(new Fact(obj.getId(), "son", subj.getId()));
			else if (obj.getGender() == Gender.FEMALE) obj.getFacts().add(new Fact(obj.getId(), "daughter", subj.getId()));
		} else if (pred.equals("sister") || pred.equals("brother") || pred.equals("sibling")) {
			if (obj.getGender() == Gender.MALE) obj.getFacts().add(new Fact(obj.getId(), "brother", subj.getId()));
			else if (obj.getGender() == Gender.FEMALE) obj.getFacts().add(new Fact(obj.getId(), "sister", subj.getId()));
		} else if (pred.equals("grandson") || pred.equals("granddaughter") || pred.equals("grandchild")) {
			if (subj.getGender() == Gender.MALE) obj.getFacts().add(new Fact(obj.getId(), "grandfather", subj.getId()));
			else if (subj.getGender() == Gender.FEMALE) obj.getFacts().add(new Fact(obj.getId(), "grandmother", subj.getId()));
		} else if (pred.equals("grandmother") || pred.equals("grandfather") || pred.equals("grandparent")) {
			if (obj.getGender() == Gender.MALE) obj.getFacts().add(new Fact(obj.getId(), "grandson", subj.getId()));
			else if (obj.getGender() == Gender.FEMALE) obj.getFacts().add(new Fact(obj.getId(), "granddaughter", subj.getId()));
		} 
	}
	
	public void addFacts(int sentIdx, int subjStartIdx, int subjEndIdx
			, int objStartIdx, int objEndIdx
			, String word, String pos
			, List<StoryEntity> entities
			, List<String> corefEntities
			) {
		String objId = "", pred = "";
		List<String> subj = new ArrayList<String>();
		
		String[] words = word.split(" ");
		String[] poss = pos.split("-");
		String lastWord = words[words.length-1];
		
		for (StoryEntity ent: entities) {
			if (ent.containedInMention(sentIdx, objStartIdx, objEndIdx)) {
				objId = ent.getId();			
			} else if (ent.isSimilar(lastWord)) {
				addAlias(entities, ent.getId(), lastWord);
				addMention(entities, ent.getId(), sentIdx, objStartIdx, objEndIdx);								
				corefEntities.add(ent.getId());
				objId = ent.getId();
			}
			
			if (ent.containedInMention(sentIdx, subjStartIdx, subjEndIdx)) {
				subj.add(ent.getId());
			}
		}
		
		pred = "";
		for (int k=0 ; k<poss.length; k++) {
			if (poss[k].equals("NN")) pred += words[k] + "_";
		}
		pred = pred.substring(0, pred.length()-1);
		
		for (String subjId : subj) {
			StoryEntity subjEntity = getEntityById(subjId, entities);
			StoryEntity objEntity = getEntityById(objId, entities);
			
			if (!subjId.equals(objId)) {
				subjEntity.getFacts().add(new Fact(subjId, pred, objId));
				inferFacts(subjEntity, objEntity, pred);
			} else {
				objEntity.removeMention(sentIdx, subjStartIdx, subjEndIdx);
			}
		}
	}
	
	public List<String> linkEntities(String summary, List<StoryEntity> entities) {
		
		List<String> output = new ArrayList<String>();
		String linkedSummary = "";
		String unlinkedEntities = "";
		
		Document doc = new Document(summary);
		for (Sentence sent : doc.sentences()) {
			
			List<String> words = new ArrayList<String>();
			words.addAll(sent.words());
			
			String currNNP = "";
			List<Integer> currIdx = new ArrayList<Integer>();
			for (int i=0; i<sent.words().size(); i++) {
				if (sent.posTag(i).equals("NNP")) {
					currNNP += sent.word(i) + " ";
					currIdx.add(i);
				
				} else {
					if (!currNNP.isEmpty()) {
						
						//Link with entities if possible
						boolean linked = false;
						for (StoryEntity ent : entities) {
							if (ent.isSimilar(currNNP)) {	//similar to an entity, link them!
								words.set(currIdx.get(0), ent.getId());
								if (currIdx.size() > 1) {
									for (int j=1; j<currIdx.size(); j++) {
										words.set(currIdx.get(j), null);
									}
								}
								linked = true;
								break;
							}
						}
						if (!linked) unlinkedEntities += currNNP + ";";
					}
					currNNP = "";
					currIdx.clear();
				}
			}
			
			//Remove null from sentence
			for (String w : words) if (w != null) linkedSummary += w + " ";
		}
		
		//output list contains: linked summary and list of unlinked entities (in string form)
		output.add(linkedSummary.trim());
		output.add(unlinkedEntities);
		
		return output;
	}
	
	public void annotate(String summary, List<StoryEntity> entities) {
		
		String newSummary = linkEntities(summary, entities).get(0);
		List<String> unlinkedEntities = Arrays.asList(linkEntities(summary, entities).get(1).split(";"));
		
		Annotation document = new Annotation(newSummary);
		pipeline.annotate(document);
		
		List<Integer> resolvedMentions = new ArrayList<Integer>();
		List<Integer> wronglyResolved = new ArrayList<Integer>();
		
		////////////////////////// Resolve coreference chains ///////////////////////////
		for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
							
			List<String> corefEntities = new ArrayList<String>();
			Gender corefGender = Gender.UNKNOWN;
			
			System.out.println("\t" + cc);
			
			CorefMention rep = cc.getRepresentativeMention();
			for (StoryEntity ent: entities) {
				if (rep.mentionSpan.equals(ent.getId())
						|| rep.mentionSpan.equals(ent.getId() + " 's")) {	//single entity
					addAlias(entities, ent.getId(), rep.mentionSpan);
					addMention(entities, ent.getId(), rep.sentNum, rep.startIndex, rep.endIndex);
					resolvedMentions.add(rep.mentionID);
					corefEntities.add(ent.getId());
					corefGender = ent.getGender();
					break;
				
				} else {
					if (ent.isBelongToAGroup(rep.mentionSpan)
							|| ent.isBelongToFamilyMention(rep.mentionSpan)) {	//a group of entities (connected by other words, or belong to the same family)
						addMention(entities, ent.getId(), rep.sentNum, rep.startIndex, rep.endIndex);
						resolvedMentions.add(rep.mentionID);
						corefEntities.add(ent.getId());
					}
				}
			}
			
			for (CorefMention m : cc.getMentionsInTextualOrder()) {
				if (!corefEntities.isEmpty()) {	//linked to Entities
					if (m.mentionID != rep.mentionID) {
						if (m.mentionType == MentionType.PROPER) {
							resolvedMentions.add(m.mentionID);
							//Add proper mentions to entities
							for (String e : corefEntities) {
								addMention(entities, e, m.sentNum, m.startIndex, m.endIndex);
							}
							
						} else if (m.mentionType == MentionType.PRONOMINAL) {
							resolvedMentions.add(m.mentionID);
							if (m.gender == corefGender) {
								//Add pronominal mentions to entities
								for (String e : corefEntities) {
									addMention(entities, e, m.sentNum, m.startIndex, m.endIndex);
								}
							} else {
								wronglyResolved.add(m.mentionID);
							}
						} 					
					}
				} else {	//not linked to any Entities
					//do nothing for now
				}
			}
			
	    }
		
		////////////////////////// Resolve all other mentions ///////////////////////////
		//List all possible mentions
		for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
			for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
				if (!resolvedMentions.contains(m.mentionID)
						|| wronglyResolved.contains(m.mentionID)) {
					
					for (StoryEntity ent: entities) {
						if (m.spanToString().equals(ent.getId())
								|| m.spanToString().equals(ent.getId() + " 's")) {	//single entity
							addAlias(entities, ent.getId(), m.spanToString());
							addMention(entities, ent.getId(), m.sentNum, m.startIndex, m.endIndex);
							resolvedMentions.add(m.mentionID);
							break;
						
						} else {
							if (ent.isBelongToAGroup(m.spanToString())
									|| ent.isBelongToFamilyMention(m.spanToString())) {	//a group of entities (connected by other words, or belong to the same family)
								addMention(entities, ent.getId(), m.sentNum, m.startIndex, m.endIndex);
								resolvedMentions.add(m.mentionID);
							}
						}
					}
				}
			}
		}
		for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
			List<Mention> ms = sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class);
			for (int i=0; i<ms.size(); i++) {
//			for (int i=ms.size()-1; i>=0; i--) {
//				if (i == 0 || (i > 0 && !ms.get(i).includedIn(ms.get(i-1)))) {
					if (!resolvedMentions.contains(ms.get(i).mentionID)
							|| wronglyResolved.contains(ms.get(i).mentionID)) {
						
						System.out.println("\t" + ms.get(i).spanToString() + "-" + ms.get(i).sentNum + "-" + ms.get(i).corefClusterID + "-" + ms.get(i).mentionID);
				
					}
//				}
			}
		}
		
		////////////////////////// Write new sentences ///////////////////////////
		Document doc = new Document(newSummary);
		for (Sentence sent : doc.sentences()) {
			System.out.println((sent.sentenceIndex()+1) + "-" + sent.text());
			
			Map<String, List<String>> mentions = new HashMap<String, List<String>>();
			for (StoryEntity entity : entities) {
				if (entity.getMentions().containsKey(sent.sentenceIndex()+1)) {
					for (EntityMention em : entity.getMentions().get(sent.sentenceIndex()+1)) {
						if (!mentions.containsKey(em.toString())) mentions.put(em.toString(), new ArrayList<String>());
						mentions.get(em.toString()).add(entity.getId());
					}
				}
			}
//			System.out.println(mentions);
			
			List<String> coreferenceSent = new ArrayList<String>();
			coreferenceSent.addAll(sent.words());
			
			for (int i=0; i<sent.words().size(); i++) {
				for (String span : mentions.keySet()) {
					if (mentions.get(span).size() == 1) {
						int startIdx = Integer.parseInt(span.split("-")[0]);
						if (i+1 == startIdx) {
							String replace = mentions.get(span).get(0);
							if (sent.word(i).equalsIgnoreCase("his")
									|| sent.word(i).equalsIgnoreCase("her")
									|| sent.word(i).equalsIgnoreCase("its")) {
								replace += " 's";
							}
							coreferenceSent.set(i, replace);
							
						} else if (isIndexInSpan(i+1, span)) {
							if (!sent.word(i).equals("'s")) {
								coreferenceSent.set(i, null);
							}
						} 
					}
				}
				
				for (String span : mentions.keySet()) {
					if (mentions.get(span).size() > 1) {
						int startIdx = Integer.parseInt(span.split("-")[0]);
						if (i+1 == startIdx) {
							String replace = "";
							for (String s : mentions.get(span)) {
								replace += s + "-";
							}
							replace = replace.substring(0, replace.length()-1);
							if (sent.word(i).equalsIgnoreCase("their")) {
								replace += " 's";
							}
							coreferenceSent.set(i, replace);
							
						} else if (isIndexInSpan(i+1, span)) {
							if (!sent.word(i).equals("'s")) {
								coreferenceSent.set(i, null);
							}
						} 
					}
				}
			}
			
			String coreferenceSentStr = "";
			for (String w : coreferenceSent) {
				if (w != null) coreferenceSentStr += w + " ";
			}
			coreferenceSentStr = coreferenceSentStr.substring(0, coreferenceSentStr.length()-1);
			System.out.println((sent.sentenceIndex()+1) + "-" + coreferenceSentStr + "\n");
		}
	}
	
	private boolean isIndexInSpan (int idx, String span) {
		int startIdx = Integer.parseInt(span.split("-")[0]);
		int endIdx = Integer.parseInt(span.split("-")[1]);
		if (idx >= startIdx && idx < endIdx) return true;
		else return false;
	}
	
	
	
	public static void main(String[] args) throws Exception {
		Resolve sa = new Resolve();
		
		List<StoryEntity> entities = new ArrayList<StoryEntity>();
		
		StoryEntity vernon = new StoryEntity();
		vernon.setId("Vernon");
		vernon.setGender(Gender.MALE);
		vernon.setName("Vernon Dursley");
		vernon.getAliases().add("Mr. Dursley");
		
		StoryEntity petunia = new StoryEntity();
		petunia.setId("Petunia");
		petunia.setGender(Gender.FEMALE);
		petunia.setName("Petunia Dursley");
		petunia.getAliases().add("Mrs. Dursley");
		
		StoryEntity dudley = new StoryEntity();
		dudley.setId("Dudley");
		dudley.setGender(Gender.MALE);
		dudley.setName("Dudley Dursley");
		
		StoryEntity james = new StoryEntity();
		james.setId("James");
		james.setGender(Gender.MALE);
		james.setName("James Potter");
		james.getAliases().add("Mr. Potter");
		
		StoryEntity lily = new StoryEntity();
		lily.setId("Lily");
		lily.setGender(Gender.FEMALE);
		lily.setName("Lily Potter");
		lily.getAliases().add("Mrs. Potter");
		
		StoryEntity harry = new StoryEntity();
		harry.setId("Harry");
		harry.setGender(Gender.MALE);
		harry.setName("Harry Potter");
		
		entities.add(vernon);
		entities.add(petunia);
		entities.add(dudley);
		entities.add(james);
		entities.add(lily);
		entities.add(harry);
		
		BufferedReader br = new BufferedReader(new FileReader(new File("./data/spark.harry.potter.book1.txt")));
		String line, document = "";
	    while ((line = br.readLine()) != null) {
	    	document += line + "\n";
	    }
		
		String document1 = ""
				+ "Vernon and Petunia Dursley, with their one-year-old son, Dudley, are proud to say that they are the most normal people possible. "
				+ "They happily occupy 4 Privet Drive, located in the Surrey County town of Little Whinging. "
				+ "Vernon is the director of a drill company called Grunnings, while Petunia does housework and looks after Dudley, yet she always spends so much time craning over garden fence to spy on the neighbours. "
				+ "Despite all this, the Dursley family are the least people anyone expects to be involved in anything funny or disturbing, for they don't hold with such nonsense in their daily lives. "
				+ "The story begins on a Tuesday 1 November, 1981, where many absurd things happen. "
				+ "When all three Dursleys are gossiping happily at breakfast trying to wrestle Dudley in his high chair, none of them even notice a large tawny owl flutter pass their window. "
				+ "It eventually comes to Vernon's attention when he is heading to work shortly afterwards, and sees a silver tabby cat reading a map and later a street sign. "
				+ "He attempts to convince himself that these are merely coincidences. "
				+ "Next, he sees people in cloaks talking in hushed voices, and while he is sitting in his office, a flock of owls fly past his window. "
				+ "On his break, Vernon goes to a bakery to get a bun and sees another group of extravagantly-cloaked figures. "
				+ "Even though they are speaking in hushed voices, he eavesdrops on some of their conversation, which includes a mention of \"the Potters and their son Harry.\" "
				+ "Vernon finds this horrifying, because the Potters are the family of Petunia's sister Lily. "
				+ "He and Petunia will be embarrassed if people find out that they are related to them, as the Potter family are very strange, in their opinion. "
				+ "When he leaves his workplace at the end of the day, he bumps into a very small, cloaked man, who tells him that someone named \"You-Know-Who\" has been defeated, and that even Muggles like Vernon should be celebrating. "
				+ "Vernon doesn't know what a Muggle is, but is offended that the man called him one.";
//		
//		String document2 = ""
//				+ "That night, when Vernon arrives at home, he turns on the news and becomes suspicious when the newsman states that owls have been seen everywhere earlier in the day, and that fireworks have been spotted in Kent. "
//				+ "Vernon asks Petunia if she and her sister have been in touch, but she becomes angry and denies it as she does not like to talk about her. "
//				+ "When the Dursleys go to bed, a long bearded old man in a purple cloak appears out of nowhere outside of their home.";
		
	    sa.annotate(document1, entities);
	    
	    for (StoryEntity ent : entities) {
	    	System.out.println(ent.toString());
	    	System.out.println(ent.getMentions());
	    	System.out.println();
	    }
	}

}
