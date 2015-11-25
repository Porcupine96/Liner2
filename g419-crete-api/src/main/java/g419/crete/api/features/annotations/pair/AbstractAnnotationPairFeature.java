package g419.crete.api.features.annotations.pair;

import g419.corpus.structure.Annotation;
import g419.crete.api.features.AbstractFeature;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 
 * @author akaczmarek
 *
 * @param <T>
 * @TODO: sprawdzić czy nie ma konfliktów spowodowanych przez type erasure dla Pair występującego w AbstractClusterMentionFeature
 * - w przypadku konfliktu rozważyć przejście z gołych obiektów (Annotation, Pair<Annotation, Cluster>)  na wrappery (Mention, Cluster, MentionPair, etc.)
 */
public abstract class AbstractAnnotationPairFeature<T> extends AbstractFeature<Pair<Annotation, Annotation>, T> {

	@Override
	@SuppressWarnings("unchecked")
	public Class<Pair<Annotation, Annotation>> getInputTypeClass() {
		Pair<Annotation, Annotation> pair = new ImmutablePair<Annotation, Annotation>(null, null);
		return (Class<Pair<Annotation, Annotation>>) pair.getClass();
	}
}