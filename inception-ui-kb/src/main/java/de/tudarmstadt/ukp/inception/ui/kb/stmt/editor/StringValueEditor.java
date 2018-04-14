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
package de.tudarmstadt.ukp.inception.ui.kb.stmt.editor;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.TextArea;

import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxFormComponentUpdatingBehavior;

public class StringValueEditor extends ValueEditor<String> {

    private static final long serialVersionUID = 6935837930064826698L;

    private TextArea<String> valueField;

    public StringValueEditor(String id) {
        super(id);
        valueField = new TextArea<String>("value");
        valueField.setOutputMarkupId(true);
        valueField.add(
                new LambdaAjaxFormComponentUpdatingBehavior("change", t -> t.add(getParent())));
        add(valueField);
    }

    @Override
    public void convertInput() {
        Object value = valueField.getValue();
        setConvertedInput((String) value);
    }

    @Override
    public Component getFocusComponent() {
        return valueField;
    }

}
