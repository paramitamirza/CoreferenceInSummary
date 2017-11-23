package mpiinf.mpg.de.CoreferenceInSummary;

import is2.data.SentenceData09;
import is2.lemmatizer.Lemmatizer;
import is2.tag.Tagger;
import se.lth.cs.srl.*;
import se.lth.cs.srl.preprocessor.Preprocessor;
import se.lth.cs.srl.preprocessor.tokenization.Tokenizer;

public class SemanticParser {
	
	public static void main(String[] args) throws Exception {
		
		SemanticRoleLabeler srl;
		
		Lemmatizer lemmatizer = new Lemmatizer("./models/CoNLL2009-ST-English-ALL.anna-3.3.lemmatizer.model");
		is2.tag.Tagger tagger = new is2.tag.Tagger("./models/CoNLL2009-ST-English-ALL.anna-3.3.postagger.model");
//		is2.mtag.Tagger mtagger = new is2.mtag.Tagger("./models/CoNLL2009-ST-English-ALL.anna-3.3.postagger.model");
		is2.parser.Parser parser = new is2.parser.Parser("./models/CoNLL2009-ST-English-ALL.anna-3.3.parser.model");
		
		Preprocessor pp = new Preprocessor(null, lemmatizer, tagger, null, parser);
		
		String line = "The quick brown fox jumps over the lazy dog .";
		
		String[] tokens = line.split(" ");
		SentenceData09 s = pp.preprocess(tokens);
		System.out.println(s);
		
	}

}
