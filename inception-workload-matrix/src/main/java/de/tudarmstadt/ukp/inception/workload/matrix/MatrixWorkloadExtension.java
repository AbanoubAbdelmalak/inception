/*
 * Copyright 2020
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
package de.tudarmstadt.ukp.inception.workload.matrix;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import de.tudarmstadt.ukp.clarin.webanno.support.JSONUtil;
import de.tudarmstadt.ukp.inception.workload.extension.WorkloadManagerExtension;
import de.tudarmstadt.ukp.inception.workload.matrix.config.MatrixWorkloadManagerAutoConfiguration;
import de.tudarmstadt.ukp.inception.workload.model.WorkloadManager;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link MatrixWorkloadManagerAutoConfiguration#matrixWorkloadExtension}
 * </p>
 */
@Order(-10)
public class MatrixWorkloadExtension<T>
    implements WorkloadManagerExtension<T>
{
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static final String MATRIX_WORKLOAD_MANAGER_EXTENSION_ID = "matrix";

    @Override
    public String getId()
    {
        return MATRIX_WORKLOAD_MANAGER_EXTENSION_ID;
    }

    @Override
    public String getLabel()
    {
        return "Static assignment";
    }

    @Override
    public T readTraits(WorkloadManager aWorkloadManager)
    {
        T traits = null;

        try {
            traits = JSONUtil.fromJsonString((Class<T>) createTraits().getClass(),
                    aWorkloadManager.getTraits());
        }
        catch (IOException var4) {
            this.log.error("Unable to read traits", var4);
        }

        if (traits == null) {
            traits = this.createTraits();
        }

        return traits;
    }

    @Override
    public void writeTraits(WorkloadManager aWorkloadManager, T aWorkloadTrait)
    {
        try {
            aWorkloadManager.setTraits(JSONUtil.toJsonString(aWorkloadTrait));
        }
        catch (Exception e) {
            this.log.error("Unable to write traits", e);
        }

    }
}
