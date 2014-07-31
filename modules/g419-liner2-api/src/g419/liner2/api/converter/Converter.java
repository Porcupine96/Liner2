package g419.liner2.api.converter;


import g419.corpus.structure.Annotation;
import g419.corpus.structure.AnnotationSet;
import g419.corpus.structure.Document;
import g419.corpus.structure.Sentence;

import java.util.LinkedHashSet;

/**
 * Created by michal on 6/3/14.
 */
public abstract class Converter {

    public void apply(Document doc){
        for(Sentence sent: doc.getSentences()){
            apply(sent.getChunks());
        }

    }

    abstract public void apply(LinkedHashSet<Annotation> sentenceAnnotations);


}
