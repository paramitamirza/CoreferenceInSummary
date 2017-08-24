package mpiinf.mpg.de.CoreferenceInSummary;

import java.util.*;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;
import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.simple.*;
import edu.stanford.nlp.simple.Document;

public class ResolveCoreference {
	
	private StanfordCoreNLP pipeline;
	private Map<String, String> characters;
	
	private List<String> currentPlural;
	private String currentMaleSingular;
	private String currentFemaleSingular;
	private String currentNeutralSingular;
	private List<String> nonReferentEntity;
	
	public ResolveCoreference() {
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
		if (!entity.getAliases().contains(alias)) {
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
	
	public void annotate(String summary, List<StoryEntity> entities) {
		
		Document doc = new Document(summary);        
		
		int sentIdx = 1;
		for (Sentence sent : doc.sentences()) {
			
			System.out.println("-------- Sentence " + sentIdx + " -------- ");
			System.out.println(sent.text());
			
			Annotation document = new Annotation(sent.text());
//			Annotation document = new Annotation(summary);
			
			pipeline.annotate(document);
			
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			
			//First of all, take care of proper nouns...
			for (Mention m : sentences.get(0).get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
				
				List<String> corefEntities = new ArrayList<String>();
				
				CoreLabel token;
				String word = "", pos = "", ner = "";
				for (int idx = m.startIndex; idx < m.endIndex; idx ++) {
					token = sentences.get(m.sentNum).get(TokensAnnotation.class).get(idx);
					pos += token.get(PartOfSpeechAnnotation.class) + "-";
					ner += token.get(NamedEntityTagAnnotation.class) + "-";
				}
				pos = pos.substring(0, pos.length()-1);
				ner = ner.substring(0, ner.length()-1);
				
				word = m.spanToString();
				
				if (pos.contains("NNP")) {
					
					//Named Entity or a collection of Named Entities					
					for (StoryEntity ent: entities) {
						if (ent.containedInMention(sentIdx, m.startIndex+1, m.endIndex+1)) {
//							addAlias(entities, ent.getId(), word);
//							addMention(entities, ent.getId(), sentIdx, m.startIndex+1, m.endIndex+1);
							corefEntities.add(ent.getId());
						
						} else if (ent.isBelongToFamilyMention(word)) {
//							addAlias(entities, ent.getId(), word);
							addMention(entities, ent.getId(), sentIdx, m.startIndex+1, m.endIndex+1);
							corefEntities.add(ent.getId());
							
						} else {
							String poss = pos+"-";
							if (pos.endsWith("POS")
									|| poss.replaceAll("NNP-", "").equals("")) {
								if (word.endsWith("'s")) word = word.replace("'s", "").trim();
								if (ent.isSimilar(word)) {
									addAlias(entities, ent.getId(), word);
									addMention(entities, ent.getId(), sentIdx, m.startIndex+1, m.endIndex+1);								
									corefEntities.add(ent.getId());
									break;
								}
							}
						}
						
					}
				}
				
				if (corefEntities.size() > 1) {
					currentPlural = corefEntities;
					
				} else if (corefEntities.size() == 1) {
					if (getEntityById(corefEntities.get(0), entities).getGender() == Gender.MALE) {
						currentMaleSingular = corefEntities.get(0);
						
					} else if (getEntityById(corefEntities.get(0), entities).getGender() == Gender.FEMALE) {
						currentFemaleSingular = corefEntities.get(0);
						
					} else {
						currentNeutralSingular = corefEntities.get(0);
					}
				}
			}
			
			//Then, take care of coreference chains...
			for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
								
				List<String> corefEntities = new ArrayList<String>();
				
//				System.out.println("\t" + cc);
//				System.out.println(corefEntities);
//				System.out.println(currentPlural);
//				System.out.println(currentMaleSingular);
//				System.out.println(currentFemaleSingular);
//				System.out.println(currentNeutralSingular);
				
				for (CorefMention m : cc.getMentionsInTextualOrder()) {
					
					//Named Entity or a collection of Named Entities
					for (StoryEntity ent: entities) {
						if (ent.containedInMention(sentIdx, m.startIndex, m.endIndex)
								|| ent.isBelongToFamilyMention(m.mentionSpan)) {
//							addAlias(entities, ent.getId(), m.mentionSpan);
							addMention(entities, ent.getId(), sentIdx, m.startIndex, m.endIndex);
							corefEntities.add(ent.getId());
						
						} else {
							if (ent.isSimilar(m.mentionSpan)) {
								addAlias(entities, ent.getId(), m.mentionSpan);
								addMention(entities, ent.getId(), sentIdx, m.startIndex, m.endIndex);
								corefEntities.add(ent.getId());
								break;
							}
						}
						
					}
					break;
				}
				
				Gender chainGender = Gender.NEUTRAL;
				if (corefEntities.size() == 1) chainGender = getEntityById(corefEntities.get(0), entities).getGender();
				
				for (CorefMention m : cc.getMentionsInTextualOrder()) {
					CoreLabel token;
					String word = "", pos = "", ner = "";
					for (int idx = m.startIndex-1; idx < m.endIndex-1; idx ++) {
						token = sentences.get(m.sentNum-1).get(TokensAnnotation.class).get(idx);
						pos += token.get(PartOfSpeechAnnotation.class) + "-";
						ner += token.get(NamedEntityTagAnnotation.class) + "-";
					}
					pos = pos.substring(0, pos.length()-1);
					ner = ner.substring(0, ner.length()-1);
					
					word = m.mentionSpan;
				
					if (pos.equals("PRP")
							|| pos.equals("PRP$")) {	//pronouns
						
						if (matchGender(word, chainGender) || chainGender == Gender.NEUTRAL) {
							for (String entId : corefEntities) {
								addMention(entities, entId, sentIdx, m.startIndex, m.endIndex);
							}
						}
					}
				}
				
				if (corefEntities.size() > 1) {
					currentPlural = corefEntities;
					
				} else if (corefEntities.size() == 1) {
					if (getEntityById(corefEntities.get(0), entities).getGender() == Gender.MALE) {
						currentMaleSingular = corefEntities.get(0);
						
					} else if (getEntityById(corefEntities.get(0), entities).getGender() == Gender.FEMALE) {
						currentFemaleSingular = corefEntities.get(0);
						
					} else {
						currentNeutralSingular = corefEntities.get(0);
					}
				}
		    }
			
			//Finally, take care of unresolved pronouns and other stuff...
			for (Mention m : sentences.get(0).get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
				
				List<String> corefEntities = new ArrayList<String>();
				
				CoreLabel token;
				String word = "", pos = "", ner = "";
				for (int idx = m.startIndex; idx < m.endIndex; idx ++) {
					token = sentences.get(m.sentNum).get(TokensAnnotation.class).get(idx);
					pos += token.get(PartOfSpeechAnnotation.class) + "-";
					ner += token.get(NamedEntityTagAnnotation.class) + "-";
				}
				pos = pos.substring(0, pos.length()-1);
				ner = ner.substring(0, ner.length()-1);
				
				word = m.spanToString();
				
				//Personal or Possessive pronouns
				if (pos.equals("PRP")
						|| pos.equals("PRP$")) {	//pronouns
					
					if (word.equalsIgnoreCase("they") || word.equalsIgnoreCase("them") || word.equalsIgnoreCase("their") || word.equalsIgnoreCase("themselves")) {
						for (String entId : currentPlural) {
							addMention(entities, entId, sentIdx, m.startIndex+1, m.endIndex+1);
						}
					
					} else if (word.equalsIgnoreCase("he") || word.equalsIgnoreCase("him") || word.equalsIgnoreCase("his") || word.equalsIgnoreCase("himself")) {
						if (currentMaleSingular != null) addMention(entities, currentMaleSingular, sentIdx, m.startIndex+1, m.endIndex+1);
					
					} else if (word.equalsIgnoreCase("she") || word.equalsIgnoreCase("her") || word.equalsIgnoreCase("her") || word.equalsIgnoreCase("herself")) {
						if (currentFemaleSingular != null) addMention(entities, currentFemaleSingular, sentIdx, m.startIndex+1, m.endIndex+1);
					
					} else if (word.equalsIgnoreCase("it") || word.equalsIgnoreCase("it") || word.equalsIgnoreCase("its") || word.equalsIgnoreCase("itself")) {
						if (currentNeutralSingular != null) addMention(entities, currentNeutralSingular, sentIdx, m.startIndex+1, m.endIndex+1);
					}
					
					
					
				} else if (pos.startsWith("PRP$")
						&& pos.endsWith("NN-NNP")) {	//possessive pronoun + common noun + proper noun
					
					addFacts(sentIdx, m.startIndex+1, m.startIndex+2
							, m.endIndex, m.endIndex+1
							, word, pos
							, entities
							, corefEntities
							);
				
				} else if (pos.startsWith("NNP-POS")
						&& pos.endsWith("NN-NNP")) {	//proper noun + 's + common noun + proper noun
					
					addFacts(sentIdx, m.startIndex+1, m.startIndex+3
							, m.endIndex, m.endIndex+1
							, word, pos
							, entities
							, corefEntities
							);
				
				}
				
				if (corefEntities.size() > 1) {
					currentPlural = corefEntities;
					
				} else if (corefEntities.size() == 1) {
					if (getEntityById(corefEntities.get(0), entities).getGender() == Gender.MALE) {
						currentMaleSingular = corefEntities.get(0);
						
					} else if (getEntityById(corefEntities.get(0), entities).getGender() == Gender.FEMALE) {
						currentFemaleSingular = corefEntities.get(0);
						
					} else {
						currentNeutralSingular = corefEntities.get(0);
					}
				}
			}
			
			Map<String, List<String>> mentions = new HashMap<String, List<String>>();
			for (StoryEntity entity : entities) {
				if (entity.getMentions().containsKey(sentIdx)) {
					for (EntityMention em : entity.getMentions().get(sentIdx)) {
						if (!mentions.containsKey(em.toString())) mentions.put(em.toString(), new ArrayList<String>());
						mentions.get(em.toString()).add(entity.getId());
					}
				}
			}
//			System.out.println(mentions);
			
			List<CoreLabel> tokens = sentences.get(0).get(TokensAnnotation.class);
			CoreLabel token;
			List<String> coreferenceSent = new ArrayList<String>();
			for (int i=0; i<tokens.size(); i++) {
				coreferenceSent.add(tokens.get(i).word());
			}
			for (int i=0; i<tokens.size(); i++) {
				token = tokens.get(i);
				for (String span : mentions.keySet()) {
					if (mentions.get(span).size() == 1) {
						int startIdx = Integer.parseInt(span.split("-")[0]);
						if (i+1 == startIdx) {
							String replace = mentions.get(span).get(0);
							if (token.word().equalsIgnoreCase("his")
									|| token.word().equalsIgnoreCase("her")
									|| token.word().equalsIgnoreCase("its")) {
								replace += " 's";
							}
							coreferenceSent.set(i, replace);
							
						} else if (isIndexInSpan(i+1, span)) {
							if (!token.word().equals("'s")) {
								coreferenceSent.set(i, "NULL");
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
							if (token.word().equalsIgnoreCase("their")) {
								replace += " 's";
							}
							coreferenceSent.set(i, replace);
							
						} else if (isIndexInSpan(i+1, span)) {
							if (!token.word().equals("'s")) {
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
			System.out.println(coreferenceSentStr);
			
			sentIdx ++;
		}
	}
	
	private boolean isIndexInSpan (int idx, String span) {
		int startIdx = Integer.parseInt(span.split("-")[0]);
		int endIdx = Integer.parseInt(span.split("-")[1]);
		if (idx >= startIdx && idx < endIdx) return true;
		else return false;
	}
	
	
	
	public static void main(String[] args) throws Exception {
		ResolveCoreference sa = new ResolveCoreference();
		
		List<StoryEntity> entities = new ArrayList<StoryEntity>();
		
		StoryEntity vernon = new StoryEntity();
		vernon.setId("vernon_dursley");
		vernon.setGender(Gender.MALE);
		vernon.setName("Vernon Dursley");
		vernon.getAliases().add("Mr. Dursley");
		
		StoryEntity petunia = new StoryEntity();
		petunia.setId("petunia_dursley");
		petunia.setGender(Gender.FEMALE);
		petunia.setName("Petunia Dursley");
		petunia.getAliases().add("Mrs. Dursley");
		
		StoryEntity dudley = new StoryEntity();
		dudley.setId("dudley_dursley");
		dudley.setGender(Gender.MALE);
		dudley.setName("Dudley Dursley");
		
		StoryEntity james = new StoryEntity();
		james.setId("james_potter");
		james.setGender(Gender.MALE);
		james.setName("James Potter");
		james.getAliases().add("Mr. Potter");
		
		StoryEntity lily = new StoryEntity();
		lily.setId("lily_potter");
		lily.setGender(Gender.FEMALE);
		lily.setName("Lily Potter");
		lily.getAliases().add("Mrs. Potter");
		
		StoryEntity harry = new StoryEntity();
		harry.setId("harry_potter");
		harry.setGender(Gender.MALE);
		harry.setName("Harry Potter");
		
		entities.add(vernon);
		entities.add(petunia);
		entities.add(dudley);
		entities.add(james);
		entities.add(lily);
		entities.add(harry);
		
		String document1 = ""
				+ "Vernon and Petunia Dursley, with their one-year-old son Dudley, are proud to say that they are the most normal people possible. "
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
		
		String document2 = ""
				+ "That night, when Vernon arrives at home, he turns on the news and becomes suspicious when the newsman states that owls have been seen everywhere earlier in the day, and that fireworks have been spotted in Kent. "
				+ "Vernon asks Petunia if she and her sister have been in touch, but she becomes angry and denies it as she does not like to talk about her. "
				+ "When the Dursleys go to bed, a long bearded old man in a purple cloak appears out of nowhere outside of their home.";
		
	    sa.annotate(document1, entities);
	    
	    for (StoryEntity ent : entities) {
	    	System.out.println(ent.toString());
	    	System.out.println(ent.getMentions());
	    	System.out.println();
	    }
	}

}
