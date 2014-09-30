package g419.liner2.api.features;

import g419.corpus.structure.Document;
import g419.corpus.structure.Paragraph;
import g419.corpus.structure.Sentence;
import g419.corpus.structure.Token;
import g419.corpus.structure.TokenAttributeIndex;
import g419.liner2.api.features.tokens.Feature;
import g419.liner2.api.features.tokens.TokenFeature;
import g419.liner2.api.features.tokens.TokenFeatureFactory;
import g419.liner2.api.features.tokens.TokenInSentenceFeature;
import g419.liner2.api.tools.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;


public class TokenFeatureGenerator {

	private ArrayList<TokenFeature> tokenGenerators = new ArrayList<TokenFeature>();
	private ArrayList<TokenInSentenceFeature> sentenceGenerators 
		= new ArrayList<TokenInSentenceFeature>();
	private TokenAttributeIndex attributeIndex = new TokenAttributeIndex();
	private String[] sourceFeatures = new String[]{"orth", "base", "ctag"};
	private ArrayList<String> featureNames;
	/**
	 * 
	 * @param features — array with feature definitions
	 */
	public TokenFeatureGenerator(LinkedHashMap<String, String> features){
        Logger.log("(TokenFeatureGenerator) Loading features...");
        this.featureNames = new ArrayList<String>(features.keySet());
		for( String sf: sourceFeatures)
			this.attributeIndex.addAttribute(sf);
		try{
			for ( String feature : features.values() ){
				Feature f = TokenFeatureFactory.create(feature);
				if  (f != null){
					if (TokenInSentenceFeature.class.isInstance(f))
						this.sentenceGenerators.add((TokenInSentenceFeature) f);
					else if (TokenFeature.class.isInstance(f))
						this.tokenGenerators.add((TokenFeature) f);
					else
						throw new Error("Unknow type of feature " + f);
					this.attributeIndex.addAttribute(f.getName());
				}
			}
		}
		catch(Exception ex){
            ex.printStackTrace();
            System.out.println(">> " + ex.getMessage());
		}
        Logger.log(featureNames.toString());
	}
	
	/**
	 * Return index of token attributes (mapping from feature name to their corresponding
	 * position in the array of attributes).
	 * @return
	 */
	public TokenAttributeIndex getAttributeIndex(){
		return this.attributeIndex;
	}
	
	/**
	 * Generates feature for every token in the paragraph set. The features are added to the 
	 * token list of attributes.
	 * @param ps
	 * @throws Exception
	 */
	public void generateFeatures(Document ps) throws Exception {
		ps.getAttributeIndex().update(this.attributeIndex.allAtributes());
		for (Paragraph p : ps.getParagraphs()){
			generateFeatures(p, false);
		}
		ps.getAttributeIndex().update(featureNames);
	}

	public void generateFeatures(Paragraph p, boolean updateAttributeIndex) throws Exception {
		if(updateAttributeIndex)
			p.getAttributeIndex().update(this.attributeIndex.allAtributes());
		for (Sentence s : p.getSentences())
			generateFeatures(s);
		if(updateAttributeIndex)
			p.getAttributeIndex().update(featureNames);
	}

	public void generateFeatures(Sentence s) throws Exception {

		LinkedList<Integer> toDel = new LinkedList<Integer>();
		for (Token t : s.getTokens()){
			t.packAtributes(this.attributeIndex.getLength());
			toDel.clear();
			generateFeatures(t);
			for(String sourceFeat: sourceFeatures)
				if(!featureNames.contains(sourceFeat))
					toDel.add(this.attributeIndex.getIndex(sourceFeat)-toDel.size());
			for(int idx: toDel)
				t.removeAttribute(idx);
		}
		
		for (TokenInSentenceFeature f : this.sentenceGenerators)
			f.generate(s);		
	}

	public void generateFeatures(Token t) throws Exception {
		for (TokenFeature f : this.tokenGenerators){
			t.setAttributeValue(
					this.attributeIndex.getIndex(f.getName()), 
					f.generate(t, this.attributeIndex));
		}
	}
}