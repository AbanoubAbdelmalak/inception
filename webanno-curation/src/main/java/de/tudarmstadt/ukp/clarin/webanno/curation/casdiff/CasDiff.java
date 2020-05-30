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
package de.tudarmstadt.ukp.clarin.webanno.curation.casdiff;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.RELATION_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.SPAN_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectFsByAddr;
import static de.tudarmstadt.ukp.clarin.webanno.model.LinkMode.NONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.SofaFS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.FSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.RelationAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.api.DiffAdapter;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.api.DiffAdapter_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.api.Position;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.internal.AID;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.relation.RelationDiffAdapter;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.span.SpanDiffAdapter;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;

public class CasDiff
{
    private final static Logger LOG = LoggerFactory.getLogger(CasDiff.class);
    
    private Map<String, List<CAS>> cases = new LinkedHashMap<>();
    
    private final Map<Position, ConfigurationSet> configSets = new TreeMap<>();

    private final Map<String, String[]> sortedFeaturesCache = new HashMap<>();

    private int begin;
    
    private int end;
    
    private final Map<String, DiffAdapter> typeAdapters = new HashMap<>();
    
    private final LinkCompareBehavior linkCompareBehavior;

    private boolean recurseIntoLinkFeatures = false;
    
    private CasDiff(int aBegin, int aEnd, Iterable<? extends DiffAdapter> aAdapters,
            LinkCompareBehavior aLinkCompareBehavior)
    {
        begin = aBegin;
        end = aEnd;
        linkCompareBehavior = aLinkCompareBehavior;
        if (aAdapters != null) {
            for (DiffAdapter adapter : aAdapters) {
                typeAdapters.put(adapter.getType(), adapter);
            }
        }
    }

    /**
     * Calculate the differences between CASes. This method scopes the calculation of differences to
     * a span instead of calculating them on the whole text.
     * 
     * @param aAdapters
     *            a set of diff adapters telling how the diff algorithm should handle different
     *            features
     * @param aCasMap
     *            a set of CASes, each associated with an ID
     * @param aBegin
     *            begin of the span for which differences should be calculated.
     * @param aEnd
     *            end of the span for which differences should be calculated.
     * @return a diff result.
     */
    public static CasDiff doDiffSingle(Iterable<? extends DiffAdapter> aAdapters,
            LinkCompareBehavior aLinkCompareBehavior, Map<String, CAS> aCasMap, int aBegin,
            int aEnd)
    {
        Map<String, List<CAS>> casMap = new LinkedHashMap<>();
        for (Entry<String, CAS> e : aCasMap.entrySet()) {
            casMap.put(e.getKey(), asList(e.getValue()));
        }
        
        return doDiff(aAdapters, aLinkCompareBehavior, casMap, aBegin, aEnd);
    }

    /**
     * Calculate the differences between CASes.
     * 
     * @param aAdapters
     *            a set of diff adapters how the diff algorithm should handle different features
     * @param aCasMap
     *            a set of CASes, each associated with an ID
     * @return a diff result.
     */
    public static CasDiff doDiff(Iterable<? extends DiffAdapter> aAdapters,
            LinkCompareBehavior aLinkCompareBehavior, Map<String, List<CAS>> aCasMap)
    {
        return doDiff(aAdapters, aLinkCompareBehavior, aCasMap, -1, -1);
    }

    /**
     * Calculate the differences between CASes. This method scopes the calculation of differences to
     * a span instead of calculating them on the whole text.
     * 
     * @param aAdapters
     *            a set of diff adapters telling how the diff algorithm should handle different
     *            features
     * @param aCasMap
     *            a set of CASes, each associated with an ID
     * @param aBegin
     *            begin of the span for which differences should be calculated.
     * @param aEnd
     *            end of the span for which differences should be calculated.
     * @return a diff.
     */
    public static CasDiff doDiff(Iterable<? extends DiffAdapter> aAdapters,
            LinkCompareBehavior aLinkCompareBehavior, Map<String, List<CAS>> aCasMap, int aBegin,
            int aEnd)
    {
        if (aCasMap.isEmpty()) {
            return new CasDiff(0, 0, aAdapters, aLinkCompareBehavior);
        }
        
        List<CAS> casList = aCasMap.values().iterator().next();
        if (casList.isEmpty()) {
            return new CasDiff(0, 0, aAdapters, aLinkCompareBehavior);
        }
        
        long startTime = System.currentTimeMillis();
        
        sanityCheck(aCasMap);
        
        CasDiff diff = new CasDiff(aBegin, aEnd, aAdapters, aLinkCompareBehavior);
        
        for (Entry<String, List<CAS>> e : aCasMap.entrySet()) {
            int casId = 0;
            for (CAS cas : e.getValue()) {
                for (DiffAdapter adapter : aAdapters) {
                    // null elements in the list can occur if a user has never worked on a CAS
                    diff.addCas(e.getKey(), casId, cas != null ? cas : null, adapter.getType());
                }
                casId++;
            }
        }
        
        LOG.trace("CASDiff completed in {} ms", System.currentTimeMillis() - startTime);
        
        return diff;
    }
    
    /**
     * Sanity check - all CASes should have the same text.
     */
    private static void sanityCheck(Map<String, List<CAS>> aCasMap)
    {
        if (aCasMap.isEmpty()) {
            return;
        }
        
        // little hack to check if asserts are enabled
        boolean assertsEnabled = false;
        assert assertsEnabled = true; // Intentional side effect!
        if (assertsEnabled) {
            Iterator<Entry<String, List<CAS>>> i = aCasMap.entrySet().iterator();
            
            Entry<String, List<CAS>> ref = i.next();
            String refUser = ref.getKey();
            List<CAS> refCASes = ref.getValue();
            while (i.hasNext()) {
                Entry<String, List<CAS>> cur = i.next();
                String curUser = cur.getKey();
                List<CAS> curCASes = cur.getValue();
                assert refCASes.size() == curCASes.size() : "CAS list sizes differ: "
                        + refCASes.size() + " vs " + curCASes.size();
                for (int n = 0; n < refCASes.size(); n++) {
                    CAS refCas = refCASes.get(n);
                    CAS curCas = curCASes.get(n);
                    // null elements in the list can occur if a user has never worked on a CAS
                    assert !(refCas != null && curCas != null) || StringUtils.equals(
                            refCas.getDocumentText(),
                            curCas.getDocumentText()) : "Trying to compare CASes with different document texts: ["
                                    + curUser + "] having ["
                                    + abbreviateMiddle(curCas.getDocumentText(), "...", 40)
                                    + "] (length: " + curCas.getDocumentText().length() + ") vs ["
                                    + refUser + "] having ["
                                    + abbreviateMiddle(refCas.getDocumentText(), "...", 40)
                                    + "] (length: " + refCas.getDocumentText().length() + ")";
                }
            }
        }
        // End sanity check
    }
    
    private DiffAdapter getAdapter(String aType)
    {
        DiffAdapter adapter = typeAdapters.get(aType);
        if (adapter == null) {
            LOG.warn("No diff adapter for type [" + aType + "] -- treating as without features");
            adapter = new SpanDiffAdapter(aType, emptySet());
            typeAdapters.put(aType, adapter);
        }
        return adapter;
    }
    
    public Map<String, DiffAdapter> getTypeAdapters()
    {
        return typeAdapters;
    }
    
    /**
     * CASes are added to the diff one after another, building the diff iteratively. A CAS can be
     * added multiple times for different types. Make sure a CAS is not added twice with the same
     * type!
     * 
     * @param aCasGroupId
     *            the ID of the CAS group to add.
     * @param aCas
     *            the CAS itself.
     * @param aType
     *            the type on which to calculate the diff.
     */
    private void addCas(String aCasGroupId, int aCasId, CAS aCas, String aType)
    {
        // Remember that we have already seen this CAS.
        List<CAS> casList = cases.get(aCasGroupId);
        if (casList == null) {
            casList = new ArrayList<>();
            cases.put(aCasGroupId, casList);
        }
        
        // Avoid adding same CAS twice in cases where we add multiple types from a CAS. If the
        // current CAS ID is greater than the size of the current CAS list, then we did not add
        // it yet. Before, we checked whether the casList already contained the current CAS, but
        // that failed when we had multiple "null" CASes.
        if ((casList.size() - 1) < aCasId) {
            casList.add(aCas);
        }
        assert (casList.size() - 1) == aCasId : "Expected CAS ID [" + (casList.size() - 1)
                + "] but was [" + aCasId + "]";
        
        // null elements in the list can occur if a user has never worked on a CAS
        // We add these to the internal list above, but then we bail out here.
        if (aCas == null) {
            LOG.debug("CAS group [" + aCasGroupId + "] does not contain a CAS at index [" + aCasId
                    + "].");
            return;
        }
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing CAS group [" + aCasGroupId + "] CAS [" + aCasId
                    + "].");
            
            String collectionId = null;
            String documentId = null;
            try {
                FeatureStructure dmd = WebAnnoCasUtil.getDocumentMetadata(aCas);
                collectionId = FSUtil.getFeature(dmd, "collectionId", String.class);
                documentId = FSUtil.getFeature(dmd, "documentId", String.class);
                LOG.debug("User [" + collectionId + "] - Document [" + documentId + "]");
            }
            catch (IllegalArgumentException e) {
                // We use this information only for debugging - so we can ignore if the information
                // is missing.
            }
        }
        
        Collection<AnnotationFS> annotations;
        if (begin == -1 && end == -1) {
            annotations = select(aCas, getType(aCas, aType));
        }
        else {
            annotations = selectCovered(aCas, getType(aCas, aType), begin, end);
        }
        
        if (annotations.isEmpty()) {
            LOG.debug("CAS group [" + aCasGroupId + "] CAS [" + aCasId
                    + "] contains no annotations of type [" + aType + "]");
            return;
        }
        else {
            LOG.debug("CAS group [" + aCasGroupId + "] CAS [" + aCasId + "] contains ["
                    + annotations.size() + "] annotations of type [" + aType + "]");
        }

        int posBefore = configSets.keySet().size();
        LOG.debug("Positions before: [" + posBefore + "]");

        for (AnnotationFS fs : annotations) {
            List<Position> positions = new ArrayList<>();
            
            // Get/create configuration set at the current position
            positions.add(getAdapter(aType).getPosition(aCasId, fs));
            
            // Generate secondary positions for multi-link features
            positions.addAll(
                    getAdapter(aType).generateSubPositions(aCasId, fs, linkCompareBehavior));

            for (Position pos : positions) {
                ConfigurationSet configSet = configSets.get(pos);
                if (configSet == null) {
                    configSet = new ConfigurationSet(pos);
                    configSets.put(pos, configSet);
                }
                
    //          REC: appears to be left-over debug code that can be removed...  
    //            if (pos.getClass() != configSet.position.getClass()) {
    //                pos.compareTo(configSet.position);
    //            }
                
                assert pos.getClass() == configSet.position.getClass() : "Position type mismatch ["
                        + pos.getClass() + "] vs [" + configSet.position.getClass() + "]";
    
                // Merge FS into current set
                addConfiguration(configSet, aCasGroupId, fs);
            }
        }

        LOG.debug("Positions after: [" + configSets.keySet().size() + "] (delta: "
                + (configSets.keySet().size() - posBefore) + ")");

//        
//        // Remember that we have processed the type
//        entryTypes.add(aType);
    }
    
    private void addConfiguration(ConfigurationSet aSet, String aCasGroupId, FeatureStructure aFS)
    {
        if (aFS instanceof SofaFS) {
            return;
        }
        
        if (aSet.position.getFeature() == null) {
            // Check if this configuration is already present
            Configuration configuration = null;
            for (Configuration cfg : aSet.configurations) {
                // Handle main positions
                if (equalsFS(cfg.getRepresentative(cases), aFS)) {
                    configuration = cfg;
                    break;
                }
            }

            // Not found, add new one
            if (configuration == null) {
                configuration = new Configuration(aSet.position);
                aSet.configurations.add(configuration);
            }
            
            configuration.add(aCasGroupId, aFS);
        }
        else {
            // For each slot at the given position in the FS-to-be-added, we need find a
            // corresponding configuration
            ArrayFS links = (ArrayFS) aFS.getFeatureValue(aFS.getType().getFeatureByBaseName(
                    aSet.position.getFeature()));
            for (int i = 0; i < links.size(); i++) {
                FeatureStructure link = links.get(i);
                DiffAdapter adapter = getAdapter(aFS.getType().getName());
                LinkFeatureDecl decl = adapter.getLinkFeature(aSet.position.getFeature());
                
                // Check if this configuration is already present
                Configuration configuration = null;
                switch (aSet.position.getLinkCompareBehavior()) {
                case LINK_TARGET_AS_LABEL: {
                    String role = link.getStringValue(
                            link.getType().getFeatureByBaseName(decl.getRoleFeature()));
                    if (!role.equals(aSet.position.getRole())) {
                        continue;
                    }
                    
                    AnnotationFS target = (AnnotationFS) link.getFeatureValue(link.getType()
                            .getFeatureByBaseName(decl.getTargetFeature()));
                    
                    cfgLoop: for (Configuration cfg : aSet.configurations) {
                        FeatureStructure repFS = cfg.getRepresentative(cases);
                        AID repAID = cfg.getRepresentativeAID();
                        FeatureStructure repLink = ((ArrayFS) repFS.getFeatureValue(
                                repFS.getType().getFeatureByBaseName(decl.getName())))
                                        .get(repAID.index);
                        AnnotationFS repTarget = (AnnotationFS) repLink.getFeatureValue(repLink
                                .getType().getFeatureByBaseName(decl.getTargetFeature()));
                        
                        // Compare targets
                        if (equalsAnnotationFS(repTarget, target)) {
                            configuration = cfg;
                            break cfgLoop;
                        }
                    }
                    break;
                }
                case LINK_ROLE_AS_LABEL: {
                    AnnotationFS target = (AnnotationFS) link.getFeatureValue(link.getType()
                            .getFeatureByBaseName(decl.getTargetFeature()));
                    if (!(target.getBegin() == aSet.position.getLinkTargetBegin() && 
                            target.getEnd() == aSet.position.getLinkTargetEnd())) {
                        continue;
                    }
                    
                    String role = link.getStringValue(link.getType().getFeatureByBaseName(
                            decl.getRoleFeature()));
                    
                    cfgLoop: for (Configuration cfg : aSet.configurations) {
                        FeatureStructure repFS = cfg.getRepresentative(cases);
                        AID repAID = cfg.getRepresentativeAID();
                        FeatureStructure repLink = ((ArrayFS) repFS.getFeatureValue(
                                repFS.getType().getFeatureByBaseName(decl.getName())))
                                        .get(repAID.index);
                        String linkRole = repLink.getStringValue(repLink.getType()
                                .getFeatureByBaseName(decl.getRoleFeature()));
                        
                        // Compare roles
                        if (role.equals(linkRole)) {
                            configuration = cfg;
                            break cfgLoop;
                        }
                    }
                    break;
                }
                default:
                    throw new IllegalStateException("Unknown link target comparison mode ["
                            + linkCompareBehavior + "]");
                }
                
                // Not found, add new one
                if (configuration == null) {
                    configuration = new Configuration(aSet.position);
                    aSet.configurations.add(configuration);
                }
                
                configuration.add(aCasGroupId, aFS, aSet.position.getFeature(), i);
            }
        }

        aSet.casGroupIds.add(aCasGroupId);
    }

    
    /**
     * The set of configurations seen at a particular position.
     */
    public static class ConfigurationSet
        implements Serializable
    {
        private static final long serialVersionUID = -2820621316555472339L;
        
        private final Position position;
        private List<Configuration> configurations = new ArrayList<>();
        private Set<String> casGroupIds = new LinkedHashSet<>();
        
        public ConfigurationSet(Position aPosition)
        {
            position = aPosition;
        }
        
        /**
         * Gets the total number of configurations recorded in this set. If a configuration has been
         * seen in multiple CASes, it will be counted multiple times. 
         */
        public int getRecordedConfigurationCount()
        {
            int i = 0;
            for (Configuration cfg : configurations) {
                i += cfg.getAddressByCasId().size();
            }
            return i;
        }
        
        /**
         * @return the IDs of the CASes in which this configuration set has been observed.
         */
        public Set<String> getCasGroupIds()
        {
            return casGroupIds;
        }
                
        /**
         * @return the different configurations observed in this set.
         */
        public List<Configuration> getConfigurations()
        {
            return configurations;
        }
        
        /**
         * @param aCasGroupId
         *            a CAS ID
         * @return the different configurations observed in this set for the given CAS ID.
         */
        public List<Configuration> getConfigurations(String aCasGroupId)
        {
            List<Configuration> configurationsForUser = new ArrayList<>();
            for (Configuration cfg : configurations) {
                if (cfg.fsAddresses.keySet().contains(aCasGroupId)) {
                    configurationsForUser.add(cfg);
                }
            }
            return configurationsForUser;
        }
        
        /**
         * @return the position of this configuration set.
         */
        public Position getPosition()
        {
            return position;
        }
    }
    
    public Collection<Position> getPositions() {
        return configSets.keySet();
    }
    
    /**
     * @param aPosition a position.
     * @return the configuration set for the given position.
     */
    public ConfigurationSet getConfigurtionSet(Position aPosition)
    {
        return configSets.get(aPosition);
    }
    
    /**
     * Compare two feature structure to each other. Comparison is done recursively, but stops at
     * feature values that are annotations. For these, only offsets are checked, but feature values
     * are not inspected further. If the annotations are relevant, their type should be added to the
     * entry types and will then be checked and grouped separately.
     * 
     * @param aFS1
     *            first feature structure.
     * @param aFS2
     *            second feature structure.
     * @return {@code true} if they are equal.
     */
    private boolean equalsFS(FeatureStructure aFS1, FeatureStructure aFS2)
    {
        // Trivial case
        if (aFS1 == aFS2) {
            return true;
        }
        
        // Null check
        if (aFS1 == null || aFS2 == null) {
            return false;
        }

        // Trivial case
        if (aFS1.getCAS() == aFS2.getCAS() && getAddr(aFS1) == getAddr(aFS2)) {
            return true;
        }
        
        Type type1 = aFS1.getType();
        Type type2 = aFS2.getType();
        
        // Types must be the same
        if (!type1.getName().equals(type2.getName())) {
            return false;
        }

        assert type1.getNumberOfFeatures() == type2.getNumberOfFeatures();

        // Sort features by name to be independent over implementation details that may change the
        // order of the features as returned from Type.getFeatures().
        String[] cachedSortedFeatures = sortedFeaturesCache.get(type1.getName());
        if (cachedSortedFeatures == null) {
            cachedSortedFeatures = new String[type1.getNumberOfFeatures()];
            int i = 0;
            for (Feature f : aFS1.getType().getFeatures()) {
                cachedSortedFeatures[i] = f.getShortName();
                i++;
            }
            sortedFeaturesCache.put(type1.getName(), cachedSortedFeatures);
        }
        
        DiffAdapter adapter = typeAdapters.get(type1.getName());

        if (adapter == null) {
            LOG.warn("No diff adapter for type [" + type1.getName() + "] -- ignoring!");
            return true;
        }

        // Only consider label features. In particular these must not include position features
        // such as begin, end, etc.
        List<String> sortedFeatures = new ArrayList<>(asList(cachedSortedFeatures));
        Set<String> labelFeatures = adapter.getLabelFeatures();
        sortedFeatures.removeIf(f -> !labelFeatures.contains(f));

        if (!recurseIntoLinkFeatures ) {
            // #1795 Chili REC: We can/should change CasDiff2 such that it does not recurse into
            // link features (or rather into any features that are covered by their own
            // sub-positions). So when when comparing two spans that differ only in their slots
            // (sub-positions) the main position could still exhibit agreement.
            sortedFeatures.removeIf(f -> adapter.getLinkFeature(f) != null);
        }
        
        for (String feature : sortedFeatures) {
            Feature f1 = type1.getFeatureByBaseName(feature);
            Feature f2 = type2.getFeatureByBaseName(feature);
            
            switch (f1.getRange().getName()) {
            case CAS.TYPE_NAME_BOOLEAN:
                if (aFS1.getBooleanValue(f1) != aFS2.getBooleanValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_BYTE:
                if (aFS1.getByteValue(f1) != aFS2.getByteValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_DOUBLE:
                if (aFS1.getDoubleValue(f1) != aFS2.getDoubleValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_FLOAT:
                if (aFS1.getFloatValue(f1) != aFS2.getFloatValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_INTEGER:
                if (aFS1.getIntValue(f1) != aFS2.getIntValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_LONG:
                if (aFS1.getLongValue(f1) != aFS2.getLongValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_SHORT:
                if (aFS1.getShortValue(f1) != aFS2.getShortValue(f2)) {
                    return false;
                }
                break;
            case CAS.TYPE_NAME_STRING:
                if (!StringUtils.equals(aFS1.getStringValue(f1), aFS2.getStringValue(f2))) {
                    return false;
                }
                break;
            default: {
                // Must be some kind of feature structure then
                FeatureStructure valueFS1 = aFS1.getFeatureValue(f1);
                FeatureStructure valueFS2 = aFS2.getFeatureValue(f2);
                
                // Ignore the SofaFS - we already checked that the CAS is the same.
                if (valueFS1 instanceof SofaFS) {
                    continue;
                }
                
                // If the feature value is an annotation, we just check the position is the same,
                // but we do not go in deeper. If we we wanted to know differences on this type,
                // then it should have been added as an entry type.
                //
                // Q: Why do we not check if they are the same based on the CAS address?
                // A: Because we are checking across CASes and addresses can differ.
                //
                // Q: Why do we not check recursively?
                // A: Because e.g. for chains, this would mean we consider the whole chain as a 
                //    single annotation, but we want to consider each link as an annotation
                TypeSystem ts1 = aFS1.getCAS().getTypeSystem();
                if (ts1.subsumes(ts1.getType(CAS.TYPE_NAME_ANNOTATION), type1)) {
                    if (!equalsAnnotationFS((AnnotationFS) aFS1, (AnnotationFS) aFS2)) {
                        return false;
                    }
                }
                
                // If the feature type is not an annotation we are still in the "feature tier"
                // just dealing with structured features. It is ok to check these deeply.
                if (!equalsFS(valueFS1, valueFS2)) {
                    return false;
                }
            }
            }
        }
         
        return true;
    }
    
    private boolean equalsAnnotationFS(AnnotationFS aFS1, AnnotationFS aFS2)
    {
        // Null check
        if (aFS1 == null || aFS2 == null) {
            return false;
        }

        // Position check
        DiffAdapter adapter = getAdapter(aFS1.getType().getName());
        Position pos1 = adapter.getPosition(0, aFS1);
        Position pos2 = adapter.getPosition(0, aFS2);
        
        return pos1.compareTo(pos2) == 0;
    }
        
    /**
     * A single configuration seen at a particular position. The configuration may have been
     * observed in multiple CASes. 
     */
    public static class Configuration
        implements Serializable
    {
        private static final long serialVersionUID = 5387873327207575817L;
        
        private final Position position;
        private final Map<String, AID> fsAddresses = new TreeMap<>();
        
        /**
         * Flag indicating that there is at least once CAS group containing more than one annotation
         * at this position - i.e. a stacked annotation.
         */
        private boolean stacked = false;

        public Set<String> getCasGroupIds()
        {
            return fsAddresses.keySet();
        } 
        
        public Configuration(Position aPosition)
        {
            position = aPosition;
        }
        
        public Position getPosition()
        {
            return position;
        }
        
        public boolean isStacked()
        {
            return stacked;
        }

        private void add(String aCasGroupId, FeatureStructure aFS) {
            AID old = fsAddresses.put(aCasGroupId, new AID(getAddr(aFS)));
            if (old != null) {
                stacked = true;
            }
        }

        private void add(String aCasGroupId, FeatureStructure aFS, String aFeature, int aSlot) {
            AID old = fsAddresses.put(aCasGroupId, new AID(getAddr(aFS), aFeature, aSlot));
            if (old != null) {
                stacked = true;
            }
        }

        public AID getRepresentativeAID()
        {
            Entry<String, AID> e = fsAddresses.entrySet().iterator().next();
            return e.getValue();
        }
        
        public FeatureStructure getRepresentative(Map<String, List<CAS>> aCasMap)
        {
            Entry<String, AID> e = fsAddresses.entrySet().iterator().next();
            return selectFsByAddr(aCasMap.get(e.getKey()).get(position.getCasId()),
                    e.getValue().addr);
        }

        private Map<String, AID> getAddressByCasId()
        {
            return fsAddresses;
        }

        public AID getAID(String aCasGroupId)
        {
            return fsAddresses.get(aCasGroupId);
        }

        public <T extends FeatureStructure> FeatureStructure getFs(String aCasGroupId, int aCasId,
                Class<T> aClass, Map<String, List<CAS>> aCasMap)
        {
            AID aid = fsAddresses.get(aCasGroupId);
            if (aid == null) {
                return null;
            }
            
            List<CAS> casses = aCasMap.get(aCasGroupId);
            if (casses == null) {
                return null;
            }
            
            CAS cas = casses.get(aCasId);
            if (cas == null) {
                return null;
            }
            
            return selectFsByAddr(cas, aid.addr);
        }

        // FIXME aCasId parameter should not be required as we can get it from the position
        public FeatureStructure getFs(String aCasGroupId, int aCasId,
                Map<String, List<CAS>> aCasMap)
        {
            return getFs(aCasGroupId, aCasId, FeatureStructure.class, aCasMap);
        }

        public FeatureStructure getFs(String aCasGroupId, Map<String, CAS> aCasMap)
        {
            Map<String, List<CAS>> casMap = new LinkedHashMap<>();
            for (Entry<String, CAS> e : aCasMap.entrySet()) {
                casMap.put(e.getKey(), asList(e.getValue()));
            }
            return getFs(aCasGroupId, 0, FeatureStructure.class, casMap);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (Entry<String, AID> e : fsAddresses.entrySet()) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(e.getKey());
                sb.append(':');
                sb.append(e.getValue());
            }
            sb.append("] -> ");
            sb.append(getRepresentativeAID());
            return sb.toString();
        }
    }
    
    /**
     * A description of the differences between CASes.
     */
    public static class DiffResult
        implements Serializable
    {
        private static final long serialVersionUID = 5208017972858534258L;
        
        private final Map<Position, ConfigurationSet> data;
        private final Set<String> casGroupIds;
        private final Map<ConfigurationSet, Boolean> completenessCache = new HashMap<>();
        private final boolean cachedHasDifferences;
        
        private DiffResult(CasDiff aDiff)
        {
            data = Collections.unmodifiableMap(aDiff.configSets);
            casGroupIds = new LinkedHashSet<>(aDiff.cases.keySet());
            cachedHasDifferences = !getDifferingConfigurationSets().isEmpty();
        }
        
        public boolean hasDifferences()
        {
            return cachedHasDifferences;
        }
        
        public Collection<Position> getPositions() {
            return data.keySet();
        }
        
        public Collection<ConfigurationSet> getConfigurationSets()
        {
            return data.values();
        }
        
        /**
         * @param aPosition a position.
         * @return the configuration set for the given position.
         */
        public ConfigurationSet getConfigurtionSet(Position aPosition)
        {
            return data.get(aPosition);
        }
        
        /**
         * Determine if all CASes see agreed on the given configuration set. This method returns
         * {@code false} if there was disagreement (there are multiple configurations in the set).
         * When using this method, make sure you also take into account whether the set is
         * actually complete (cf. {@link #isComplete(ConfigurationSet)}.
         * 
         * @param aConfigurationSet
         *            a configuration set.
         * @return if all seen CASes agreed on this set.
         */
        public boolean isAgreement(ConfigurationSet aConfigurationSet)
        {
            if (data.get(aConfigurationSet.position) != aConfigurationSet) {
                throw new IllegalArgumentException(
                        "Configuration set does not belong to this diff or positions mismatch");
            }
            
            // If there is only a single configuration in the set, we call it an agreement
            if (aConfigurationSet.configurations.size() == 1) {
                return true;
            }

//          Issue 21 GitHub - REC - not really sure if we should call this an agreement            
//            // If there are multiple configurations in the set, we only call it an agreement if
//            // at least one of these configurations has been made by all annotators
//            for (Configuration cfg : aConfigurationSet.configurations) {
//                HashSet<String> unseenGroupCasIDs = new HashSet<>(casGroupIds);
//                unseenGroupCasIDs.removeAll(cfg.fsAddresses.keySet());
//                if (unseenGroupCasIDs.isEmpty()) {
//                    return true;
//                }
//            }
            
            return false;
        }
        
        /**
         * Determine if the given set has been observed in all CASes.
         * 
         * @param aConfigurationSet
         *            a configuration set.
         * @return if seen in all CASes.
         */
        public boolean isComplete(ConfigurationSet aConfigurationSet)
        {
            if (data.get(aConfigurationSet.position) != aConfigurationSet) {
                throw new IllegalArgumentException(
                        "Configuration set does not belong to this diff or positions mismatch");
            }

            Boolean complete = completenessCache.get(aConfigurationSet);
            if (complete == null) {
                HashSet<String> unseenGroupCasIDs = new HashSet<>(casGroupIds);
                for (Configuration cfg : aConfigurationSet.configurations) {
                    unseenGroupCasIDs.removeAll(cfg.fsAddresses.keySet());
                }
                complete = unseenGroupCasIDs.isEmpty();
                completenessCache.put(aConfigurationSet, complete);
            }
            
            return complete;
        }
        
        public Map<Position, ConfigurationSet> getDifferingConfigurationSets()
        {
            Map<Position, ConfigurationSet> diffs = new LinkedHashMap<>();
            for (Entry<Position, ConfigurationSet> e : data.entrySet()) {
                if (!isAgreement(e.getValue())) {
                    diffs.put(e.getKey(), e.getValue());
                }
            }
            
            return diffs;
        }

        public Map<Position, ConfigurationSet> getIncompleteConfigurationSets()
        {
            Map<Position, ConfigurationSet> diffs = new LinkedHashMap<>();
            for (Entry<Position, ConfigurationSet> e : data.entrySet()) {
                if (!isComplete(e.getValue())) {
                    diffs.put(e.getKey(), e.getValue());
                }
            }
            
            return diffs;
        }

        public int size()
        {
            return data.size();
        }

        public int size(String aType)
        {
            int n = 0;
            for (Position pos : data.keySet()) {
                if (pos.getType().equals(aType)) {
                    n++;
                }
            }
            
            return n;
        }
        
        public void print(PrintStream aOut)
        {
            for (Position p : getPositions()) {
                ConfigurationSet configurationSet = getConfigurtionSet(p);
                aOut.printf("=== %s -> %s %s%n", p, 
                        isAgreement(configurationSet) ? "AGREE" : "DISAGREE",
                        isComplete(configurationSet) ? "COMPLETE" : "INCOMPLETE");
                if (!isAgreement(configurationSet) || !isComplete(configurationSet)) {
                    for (Configuration cfg : configurationSet.getConfigurations()) {
                        aOut.println();
                        aOut.println(cfg);
                    }
                }
            }
        }
    }
    
    public static List<DiffAdapter> getDiffAdapters(AnnotationSchemaService schemaService,
            Iterable<AnnotationLayer> aLayers)
    {
        List<DiffAdapter> adapters = new ArrayList<>();
        nextLayer: for (AnnotationLayer layer : aLayers) {
            if (!layer.isEnabled()) {
                continue nextLayer;
            }
            
            Set<String> labelFeatures = new LinkedHashSet<>();
            nextFeature: for (AnnotationFeature f : schemaService.listSupportedFeatures(layer)) {
                if (!f.isEnabled()) {
                    continue nextFeature;
                }
                
                // Link features are treated separately from primitive label features
                if (!NONE.equals(f.getLinkMode())) {
                    continue nextFeature;
                }
                
                labelFeatures.add(f.getName());
            }
            
            DiffAdapter_ImplBase adapter;
            switch (layer.getType()) {
            case SPAN_TYPE: {
                adapter = new SpanDiffAdapter(layer.getName(), labelFeatures);
                break;
            }
            case RELATION_TYPE: {
                RelationAdapter typeAdpt = (RelationAdapter) schemaService.getAdapter(layer);
                adapter = new RelationDiffAdapter(layer.getName(),
                        typeAdpt.getSourceFeatureName(), typeAdpt.getTargetFeatureName(),
                        labelFeatures);
                break;
            }
            default:
                LOG.debug("Curation for layer type [{}] not supported - ignoring", layer.getType());
                continue nextLayer;
            }

            adapters.add(adapter);

            nextFeature: for (AnnotationFeature f : schemaService.listSupportedFeatures(layer)) {
                if (!f.isEnabled()) {
                    continue nextFeature;
                }
                
                switch (f.getLinkMode()) {
                case NONE:
                    // Nothing to do here
                    break;
                case SIMPLE:
                    adapter.addLinkFeature(f.getName(), f.getLinkTypeRoleFeatureName(), null);
                    break;
                case WITH_ROLE:
                    adapter.addLinkFeature(f.getName(), f.getLinkTypeRoleFeatureName(),
                            f.getLinkTypeTargetFeatureName());
                    break;
                default:
                    throw new IllegalStateException("Unknown link mode [" + f.getLinkMode() + "]");
                }
                
                labelFeatures.add(f.getName());
            }
        }
        return adapters;
    }
    
    public DiffResult toResult()
    {
        return new DiffResult(this);
    }

//  private Set<String> entryTypes = new LinkedHashSet<>();

//  /**
//   * Clear the attachment to CASes allowing the class to be serialized.
//   */
//  public void detach()
//  {
//      if (cases != null) {
//          cases.clear();
//      }
//  }
  
//  /**
//   * Rebuilds the diff with the current offsets and entry types. This can be used to fix the diff
//   * after reattaching to CASes that have changed. Mind that the diff results can be differnent
//   * due to the changes.
//   */
//  public void rebuild()
//  {
//      Map<String, CAS> oldCases = cases;
//      cases = new HashMap<>();
//      
//      for (String t : entryTypes) {
//          for (Entry<String, CAS> e : oldCases.entrySet()) {
//              addCas(e.getKey(), e.getValue(), t);
//          }
//      }
//  }
  
//  /**
//   * Attach CASes back so that representatives can be resolved. CASes must not have been changed
//   * or upgraded between detaching and reattaching - the CAS addresses of the feature structures
//   * must still be the same.
//   */
//  public void attach(Map<String, CAS> aCases)
//  {
//      cases = new HashMap<>(aCases);
//  }
}
