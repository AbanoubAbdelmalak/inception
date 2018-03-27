/*
 * Copyright 2017
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
package de.tudarmstadt.ukp.inception.ui.kb;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.inception.kb.KnowledgeBase;
import de.tudarmstadt.ukp.inception.kb.KnowledgeBaseService;
import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;
import de.tudarmstadt.ukp.inception.kb.graph.KBInstance;
import de.tudarmstadt.ukp.inception.ui.kb.event.AjaxInstanceSelectionEvent;
import de.tudarmstadt.ukp.inception.ui.kb.stmt.StatementGroupBean;

public class InstanceInfoPanel extends AbstractInfoPanel<KBInstance> {

    private static final long serialVersionUID = 7894987557444275022L;
    
    private static final Set<String> IMPORTANT_INSTANCE_URIS = new HashSet<>(
            Arrays.asList("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));

    private @SpringBean KnowledgeBaseService kbService;    

    public InstanceInfoPanel(String aId, IModel<KnowledgeBase> aKbModel,
            IModel<KBHandle> selectedInstanceHandle, IModel<KBInstance> selectedInstanceModel) {
        super(aId, aKbModel, selectedInstanceHandle, selectedInstanceModel);
    }

    @Override
    protected void actionCreate(AjaxRequestTarget aTarget, Form<KBInstance> aForm) {
        KBInstance instance = kbObjectModel.getObject();

        assert isEmpty(instance.getIdentifier());
        KBHandle handle = kbService.createInstance(kbModel.getObject(), instance);

        // select newly created property right away to show the statements
        send(getPage(), Broadcast.BREADTH, new AjaxInstanceSelectionEvent(aTarget, handle));
    }

    @Override
    protected void actionDelete(AjaxRequestTarget aTarget) {
        kbService.deleteInstance(kbModel.getObject(), kbObjectModel.getObject());
        kbObjectModel.setObject(null);

        // send deselection event
        send(getPage(), Broadcast.BREADTH, new AjaxInstanceSelectionEvent(aTarget, null));
    }

    @Override
    protected void actionCancel(AjaxRequestTarget aTarget) {
        kbObjectModel.setObject(null);

        // send deselection event
        send(getPage(), Broadcast.BREADTH, new AjaxInstanceSelectionEvent(aTarget, null));
    }
    
    @Override
    protected String getTypeLabelResourceKey() {
        return "instance";
    }

    @Override
    protected String getNamePlaceholderResourceKey() {
        return "instance.new.placeholder";
    }

    protected Comparator<StatementGroupBean> getStatementGroupComparator() {
        return new ImportantStatementComparator(
            sgb -> IMPORTANT_INSTANCE_URIS.contains(sgb.getProperty().getIdentifier()));
    }

}
