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
package de.tudarmstadt.ukp.inception.diam.model.compact;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.apache.uima.cas.CAS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.RenderRequest;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VAnnotationMarker;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VArc;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VSpan;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VTextMarker;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.inception.diam.editor.config.DiamAutoConfig;
import de.tudarmstadt.ukp.inception.diam.model.Offsets;
import de.tudarmstadt.ukp.inception.support.text.TextUtils;

/**
 * <p>
 * This class is exposed as a Spring Component via {@link DiamAutoConfig#compactSerializer}.
 * </p>
 */
public class CompactSerializerImpl
    implements CompactSerializer
{
    private static final Logger LOG = LoggerFactory.getLogger(CompactSerializerImpl.class);
    public static final String ID = "compact";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public CompactAnnotatedText render(VDocument aVDoc, RenderRequest aRequest)
    {
        CompactAnnotatedText aResponse = new CompactAnnotatedText();

        CAS aCas = aRequest.getCas();

        renderText(aCas, aResponse, aRequest);

        renderLayers(aResponse, aVDoc, aRequest);

        return aResponse;
    }

    private void renderLayers(CompactAnnotatedText aResponse, VDocument aVDoc,
            RenderRequest aRequest)
    {
        for (AnnotationLayer layer : aVDoc.getAnnotationLayers()) {
            for (VSpan vspan : aVDoc.spans(layer.getId())) {
                List<Offsets> offsets = vspan.getRanges().stream()
                        .map(range -> new Offsets(range.getBegin(), range.getEnd()))
                        .collect(toList());

                CompactSpan entity = new CompactSpan(vspan.getVid(), offsets, vspan.getLabelHint(),
                        vspan.getColorHint());
                entity.getAttributes()
                        .setClippedAtStart(vspan.getRanges().get(0).isClippedAtBegin());
                entity.getAttributes().setClippedAtEnd(
                        vspan.getRanges().get(vspan.getRanges().size() - 1).isClippedAtEnd());

                aResponse.addSpan(entity);
            }

            for (VArc varc : aVDoc.arcs(layer.getId())) {
                CompactRelation arc = new CompactRelation(varc.getVid(),
                        getArgument(varc.getSource(), varc.getTarget()), varc.getLabelHint(),
                        varc.getColorHint());
                aResponse.addRelation(arc);
            }
        }

        for (var marker : aVDoc.getMarkers()) {
            if (marker instanceof VAnnotationMarker) {
                aResponse.addAnnotationMarker(
                        new CompactAnnotationMarker((VAnnotationMarker) marker));
            }
            else if (marker instanceof VTextMarker) {
                aResponse.addTextMarker(new CompactTextMarker((VTextMarker) marker));
            }
        }
    }

    /**
     * Argument lists for the arc annotation
     */
    private List<CompactArgument> getArgument(VID aGovernorFs, VID aDependentFs)
    {
        return asList(new CompactArgument("Arg1", aGovernorFs),
                new CompactArgument("Arg2", aDependentFs));
    }

    private void renderText(CAS aCas, CompactAnnotatedText aResponse, RenderRequest aRequest)
    {
        if (!aRequest.isIncludeText()) {
            return;
        }

        int windowBegin = aRequest.getWindowBeginOffset();
        int windowEnd = aRequest.getWindowEndOffset();

        String visibleText = aCas.getDocumentText().substring(windowBegin, windowEnd);
        visibleText = TextUtils.sanitizeVisibleText(visibleText, '\uFFFD');
        aResponse.setText(visibleText);
    }
}