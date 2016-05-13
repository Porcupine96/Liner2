package g419.spatial.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.maltparser.core.exception.MaltChainedException;

import g419.corpus.schema.kpwr.KpwrSpatial;
import g419.corpus.structure.Annotation;
import g419.corpus.structure.Document;
import g419.corpus.structure.Frame;
import g419.corpus.structure.Paragraph;
import g419.corpus.structure.Sentence;
import g419.corpus.structure.Token;
import g419.liner2.api.features.tokens.ClassFeature;
import g419.liner2.api.tools.parser.MaltParser;
import g419.liner2.api.tools.parser.MaltSentence;
import g419.liner2.api.tools.parser.MaltSentenceLink;
import g419.spatial.filter.IRelationFilter;
import g419.spatial.filter.RelationFilterDifferentObjects;
import g419.spatial.filter.RelationFilterHolonyms;
import g419.spatial.filter.RelationFilterLandmarkTrajectorException;
import g419.spatial.filter.RelationFilterPrepositionBeforeLandmark;
import g419.spatial.filter.RelationFilterPronoun;
import g419.spatial.filter.RelationFilterSemanticPattern;
import g419.spatial.structure.SpatialExpression;
import g419.spatial.structure.SpatialRelationSchema;
import g419.toolbox.wordnet.NamToWordnet;
import g419.toolbox.wordnet.Wordnet3;

public class SpatialRelationRecognizer {

	private MaltParser malt = null;
	List<IRelationFilter> filters = null;
	RelationFilterSemanticPattern semanticFilter = null;
	
	private Pattern annotationsPrep = Pattern.compile("^PrepNG.*$");	
	private Pattern annotationsNg = Pattern.compile("^NG.*$");	
	private Pattern patternAnnotationNam = Pattern.compile("^nam(_(fac|liv|loc|pro|oth).*|$)");
	
	private Set<String> objectPos = new HashSet<String>();
	private Set<String> regions = SpatialResources.getRegions();
	
	private Logger logger = Logger.getLogger(this.getClass());
	
	
	/**
	 * @throws IOException 
	 * @param maltparser Ścieżka do modelu Maltparsera
	 * @param wordnet Ścieżka do wordnetu w formacie PWN
	 */
	public SpatialRelationRecognizer(MaltParser malt, Wordnet3 wordnet) throws IOException{		
		this.malt = malt;
		
		this.objectPos.add("subst");
		this.objectPos.add("ign");
		this.objectPos.add("brev");
		
		this.semanticFilter = new RelationFilterSemanticPattern();

		NamToWordnet nam2wordnet = new NamToWordnet(wordnet);
		
		this.filters = new LinkedList<IRelationFilter>();
		this.filters.add(new RelationFilterPronoun());
		this.filters.add(new RelationFilterDifferentObjects());
		this.filters.add(this.semanticFilter);
		this.filters.add(new RelationFilterPrepositionBeforeLandmark());
		this.filters.add(new RelationFilterLandmarkTrajectorException());
		this.filters.add(new RelationFilterHolonyms(wordnet, nam2wordnet));		
	}
	
	/**
	 * 
	 * @return
	 */
	public List<IRelationFilter> getFilters(){
		return this.filters;
	}
	
	/**
	 * 
	 * @return
	 */
	public RelationFilterSemanticPattern getSemanticFilter(){
		return this.semanticFilter;
	}
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne i dodaje je do dokumentu jako obiekty Frame o type "spatial"
	 * @param document
	 * @throws MaltChainedException 
	 */
	public void recognizeInPlace(Document document) throws MaltChainedException{
		for (Paragraph paragraph : document.getParagraphs()){
			for (Sentence sentence : paragraph.getSentences()){
				for ( SpatialExpression rel : this.recognize(sentence) ){
					Frame f = SpatialRelationRecognizer.convertSpatialToFrame(rel);
					document.getFrames().add(f);
				}		
			}
		}		
	}
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne i zwraca je jako listę obiektów SpatialExpression
	 * @param sentence
	 * @return
	 * @throws MaltChainedException 
	 */
	public List<SpatialExpression> recognize(Sentence sentence) throws MaltChainedException{
		List<SpatialExpression> candidateRelations = this.findCandidates(sentence);
		List<SpatialExpression> finalRelations = new ArrayList<SpatialExpression>();
		if ( candidateRelations.size() > 0 ){
			for ( SpatialExpression rel : candidateRelations ){
				
				boolean pass = true;
				
				for ( IRelationFilter filter : filters ){
					if ( !filter.pass(rel) ){
						pass = false;
						break;
					}								
				}
				
				if ( pass ){
					finalRelations.add(rel);
				}
			}				
		}
		return finalRelations;
	}
	
	/**
	 * Konwertuje strukturę SpatialRelation do uniwersalnego formatu Frame.
	 * @param relation
	 * @return
	 */
	public static Frame convertSpatialToFrame(SpatialExpression relation){
		Frame f = new Frame(KpwrSpatial.SPATIAL_FRAME_TYPE);
		f.setSlot(KpwrSpatial.SPATIAL_INDICATOR, relation.getSpatialIndicator());
		f.setSlot(KpwrSpatial.SPATIAL_LANDMARK, relation.getLandmark());
		f.setSlot(KpwrSpatial.SPATIAL_TRAJECTOR, relation.getTrajector());
		f.setSlot(KpwrSpatial.SPATIAL_REGION, relation.getRegion());
		
		f.setSlotAttribute(KpwrSpatial.SPATIAL_TRAJECTOR, "sumo", String.join(", ",relation.getTrajectorConcepts()));
		f.setSlotAttribute(KpwrSpatial.SPATIAL_LANDMARK, "sumo", String.join(", ",relation.getLandmarkConcepts()));
		f.setSlotAttribute("debug", "pattern", relation.getType());
		
		Set<String> schemas = new HashSet<String>();
		for ( SpatialRelationSchema schema : relation.getSchemas() ){
			schemas.add(schema.getName());
		}
		f.setSlotAttribute("debug", "schema", String.join("; ", schemas));
		
		return f;
	}

	/**
	 * 
	 * @param sentence
	 * @return
	 * @throws MaltChainedException
	 */
	public List<SpatialExpression> findCandidates(Sentence sentence) throws MaltChainedException{
		this.splitPrepNg(sentence);
		
		MaltSentence maltSentence = new MaltSentence(sentence);
		this.malt.parse(maltSentence);

		/* Zaindeksuj frazy np */
		Map<Integer, Annotation> chunkNpTokens = new HashMap<Integer, Annotation>();
		Map<Integer, Annotation> chunkVerbfinTokens = new HashMap<Integer, Annotation>();
		Map<Integer, Annotation> chunkPrepTokens = new HashMap<Integer, Annotation>();
		Map<Integer, Annotation> chunkPpasTokens = new HashMap<Integer, Annotation>();
		Map<Integer, Annotation> chunkPactTokens = new HashMap<Integer, Annotation>();
		for ( Annotation an : sentence.getChunks() ){
			if ( an.getType().equals("chunk_np") ){
				for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
					chunkNpTokens.put(n, an);
				}
			}
			else if ( an.getType().equals("Prep") ){
				for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
					chunkPrepTokens.put(n, an);					
				}
			}
			else if ( an.getType().equals("Pact") ){
				for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
					chunkPactTokens.put(n, an);					
				}
			}
			else if ( an.getType().equals("Ppas") ){
				for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
					chunkPpasTokens.put(n, an);					
				}
			}
			else if ( an.getType().equals("Verbfin") ){
				for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
					chunkVerbfinTokens.put(n, an);					
				}
			}
		}

		/* Zaindeksuj tokeny jednostek identyfikacyjnych */
		Map<Integer, Annotation> chunkNamesTokens = new HashMap<Integer, Annotation>();
		for ( Annotation an : sentence.getAnnotations(this.patternAnnotationNam) ){
			for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
				chunkNamesTokens.put(n, an);					
			}
		}
		
		/* Zaindeksuj pierwsze tokeny anotacji NG* */
		Map<Integer, List<Annotation>> mapTokenIdToAnnotations = new HashMap<Integer, List<Annotation>>();
		for ( Annotation an : sentence.getAnnotations(this.annotationsNg) ){
			for ( Integer n = an.getBegin(); n <= an.getEnd(); n++){
				if ( !mapTokenIdToAnnotations.containsKey(n) ){
					mapTokenIdToAnnotations.put(n, new LinkedList<Annotation>());
				}
				mapTokenIdToAnnotations.get(n).add(an);
			}
		}
		
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();

		// Second Iteration only
		
		relations.addAll( this.findCandidatesFirstNgAnyPrepNg(sentence, mapTokenIdToAnnotations, chunkNpTokens, chunkPrepTokens) );
		relations.addAll( this.findCandidatesByMalt(sentence, maltSentence, chunkPrepTokens, chunkNamesTokens, mapTokenIdToAnnotations));
		

		relations.addAll( this.findCandidatesNgPpasPrepNg(
				sentence, mapTokenIdToAnnotations, chunkNpTokens, chunkPpasTokens, chunkPrepTokens));
		relations.addAll( this.findCandidatesNgPactPrepNg(
				sentence, mapTokenIdToAnnotations, chunkNpTokens, chunkPactTokens, chunkPrepTokens));

		// Sprawdź, czy landmarkiem jest region. Jeżeli tak, to przesuń landmark na najbliższych ign lub subst
		for ( SpatialExpression rel : relations ){
			if ( this.regions.contains(rel.getLandmark().getHeadToken().getDisambTag().getBase()) ){
				int i = rel.getLandmark().getEnd() + 1;
				List<Token> tokens = rel.getLandmark().getSentence().getTokens();
				
				if ( i < tokens.size() && chunkNamesTokens.get(i) != null ){
					Annotation an = chunkNamesTokens.get(i);
					this.logger.info("REPLACE_REGION_NEXT_NAME " + rel.getLandmark().toString() + " => " + an.toString());
					rel.setRegion(rel.getLandmark());
					rel.setLandmark(an);
				}
				else{
					// Znajdż zagnieżdżone NG występujące po regionie
					Annotation ng = null;
					int j = rel.getLandmark().getHead() + 1;
					while ( j <= rel.getLandmark().getEnd() && ng == null ){
						List<Annotation> innerNgs = mapTokenIdToAnnotations.get(j);
						if ( innerNgs != null ){
							for (Annotation an : innerNgs ){
								if ( an.getBegin() > rel.getLandmark().getHead() ){
									ng = an;
								}
							}							
						}
						j++;
					}
					if ( ng != null ){
						this.logger.info("REPLACE_REGION_INNER_NG" + rel.getLandmark().toString() + " => " + ng.toString());
						Annotation newLandmark = null;
						List<Annotation> newLandmakrs = mapTokenIdToAnnotations.get(rel.getLandmark().getHead());
						if ( newLandmakrs != null ){
							for (Annotation an : newLandmakrs ){
								if ( an != rel.getLandmark() && (newLandmark == null || newLandmark.getTokens().size() < an.getTokens().size() ) ){
									newLandmark = an;
								}
							}
						}
						if ( newLandmark == null ){
							newLandmark = new Annotation(rel.getLandmark().getBegin(), ng.getBegin()-1, "NG", sentence);
						}
						rel.setRegion(newLandmark);
						rel.setLandmark(ng);						
					}
					else{
						// Znajdź pierwszy subst lub ign po prawej stronie
						Integer subst_or_ign = null;
						int k = rel.getLandmark().getHead() + 1;
						while ( k <= rel.getLandmark().getEnd() && subst_or_ign == null ){
							if ( this.objectPos.contains(tokens.get(k).getDisambTag().getPos()) ){
								subst_or_ign = k;
							}
							k++;
						}
						if ( subst_or_ign != null ){
							// Wszystko po prawje staje się nową anotacją NG
							Annotation newRegion = new Annotation(rel.getLandmark().getHead(), "NG", sentence);
							Annotation newLandmark = new Annotation(rel.getLandmark().getHead()+1, rel.getLandmark().getEnd(), "NG", sentence);
							sentence.addChunk(newRegion);
							sentence.addChunk(newLandmark);
							this.logger.info("REPLACE_REGION_INNER_SUBST_IGN" + rel.getLandmark().toString() + " => " + newLandmark);
							rel.setLandmark(newLandmark);
							rel.setRegion(newRegion);
						}
					}
				}
			}
		}
		
		// Usuń kandydatów, dla których spatial indicator jest częścią nazwy
		List<SpatialExpression> toRemove = new ArrayList<SpatialExpression>();
		for ( SpatialExpression spatial : relations ){
			if ( chunkNamesTokens.get(spatial.getSpatialIndicator().getHead()) != null ){
				toRemove.add(spatial);
			}
		}
		relations.removeAll(toRemove);
		
		// Jeżeli frazy NG pokrywają się z nam_, to podmień anotacje
		this.replaceNgWithNames(sentence, relations, chunkNamesTokens);
		
		return relations;
	}
	
	/**
	 * Dla fraz NG, które pokrywają się z nazwą własną, zmienia NG na nazwę.
	 * @param sentence
	 * @param relations
	 */
	private void replaceNgWithNames(Sentence sentence, List<SpatialExpression> relations, Map<Integer, Annotation> names){
		for ( SpatialExpression relation : relations ){
			// Sprawdź landmark
			//String landmarkKey = String.format("%d:%d",relation.getLandmark().getBegin(), relation.getLandmark().getEnd());
			Integer landmarkKey = relation.getLandmark().getBegin();
			Annotation landmarkName = names.get(landmarkKey);
			if ( landmarkName != null && landmarkName != relation.getLandmark() ){
				Logger.getLogger(this.getClass()).info(String.format("Replace %s (%s) with nam (%s)", relation.getLandmark().getType(), relation.getLandmark(), landmarkName));
				landmarkName.setHead(relation.getLandmark().getHead());
				relation.setLandmark(landmarkName);
			}
			// Sprawdź trajector
			//String trajectorKey = String.format("%d:%d",relation.getTrajector().getBegin(), relation.getTrajector().getEnd());
			Integer trajectorKey = relation.getTrajector().getBegin();
			Annotation trajectorName = names.get(trajectorKey);
			if ( trajectorName != null && trajectorName != relation.getTrajector() ){
				Logger.getLogger(this.getClass()).info(String.format("Replace %s (%s) with nam (%s)", relation.getTrajector().getType(), relation.getTrajector(), trajectorName));
				trajectorName.setHead(relation.getTrajector().getHead());
				relation.setTrajector(trajectorName);
			}
		}
	}
	
	/**
	 * Wydziela z anotacji PrepNG* anotacje zagnieżdżone poprzez odcięcie przymika.
	 * @param sentence
	 */
	public void splitPrepNg(Sentence sentence){
		/* Zaindeksuj tokeny anotacji NG* */
		Map<Integer, List<Annotation>> mapTokenIdToAnnotations = new HashMap<Integer, List<Annotation>>();
		for ( Annotation an : sentence.getAnnotations(this.annotationsNg) ){
			for ( int i = an.getBegin(); i<=an.getEnd(); i++ ){
				if ( !mapTokenIdToAnnotations.containsKey(i) ){
					mapTokenIdToAnnotations.put(i, new LinkedList<Annotation>());
				}
				mapTokenIdToAnnotations.get(i).add(an);
			}
		}
		
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){
			if ( !mapTokenIdToAnnotations.containsKey(an.getBegin()+1) ){
				Annotation ani = new Annotation(an.getBegin()+1, an.getEnd(), an.getType().substring(4), an.getSentence());
				ani.setHead(an.getHead());
				sentence.addChunk(ani);
			}
			else{
				Integer newNgStart = null;
				for ( int i=an.getBegin() + 1; i <= an.getEnd(); i++ ){
					if ( mapTokenIdToAnnotations.get(i) == null ){
						if ( newNgStart == null ){
							newNgStart = i;
						}
					}
					else{
						if ( newNgStart != null ){
							Annotation newNg = new Annotation(newNgStart, i-1, "NG", sentence);
							this.logger.info("NEW NG: " + newNg.toString());
							sentence.addChunk(newNg);
							newNgStart = null;
						}
					}
				}
				if ( newNgStart != null ) {
					Annotation newNg = new Annotation(newNgStart, an.getEnd(), "NG", sentence);
					this.logger.info("NEW NG: " + newNg.toString());
					sentence.addChunk(newNg);
					newNgStart = null;				
				}
			}
		}
	}

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgAnyPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer trajectorPrevId = an.getBegin()-1;
			
			boolean breakSearchPrev = false;
			while ( !breakSearchPrev && trajectorPrevId >=0 && mapTokenIdToAnnotations.get(trajectorPrevId) == null ){
				/* Przecinek i nawias zamykający przerywają poszykiwanie */
				String orth = sentence.getTokens().get(trajectorPrevId).getOrth(); 
				if ( orth.equals(",") || orth.equals(")") ){
					breakSearchPrev = true;
				}
				trajectorPrevId--;
			}
			Integer trajectorId = trajectorPrevId;
						
			if ( chunkNpTokens.get(landmarkId) != null && !breakSearchPrev && chunkNpTokens.get(trajectorPrevId) == chunkNpTokens.get(landmarkId) ){
				String type = "";
				if ( trajectorPrevId+1 == preposition.getBegin() ){
					type = "<NG|PrepNG>";
				}
				else{
					type = "<NG|...|PrepNG>";						
				}
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
			}
		}
		return relations;
	}
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPrepNgNoNp(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer trajectorId = an.getBegin()-1;
						
			if ( chunkNpTokens.get(landmarkId) == null
					&& chunkNpTokens.get(trajectorId) == null ){
				String type = "NG|PrepNG";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}	

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPrepNgDiffNp(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer trajectorId = an.getBegin()-1;
						
			if ( chunkNpTokens.get(landmarkId) != null
					&& chunkNpTokens.get(trajectorId) != null
					&& chunkNpTokens.get(trajectorId) != chunkNpTokens.get(landmarkId) ){
				String type = "<NG><PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesPrepNgNgDiffNp(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin() + preposition.getTokens().size();
			Integer trajectorId = an.getEnd() + 1;
						
			if ( chunkNpTokens.get(landmarkId) != null
					&& chunkNpTokens.get(trajectorId) != null
					&& chunkNpTokens.get(trajectorId) != chunkNpTokens.get(landmarkId) ){
				String type = "<PrepNG><NG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
			}
		}
		return relations;
	}

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPrepNgPpasPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens,
			Map<Integer, Annotation> chunkPpasTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			if ( an.getBegin()-1 <= 0 || !sentence.getTokens().get(an.getBegin()-1).getOrth().equals(",") ){
				continue;
			}
			
			Annotation ppas = chunkPpasTokens.get(an.getBegin()-1);
			
			if ( ppas == null ){
				continue;
			}
			
			List<Annotation> ngs = mapTokenIdToAnnotations.get(ppas.getBegin()-1);
			if ( ngs == null ){
				continue;
			}
			
			Annotation prep = chunkPrepTokens.get(ngs.get(0).getBegin()-1);
			if ( prep == null ){
				continue;
			}
			
			Integer trajectorId = prep.getBegin()-1;
						
			if ( chunkNpTokens.get(landmarkId) != null && chunkNpTokens.get(trajectorId) == chunkNpTokens.get(landmarkId) ){
				String type = "<NG|PrepNG|Ppas|PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}	

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPrepNgCommaPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer commaId = preposition.getBegin()-1;
			
			if ( commaId <= 0 || !sentence.getTokens().get(commaId).getOrth().equals(",") ){
				continue;
			}			
						
			Integer prepId = null;
			List<Annotation> ngs = mapTokenIdToAnnotations.get(commaId-1);
			if ( ngs == null ){
				continue;
			}
			else{
				for ( Annotation a : ngs ){
					if ( prepId == null ){
						prepId = a.getBegin()-1;
					}
					else{
						prepId = Math.min(prepId, a.getBegin()-1);
					}
				}
			}
			
			Annotation prep = chunkPrepTokens.get(prepId);
			if ( prep == null ){
				continue;
			}
			
			Integer trajectorId = prep.getBegin()-1;
						
			if ( chunkNpTokens.get(landmarkId) != null 
					&& chunkNpTokens.get(trajectorId) == chunkNpTokens.get(landmarkId) ){
				String type = "<NG|PrepNG|Comma|PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}	
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPrepNgPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();

			List<Annotation> ngs = mapTokenIdToAnnotations.get(an.getBegin()-1);
			Integer prepId = null;
			if ( ngs == null ){
				continue;
			}
			else{
				for ( Annotation a : ngs ){
					if ( prepId == null ){
						prepId = a.getBegin()-1;
					}
					else{
						prepId = Math.min(prepId, a.getBegin()-1);
					}
				}
			}
			
			Annotation prep = chunkPrepTokens.get(prepId);
			if ( prep == null ){
				continue;
			}
			
			Integer trajectorId = prep.getBegin()-1;
						
			if ( chunkNpTokens.get(landmarkId) != null 
					&& chunkNpTokens.get(trajectorId) == chunkNpTokens.get(landmarkId) ){
				String type = "<NG|PrepNG|PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
			}
		}
		return relations;
	}			
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgNgPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
						
			List<Annotation> ngs = mapTokenIdToAnnotations.get(preposition.getBegin()-1);
			Integer trajectorId = null;
			if ( ngs == null ){
				continue;
			}
			else{
				for ( Annotation a : ngs ){
					if ( trajectorId == null ){
						trajectorId = a.getBegin()-1;
					}
					else{
						trajectorId = Math.min(trajectorId, a.getBegin()-1);
					}
				}
			}
			
			if ( chunkNpTokens.get(landmarkId) != null && chunkNpTokens.get(trajectorId) == chunkNpTokens.get(landmarkId) ){
				String type = "<NG|NG|PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}			

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesFirstNgAnyPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców NG* prep NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer trajectorId = an.getBegin()-1;
			
			boolean breakSearchPrev = false;
			while ( !breakSearchPrev && trajectorId >=0 && mapTokenIdToAnnotations.get(trajectorId) == null ){
				/* Przecinek i nawias zamykający przerywają poszykiwanie */
				String orth = sentence.getTokens().get(trajectorId).getOrth(); 
				if ( orth.equals(",") || orth.equals(")") ){
					breakSearchPrev = true;
				}
				trajectorId--;
			}			
						
			if ( chunkNpTokens.get(landmarkId) != null && !breakSearchPrev && chunkNpTokens.get(trajectorId) == chunkNpTokens.get(landmarkId) ){
				
				while ( trajectorId > 0
						&& mapTokenIdToAnnotations.get(trajectorId-1) != null
						&& chunkNpTokens.get(trajectorId-1) == chunkNpTokens.get(landmarkId)
						){
					trajectorId = mapTokenIdToAnnotations.get(trajectorId-1).get(0).getBegin();
				}
				
				String type = "";
				if ( trajectorId+1 == preposition.getBegin() ){
					type = "<FirstNG|PrepNG>";
				}
				else{
					type = "<FirstNG|...|PrepNG>";						
				}
									
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
			}
		}
		return relations;
	}		
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesPrepNgNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców prep NG* NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer trajectorId = an.getEnd()+1;
			
			if ( chunkNpTokens.get(landmarkId) != null
					&& chunkNpTokens.get(landmarkId) == chunkNpTokens.get(trajectorId) ){
				String type = "<PrepNG|NG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
			}
		}
		return relations;
	}	

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkNpTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców prep NG* NG* */
		for ( Annotation landmark : sentence.getAnnotations(this.annotationsNg) ){
			
			Integer prepId = landmark.getBegin()-1;
			if ( prepId <= 0 
					|| !sentence.getTokens().get(prepId).getDisambTag().equals("prep")
					|| chunkPrepTokens.get(prepId) != null ){
				continue;
			}
			
			Integer landmarkId = landmark.getBegin();
			Integer trajectorId = prepId-1;
			
			if ( chunkNpTokens.get(landmarkId) != null
					&& chunkNpTokens.get(landmarkId) == chunkNpTokens.get(trajectorId) ){
				String type = "<NG|prep|NG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, 
						new Annotation(prepId, "Prep", sentence)));
			}
		}
		return relations;
	}	

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesPrepNgVerbfinNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkVerbfinTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców prep NG* NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer verbfinId = an.getEnd()+1;
			Annotation verbfin = chunkVerbfinTokens.get(verbfinId);
			
			if ( verbfin != null ){
				Integer trajectorId = verbfin.getEnd()+1;			
				String type = "PrepNG|Verbfin|NG";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
			}
		}
		return relations;
	}		
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgVerbfinPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations, 
			Map<Integer, Annotation> chunkVerbfinTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców prep NG* NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer verbfinId = preposition.getBegin()-1;
			Annotation verbfin = chunkVerbfinTokens.get(verbfinId);
			
			if ( verbfin != null ){
				Integer trajectorId = verbfin.getBegin()-1;			
				String type = "NG|Verbfin|PrepNG";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}		

	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPpasPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations,
			Map<Integer, Annotation> chunkNpTokens,
			Map<Integer, Annotation> chunkPpasTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców prep NG* NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer verbfinId = preposition.getBegin()-1;
			Annotation ppas = chunkPpasTokens.get(verbfinId);
			if ( ppas == null ){
				continue;
			}
			Integer trajectorId = ppas.getBegin()-1;			
			
			if ( ppas != null && chunkNpTokens.get(landmarkId) != null 
					&& chunkNpTokens.get(landmarkId) == chunkNpTokens.get(trajectorId) ){
				String type = "<NG|Ppas|PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}		
	
	/**
	 * Rozpoznaje wyrażenia przestrzenne występujące we wzrocu NG* prep NG*
	 * @param sentence
	 */
	public List<SpatialExpression> findCandidatesNgPactPrepNg(Sentence sentence, 
			Map<Integer, List<Annotation>> mapTokenIdToAnnotations,
			Map<Integer, Annotation> chunkNpTokens,
			Map<Integer, Annotation> chunkPactTokens, 
			Map<Integer, Annotation> chunkPrepTokens){				
		List<SpatialExpression> relations = new LinkedList<SpatialExpression>();
		/* Szukaj wzorców prep NG* NG* */
		for ( Annotation an : sentence.getAnnotations(this.annotationsPrep) ){						
			Annotation preposition = chunkPrepTokens.get(an.getBegin());
			if ( preposition == null ){
				this.logger.warn("Prep annotation for PrepNG not found: " + an.toString());
				continue;
			}
						
			Integer landmarkId = an.getBegin()+preposition.getTokens().size();
			Integer verbfinId = preposition.getBegin()-1;
			Annotation pact = chunkPactTokens.get(verbfinId);
			if ( pact == null ){
				continue;
			}
			Integer trajectorId = pact.getBegin()-1;			
			
			if ( pact != null && chunkNpTokens.get(landmarkId) != null 
					&& chunkNpTokens.get(landmarkId) == chunkNpTokens.get(trajectorId) ){
				String type = "<NG|Pact|PrepNG>";
				List<Annotation> trajectors = mapTokenIdToAnnotations.get(trajectorId);
				List<Annotation> landmarks = mapTokenIdToAnnotations.get(landmarkId);
				if ( trajectors != null ){
					relations.addAll(this.generateAllCombinations(type, trajectors, landmarks, preposition));
				}
			}
		}
		return relations;
	}		

	/**
	 * 
	 * @param sentence
	 * @param maltSentence
	 * @return
	 */
	public List<SpatialExpression> findCandidatesByMalt(Sentence sentence, MaltSentence maltSentence,
			Map<Integer, Annotation> chunkPrepTokens, Map<Integer, Annotation> chunkNamesTokens, Map<Integer, List<Annotation>> mapTokenIdToAnnotations){
		List<SpatialExpression> srs = new ArrayList<SpatialExpression>();
		for (int i=0; i< sentence.getTokens().size(); i++){
			Token token = sentence.getTokens().get(i);
			List<Integer> landmarks = new ArrayList<Integer>();
			List<Integer> trajectors = new ArrayList<Integer>();
			Integer indicator = null;
			String type = "MALT";
			String typeLM = "";
			String typeTR = "";
			if ( ClassFeature.BROAD_CLASSES.get("verb").contains(token.getDisambTag().getPos()) ){
				List<MaltSentenceLink> links = maltSentence.getLinksByTargetIndex(i);
				for ( MaltSentenceLink link : links ){
					Token tokenChild = sentence.getTokens().get(link.getSourceIndex());
					if ( link.getRelationType().equals("subj") ){
						if ( tokenChild.getDisambTag().getBase().equals("i")
								|| tokenChild.getDisambTag().getBase().equals("oraz")){
							typeTR = "_TRconj";
							for ( MaltSentenceLink trLink : maltSentence.getLinksByTargetIndex(link.getSourceIndex())){
								landmarks.add(trLink.getSourceIndex());
							}										
						}
						else if ( this.objectPos.contains(tokenChild.getDisambTag().getPos()) ){
							trajectors.add(link.getSourceIndex());
						}
					}
					else if ( tokenChild.getDisambTag().getPos().equals("prep") ){
						indicator = link.getSourceIndex();
						for ( MaltSentenceLink prepLink : maltSentence.getLinksByTargetIndex(link.getSourceIndex())){
							Token lm = sentence.getTokens().get(prepLink.getSourceIndex());
							if ( lm.getOrth().equals(",")){
								typeLM = "_LMconj";
								for ( MaltSentenceLink prepLinkComma : maltSentence.getLinksByTargetIndex(prepLink.getSourceIndex())){
									landmarks.add(prepLinkComma.getSourceIndex());
								}
							}
							else if ( this.objectPos.contains(lm.getDisambTag().getPos()) ){
								landmarks.add(prepLink.getSourceIndex());
							}
						}
					}
				}
			}
			
			if ( landmarks.size() > 0 && trajectors.size() > 0 && indicator != null ){
				for ( Integer landmark : landmarks ){
					for ( Integer trajector : trajectors ){
						/* Ustal spatial indicator */
						Annotation si = chunkPrepTokens.get(indicator);
						if ( si == null ){
							si = new Annotation(indicator, "SI", sentence);
							sentence.addChunk(si);
						}
						
						/* Ustal trajector */
						Annotation tr = null;
						List<Annotation> trs = mapTokenIdToAnnotations.get(trajector);
						if ( trs != null ){
							tr = trs.get(0);
						}
						if ( tr == null ){
							tr = new Annotation(trajector, "TR", sentence);
							sentence.addChunk(tr);
						}
						
						/* Ustal landmark */
						Annotation lm = null;
						List<Annotation> lms = mapTokenIdToAnnotations.get(landmark);
						if ( lms != null ){
							lm = lms.get(0);
						}
						if ( lm == null ){
							lm = new Annotation(landmark, "LM", sentence);
							sentence.addChunk(lm);
						}
						
						srs.add(new SpatialExpression(type + typeLM + typeTR, tr, si, lm));
					}
				}
			}
		}
		return srs;
	}
	
	/**
	 * 
	 * @param type
	 * @param trajectors
	 * @param landmarks
	 * @param preposition
	 * @return
	 */
	private List<SpatialExpression> generateAllCombinations(String type, List<Annotation> trajectors, List<Annotation> landmarks, Annotation preposition){
		List<SpatialExpression> relations = new ArrayList<SpatialExpression>();
		if ( trajectors != null && landmarks != null ){
			for ( Annotation trajector : trajectors ){
				for ( Annotation landmark : landmarks ){
					SpatialExpression sr = new SpatialExpression(type, trajector, preposition, landmark);
					relations.add(sr);						
				}
			}
		}	
		return relations;
	}
	
}