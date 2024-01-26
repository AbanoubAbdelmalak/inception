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
package de.tudarmstadt.ukp.inception.curation.merge.strategy;

import static de.tudarmstadt.ukp.inception.support.WebAnnoConst.CURATION_USER;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.Configuration;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.ConfigurationSet;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.DiffResult;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;

public class DefaultMergeStrategy
    implements MergeStrategy
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public List<Configuration> chooseConfigurationsToMerge(DiffResult aDiff, ConfigurationSet aCfgs,
            AnnotationLayer aLayer)
    {
        boolean stacked = aCfgs.getConfigurations().stream() //
                .filter(Configuration::isStacked) //
                .findAny().isPresent();

        if (stacked) {
            LOG.trace(" `-> Not merging stacked annotation");
            return emptyList();
        }

        if (!aDiff.isCompleteWithExceptions(aCfgs, CURATION_USER)) {
            LOG.trace(" `-> Not merging incomplete annotation");
            return emptyList();
        }

        if (!aDiff.isAgreementWithExceptions(aCfgs, CURATION_USER)) {
            LOG.trace(" `-> Not merging annotation with disagreement");
            return emptyList();
        }

        return asList(aCfgs.getConfigurations().get(0));
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE).toString();
    }
}
