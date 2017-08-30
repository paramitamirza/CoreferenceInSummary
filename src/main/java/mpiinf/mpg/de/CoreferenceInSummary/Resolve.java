package mpiinf.mpg.de.CoreferenceInSummary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	
	public void addMention(List<StoryEntity> entities, String entityId, int paraIdx, int sentIdx, int startIdx, int endIdx) {
		StoryEntity entity = getEntityById(entityId, entities);
		if (!entity.getMentions().containsKey(paraIdx)) {
			entity.getMentions().put(paraIdx, new HashMap<Integer, List<EntityMention>>());
		}
		if (!entity.getMentions().get(paraIdx).containsKey(sentIdx)) {
			entity.getMentions().get(paraIdx).put(sentIdx, new ArrayList<EntityMention>());
		}
		if (!entity.containMention(paraIdx, sentIdx, startIdx, endIdx)) {
//			entity.removeIncludedMention(paraIdx, sentIdx, startIdx, endIdx);
			entity.getMentions().get(paraIdx).get(sentIdx).add(new EntityMention(startIdx, endIdx));
		}		
	}
	
	public void addAlias(List<StoryEntity> entities, String entityId, String alias) {
		StoryEntity entity = getEntityById(entityId, entities);
		if (!alias.equals(entity.getId())
				&& !alias.equals(entity.getName())
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
	
	public void addFacts(int paraIdx, int sentIdx
			, int subjStartIdx, int subjEndIdx
			, int objStartIdx, int objEndIdx
			, String word, String pos
			, List<StoryEntity> entities
//			, List<String> corefEntities
			) {
		
		String posCheck = pos + "-";
		posCheck = posCheck.replaceAll("PRP-", "");
		posCheck = posCheck.replaceAll("PRP\\$-", "");
		posCheck = posCheck.replaceAll("POS-", "");
		posCheck = posCheck.replaceAll("NN-", "");
		posCheck = posCheck.replaceAll("NNP-", "");
		posCheck = posCheck.replaceAll("DT-NNS-", "");
		posCheck = posCheck.replaceAll("DT-NNPS-", "");
		posCheck = posCheck.replaceAll("JJ-", "");
		posCheck = posCheck.replaceAll(",-", "");
		
		if (posCheck.trim().isEmpty()) {
		
			String objId = "", pred = "";
			List<String> subj = new ArrayList<String>();
			String[] words = word.split(" ");
			String[] poss = pos.split("-");
			
//			System.out.println(word + ":" + subjStartIdx + ":" + subjEndIdx + ":" + objStartIdx + ":" + objEndIdx);
			
			String objStr = "";
			for (int x=objStartIdx-subjStartIdx; x<objEndIdx-subjStartIdx; x++) {
				objStr += words[x] + " ";
			}
			
			for (StoryEntity ent: entities) {
				
				if (ent.getId().equals(objStr)
						|| ent.isSimilar(objStr)) {
					addAlias(entities, ent.getId(), objStr);
					addMention(entities, ent.getId(), paraIdx, sentIdx, subjStartIdx, objEndIdx);
					objId = ent.getId();
				} 
				
				if (ent.containedInMention(paraIdx, sentIdx, subjStartIdx, subjEndIdx)) {
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
					
					subjEntity.removeMention(paraIdx, sentIdx, subjStartIdx, subjEndIdx);
					objEntity.removeMention(paraIdx, sentIdx, objStartIdx, objEndIdx);
				
				} else {
					objEntity.removeMention(paraIdx, sentIdx, subjStartIdx, subjEndIdx);
				}
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
						currNNP = currNNP.trim();
						
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
	
	public String annotate(String docName, String summary, List<StoryEntity> entities, int paraIdx) throws IOException {
		
		StringBuilder sb = new StringBuilder();
		
		String newSummary = linkEntities(summary, entities).get(0);
		List<String> unlinkedEntities = Arrays.asList(linkEntities(summary, entities).get(1).split(";"));
		
		Annotation document = new Annotation(newSummary);
		pipeline.annotate(document);
		
		Map<Integer, Set<Integer>> resolvedTokens = new HashMap<Integer, Set<Integer>>();
		List<Integer> wronglyResolved = new ArrayList<Integer>();
		
		////////////////////////// Resolve coreference chains ///////////////////////////
		for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
							
			List<String> corefEntities = new ArrayList<String>();
			Gender corefGender = Gender.UNKNOWN;
			
//			System.out.println("\t" + cc);
			
			CorefMention rep = cc.getRepresentativeMention();
			for (StoryEntity ent: entities) {
				if (rep.mentionSpan.equals(ent.getId())
						|| rep.mentionSpan.equals(ent.getId() + " 's")) {	//single entity
					addAlias(entities, ent.getId(), rep.mentionSpan);
					addMention(entities, ent.getId(), paraIdx, rep.sentNum, rep.startIndex, rep.endIndex);
					
					if (!resolvedTokens.containsKey(rep.sentNum)) resolvedTokens.put(rep.sentNum, new HashSet<Integer>());
					for (int x=rep.startIndex; x<rep.endIndex; x++) resolvedTokens.get(rep.sentNum).add(x);
					
					corefEntities.add(ent.getId());
					corefGender = ent.getGender();
					break;
				
				} else {
					if (ent.isBelongToAGroup(rep.mentionSpan, paraIdx, rep.sentNum, rep.startIndex, rep.endIndex)
							|| ent.isBelongToFamilyMention(rep.mentionSpan)) {	//a group of entities (connected by other words, or belong to the same family)
						addMention(entities, ent.getId(), paraIdx, rep.sentNum, rep.startIndex, rep.endIndex);
						
						if (!resolvedTokens.containsKey(rep.sentNum)) resolvedTokens.put(rep.sentNum, new HashSet<Integer>());
						for (int x=rep.startIndex; x<rep.endIndex; x++) resolvedTokens.get(rep.sentNum).add(x);
						
						corefEntities.add(ent.getId());
					}
				}
			}
			
			for (CorefMention m : cc.getMentionsInTextualOrder()) {
				if (!corefEntities.isEmpty()) {	//linked to Entities
					if (m.mentionID != rep.mentionID) {
						if (m.mentionType == MentionType.PROPER) {
							
							if (!resolvedTokens.containsKey(m.sentNum)) resolvedTokens.put(m.sentNum, new HashSet<Integer>());
							for (int x=m.startIndex; x<m.endIndex; x++) resolvedTokens.get(m.sentNum).add(x);
							
							//Add proper mentions to entities
							for (String e : corefEntities) {
								addMention(entities, e, paraIdx, m.sentNum, m.startIndex, m.endIndex);
							}
							
						} else if (m.mentionType == MentionType.PRONOMINAL) {
							
							if (!resolvedTokens.containsKey(m.sentNum)) resolvedTokens.put(m.sentNum, new HashSet<Integer>());
							for (int x=m.startIndex; x<m.endIndex; x++) resolvedTokens.get(m.sentNum).add(x);
							
							if (m.gender == corefGender) {
								//Add pronominal mentions to entities
								for (String e : corefEntities) {
									addMention(entities, e, paraIdx, m.sentNum, m.startIndex, m.endIndex);
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
		for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
			List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
			List<Mention> ms = sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class);
			
//			for (int i=0; i<ms.size(); i++) {
			for (int i=ms.size()-1; i>=0; i--) {
				
				Mention m = ms.get(i);
				
				boolean includedInResolved = true;
				if (resolvedTokens.containsKey((ms.get(i).sentNum+1))) {
					for (int x=(ms.get(i).startIndex+1); x<(ms.get(i).endIndex+1); x++) {
						includedInResolved = includedInResolved && resolvedTokens.get((ms.get(i).sentNum+1)).contains(x);
					}
				} else {
					includedInResolved = false;
				}
				
				if (!includedInResolved
						|| wronglyResolved.contains(m.mentionID)) {
					
					String pos = "", word = "";
					for (int x=m.startIndex; x<m.endIndex; x++) {
						word += tokens.get(x).get(TextAnnotation.class) + " ";
						pos += tokens.get(x).get(PartOfSpeechAnnotation.class) + "-";
					}
					pos = pos.substring(0, pos.length()-1);
					
//					System.out.println("\"" + m.spanToString() + "\"" + "," + pos + "," + m.mentionType + "," + paraIdx + ":" + (m.sentNum+1) + ":" + (m.startIndex+1) + "-" + (m.endIndex+1));
					
					if (pos.startsWith("PRP$")) {	//possessive pronoun + common noun + (,) + proper noun
						if (pos.endsWith("NN-NNP")
								|| pos.endsWith("NN-,-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+2
									, m.endIndex, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN-NNP-NNP")
								|| pos.endsWith("NN-,-NNP-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+2
									, m.endIndex-1, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
						
						} else if (pos.endsWith("NN")) {
							if (tokens.get(m.endIndex).get(PartOfSpeechAnnotation.class).equals(",")) {
								String proper = "", posss = "";
								int endUpdated = m.endIndex;
								for (int z=m.endIndex+1; z<tokens.size(); z++) {
									if (tokens.get(z).get(PartOfSpeechAnnotation.class).equals("NNP")) {
										proper += tokens.get(z).get(TextAnnotation.class) + " ";
										posss += "NNP-";
										endUpdated = z+1;
									} else {
										break;
									}
								}
								if (!proper.isEmpty()) {
									addFacts(paraIdx, m.sentNum+1
											, m.startIndex+1, m.startIndex+2
											, m.endIndex+2, endUpdated+1
											, word.trim() + " , " + proper.substring(0, proper.length()-1), pos + "-,-" + posss.substring(0, posss.length()-1)
											, entities
	//										, corefEntities
											);
									if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
									for (int x=m.startIndex+1; x<endUpdated; x++) resolvedTokens.get(m.sentNum+1).add(x);
								}
							}
						}
						
					} else if ((pos.startsWith("NNP-POS"))) {
						if (pos.endsWith("NN-NNP")
								|| pos.endsWith("NN-,-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+3
									, m.endIndex, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN-NNP-NNP")
								|| pos.endsWith("NN-,-NNP-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+3
									, m.endIndex-1, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN")) {
							if (tokens.get(m.endIndex).get(PartOfSpeechAnnotation.class).equals(",")) {
								String proper = "", posss = "";
								int endUpdated = m.endIndex;
								for (int z=m.endIndex+1; z<tokens.size(); z++) {
									if (tokens.get(z).get(PartOfSpeechAnnotation.class).equals("NNP")) {
										proper += tokens.get(z).get(TextAnnotation.class) + " ";
										posss += "NNP-";
										endUpdated = z+1;
									} else {
										break;
									}
								}
								if (!proper.isEmpty()) {
									addFacts(paraIdx, m.sentNum+1
											, m.startIndex+1, m.startIndex+3
											, m.endIndex+2, endUpdated+1
											, word.trim() + " , " + proper.substring(0, proper.length()-1), pos + "-,-" + posss.substring(0, posss.length()-1)
											, entities
	//										, corefEntities
											);
									if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
									for (int x=m.startIndex+1; x<endUpdated; x++) resolvedTokens.get(m.sentNum+1).add(x);
								}
							}
						}
					} else if ((pos.startsWith("NNP-NNP-POS"))) {
						if (pos.endsWith("NN-NNP")
								|| pos.endsWith("NN-,-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+4
									, m.endIndex, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN-NNP-NNP")
								|| pos.endsWith("NN-,-NNP-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+4
									, m.endIndex-1, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN")) {
							if (tokens.get(m.endIndex).get(PartOfSpeechAnnotation.class).equals(",")) {
								String proper = "", posss = "";
								int endUpdated = m.endIndex;
								for (int z=m.endIndex+1; z<tokens.size(); z++) {
									if (tokens.get(z).get(PartOfSpeechAnnotation.class).equals("NNP")) {
										proper += tokens.get(z).get(TextAnnotation.class) + " ";
										posss += "NNP-";
										endUpdated = z+1;
									} else {
										break;
									}
								}
								if (!proper.isEmpty()) {
									addFacts(paraIdx, m.sentNum+1
											, m.startIndex+1, m.startIndex+4
											, m.endIndex+2, endUpdated+1
											, word.trim() + " , " + proper.substring(0, proper.length()-1), pos + "-,-" + posss.substring(0, posss.length()-1)
											, entities
	//										, corefEntities
											);
									if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
									for (int x=m.startIndex+1; x<endUpdated; x++) resolvedTokens.get(m.sentNum+1).add(x);
								}
							}
						}
					} else if ((pos.startsWith("DT-NNS-POS") || pos.startsWith("DT-NNPS-POS"))) {
						if (pos.endsWith("NN-NNP")
								|| pos.endsWith("NN-,-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+4
									, m.endIndex, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN-NNP-NNP")
								|| pos.endsWith("NN-,-NNP-NNP")) {
							addFacts(paraIdx, m.sentNum+1
									, m.startIndex+1, m.startIndex+4
									, m.endIndex-1, m.endIndex+1
									, word, pos
									, entities
//									, corefEntities
									);
							if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
							for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
							
						} else if (pos.endsWith("NN")) {
							if (tokens.get(m.endIndex).get(PartOfSpeechAnnotation.class).equals(",")) {
								String proper = "", posss = "";
								int endUpdated = m.endIndex;
								for (int z=m.endIndex+1; z<tokens.size(); z++) {
									if (tokens.get(z).get(PartOfSpeechAnnotation.class).equals("NNP")) {
										proper += tokens.get(z).get(TextAnnotation.class) + " ";
										posss += "NNP-";
										endUpdated = z+1;
									} else {
										break;
									}
								}
								if (!proper.isEmpty()) {
									addFacts(paraIdx, m.sentNum+1
											, m.startIndex+1, m.startIndex+4
											, m.endIndex+2, endUpdated+1
											, word.trim() + " , " + proper.substring(0, proper.length()-1), pos + "-,-" + posss.substring(0, posss.length()-1)
											, entities
	//										, corefEntities
											);
									if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
									for (int x=m.startIndex+1; x<endUpdated; x++) resolvedTokens.get(m.sentNum+1).add(x);
								}
							}
						}
					}
					
					for (StoryEntity ent: entities) {
						if (wronglyResolved.contains(m.mentionID)) {
							//Find the correct entity!
							if (ent.getMentions().containsKey(paraIdx)) {
								if (ent.getMentions().get(paraIdx).containsKey(m.sentNum+1)
										&& ent.getGender() == m.gender) {
									addMention(entities, ent.getId(), paraIdx, m.sentNum+1, m.startIndex+1, m.endIndex+1);
									
									if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
									for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
									
									break;
								}
							}
						
						} else {
						
							if (m.spanToString().equals(ent.getId())
									|| m.spanToString().equals(ent.getId() + " 's")) {	//single entity
								addAlias(entities, ent.getId(), m.spanToString());
								addMention(entities, ent.getId(), paraIdx, m.sentNum+1, m.startIndex+1, m.endIndex+1);
								
								if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
								for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
								
								break;
							
							} else {
								if (ent.isBelongToAGroup(m.spanToString(), paraIdx, m.sentNum+1, m.startIndex+1, m.endIndex+1)
										|| ent.isBelongToFamilyMention(m.spanToString())) {	//a group of entities (connected by other words, or belong to the same family)
									
									addMention(entities, ent.getId(), paraIdx, m.sentNum+1, m.startIndex+1, m.endIndex+1);
									
									if (!resolvedTokens.containsKey(m.sentNum+1)) resolvedTokens.put(m.sentNum+1, new HashSet<Integer>());
									for (int x=m.startIndex+1; x<m.endIndex+1; x++) resolvedTokens.get(m.sentNum+1).add(x);
								}
							}
						}
					}
				
				} 
			}
		}
		
		////////////////////////// Unlinked mentions (for logging purpose) /////////////////////////////
		BufferedWriter bw = new BufferedWriter(new FileWriter("./output/" + docName + ".unlinked.csv", true));
		
		for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
			List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
			List<Mention> ms = sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class);
			
//			for (int i=0; i<ms.size(); i++) {
			for (int i=ms.size()-1; i>=0; i--) {
				
				Mention m = ms.get(i);
				
				boolean includedInResolved = true;
				if (resolvedTokens.containsKey((ms.get(i).sentNum+1))) {
					for (int x=(ms.get(i).startIndex+1); x<(ms.get(i).endIndex+1); x++) {
						includedInResolved = includedInResolved && resolvedTokens.get((ms.get(i).sentNum+1)).contains(x);
					}
				} else {
					includedInResolved = false;
				}
				
				if (!includedInResolved) {
					String pos = "";
					for (int x=m.startIndex; x<m.endIndex; x++) {
						pos += tokens.get(x).get(PartOfSpeechAnnotation.class) + "-";
					}
					pos = pos.substring(0, pos.length()-1);
					bw.write("\"" + m.spanToString() + "\"" + "," + "\"" + pos + "\"" + "," + m.mentionType + "," + paraIdx + ":" + (m.sentNum+1) + ":" + (m.startIndex+1) + "-" + (m.endIndex+1) + "\n");
					
				}
			}
		}
		
		bw.close();
		
		////////////////////////// Write new sentences ///////////////////////////
		bw = new BufferedWriter(new FileWriter("./output/" + docName + ".resolved.sentences.txt", true));
		
		Document doc = new Document(newSummary);
		for (Sentence sent : doc.sentences()) {
			Map<String, List<String>> mentions = new HashMap<String, List<String>>();
			for (StoryEntity entity : entities) {
				if (entity.getMentions().containsKey(paraIdx)) {
					if (entity.getMentions().get(paraIdx).containsKey(sent.sentenceIndex()+1)) {
						for (EntityMention em : entity.getMentions().get(paraIdx).get(sent.sentenceIndex()+1)) {
							if (!mentions.containsKey(em.toString())) mentions.put(em.toString(), new ArrayList<String>());
							mentions.get(em.toString()).add(entity.getId().replace(" ", "_"));
						}
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
						int endIdx = Integer.parseInt(span.split("-")[1]);
						
						if (i+1 == startIdx) {
							String replace = mentions.get(span).get(0);
							if (!sent.posTag(endIdx-2).equals("NNP")) {
								if (sent.posTag(i).equals("PRP$")
										&& (sent.word(i).equals("his")
												|| sent.word(i).equals("her")
												|| sent.word(i).equals("its"))) {
									replace += " 's";
								}
							}
							coreferenceSent.set(i, replace);
							
						} else if (isIndexInSpan(i+1, span)) {
							if (i+1 == endIdx-1 && sent.posTag(i).equals("POS")) {
								coreferenceSent.set(i, "'s");
							} else {
								coreferenceSent.set(i, null);
							}
						} 
					}
				}
				
				for (String span : mentions.keySet()) {
					if (mentions.get(span).size() > 1) {
						int startIdx = Integer.parseInt(span.split("-")[0]);
						int endIdx = Integer.parseInt(span.split("-")[1]);
						
						if (i+1 == startIdx) {
							String replace = "";
							for (String s : mentions.get(span)) {
								replace += s + "-";
							}
							replace = replace.substring(0, replace.length()-1);
							if (sent.posTag(i).equals("PRP$")
									&& (sent.word(i).equals("their"))) {
								replace += " 's";
							}
							coreferenceSent.set(i, replace);
							
						} else if (isIndexInSpan(i+1, span)) {
							if (i+1 == endIdx-1 && sent.posTag(i).equals("POS")) {
								coreferenceSent.set(i, "'s");
							} else {
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
			bw.write(paraIdx + ":" + (sent.sentenceIndex()+1) + "-" + sent.text() + "\n");
			bw.write(paraIdx + ":" + (sent.sentenceIndex()+1) + "-" + coreferenceSentStr + "\n\n");
			
			sb.append(coreferenceSentStr + "\n");
		}
		bw.close();
		
		return sb.toString();
	}
	
	private boolean isIndexInSpan (int idx, String span) {
		int startIdx = Integer.parseInt(span.split("-")[0]);
		int endIdx = Integer.parseInt(span.split("-")[1]);
		if (idx >= startIdx && idx < endIdx) return true;
		else return false;
	}
	
	
	
	public static void main(String[] args) throws Exception {
		Resolve sa = new Resolve();
		
		
		////////////////////////// Read list of entities ///////////////////////////
		List<StoryEntity> entities = new ArrayList<StoryEntity>();
		Set<String> keys = new HashSet<String>();
		BufferedReader br = new BufferedReader(new FileReader(new File("./data/spark.harry.potter.entities.csv")));
		String line;
		while ((line = br.readLine()) != null) {
			String[] cols = line.split(",");
			StoryEntity ent = new StoryEntity();
			
			if (cols[0].equals("M")) ent.setGender(Gender.MALE);
			else if (cols[0].equals("F")) ent.setGender(Gender.FEMALE);
			else if (cols[0].equals("N")) ent.setGender(Gender.NEUTRAL);
			else ent.setGender(Gender.UNKNOWN);
			
			if (cols[1].split(" ").length > 1) {
				String[] names = cols[1].split(" ");
				String firstName = names[0];
				String lastName = names[names.length-1];
				if (!keys.contains(firstName)) {
					ent.setId(firstName);
					keys.add(firstName);
				}
				else ent.setId(firstName + lastName.substring(0, 2));
			} else {
				ent.setId(cols[1]);
			}
			ent.setName(cols[1]);
			if (cols.length > 2) {
				for (int i=2; i<cols.length; i++) {
					ent.getAliases().add(cols[i]);
				}
			}
			
			entities.add(ent);
	    }
		
		////////////////////////// Resolve coreference! ///////////////////////////
		File input = new File("./data/spark.harry.potter.book1.txt");
		br = new BufferedReader(new FileReader(input));
		List<String> paragraphs = new ArrayList<String>();
	    while ((line = br.readLine()) != null) {
	    	if (!line.trim().isEmpty()) {
	    		paragraphs.add(line);
	    	}
	    }
	    
	    BufferedWriter bw = new BufferedWriter(new FileWriter("./output/" + input.getName() + ".resolved.txt"));
	    File logSent = new File("./output/" + input.getName() + ".resolved.sentences.txt"); logSent.delete();
	    File logMentions = new File("./output/" + input.getName() + ".unlinked.csv"); logMentions.delete();
	    for (int i=0; i<paragraphs.size(); i++) {
	    	bw.write(sa.annotate(input.getName(), paragraphs.get(i), entities, i+1) + "\n");
	    }
	    bw.close();
		
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
		
		String document3 = ""
				+ "Ten years have passed. "
				+ "Harry is now almost eleven and living in wretchedness in a cupboard under the stairs in the Dursley house. "
				+ "He is tormented by the Dursleys’ son, Dudley, a spoiled and whiny boy. "
				+ "Harry is awakened one morning by his aunt, Petunia, telling him to tend to the bacon immediately, because it is Dudley’s birthday and everything must be perfect. "
				+ "Dudley gets upset because he has only thirty-seven presents, one fewer than the previous year. "
				+ "When a neighbor calls to say she will not be able to watch Harry for the day, Dudley begins to cry, as he is upset that Harry will have to be brought along on Dudley’s birthday trip to the zoo. "
				+ "At the zoo, the Dursleys spoil Dudley and his friend Piers, neglecting Harry as usual. "
				+ "In the reptile house, Harry pays close attention to a boa constrictor and is astonished when he is able to have a conversation with it. "
				+ "Noticing what Harry is doing, Piers calls over Mr. Dursley and Dudley, who pushes Harry aside to get a better look at the snake. "
				+ "At this moment, the glass front of the snake’s tank vanishes and the boa constrictor slithers out onto the floor. "
				+ "Dudley and Piers claim that the snake attacked them. "
				+ "The Dursleys are in shock. "
				+ "At home, Harry is punished for the snake incident, being sent to his cupboard without any food, though he feels he had nothing to do with what happened.";
		
//	    sa.annotate("harry.potter", document1, entities, 1);
//	    sa.annotate("harry.potter", document2, entities, 1);
//		sa.annotate("harry.potter", document3, entities, 2);
	    
		bw = new BufferedWriter(new FileWriter("./output/" + input.getName() + ".entities.txt"));
	    for (StoryEntity ent : entities) {
	    	bw.write(ent.toString() + "\n\n");
	    }
	    bw.close();
	}

}
