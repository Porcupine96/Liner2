package g419.crete.api.features.factory.item;

import g419.corpus.structure.Annotation;
import g419.corpus.structure.AnnotationCluster;
import g419.crete.api.features.AbstractFeature;
import g419.crete.api.features.clustermention.preceeding.ClusterMentionClosestPreceedingIsReflexivePossesive;

import org.apache.commons.lang3.tuple.Pair;


public class ClusterMentionClosestPreceedingIsReflexivePossesiveItem implements IFeatureFactoryItem<Pair<Annotation, AnnotationCluster>, Boolean> {

	@Override
	public AbstractFeature<Pair<Annotation, AnnotationCluster>, Boolean> createFeature() {
		return new ClusterMentionClosestPreceedingIsReflexivePossesive();
	}

}