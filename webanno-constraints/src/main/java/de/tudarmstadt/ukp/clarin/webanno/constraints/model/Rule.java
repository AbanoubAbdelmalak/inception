/*
 * Copyright 2015
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
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
package de.tudarmstadt.ukp.clarin.webanno.constraints.model;

import java.io.Serializable;
import java.util.List;

/**
 * Class representing object representation of a Rule, contains Condition(s) and Restriction(s)
 * 
 *
 */
public class Rule
    implements Serializable
{

    private static final long serialVersionUID = 5230339537568449002L;
    private final List<Condition> conditions;
    private final List<Restriction> restrictions;

    public Rule(List<Condition> aConditions, List<Restriction> aRestrictions)
    {
        conditions = aConditions;
        restrictions = aRestrictions;
    }

    public List<Condition> getConditions()
    {
        return conditions;
    }

    public List<Restriction> getRestrictions()
    {
        return restrictions;
    }

    @Override
    public String toString()
    {
        return "Rule [conditions=" + conditions + ", restrictions=" + restrictions + "]";
    }
}
