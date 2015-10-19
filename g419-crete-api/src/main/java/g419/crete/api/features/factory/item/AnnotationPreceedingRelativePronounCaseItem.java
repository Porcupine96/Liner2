package g419.crete.api.features.factory.item;

import g419.corpus.structure.Annotation;
import g419.crete.api.features.AbstractFeature;
import g419.crete.api.features.annotations.AnnotationFeaturePreceedingRelativePronounCase;
import g419.crete.api.features.enumvalues.Case;

public class AnnotationPreceedingRelativePronounCaseItem implements IFeatureFactoryItem<Annotation, Case> {

	@Override
	public AbstractFeature<Annotation, Case> createFeature() {
		return new AnnotationFeaturePreceedingRelativePronounCase();
	}

}
