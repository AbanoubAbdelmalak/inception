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
package de.tudarmstadt.ukp.inception.recommendation.api;

import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_TRANSIENT_ACCEPTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion.FLAG_TRANSIENT_CORRECTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction.ACCEPTED;

import org.apache.uima.cas.AnnotationBaseFS;
import org.apache.uima.cas.CAS;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.ApplicationEventPublisher;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.recommendation.api.event.RecommendationAcceptedEvent;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordChangeLocation;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction;
import de.tudarmstadt.ukp.inception.schema.api.adapter.AnnotationException;
import de.tudarmstadt.ukp.inception.schema.api.adapter.TypeAdapter;
import de.tudarmstadt.ukp.inception.support.uima.ICasUtil;

public abstract class LayerRecommendationSupport_ImplBase<S extends AnnotationSuggestion>
    implements LayerRecommendationSupport<S>, BeanNameAware
{
    protected final RecommendationService recommendationService;
    protected final LearningRecordService learningRecordService;
    protected final ApplicationEventPublisher applicationEventPublisher;

    private String id;

    public LayerRecommendationSupport_ImplBase(RecommendationService aRecommendationService,
            LearningRecordService aLearningRecordService,
            ApplicationEventPublisher aApplicationEventPublisher)
    {
        recommendationService = aRecommendationService;
        learningRecordService = aLearningRecordService;
        applicationEventPublisher = aApplicationEventPublisher;
    }

    @Override
    public void setBeanName(String aName)
    {
        id = aName;
    }

    @Override
    public String getId()
    {
        return id;
    }

    protected void commmitLabel(String aSessionOwner, SourceDocument aDocument,
            String aDataOwner, CAS aCas, TypeAdapter aAdapter, AnnotationFeature aFeature,
            AnnotationSuggestion aSuggestion, String aValue, AnnotationBaseFS annotation,
            LearningRecordChangeLocation aLocation, LearningRecordUserAction aAction)
        throws AnnotationException
    {
        // Update the feature value
        aAdapter.setFeatureValue(aDocument, aDataOwner, aCas, ICasUtil.getAddr(annotation),
                aFeature, aValue);

        // Hide the suggestion. This is faster than having to recalculate the visibility status for
        // the entire document or even for the part visible on screen.
        aSuggestion
                .hide((aAction == ACCEPTED) ? FLAG_TRANSIENT_ACCEPTED : FLAG_TRANSIENT_CORRECTED);

        // Log the action to the learning record
        if (!aAdapter.isSilenced()) {
            learningRecordService.logRecord(aSessionOwner, aDocument, aDataOwner, aSuggestion,
                    aFeature, aAction, aLocation);

            // Send an application event that the suggestion has been accepted
            aAdapter.publishEvent(() -> new RecommendationAcceptedEvent(this, aDocument, aDataOwner,
                    annotation, aFeature, aSuggestion.getLabel()));
        }
    }
}
