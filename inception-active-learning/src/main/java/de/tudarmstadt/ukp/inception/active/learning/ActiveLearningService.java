/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.active.learning;

import java.util.Date;
import java.util.List;

import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.active.learning.sidebar.ActiveLearningRecommender;
import de.tudarmstadt.ukp.inception.active.learning.sidebar.RecommendationDifference;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationObject;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Predictions;

public interface ActiveLearningService
{
    List<List<AnnotationObject>> getRecommendationsForWholeProject(Predictions model,
            AnnotationLayer aLayer);
    
    List<List<AnnotationObject>> getRecommendationFromRecommendationModel(AnnotatorState aState,
            AnnotationLayer aLayer);

    List<AnnotationObject> getFlattenedRecommendationsFromRecommendationModel(JCas aJcas,
            AnnotatorState aState, AnnotationLayer aSelectedLayer);

    void putSessionActive(User aUser, Project aProject, boolean aSesscionActive);

    boolean getSessionActive(User aUser, Project aProject);

    void putHasUnseenRecommendation(User aUser, Project aProject, boolean aHasUnseenRecommendation);

    boolean getHasUnseenRecommendation(User aUser, Project aProject);

    void putHasSkippedRecommendation(User aUser, Project aProject,
        boolean aHasSkippedRecommendation);

    boolean getHasSkippedRecommendation(User aUser, Project aProject);

    void putDoExistRecommender(User aUser, Project aProject, boolean aDoExistRecommenders);

    boolean getDoExistRecommenders(User aUser, Project aProject);

    void putCurrentRecommendation(User aUser, Project aProject,
        AnnotationObject aCurrentRecommendation);

    AnnotationObject getCurrentRecommendation(User aUser, Project aProject);

    void putCurrentDifference(User aUser, Project aProject,
        RecommendationDifference aCurrentDifference);

    RecommendationDifference getCurrentDifference(User aUser, Project aProject);

    void putSelectedLayer(User aUser, Project aProject, AnnotationLayer aSelectedLayer);

    AnnotationLayer getSelectedLayer(User aUser, Project aProject);

    void putActiveLearningRecommender(User aUser, Project aProject,
        ActiveLearningRecommender aActiveLearningRecommender);

    ActiveLearningRecommender getActiveLearningRecommender(User aUser, Project aProject);

    void putLearnSkippedRecommendationTime(User aUser, Project aProject,
        Date aLearnSkippedRecommendationTime);

    Date getLearnSkippedRecommendationTime(User aUser, Project aProject);
}
