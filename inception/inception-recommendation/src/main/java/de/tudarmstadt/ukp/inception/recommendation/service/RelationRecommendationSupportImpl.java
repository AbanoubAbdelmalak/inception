/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.service;

import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_OVERLAP;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_SKIPPED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_TRANSIENT_REJECTED;
import static de.tudarmstadt.ukp.inception.support.WebAnnoConst.FEAT_REL_SOURCE;
import static de.tudarmstadt.ukp.inception.support.WebAnnoConst.FEAT_REL_TARGET;
import static java.util.stream.Collectors.toList;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.CasUtil.selectAt;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.annotation.layer.relation.RelationAdapter;
import de.tudarmstadt.ukp.inception.recommendation.api.LayerRecommendationSupport_ImplBase;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecord;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordChangeLocation;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Position;
import de.tudarmstadt.ukp.inception.recommendation.api.model.RelationPosition;
import de.tudarmstadt.ukp.inception.recommendation.api.model.RelationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.SuggestionGroup;
import de.tudarmstadt.ukp.inception.schema.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.inception.schema.api.adapter.AnnotationException;

public class RelationRecommendationSupportImpl
    extends LayerRecommendationSupport_ImplBase<RelationAdapter, RelationSuggestion>
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AnnotationSchemaService schemaService;

    public RelationRecommendationSupportImpl(RecommendationService aRecommendationService,
            LearningRecordService aLearningRecordService,
            ApplicationEventPublisher aApplicationEventPublisher,
            AnnotationSchemaService aSchemaService)
    {
        super(aRecommendationService, aLearningRecordService, aApplicationEventPublisher);

        schemaService = aSchemaService;
    }

    /**
     * Uses the given annotation suggestion to create a new annotation or to update a feature in an
     * existing annotation.
     * 
     * @param aDocument
     *            the source document to which the annotations belong
     * @param aDataOwner
     *            the annotator user to whom the annotations belong
     * @param aCas
     *            the CAS containing the annotations
     * @param aAdapter
     *            an adapter for the layer to upsert
     * @param aFeature
     *            the feature on the layer that should be upserted
     * @param aSuggestion
     *            the suggestion
     * @param aLocation
     *            the location from where the change was triggered
     * @param aAction
     *            TODO
     * @return the created/updated annotation.
     * @throws AnnotationException
     *             if there was an annotation-level problem
     */
    @Override
    public AnnotationFS acceptSuggestion(String aSessionOwner, SourceDocument aDocument,
            String aDataOwner, CAS aCas, RelationAdapter aAdapter, AnnotationFeature aFeature,
            RelationSuggestion aSuggestion, LearningRecordChangeLocation aLocation,
            LearningRecordUserAction aAction)
        throws AnnotationException
    {
        var sourceBegin = aSuggestion.getPosition().getSourceBegin();
        var sourceEnd = aSuggestion.getPosition().getSourceEnd();
        var targetBegin = aSuggestion.getPosition().getTargetBegin();
        var targetEnd = aSuggestion.getPosition().getTargetEnd();

        // Check if there is already a relation for the given source and target
        var type = CasUtil.getType(aCas, aAdapter.getAnnotationTypeName());
        var attachType = CasUtil.getType(aCas, aAdapter.getAttachTypeName());

        var sourceFeature = type.getFeatureByBaseName(FEAT_REL_SOURCE);
        var targetFeature = type.getFeatureByBaseName(FEAT_REL_TARGET);

        // The begin and end feature of a relation in the CAS are of the dependent/target
        // annotation. See also RelationAdapter::createRelationAnnotation.
        // We use that fact to search for existing relations for this relation suggestion
        var candidates = new ArrayList<AnnotationFS>();
        for (AnnotationFS relationCandidate : selectAt(aCas, type, targetBegin, targetEnd)) {
            AnnotationFS source = (AnnotationFS) relationCandidate.getFeatureValue(sourceFeature);
            AnnotationFS target = (AnnotationFS) relationCandidate.getFeatureValue(targetFeature);

            if (source == null || target == null) {
                continue;
            }

            if (source.getBegin() == sourceBegin && source.getEnd() == sourceEnd
                    && target.getBegin() == targetBegin && target.getEnd() == targetEnd) {
                candidates.add(relationCandidate);
            }
        }

        AnnotationFS annotation = null;
        if (candidates.size() == 1) {
            // One candidate, we just return it
            annotation = candidates.get(0);
        }
        else if (candidates.size() == 2) {
            LOG.warn("Found multiple candidates for upserting relation from suggestion");
            annotation = candidates.get(0);
        }

        // We did not find a relation for this suggestion, so we create a new one
        if (annotation == null) {
            // FIXME: We get the first match for the (begin, end) span. With stacking, there can
            // be more than one and we need to get the right one then which does not need to be
            // the first. We wait for #2135 to fix this. When stacking is enabled, then also
            // consider creating a new relation instead of upserting an existing one.

            var source = selectAt(aCas, attachType, sourceBegin, sourceEnd).stream().findFirst()
                    .orElse(null);
            var target = selectAt(aCas, attachType, targetBegin, targetEnd).stream().findFirst()
                    .orElse(null);

            if (source == null || target == null) {
                String msg = "Cannot find source or target annotation for upserting relation";
                LOG.error(msg);
                throw new IllegalStateException(msg);
            }

            annotation = aAdapter.add(aDocument, aDataOwner, source, target, aCas);
        }

        commmitAcceptedLabel(aSessionOwner, aDocument, aDataOwner, aCas, aAdapter, aFeature,
                aSuggestion, aSuggestion.getLabel(), annotation, aLocation, aAction);

        return annotation;
    }

    @Override
    public void rejectSuggestion(String aSessionOwner, SourceDocument aDocument, String aDataOwner,
            RelationSuggestion aSuggestion, LearningRecordChangeLocation aAction)
    {
        // Hide the suggestion. This is faster than having to recalculate the visibility status
        // for
        // the entire document or even for the part visible on screen.
        aSuggestion.hide(FLAG_TRANSIENT_REJECTED);

        // TODO: See span recommendation support...

    }

    @Override
    public void skipSuggestion(String aSessionOwner, SourceDocument aDocument, String aDataOwner,
            RelationSuggestion aSuggestion, LearningRecordChangeLocation aAction)
        throws AnnotationException
    {
        // Hide the suggestion. This is faster than having to recalculate the visibility status
        // for
        // the entire document or even for the part visible on screen.
        aSuggestion.hide(FLAG_SKIPPED);

        // TODO: Log rejection
        // TODO: Publish rejection event
    }

    @Override
    public void calculateSuggestionVisibility(String aSessionOwner, SourceDocument aDocument,
            CAS aCas, String aUser, AnnotationLayer aLayer,
            Collection<SuggestionGroup<RelationSuggestion>> aRecommendations, int aWindowBegin,
            int aWindowEnd)
    {
        var type = getAnnotationType(aCas, aLayer);

        if (type == null) {
            // The type does not exist in the type system of the CAS. Probably it has not
            // been upgraded to the latest version of the type system yet. If this is the case,
            // we'll just skip.
            return;
        }

        var governorFeature = type.getFeatureByBaseName(FEAT_REL_SOURCE);
        var dependentFeature = type.getFeatureByBaseName(FEAT_REL_TARGET);

        if (dependentFeature == null || governorFeature == null) {
            LOG.warn("Missing Dependent or Governor feature on [{}]", aLayer.getName());
            return;
        }

        var annotationsInWindow = getAnnotationsInWindow(aCas, type, aWindowBegin, aWindowEnd);

        // Group annotations by relation position, that is (source, target) address
        MultiValuedMap<Position, AnnotationFS> groupedAnnotations = new ArrayListValuedHashMap<>();
        for (AnnotationFS annotationFS : annotationsInWindow) {
            var source = (AnnotationFS) annotationFS.getFeatureValue(governorFeature);
            var target = (AnnotationFS) annotationFS.getFeatureValue(dependentFeature);

            var relationPosition = new RelationPosition(source.getBegin(), source.getEnd(),
                    target.getBegin(), target.getEnd());

            groupedAnnotations.put(relationPosition, annotationFS);
        }

        // Collect all suggestions of the given layer
        var groupedSuggestions = aRecommendations.stream()
                .filter(group -> group.getLayerId() == aLayer.getId()) //
                .collect(toList());

        // Get previously rejected suggestions
        MultiValuedMap<Position, LearningRecord> groupedRecordedAnnotations = new ArrayListValuedHashMap<>();
        for (var learningRecord : learningRecordService.listLearningRecords(aSessionOwner, aUser,
                aLayer)) {
            RelationPosition relationPosition = new RelationPosition(
                    learningRecord.getOffsetSourceBegin(), learningRecord.getOffsetSourceEnd(),
                    learningRecord.getOffsetTargetBegin(), learningRecord.getOffsetTargetEnd());

            groupedRecordedAnnotations.put(relationPosition, learningRecord);
        }

        for (AnnotationFeature feature : schemaService.listSupportedFeatures(aLayer)) {
            Feature feat = type.getFeatureByBaseName(feature.getName());

            if (feat == null) {
                // The feature does not exist in the type system of the CAS. Probably it has not
                // been upgraded to the latest version of the type system yet. If this is the case,
                // we'll just skip.
                return;
            }

            for (SuggestionGroup<RelationSuggestion> group : groupedSuggestions) {
                if (!feature.getName().equals(group.getFeature())) {
                    continue;
                }

                group.showAll(AnnotationSuggestion.FLAG_ALL);

                Position position = group.getPosition();

                // If any annotation at this position has a non-null label for this feature,
                // then we hide the suggestion group
                for (AnnotationFS annotationFS : groupedAnnotations.get(position)) {
                    if (annotationFS.getFeatureValueAsString(feat) != null) {
                        for (RelationSuggestion suggestion : group) {
                            suggestion.hide(FLAG_OVERLAP);
                        }
                    }
                }

                // Hide previously rejected suggestions
                for (LearningRecord learningRecord : groupedRecordedAnnotations.get(position)) {
                    for (RelationSuggestion suggestion : group) {
                        if (suggestion.labelEquals(learningRecord.getAnnotation())) {
                            suggestion.hideSuggestion(learningRecord.getUserAction());
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private Type getAnnotationType(CAS aCas, AnnotationLayer aLayer)
    {
        // NOTE: In order to avoid having to upgrade the "original CAS" in computePredictions,this
        // method is implemented in such a way that it gracefully handles cases where the CAS and
        // the project type system are not in sync - specifically the CAS where the project defines
        // layers or features which do not exist in the CAS.

        try {
            return CasUtil.getAnnotationType(aCas, aLayer.getName());
        }
        catch (IllegalArgumentException e) {
            // Type does not exist in the type system of the CAS. Probably it has not been upgraded
            // to the latest version of the type system yet. If this is the case, we'll just skip.
            return null;
        }
    }

    private List<AnnotationFS> getAnnotationsInWindow(CAS aCas, Type type, int aWindowBegin,
            int aWindowEnd)
    {
        if (type == null) {
            return Collections.emptyList();
        }

        return select(aCas, type).stream() //
                .filter(fs -> fs.coveredBy(aWindowBegin, aWindowEnd)) //
                .toList();
    }
}
