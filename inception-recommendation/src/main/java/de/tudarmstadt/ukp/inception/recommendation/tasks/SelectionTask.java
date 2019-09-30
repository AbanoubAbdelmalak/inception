/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.NoResultException;

import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.DataSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.PercentageBasedSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.model.EvaluatedRecommender;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderEvaluationResultEvent;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderState;
import de.tudarmstadt.ukp.inception.scheduling.SchedulingService;
import de.tudarmstadt.ukp.inception.scheduling.Task;
import de.tudarmstadt.ukp.inception.scheduling.TaskState;
import de.tudarmstadt.ukp.inception.scheduling.TaskUpdateEvent;

/**
 * This task evaluates all available classification tools for all annotation layers of the current
 * project. If a classifier exceeds its specific activation f-score limit during the evaluation it
 * is selected for active prediction.
 */
public class SelectionTask
    extends Task
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private @Autowired AnnotationSchemaService annoService;
    private @Autowired DocumentService documentService;
    private @Autowired RecommendationService recommendationService;
    private @Autowired ApplicationEventPublisher appEventPublisher;
    private @Autowired SchedulingService schedulingService;

    public SelectionTask(Project aProject, User aUser, String aTrigger)
    {
        super(aUser, aProject, aTrigger);
    }

    @Override
    public void run()
    {
        Project project = getProject();
        User user = getUser();
        String userName = user.getUsername();
        
        // Read the CASes only when they are accessed the first time. This allows us to skip reading
        // the CASes in case that no layer / recommender is available or if no recommender requires
        // evaluation.
        LazyInitializer<List<CAS>> casses = new LazyInitializer<List<CAS>>()
        {
            @Override
            protected List<CAS> initialize()
            {
                return readCasses(project, userName);
            }
        };

        boolean seenRecommender = false;
        for (AnnotationLayer layer : annoService.listAnnotationLayer(getProject())) {
            if (!layer.isEnabled()) {
                continue;
            }
            
            List<Recommender> recommenders = recommendationService.listRecommenders(layer);
            if (recommenders == null || recommenders.isEmpty()) {
                log.trace("[{}][{}]: No recommenders, skipping selection.", userName,
                        layer.getUiName());
                continue;
            }
            
            seenRecommender = true;
    
            List<EvaluatedRecommender> activeRecommenders = new ArrayList<>();
            
            int recommenderCount = 0;
            for (Recommender r : recommenders) {
                recommenderCount++;
                // Make sure we have the latest recommender config from the DB - the one from
                // the active recommenders list may be outdated
                Recommender recommender;
                try {
                    recommender = recommendationService.getRecommender(r.getId());
                }
                catch (NoResultException e) {
                    log.info("[{}][{}]: Recommender no longer available... skipping",
                            userName, r.getName());
                    continue;
                }

                if (!recommender.isEnabled()) {
                    log.debug("[{}][{}]: Disabled - skipping", userName, recommender.getName());
                    continue;
                }

                String recommenderName = recommender.getName();
                
                try {
                    long start = System.currentTimeMillis();
                    RecommendationEngineFactory factory = recommendationService
                        .getRecommenderFactory(recommender);
                    
                    if (factory == null) {
                        log.error("[{}][{}]: No recommender factory available for [{}]",
                                userName, r.getName(), r.getTool());
                        continue;
                    }
                    
                    if (!factory.accepts(recommender.getLayer(), recommender.getFeature())) {
                        log.info("[{}][{}]: Recommender configured with invalid layer or feature "
                                + "- skipping recommender", userName, r.getName());
                        continue;
                    }
                    
                    RecommendationEngine recommendationEngine = factory.build(recommender);

                    if (recommender.isAlwaysSelected()) {
                        handleEvaluationSkipped(userName, recommenders.size(), recommenderCount,
                                activeRecommenders, recommender, recommenderName,
                                "Activated always selected recommender.", start);
                    }
                    else if (!factory.isEvaluable()) {
                        handleEvaluationSkipped(userName, recommenders.size(), recommenderCount,
                                activeRecommenders, recommender, recommenderName,
                                "Activated non-evaluable recommender.", start);
                        continue;
                    }
    
                    log.info("[{}][{}]: Evaluating...", userName, recommenderName);

                    DataSplitter splitter = new PercentageBasedSplitter(0.8, 10);
                    EvaluationResult result = recommendationEngine.evaluate(casses.get(), splitter);
                    
                    if (result.isEvaluationSkipped()) {
                        log.info("[{}][{}]: Evaluation could not be performed: {}",
                                user.getUsername(), recommenderName,
                                result.getErrorMsg().orElse("unknown reason"));
                        continue;
                    }
                    
                    double score = result.computeF1Score();

                    Double threshold = recommender.getThreshold();
                    boolean activated;
                    if (score >= threshold) {
                        activated = true;
                        activeRecommenders.add(new EvaluatedRecommender(recommender, result, true));
                        log.info("[{}][{}]: Activated ({} is above threshold {})",
                                userName, recommenderName, score,
                                threshold);
                    }
                    else {
                        activated = false;
                        log.info("[{}][{}]: Not activated ({} is not above threshold {})",
                                userName, recommenderName, score,
                                threshold);
                    }
                    publishEvalEvent(userName, recommenders.size(), recommenderCount, recommender,
                            start, result, activated);
                    
                }
                catch (Throwable e) {
                    log.error("[{}][{}]: Failed", userName, recommenderName, e);
                    appEventPublisher
                            .publishEvent(new TaskUpdateEvent(this, userName, TaskState.DONE, 1,
                                    String.format("Recommender %s failed.", recommender)));
                }
            }
            recommendationService.setActiveRecommenders(user, layer, activeRecommenders);
        }
        
        if (!seenRecommender) {
            log.trace("[{}]: No recommenders configured, skipping training.", userName);
            return;
        }

        if (!recommendationService.hasActiveRecommenders(user.getUsername(), project)) {
            log.debug("[{}]: No recommenders active, skipping training.", userName);
            return;
        }
        
        schedulingService.enqueue(new TrainingTask(user, getProject(),
                "SelectionTask after activating recommenders"));
        
    }

    private void handleEvaluationSkipped(String userName, int aRecommenderSize,
            int aRecommenderCount, List<EvaluatedRecommender> aActiveRecommenders,
            Recommender aRecommender, String aRecommenderName, String aMessage, long start)
    {
        log.debug("[{}][{}]: {}", userName, aRecommenderName, aMessage);
        EvaluationResult skipped = EvaluationResult.skipped();
        aActiveRecommenders.add(new EvaluatedRecommender(aRecommender,
                skipped, true));
        //publish
        publishEvalEvent(userName, aRecommenderSize, aRecommenderCount, aRecommender,
                start, skipped, true);
    }

    private void publishEvalEvent(String user, int aRecommenderSize, int aRecommenderCount,
            Recommender aRecommender, long aStart, EvaluationResult aResult, boolean aActivated)
    {
        RecommenderState recommenderState = aRecommenderCount < aRecommenderSize ? 
                RecommenderState.EVALUATION_STARTED : 
                    RecommenderState.EVALUATION_FINISHED;
        double progress = (double) aRecommenderCount / aRecommenderSize;
        long duration = System.currentTimeMillis() - aStart;
        RecommenderEvaluationResultEvent event = new RecommenderEvaluationResultEvent(this, 
                user, TaskState.RUNNING, 
                progress, aRecommender, aActivated, 
                recommenderState, aResult, duration); 
        appEventPublisher.publishEvent(event);
        log.debug("Published event: {}", event.toString());
    }

    private List<CAS> readCasses(Project aProject, String aUserName)
    {
        List<CAS> casses = new ArrayList<>();
        for (SourceDocument document : documentService.listSourceDocuments(aProject)) {
            try {
                CAS cas = documentService.readAnnotationCas(document, aUserName);
                annoService.upgradeCasIfRequired(cas, document, aUserName);
                casses.add(cas);
            } catch (IOException e) {
                log.error("Cannot read annotation CAS.", e);
            } catch (UIMAException e) {
                log.error("Cannot upgrade annotation CAS.", e);
            }
        }
        return casses;
    }
}
