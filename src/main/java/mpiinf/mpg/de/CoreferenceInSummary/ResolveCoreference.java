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
	
	public void annotate(String summary, List<StoryEntity> entities, List<Fact> extractedFacts) {
		
		Document doc = new Document(summary);        
		
		int sentIdx = 1;
		for (Sentence sent : doc.sentences()) {
			
			System.out.println("-------- Sentence " + sentIdx + " -------- " + sent.text());
			
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
						if (ent.containedInMention(sentIdx, m.startIndex+1, m.endIndex+1)
								|| ent.isBelongToFamilyMention(word)) {
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
							addAlias(entities, ent.getId(), m.mentionSpan);
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
					
					String subjId = "", pred = "";
					List<String> obj = new ArrayList<String>();
					
					String[] words = word.split(" ");
					String lastWord = words[words.length-1];
					
					for (StoryEntity ent: entities) {
						if (ent.containedInMention(sentIdx, m.endIndex, m.endIndex+1)) {
							subjId = ent.getId();
						} else if (ent.isSimilar(lastWord)) {
							addAlias(entities, ent.getId(), lastWord);
							addMention(entities, ent.getId(), sentIdx, m.endIndex, m.endIndex+1);								
							corefEntities.add(ent.getId());
							subjId = ent.getId();
						}
						if (ent.containedInMention(sentIdx, m.startIndex+1, m.startIndex+2)) {
							obj.add(ent.getId());
						}
					}
					
					pred = String.join("_", Arrays.asList(words).subList(1, words.length-1)) + "_of";
					
					for (String objId : obj) {
						extractedFacts.add(new Fact(subjId, pred, objId));
					}
				
				} else if (pos.startsWith("NNP-POS")
						&& pos.endsWith("NN-NNP")) {	//proper noun + 's + common noun + proper noun
					
					String subjId = "", pred = "";
					List<String> obj = new ArrayList<String>();
					
					String[] words = word.split(" ");
					String lastWord = words[words.length-1];
					
					for (StoryEntity ent: entities) {
						if (ent.containedInMention(sentIdx, m.endIndex, m.endIndex+1)) {
							subjId = ent.getId();
						} else if (ent.isSimilar(lastWord)) {
							addAlias(entities, ent.getId(), lastWord);
							addMention(entities, ent.getId(), sentIdx, m.endIndex, m.endIndex+1);								
							corefEntities.add(ent.getId());
							subjId = ent.getId();
						}
						if (ent.containedInMention(sentIdx, m.startIndex+1, m.startIndex+3)) {
							obj.add(ent.getId());
						}
					}
					
					pred = String.join("_", Arrays.asList(words).subList(2, words.length-1)) + "_of";
					
					for (String objId : obj) {
						extractedFacts.add(new Fact(subjId, pred, objId));
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
			
			sentIdx ++;
		}
	}
	
	public static void main(String[] args) throws Exception {
		ResolveCoreference sa = new ResolveCoreference();
		
		List<StoryEntity> entities = new ArrayList<StoryEntity>();
		
		StoryEntity vernon = new StoryEntity();
		vernon.setId("vernon_dursley");
		vernon.setGender(Gender.MALE);
		vernon.setName("Vernon Dursley");
		
		StoryEntity petunia = new StoryEntity();
		petunia.setId("petunia_dursley");
		petunia.setGender(Gender.FEMALE);
		petunia.setName("Petunia Dursley");
		
		StoryEntity dudley = new StoryEntity();
		dudley.setId("dudley_dursley");
		dudley.setGender(Gender.MALE);
		dudley.setName("Dudley Dursley");
		
		StoryEntity james = new StoryEntity();
		james.setId("james_potter");
		james.setGender(Gender.MALE);
		james.setName("James Potter");
		
		StoryEntity lily = new StoryEntity();
		lily.setId("lily_potter");
		lily.setGender(Gender.FEMALE);
		lily.setName("Lily Potter");
		
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
		
		
		List<Fact> extractedFacts = new ArrayList<Fact>();
		
		String document = ""
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
				+ "The Dursleys are a well-to-do, status-conscious family living in Surrey, England. "
				+ "Eager to keep up proper appearances, they are embarrassed by Mrs. Dursley’s eccentric sister, Mrs. Potter, whom for years Mrs. Dursley has pretended not to know. "
				+ "On his way to work one ordinary morning, Mr. Dursley notices a cat reading a map. "
				+ "He is unsettled, but tells himself that he has only imagined it. "
				+ "Then, as Mr. Dursley is waiting in traffic, he notices people dressed in brightly colored cloaks. "
				+ "Walking past a bakery later that day, he overhears people talking in an excited manner about his sister-in-law’s family, the Potters, and the Potters’ one-year-old son, Harry. "
				+ "Disturbed but still not sure anything is wrong, Mr. Dursley decides not to say anything to his wife. "
				+ "On the way home, he bumps into a strangely dressed man who gleefully exclaims that someone named “You-Know-Who” has finally gone and that even a “Muggle” like Mr. Dursley should rejoice. "
				+ "Meanwhile, the news is full of unusual reports of shooting stars and owls flying during the day.";
		
	    sa.annotate(document2, entities, extractedFacts);
	    
	    for (StoryEntity ent : entities) {
	    	System.out.println(ent.toString());
	    	System.out.println(ent.getMentions());
	    	System.out.println();
	    }
	    System.out.println(extractedFacts);
	}

}
