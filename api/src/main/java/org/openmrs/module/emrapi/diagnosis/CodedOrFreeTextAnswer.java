/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.emrapi.diagnosis;

import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.util.OpenmrsUtil;

import java.util.List;
import java.util.Locale;

/**
 * Class representing a value that can be either coded (as a Concept or a more specific ConceptName) or non-coded.
 */
public class CodedOrFreeTextAnswer {

    public static final String CONCEPT_NAME_PREFIX = "ConceptName:";
    public static final String CONCEPT_PREFIX = "Concept:";
    public static final String NON_CODED_PREFIX = "Non-Coded:";

    Concept codedAnswer;

    ConceptName specificCodedAnswer;

    String nonCodedAnswer;

    public CodedOrFreeTextAnswer(String spec, ConceptService conceptService) {
        if (spec.startsWith(CONCEPT_NAME_PREFIX)) {
            String conceptNameId = spec.substring(CONCEPT_NAME_PREFIX.length());
            setSpecificCodedAnswer(conceptService.getConceptName(Integer.valueOf(conceptNameId)));
        } else if (spec.startsWith(CONCEPT_PREFIX)) {
            String conceptId = spec.substring(CONCEPT_PREFIX.length());
            setCodedAnswer(conceptService.getConcept(Integer.valueOf(conceptId)));
        } else if (spec.startsWith(NON_CODED_PREFIX)) {
            setNonCodedAnswer(spec.substring(NON_CODED_PREFIX.length()));
        } else {
            throw new IllegalArgumentException("Unknown format: " + spec);
        }
    }

    public CodedOrFreeTextAnswer(Concept codedAnswer) {
        this.codedAnswer = codedAnswer;
    }

    public CodedOrFreeTextAnswer(ConceptName specificCodedAnswer) {
        this.specificCodedAnswer = specificCodedAnswer;
        this.codedAnswer = specificCodedAnswer.getConcept();
    }

    public CodedOrFreeTextAnswer(String nonCodedAnswer) {
        this.nonCodedAnswer = nonCodedAnswer;
    }

    public Concept getCodedAnswer() {
        return codedAnswer;
    }

    public void setCodedAnswer(Concept codedAnswer) {
        this.codedAnswer = codedAnswer;
    }

    public ConceptName getSpecificCodedAnswer() {
        return specificCodedAnswer;
    }

    public void setSpecificCodedAnswer(ConceptName specificCodedAnswer) {
        this.specificCodedAnswer = specificCodedAnswer;
        this.codedAnswer = specificCodedAnswer.getConcept();
    }

    public String getNonCodedAnswer() {
        return nonCodedAnswer;
    }

    public void setNonCodedAnswer(String nonCodedAnswer) {
        this.nonCodedAnswer = nonCodedAnswer;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CodedOrFreeTextAnswer)) {
            return false;
        }
        CodedOrFreeTextAnswer other = (CodedOrFreeTextAnswer) o;
        return OpenmrsUtil.nullSafeEquals(codedAnswer, other.codedAnswer) &&
                OpenmrsUtil.nullSafeEquals(specificCodedAnswer, other.specificCodedAnswer) &&
                OpenmrsUtil.nullSafeEquals(nonCodedAnswer, other.nonCodedAnswer);
    }

    /**
     * Format as either:
     * <ul>
     * <li>non-coded value</li>
     * <li>coded value's preferred name in the current locale</li>
     * </ul>
     * (Does not show the specific answer, even if it is set.)
     *
     * @param locale
     * @return
     */
    public String formatWithoutSpecificAnswer(Locale locale) {
        if (nonCodedAnswer != null) {
            return nonCodedAnswer;
        } else if (codedAnswer == null) {
            return "?";
        } else {
            return codedAnswer.getName(locale).getName();
        }
    }

    /**
     * Formats as either of:
     * <ul>
     * <li>non-coded value</li>
     * <li>coded value's preferred name in the current locale</li>
     * <li>specific coded value → coded value's preferred name in the current locale</li>
     * </ul>
     *
     * @param locale
     * @return
     */
    public String format(Locale locale) {
        if (nonCodedAnswer != null) {
            return nonCodedAnswer;
        } else if (codedAnswer == null) {
            return "?";
        } else if (specificCodedAnswer == null) {
            return codedAnswer.getName(locale).getName();
        } else {
            if (specificCodedAnswer.isLocalePreferred() && specificCodedAnswer.getLocale().equals(locale)) {
                return specificCodedAnswer.getName();
            }
            ConceptName preferredName = codedAnswer.getName(locale);
            if (preferredName == null || preferredName.equals(specificCodedAnswer)) {
                return specificCodedAnswer.getName();
            } else {
                return specificCodedAnswer.getName() + " → " + preferredName.getName();
            }
        }
    }

    /**
     * Like {link #format(Locale)} but if this is a concept, and is mapped to a term in the given fromSources, append that term's code
     *
     * @param locale
     * @param codeFromSources
     * @return
     */
    public String formatWithCode(Locale locale, List<ConceptSource> codeFromSources) {
        if (codedAnswer == null) {
            return format(locale);
        } else {
            String formatted = format(locale);
            ConceptReferenceTerm mappedTerm = getBestMapping(codedAnswer, codeFromSources);
            return mappedTerm == null ? formatted : formatted + " [" + mappedTerm.getCode() + "]";
        }
    }

    /**
     * Tries to get a SAME-AS mapping. If none exists, looks for a NARROWER-THAN.
     *
     * @param concept
     * @param fromSources
     * @return
     */
    private ConceptReferenceTerm getBestMapping(Concept concept, List<ConceptSource> fromSources) {
        ConceptReferenceTerm nextBest = null;
        for (ConceptMap candidate : concept.getConceptMappings()) {
            if (fromSources.contains(candidate.getConceptReferenceTerm().getConceptSource())) {
                if (candidate.getConceptMapType().getUuid().equals(EmrApiConstants.SAME_AS_CONCEPT_MAP_TYPE_UUID)) {
                    return candidate.getConceptReferenceTerm();
                } else if (candidate.getConceptMapType().getUuid().equals(EmrApiConstants.NARROWER_THAN_CONCEPT_MAP_TYPE_UUID)) {
                    nextBest = candidate.getConceptReferenceTerm();
                }
            }
        }
        return nextBest;
    }

}
