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
package de.tudarmstadt.ukp.inception.revieweditor;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectFsByAddr;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FeatureStructure;
import org.apache.wicket.Component;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.TypeAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.editor.FeatureEditor;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.FeatureState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.LinkWithRoleModel;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.support.DescriptionTooltipBehavior;

public class SpanAnnotationPanel 
    extends Panel
{
    private static final long serialVersionUID = 7375798934091777439L;
    
    private final Logger LOG = LoggerFactory.getLogger(getClass());
    
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean FeatureSupportRegistry featureSupportRegistry;
    
    private static final String CID_TEXT = "text";
    private static final String CID_FEATURES_CONTAINER = "featuresContainer";
    private static final String CID_FEATURES = "features";
    private static final String CID_FEATURE = "feature";
    
    private final IModel<LinkWithRoleModel> model;
    private final CasProvider casProvider;
    private final Project project;
    private WebMarkupContainer featuresContainer;
    
    public SpanAnnotationPanel(String aId, IModel<LinkWithRoleModel> aModel,
        CasProvider aCasProvider, Project aProject)
    {
        super(aId, aModel);
        model = aModel;
        casProvider = aCasProvider;
        project = aProject;
    
        LinkWithRoleModel link = aModel.getObject();
    
        try {
            CAS cas = casProvider.get();
            FeatureStructure fs = selectFsByAddr(cas, link.targetAddr);
            VID vid = new VID(fs);
            AnnotationLayer layer = annotationService.findLayer(project, fs);
            
            List<FeatureState> features = listFeatures(fs, layer, vid);
    
            featuresContainer = new WebMarkupContainer(CID_FEATURES_CONTAINER);
            featuresContainer.setOutputMarkupId(true);
            featuresContainer.add(createFeaturesList(features));
            // TODO: there are probably better ways to get the text of an annotation?
            featuresContainer.add(new Label(CID_TEXT, link.label));
            
            add(featuresContainer);
        }
        catch (IOException e) {
            LOG.error("Unable to load CAS", e);
        }
    }
    
    private List<FeatureState> listFeatures(FeatureStructure aFs,
        AnnotationLayer aLayer, VID aVid)
    {
        
        TypeAdapter adapter = annotationService.getAdapter(aLayer);
    
        // Populate from feature structure
        List<FeatureState> featureStates = new ArrayList<>();
        for (AnnotationFeature feature : annotationService.listAnnotationFeature(aLayer)) {
            if (!feature.isEnabled()) {
                continue;
            }
        
            Serializable value = null;
            if (aFs != null) {
                value = adapter.getFeatureValue(feature, aFs);
            }
        
            FeatureState featureState = new FeatureState(aVid, feature, value);
            featureStates.add(featureState);
            featureState.tagset = annotationService.listTags(featureState.feature.getTagset());
        }
    
        return featureStates;
    }
    
    private ListView<FeatureState> createFeaturesList(List<FeatureState> features)
    {
        return new ListView<FeatureState>(CID_FEATURES, features)
        {
            private static final long serialVersionUID = -1139622234318691941L;
            
            @Override
            protected void populateItem(ListItem<FeatureState> item)
            {
                // Feature editors that allow multiple values may want to update themselves,
                // e.g. to add another slot.
                item.setOutputMarkupId(true);
                
                final FeatureState featureState = item.getModelObject();
                final FeatureEditor editor;
                FeatureSupport<Object> featureSupport = featureSupportRegistry
                    .getFeatureSupport(featureState.feature);
                editor = featureSupport.createEditor(CID_FEATURE,
                    SpanAnnotationPanel.this, null, null, item.getModel());
                editor.setEnabled(false);
                
                if (!featureState.feature.getLayer().isReadonly()) {
                    // Whenever it is updating an annotation, it updates automatically when a
                    // component for the feature lost focus - but updating is for every component
                    // edited LinkFeatureEditors must be excluded because the auto-update will break
                    // the ability to add slots. Adding a slot is NOT an annotation action.
                    //                    addAnnotateActionBehavior(editor);
                    
                    // Add tooltip on label
                    StringBuilder tooltipTitle = new StringBuilder();
                    tooltipTitle.append(featureState.feature.getUiName());
                    if (featureState.feature.getTagset() != null) {
                        tooltipTitle.append(" (");
                        tooltipTitle.append(featureState.feature.getTagset().getName());
                        tooltipTitle.append(')');
                    }
                    
                    Component labelComponent = editor.getLabelComponent();
                    labelComponent.add(new AttributeAppender("style", "cursor: help", ";"));
                    labelComponent.add(new DescriptionTooltipBehavior(tooltipTitle.toString(),
                        featureState.feature.getDescription()));
                }
                else {
                    editor.getFocusComponent().setEnabled(false);
                }
                
                // We need to enable the markup ID here because we use it during the AJAX behavior
                // that automatically saves feature editors on change/blur.
                // Check addAnnotateActionBehavior.
                editor.setOutputMarkupId(true);
                editor.setOutputMarkupPlaceholderTag(true);
                
                // Ensure that markup IDs of feature editor focus components remain constant
                // across refreshes of the feature editor panel. This is required to restore the
                // focus.
                editor.getFocusComponent().setOutputMarkupId(true);
                editor.getFocusComponent().setMarkupId(
                    "" + editor.getModelObject().feature.getId());
                
                item.add(editor);
            }
        };
    }
}
