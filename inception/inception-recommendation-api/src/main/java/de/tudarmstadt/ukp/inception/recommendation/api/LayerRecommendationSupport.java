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

import org.apache.uima.cas.AnnotationBaseFS;
import org.apache.uima.cas.CAS;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordChangeLocation;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordUserAction;
import de.tudarmstadt.ukp.inception.schema.api.adapter.AnnotationException;
import de.tudarmstadt.ukp.inception.schema.api.adapter.TypeAdapter;

public interface LayerRecommendationSupport<T extends TypeAdapter, S extends AnnotationSuggestion>
{
    AnnotationBaseFS acceptSuggestion(String aSessionOwner, SourceDocument aDocument,
            String aDataOwner, CAS aCas, T aAdapter, AnnotationFeature aFeature, S aSuggestion,
            LearningRecordChangeLocation aLocation, LearningRecordUserAction aAction)
        throws AnnotationException;

    void rejectSuggestion(String aSessionOwner, SourceDocument aDocument, String aDataOwner,
            S suggestion, LearningRecordChangeLocation aAction)
        throws AnnotationException;

    void skipSuggestion(String aSessionOwner, SourceDocument aDocument, String aDataOwner,
            S suggestion, LearningRecordChangeLocation aAction)
        throws AnnotationException;
}
