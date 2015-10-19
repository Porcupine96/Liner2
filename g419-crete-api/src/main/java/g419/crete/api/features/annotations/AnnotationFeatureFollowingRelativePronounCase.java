package g419.crete.api.features.annotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import g419.corpus.structure.Annotation;
import g419.corpus.structure.Token;
import g419.corpus.structure.TokenAttributeIndex;
import g419.crete.api.features.AbstractFeature;
import g419.crete.api.features.enumvalues.Case;
import g419.crete.api.features.enumvalues.MentionType;

public class AnnotationFeatureFollowingRelativePronounCase extends AbstractFeature<Annotation, Case>{
	
	final static String KTORY_BASE = "który";
	
	@Override
	public void generateFeature(Annotation input) {
		this.value  = Case.OTHER;
		TokenAttributeIndex ai = input.getSentence().getAttributeIndex();
		ArrayList<Token> tokens = input.getSentence().getTokens();
		int totalTokens = tokens.size();
		
		for(int i = input.getEnd(); i < totalTokens; i++){
			Token token = tokens.get(i);
			String base = ai.getAttributeValue(token, "base");
			if(KTORY_BASE.equalsIgnoreCase(base))
				this.value = Case.fromValue(ai.getAttributeValue(token, "case"));
		}		
	}

	@Override
	public String getName() {
		return "annotation_following_relative_pronoun_case";
	}

	@Override
	public Class<Annotation> getInputTypeClass() {
		return Annotation.class;
	}

	@Override
	public Class<Case> getReturnTypeClass() {
		return Case.class;
	}
	
	@Override
	public List<Case> getAllValues(){
		return Arrays.asList(Case.values());
	}
	
	
	
}
