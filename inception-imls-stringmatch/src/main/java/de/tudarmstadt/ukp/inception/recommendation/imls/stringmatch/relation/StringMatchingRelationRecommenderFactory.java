/*
 * Copyright 2019
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
package de.tudarmstadt.ukp.inception.recommendation.imls.stringmatch.relation;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.RELATION_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode.SINGLE_TOKEN;
import static de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode.TOKENS;
import static java.util.Arrays.asList;

import org.apache.uima.cas.CAS;
import org.apache.wicket.model.IModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactoryImplBase;
import de.tudarmstadt.ukp.inception.recommendation.imls.stringmatch.relation.settings.StringMatchingRelationRecommenderTraitsEditor;

@Component
@ConditionalOnProperty(prefix = "imls.relation.string", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StringMatchingRelationRecommenderFactory
    extends RecommendationEngineFactoryImplBase<StringMatchingRelationRecommenderTraits>
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    // This is a string literal so we can rename/refactor the class without it changing its ID
    // and without the database starting to refer to non-existing recommendation tools.
    public static final String ID =
        "StringMatchingRelationRecommender";

    
    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public RecommendationEngine build(Recommender aRecommender)
    {
        StringMatchingRelationRecommenderTraits traits
                = new StringMatchingRelationRecommenderTraits();
        StringMatchingRelationRecommender recommender =
                new StringMatchingRelationRecommender(aRecommender, traits);

        return recommender;
    }

    @Override
    public String getName()
    {
        return "String Matcher for relations";
    }

    @Override
    public boolean accepts(AnnotationLayer aLayer, AnnotationFeature aFeature)
    {
        if (aLayer == null || aFeature == null) {
            return false;
        }

        return (asList(SINGLE_TOKEN, TOKENS).contains(aLayer.getAnchoringMode()))
            && RELATION_TYPE.equals(aLayer.getType())
            && (CAS.TYPE_NAME_STRING.equals(aFeature.getType()) || aFeature.isVirtualFeature());
    }
    
    @Override
    public StringMatchingRelationRecommenderTraitsEditor createTraitsEditor(String aId,
            IModel<Recommender> aModel)
    {
        return new StringMatchingRelationRecommenderTraitsEditor(aId, aModel);
    }

    @Override
    public StringMatchingRelationRecommenderTraits createTraits()
    {
        return new StringMatchingRelationRecommenderTraits();
    }
}
