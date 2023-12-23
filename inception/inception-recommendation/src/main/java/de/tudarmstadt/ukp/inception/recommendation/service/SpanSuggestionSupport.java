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
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction.REJECTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction.SKIPPED;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static org.apache.uima.cas.text.AnnotationPredicates.colocated;
import static org.apache.uima.fit.util.CasUtil.select;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.uima.cas.AnnotationBaseFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationPredicates;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.tcas.Annotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.annotation.layer.span.SpanAdapter;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.SuggestionSupport_ImplBase;
import de.tudarmstadt.ukp.inception.recommendation.api.event.RecommendationRejectedEvent;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecord;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordChangeLocation;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Offset;
import de.tudarmstadt.ukp.inception.recommendation.api.model.SpanSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.SuggestionGroup;
import de.tudarmstadt.ukp.inception.recommendation.util.OverlapIterator;
import de.tudarmstadt.ukp.inception.schema.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.inception.schema.api.adapter.AnnotationException;
import de.tudarmstadt.ukp.inception.schema.api.adapter.TypeAdapter;

public class SpanSuggestionSupport
    extends SuggestionSupport_ImplBase<SpanSuggestion>
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String TYPE = "SPAN";

    public SpanSuggestionSupport(RecommendationService aRecommendationService,
            LearningRecordService aLearningRecordService,
            ApplicationEventPublisher aApplicationEventPublisher,
            AnnotationSchemaService aSchemaService)
    {
        super(aRecommendationService, aLearningRecordService, aApplicationEventPublisher,
                aSchemaService);
    }

    @Override
    public boolean accepts(AnnotationSuggestion aContext)
    {
        return aContext instanceof SpanSuggestion;
    }

    @Override
    public AnnotationBaseFS acceptSuggestion(String aSessionOwner, SourceDocument aDocument,
            String aDataOwner, CAS aCas, TypeAdapter aAdapter, AnnotationFeature aFeature,
            AnnotationSuggestion aSuggestion, LearningRecordChangeLocation aLocation,
            LearningRecordUserAction aAction)
        throws AnnotationException
    {
        return acceptOrCorrectSuggestion(aSessionOwner, aDocument, aDataOwner, aCas,
                (SpanAdapter) aAdapter, aFeature, (SpanSuggestion) aSuggestion, aLocation, aAction);
    }

    public AnnotationFS acceptOrCorrectSuggestion(String aSessionOwner, SourceDocument aDocument,
            String aDataOwner, CAS aCas, SpanAdapter aAdapter, AnnotationFeature aFeature,
            SpanSuggestion aSuggestion, LearningRecordChangeLocation aLocation,
            LearningRecordUserAction aAction)
        throws AnnotationException
    {
        var aBegin = aSuggestion.getBegin();
        var aEnd = aSuggestion.getEnd();
        var aValue = aSuggestion.getLabel();

        var candidates = aCas.<Annotation> select(aAdapter.getAnnotationTypeName()) //
                .at(aBegin, aEnd) //
                .asList();

        var candidateWithEmptyLabel = candidates.stream() //
                .filter(c -> aAdapter.getFeatureValue(aFeature, c) == null) //
                .findFirst();

        AnnotationFS annotation;
        if (candidateWithEmptyLabel.isPresent()) {
            // If there is an annotation where the predicted feature is unset, use it ...
            annotation = candidateWithEmptyLabel.get();
        }
        else if (candidates.isEmpty() || aAdapter.getLayer().isAllowStacking()) {
            // ... if not or if stacking is allowed, then we create a new annotation - this also
            // takes care of attaching to an annotation if necessary
            var newAnnotation = aAdapter.add(aDocument, aDataOwner, aCas, aBegin, aEnd);
            annotation = newAnnotation;
        }
        else {
            // ... if yes and stacking is not allowed, then we update the feature on the existing
            // annotation
            annotation = candidates.get(0);
        }

        commmitLabel(aSessionOwner, aDocument, aDataOwner, aCas, aAdapter, aFeature, aSuggestion,
                aValue, annotation, aLocation, aAction);

        return annotation;
    }

    @Override
    public void rejectSuggestion(String aSessionOwner, SourceDocument aDocument, String aDataOwner,
            AnnotationSuggestion suggestion, LearningRecordChangeLocation aAction)
    {
        var spanSuggestion = (SpanSuggestion) suggestion;

        // Hide the suggestion. This is faster than having to recalculate the visibility status
        // for the entire document or even for the part visible on screen.
        spanSuggestion.hide(FLAG_TRANSIENT_REJECTED);

        var recommender = recommendationService.getRecommender(spanSuggestion.getVID().getId());
        var feature = recommender.getFeature();
        // Log the action to the learning record
        learningRecordService.logRecord(aSessionOwner, aDocument, aDataOwner, spanSuggestion,
                feature, REJECTED, aAction);

        // Send an application event that the suggestion has been rejected
        applicationEventPublisher.publishEvent(new RecommendationRejectedEvent(this, aDocument,
                aDataOwner, spanSuggestion.getBegin(), spanSuggestion.getEnd(),
                spanSuggestion.getCoveredText(), feature, spanSuggestion.getLabel()));

    }

    @Override
    public void skipSuggestion(String aSessionOwner, SourceDocument aDocument, String aDataOwner,
            AnnotationSuggestion aSuggestion, LearningRecordChangeLocation aAction)
        throws AnnotationException
    {
        // Hide the suggestion. This is faster than having to recalculate the visibility status
        // for the entire document or even for the part visible on screen.
        aSuggestion.hide(FLAG_SKIPPED);

        var recommender = recommendationService.getRecommender(aSuggestion.getVID().getId());
        var feature = recommender.getFeature();

        // Log the action to the learning record
        learningRecordService.logRecord(aSessionOwner, aDocument, aDataOwner, aSuggestion, feature,
                SKIPPED, aAction);

        // // Send an application event that the suggestion has been rejected
        // applicationEventPublisher.publishEvent(new RecommendationSkippedEvent(this,
        // aDocument,
        // aDataOwner, spanSuggestion.getBegin(), spanSuggestion.getEnd(),
        // spanSuggestion.getCoveredText(), feature, spanSuggestion.getLabel()));
    }

    @Override
    public <T extends AnnotationSuggestion> void calculateSuggestionVisibility(String aSessionOwner,
            SourceDocument aDocument, CAS aCas, String aDataOwner, AnnotationLayer aLayer,
            Collection<SuggestionGroup<T>> aRecommendations, int aWindowBegin, int aWindowEnd)
    {
        LOG.trace(
                "calculateSpanSuggestionVisibility() for layer {} on document {} in range [{}, {}]",
                aLayer, aDocument, aWindowBegin, aWindowEnd);

        var type = getAnnotationType(aCas, aLayer);
        if (type == null) {
            // The type does not exist in the type system of the CAS. Probably it has not
            // been upgraded to the latest version of the type system yet. If this is the case,
            // we'll just skip.
            return;
        }

        var annotationsInWindow = getAnnotationsInWindow(aCas, type, aWindowBegin, aWindowEnd);

        // Collect all suggestions of the given layer within the view window
        var suggestionsInWindow = aRecommendations.stream()
                // Only suggestions for the given layer
                .filter(group -> group.getLayerId() == aLayer.getId())
                // ... and in the given window
                .filter(group -> {
                    Offset offset = (Offset) group.getPosition();
                    return AnnotationPredicates.coveredBy(offset.getBegin(), offset.getEnd(),
                            aWindowBegin, aWindowEnd);
                }) //
                .collect(toList());

        // Get all the skipped/rejected entries for the current layer
        var recordedAnnotations = learningRecordService.listLearningRecords(aSessionOwner,
                aDataOwner, aLayer);

        for (var feature : schemaService.listSupportedFeatures(aLayer)) {
            var feat = type.getFeatureByBaseName(feature.getName());

            if (feat == null) {
                // The feature does not exist in the type system of the CAS. Probably it has not
                // been upgraded to the latest version of the type system yet. If this is the case,
                // we'll just skip.
                return;
            }

            // Reduce the suggestions to the ones for the given feature. We can use the tree here
            // since we only have a single SuggestionGroup for every position
            var suggestions = new TreeMap<Offset, SuggestionGroup<SpanSuggestion>>(
                    comparingInt(Offset::getBegin).thenComparingInt(Offset::getEnd));
            suggestionsInWindow.stream()
                    .filter(group -> group.getFeature().equals(feature.getName())) //
                    .map(group -> {
                        group.showAll(AnnotationSuggestion.FLAG_ALL);
                        return group;
                    }) //
                    .forEach(group -> suggestions.put((Offset) group.getPosition(),
                            (SuggestionGroup) group));

            hideSpanSuggestionsThatOverlapWithAnnotations(annotationsInWindow, feature, feat,
                    suggestions);

            // Anything that was not hidden so far might still have been rejected
            suggestions.values().stream() //
                    .flatMap(SuggestionGroup::stream) //
                    .filter(AnnotationSuggestion::isVisible) //
                    .forEach(suggestion -> hideSuggestionsRejectedOrSkipped(suggestion,
                            recordedAnnotations));
        }
    }

    private void hideSpanSuggestionsThatOverlapWithAnnotations(
            List<AnnotationFS> annotationsInWindow, AnnotationFeature feature, Feature feat,
            Map<Offset, SuggestionGroup<SpanSuggestion>> suggestions)
    {
        // If there are no suggestions or annotations, there is nothing to do here
        if (annotationsInWindow.isEmpty() || suggestions.isEmpty()) {
            return;
        }

        // Reduce the annotations to the ones which have a non-null feature value. We need to
        // use a multi-valued map here because there may be multiple annotations at a
        // given position.
        var annotations = new ArrayListValuedHashMap<Offset, AnnotationFS>();
        annotationsInWindow
                .forEach(fs -> annotations.put(new Offset(fs.getBegin(), fs.getEnd()), fs));

        // We need to constructed a sorted list of the keys for the OverlapIterator below
        var sortedAnnotationKeys = new ArrayList<Offset>(annotations.keySet());
        sortedAnnotationKeys.sort(comparingInt(Offset::getBegin).thenComparingInt(Offset::getEnd));

        // This iterator gives us pairs of annotations and suggestions. Note that both lists
        // must be sorted in the same way. The suggestion offsets are sorted because they are
        // the keys in a TreeSet - and the annotation offsets are sorted in the same way manually
        var oi = new OverlapIterator(sortedAnnotationKeys, new ArrayList<>(suggestions.keySet()));

        // Bulk-hide any groups that overlap with existing annotations on the current layer
        // and for the current feature
        var hiddenForOverlap = new ArrayList<AnnotationSuggestion>();
        while (oi.hasNext()) {
            if (oi.getA().overlaps(oi.getB())) {
                // Fetch the current suggestion and annotation
                var group = suggestions.get(oi.getB());
                for (var annotation : annotations.get(oi.getA())) {
                    var label = annotation.getFeatureValueAsString(feat);
                    for (var suggestion : group) {
                        // The suggestion would just create an annotation and not set any
                        // feature
                        boolean colocated = colocated(annotation, suggestion.getBegin(),
                                suggestion.getEnd());
                        if (suggestion.getLabel() == null) {
                            // If there is already an annotation, then we hide any suggestions
                            // that would just trigger the creation of the same annotation and
                            // not set any new feature. This applies whether stacking is allowed
                            // or not.
                            if (colocated) {
                                suggestion.hide(FLAG_OVERLAP);
                                hiddenForOverlap.add(suggestion);
                                continue;
                            }

                            // If stacking is enabled, we do allow suggestions that create an
                            // annotation with no label, but only if the offsets differ
                            if (feature.getLayer().isAllowStacking() && !colocated) {
                                suggestion.hide(FLAG_OVERLAP);
                                hiddenForOverlap.add(suggestion);
                                continue;
                            }
                        }
                        // The suggestion would merge the suggested feature value into an
                        // existing annotation or create a new annotation with the feature if
                        // stacking were enabled.
                        else {
                            // Is the feature still unset in the current annotation - i.e. would
                            // accepting the suggestion merge the feature into it? If yes, we do
                            // not hide
                            if (label == null && colocated) {
                                continue;
                            }

                            // Does the suggested label match the label of an existing annotation
                            // at the same position then we hide
                            if (label != null && label.equals(suggestion.getLabel()) && colocated) {
                                suggestion.hide(FLAG_OVERLAP);
                                hiddenForOverlap.add(suggestion);
                                continue;
                            }

                            // Would accepting the suggestion create a new annotation but
                            // stacking is not enabled - then we need to hide
                            if (!feature.getLayer().isAllowStacking()) {
                                suggestion.hide(FLAG_OVERLAP);
                                hiddenForOverlap.add(suggestion);
                                continue;
                            }
                        }
                    }
                }

                // Do not want to process the annotation again since the relevant suggestions are
                // already hidden
                oi.ignoreA();
            }

            oi.step();
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Hidden due to overlapping: {}", hiddenForOverlap.size());
            for (var s : hiddenForOverlap) {
                LOG.trace("- {}", s);
            }
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

    static void hideSuggestionsRejectedOrSkipped(SpanSuggestion aSuggestion,
            List<LearningRecord> aRecordedRecommendations)
    {
        aRecordedRecommendations.stream() //
                .filter(r -> Objects.equals(r.getLayer().getId(), aSuggestion.getLayerId())) //
                .filter(r -> Objects.equals(r.getAnnotationFeature().getName(),
                        aSuggestion.getFeature())) //
                .filter(r -> Objects.equals(r.getSourceDocument().getName(),
                        aSuggestion.getDocumentName())) //
                .filter(r -> aSuggestion.labelEquals(r.getAnnotation())) //
                .filter(r -> r.getOffsetBegin() == aSuggestion.getBegin()
                        && r.getOffsetEnd() == aSuggestion.getEnd()) //
                .filter(r -> aSuggestion.hideSuggestion(r.getUserAction())) //
                .findAny();
    }

    @Override
    public LearningRecord toLearningRecord(SourceDocument aDocument, String aUsername,
            AnnotationSuggestion aSuggestion, AnnotationFeature aFeature,
            LearningRecordUserAction aUserAction, LearningRecordChangeLocation aLocation)
    {
        var pos = ((SpanSuggestion) aSuggestion).getPosition();
        var record = new LearningRecord();
        record.setUser(aUsername);
        record.setSourceDocument(aDocument);
        record.setUserAction(aUserAction);
        record.setOffsetBegin(pos.getBegin());
        record.setOffsetEnd(pos.getEnd());
        record.setOffsetBegin2(-1);
        record.setOffsetEnd2(-1);
        record.setTokenText(((SpanSuggestion) aSuggestion).getCoveredText());
        record.setAnnotation(aSuggestion.getLabel());
        record.setLayer(aFeature.getLayer());
        record.setSuggestionType(TYPE);
        record.setChangeLocation(aLocation);
        record.setAnnotationFeature(aFeature);
        return record;
    }
}
