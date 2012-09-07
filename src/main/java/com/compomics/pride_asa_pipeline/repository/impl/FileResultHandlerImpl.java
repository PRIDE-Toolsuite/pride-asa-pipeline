/*
 *

 */
package com.compomics.pride_asa_pipeline.repository.impl;

import com.compomics.pride_asa_pipeline.config.PropertiesConfigurationHolder;
import com.compomics.pride_asa_pipeline.model.AminoAcidSequence;
import com.compomics.pride_asa_pipeline.model.AnnotationData;
import com.compomics.pride_asa_pipeline.model.FragmentIonAnnotation;
import com.compomics.pride_asa_pipeline.model.Identification;
import com.compomics.pride_asa_pipeline.model.IdentificationScore;
import com.compomics.pride_asa_pipeline.model.Modification;
import com.compomics.pride_asa_pipeline.model.ModificationFacade;
import com.compomics.pride_asa_pipeline.model.ModifiedPeptide;
import com.compomics.pride_asa_pipeline.model.Peptide;
import com.compomics.pride_asa_pipeline.model.PipelineExplanationType;
import com.compomics.pride_asa_pipeline.model.SpectrumAnnotatorResult;
import com.compomics.pride_asa_pipeline.model.UnknownAAException;
import com.compomics.pride_asa_pipeline.model.comparator.FragmentIonAnnotationComparator;
import com.compomics.pride_asa_pipeline.repository.FileResultHandler;
import com.compomics.pride_asa_pipeline.service.ModificationService;
import com.compomics.pride_asa_pipeline.util.MathUtils;
import com.compomics.pride_asa_pipeline.util.ResourceUtils;
import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jdom2.JDOMException;

/**
 *
 * @author Niels Hulstaert
 */
public class FileResultHandlerImpl implements FileResultHandler {

    private static final Logger LOGGER = Logger.getLogger(FileResultHandlerImpl.class);
    private static final String COLUMN_DELIMITER = "\t";
    private static final String SCORE_OPEN = "(";
    private static final String SCORE_CLOSE = ")";
    private static final String SCORE_DELIMITER = ";";
    private static final String FRAGMENT_IONS_OPEN = "ions[";
    private static final String FRAGMENT_IONS_CLOSE = "]";
    private static final String FRAGMENT_ION_TYPE_DELIMITER = ";";
    private static final String FRAGMENT_ION_TYPE_CHARGE_DELIMITER = "_";
    private static final String FRAGMENT_ION_NUMBER_DELIMITER = "|";
    private static final String FRAGMENT_ION_PEAK_VALUES_OPEN = "{";
    private static final String FRAGMENT_ION_PEAK_VALUES_CLOSE = "}";
    private static final String FRAGMENT_ION_PEAK_VALUES_DELIMITER = ":";
    private static final String FRAGMENT_ION_NUMBERS_OPEN = "(";
    private static final String FRAGMENT_ION_NUMBERS_CLOSE = ")";
    private static final String MODIFICATIONS_DELIMITER = ";";
    private static final String MODIFICATIONS_OPEN = "mods[";
    private static final String MODIFICATIONS_CLOSE = "]";
    private static final String MODIFICATIONS_N_TERMINAL = "NT";
    private static final String MODIFICATIONS_C_TERMINAL = "CT";
    private static final String MODIFICATIONS_LOCATION_DELIMITER = "_";
    private static final String NOT_AVAILABLE = "N/A";
    private static final int SPECTRUM_ID = 0;
    private static final int PEPTIDE_ID = 1;
    private static final int PEPTIDE_SEQUENCE = 2;
    private static final int PRECURSOR_MZ = 3;
    private static final int PRECURSOR_CHARGE = 4;
    private static final int EXPLANATION = 5;
    private static final int NOISE_THRESHOLD = 6;
    private static final int SCORE = 7;
    private static final int FRAGMENT_IONS = 8;
    private static final int MODIFICATIONS = 9;
    private ModificationService modificationService;
    private Map<String, Modification> modifications;

    public ModificationService getModificationService() {
        return modificationService;
    }

    public void setModificationService(ModificationService modificationService) {
        this.modificationService = modificationService;
    }

    @Override
    public void writeResult(File resultFile, List<Identification> identifications) {
        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(resultFile)));

            //print identification header
            pw.println("spectrum_id" + COLUMN_DELIMITER
                    + "peptide_id" + COLUMN_DELIMITER
                    + "peptide_sequence" + COLUMN_DELIMITER
                    + "precursor_mz" + COLUMN_DELIMITER
                    + "precursor_charge" + COLUMN_DELIMITER
                    + "explanation" + COLUMN_DELIMITER
                    + "noise_threshold" + COLUMN_DELIMITER
                    + "annotation_score" + COLUMN_DELIMITER
                    + "fragment_ions" + COLUMN_DELIMITER
                    + "modifications");

            //print identification
            for (Identification identification : identifications) {
                String fragmentIonsString = NOT_AVAILABLE;
                String modificationsString = NOT_AVAILABLE;
                String annotationScoreString = NOT_AVAILABLE;
                String noiseThresholdString = NOT_AVAILABLE;
                //check if there are fragment ion annotations
                if (identification.getAnnotationData() != null) {
                    if (identification.getAnnotationData().getIdentificationScore() != null) {
                        annotationScoreString = constructScore(identification.getAnnotationData().getIdentificationScore());
                        noiseThresholdString = Double.toString(MathUtils.roundDouble(identification.getAnnotationData().getNoiseThreshold()));
                        if (identification.getAnnotationData().getFragmentIonAnnotations() != null) {
                            fragmentIonsString = constructFragmentIons(identification.getAnnotationData().getFragmentIonAnnotations());
                        }
                    }
                }

                //check if there are modifications
                if (identification.getPeptide() instanceof ModifiedPeptide) {
                    modificationsString = constructModifications((ModifiedPeptide) identification.getPeptide());
                }

                pw.print(identification.getSpectrumId()
                        + COLUMN_DELIMITER + identification.getPeptide().getPeptideId()
                        + COLUMN_DELIMITER + identification.getPeptide().getSequenceString()
                        + COLUMN_DELIMITER + MathUtils.roundDouble(identification.getPeptide().getMzRatio())
                        + COLUMN_DELIMITER + identification.getPeptide().getCharge()
                        + COLUMN_DELIMITER + identification.getPipelineExplanationType().toString()
                        + COLUMN_DELIMITER + noiseThresholdString
                        + COLUMN_DELIMITER + annotationScoreString
                        + COLUMN_DELIMITER + fragmentIonsString
                        + COLUMN_DELIMITER + modificationsString);

                pw.println();
            }

            pw.close();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public SpectrumAnnotatorResult readResult(File resultFile) {
        SpectrumAnnotatorResult spectrumAnnotatorResult = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(resultFile));

            String experimentAccession = resultFile.getName().substring(0, resultFile.getName().lastIndexOf(".txt"));
            LOGGER.info("Start reading pipeline result file for experiment " + experimentAccession);
            spectrumAnnotatorResult = new SpectrumAnnotatorResult(experimentAccession);
            //load modifications if necessary
            if (modifications == null) {
                loadModifications();
            }

            String line = null;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("spectrum_id")) {
                    String[] splits = line.split(COLUMN_DELIMITER);

                    long spectrumId = Long.parseLong(splits[SPECTRUM_ID]);
                    long peptide_id = Long.parseLong(splits[PEPTIDE_ID]);
                    String sequence = splits[PEPTIDE_SEQUENCE];
                    double precursorMass = Double.parseDouble(splits[PRECURSOR_MZ]);
                    int precursorCharge = Integer.parseInt(splits[PRECURSOR_CHARGE]);
                    PipelineExplanationType pipelineExplanationType = PipelineExplanationType.valueOf(splits[EXPLANATION]);

                    Peptide peptide = null;
                    //check for modifications
                    if (splits[MODIFICATIONS].equals(NOT_AVAILABLE)) {
                        peptide = new Peptide(precursorCharge, precursorMass, new AminoAcidSequence(sequence), peptide_id);
                    } else {
                        peptide = new ModifiedPeptide(precursorCharge, precursorMass, new AminoAcidSequence(sequence), peptide_id);
                        //add the modifications to the modified peptide                        
                        parseModifications((ModifiedPeptide) peptide, splits[MODIFICATIONS]);
                    }

                    Identification identification = new Identification(peptide, "", spectrumId, 0L);
                    identification.setPipelineExplanationType(pipelineExplanationType);

                    //check for noise threshold and score
                    if (!splits[SCORE].equals(NOT_AVAILABLE)) {
                        AnnotationData annotationData = new AnnotationData();
                        annotationData.setNoiseThreshold(Double.parseDouble(splits[NOISE_THRESHOLD]));
                        annotationData.setIdentificationScore(parseScore(splits[SCORE], peptide.length()));

                        //check for annotations
                        if (!splits[FRAGMENT_IONS].equals(NOT_AVAILABLE)) {
                            List<FragmentIonAnnotation> fragmentIonAnnotations = parseFragmentIonAnnotations(splits[FRAGMENT_IONS]);
                            annotationData.setFragmentIonAnnotations(fragmentIonAnnotations);
                        }

                        identification.setAnnotationData(annotationData);
                    }
                    //add identification to SpectrumAnnotatorResult
                    spectrumAnnotatorResult.addIdentification(identification);
                }
            }
            LOGGER.info("Finished reading " + spectrumAnnotatorResult.getNumberOfIdentifications() + " identifications for experiment " + experimentAccession);
            br.close();
        } catch (UnknownAAException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (FileNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return spectrumAnnotatorResult;
    }

    /**
     * Constructs the modifications string for a modified peptide before
     *
     * @param modifiedPeptide the modified peptide
     * @return
     */
    private String constructModifications(ModifiedPeptide modifiedPeptide) {
        Joiner modificationsJoiner = Joiner.on(MODIFICATIONS_DELIMITER);
        List<String> modifications = new ArrayList<String>();
        if (modifiedPeptide.getNTermMod() != null) {
            modifications.add("NT_" + modifiedPeptide.getNTermMod().getName());
        }
        if (modifiedPeptide.getNTModifications() != null) {
            for (int i = 0; i < modifiedPeptide.getNTModifications().length; i++) {
                ModificationFacade modificationFacade = modifiedPeptide.getNTModifications()[i];
                if (modificationFacade != null) {
                    modifications.add((i + 1) + "_" + modificationFacade.getName());
                }
            }
        }
        if (modifiedPeptide.getCTermMod() != null) {
            modifications.add("CT_" + modifiedPeptide.getCTermMod().getName());
        }

        return "mods[" + modificationsJoiner.join(modifications) + "]";
    }

    private String constructScore(IdentificationScore identificationScore) {
        String score = "" + MathUtils.roundDouble(identificationScore.getAverageFragmentIonScore()) + SCORE_OPEN
                + identificationScore.getMatchingPeaks() + SCORE_DELIMITER
                + identificationScore.getTotalPeaks() + SCORE_DELIMITER
                + identificationScore.getMatchingIntensity() + SCORE_DELIMITER
                + identificationScore.getTotalIntensity() + SCORE_CLOSE;


        return score;
    }

    private String constructFragmentIons(List<FragmentIonAnnotation> fragmentIonAnnotations) {
        //first sort the fragment ion annotations before iterating over them
        Collections.sort(fragmentIonAnnotations, new FragmentIonAnnotationComparator());

        List<String> fragmentIonsByIonType = new ArrayList<String>();
        List<String> fragmentIonNumbersByIonType = new ArrayList<String>();
        Joiner fragmentIonTypeJoiner = Joiner.on(FRAGMENT_ION_TYPE_DELIMITER);
        Joiner fragmentIonNumberJoiner = Joiner.on(FRAGMENT_ION_NUMBER_DELIMITER);
        String currentIonType = constructIonType(fragmentIonAnnotations.get(0));

        for (int i = 0; i < fragmentIonAnnotations.size(); i++) {
            //check if the ion type is still the same
            if (currentIonType.equals(constructIonType(fragmentIonAnnotations.get(i)))) {
                //add fragment ion number to the correct ion type
                fragmentIonNumbersByIonType.add(constructFragmentIonPeakValues(fragmentIonAnnotations.get(i)));
            } else {
                //join fragment ion numbers by ion type
                fragmentIonsByIonType.add(currentIonType + FRAGMENT_ION_NUMBERS_OPEN + fragmentIonNumberJoiner.join(fragmentIonNumbersByIonType) + FRAGMENT_ION_NUMBERS_CLOSE);
                //clear fragment ion number list and add first element
                fragmentIonNumbersByIonType.clear();
                fragmentIonNumbersByIonType.add(constructFragmentIonPeakValues(fragmentIonAnnotations.get(i)));
                //change current ion type
                currentIonType = constructIonType(fragmentIonAnnotations.get(i));
            }
        }
        //add the last fragment ion type        
        fragmentIonsByIonType.add(currentIonType + FRAGMENT_ION_NUMBERS_OPEN + fragmentIonNumberJoiner.join(fragmentIonNumbersByIonType) + FRAGMENT_ION_NUMBERS_CLOSE);

        return FRAGMENT_IONS_OPEN + fragmentIonTypeJoiner.join(fragmentIonsByIonType) + FRAGMENT_IONS_CLOSE;
    }

    private String constructFragmentIonPeakValues(FragmentIonAnnotation fragmentIonAnnotation) {
        String fragmentIonNumberValue = "" + fragmentIonAnnotation.getFragment_ion_number() + FRAGMENT_ION_PEAK_VALUES_OPEN
                + fragmentIonAnnotation.getMz() + FRAGMENT_ION_PEAK_VALUES_DELIMITER
                + fragmentIonAnnotation.getIntensity()
                + FRAGMENT_ION_PEAK_VALUES_CLOSE;

        return fragmentIonNumberValue;
    }

    private String constructIonType(FragmentIonAnnotation fragmentIonAnnotation) {
        return fragmentIonAnnotation.getIon_type_name() + "_" + fragmentIonAnnotation.getIon_charge() + "+";
    }

    private IdentificationScore parseScore(String score, int peptideLength) {
        String scoreValues = score.substring(score.indexOf(SCORE_OPEN) + 1, score.indexOf(SCORE_CLOSE));
        //split into score values: 
        //1. matching peaks
        //2. total peaks
        //3. matching intensity
        //4. total intensity        
        String[] splits = scoreValues.split(SCORE_DELIMITER);

        return new IdentificationScore(Integer.parseInt(splits[0]), Integer.parseInt(splits[1]), Long.parseLong(splits[2]), Long.parseLong(splits[3]), peptideLength);
    }

    private List<FragmentIonAnnotation> parseFragmentIonAnnotations(String fragmentIonAnnotationString) {
        List<FragmentIonAnnotation> fragmentIonAnnotations = new ArrayList<FragmentIonAnnotation>();

        String fragmentIons = fragmentIonAnnotationString.substring(fragmentIonAnnotationString.indexOf(FRAGMENT_IONS_OPEN) + FRAGMENT_IONS_OPEN.length(), fragmentIonAnnotationString.indexOf(FRAGMENT_IONS_CLOSE));
        //split the fragments by ion types
        String[] fragmentsByIonType = fragmentIons.split(FRAGMENT_ION_TYPE_DELIMITER);
        for (String fragmentByIonType : fragmentsByIonType) {
            String ionType = fragmentByIonType.substring(0, fragmentByIonType.indexOf(FRAGMENT_ION_TYPE_CHARGE_DELIMITER));
            int charge = Integer.parseInt(fragmentByIonType.substring(fragmentByIonType.indexOf(FRAGMENT_ION_TYPE_CHARGE_DELIMITER) + 1, fragmentByIonType.indexOf(FRAGMENT_ION_NUMBERS_OPEN) - 1));

            String fragmentIonNumbers = fragmentByIonType.substring(fragmentByIonType.indexOf(FRAGMENT_ION_NUMBERS_OPEN) + 1, fragmentByIonType.indexOf(FRAGMENT_ION_NUMBERS_CLOSE));
            //split by ion number
            String[] fragments = fragmentIonNumbers.split("\\" + FRAGMENT_ION_NUMBER_DELIMITER);
            for (String fragment : fragments) {
                double[] peakValues = parseFragmentIonPeakValues(fragment);
                int ionNumber = Integer.parseInt(fragment.substring(0, fragment.indexOf(FRAGMENT_ION_PEAK_VALUES_OPEN)));
                FragmentIonAnnotation fragmentIonAnnotation = new FragmentIonAnnotation(0L, FragmentIonAnnotation.resolveIonType(ionType), ionNumber, peakValues[0], peakValues[1], 0.0, charge);
                fragmentIonAnnotations.add(fragmentIonAnnotation);
            }
        }

        return fragmentIonAnnotations;
    }

    private double[] parseFragmentIonPeakValues(String fragment) {
        double[] peakValues = new double[2];
        peakValues[0] = Double.parseDouble(fragment.substring(fragment.indexOf(FRAGMENT_ION_PEAK_VALUES_OPEN) + 1, fragment.indexOf(FRAGMENT_ION_PEAK_VALUES_DELIMITER)));
        peakValues[1] = Double.parseDouble(fragment.substring(fragment.indexOf(FRAGMENT_ION_PEAK_VALUES_DELIMITER) + 1, fragment.indexOf(FRAGMENT_ION_PEAK_VALUES_CLOSE)));

        return peakValues;
    }

    /**
     * Parses the modifications in the result file and adds them to the modified
     * peptide. If the modifications is not found by name in the
     * modifications.xml file, a default modifications is added.
     *
     * @param modifiedPeptide the modified peptide
     * @param modificationsString
     */
    private void parseModifications(ModifiedPeptide modifiedPeptide, String modificationsString) {
        String mods = modificationsString.substring(modificationsString.indexOf(MODIFICATIONS_OPEN) + MODIFICATIONS_OPEN.length(), modificationsString.indexOf(MODIFICATIONS_CLOSE));
        //split modifications
        String[] splits = mods.split(MODIFICATIONS_DELIMITER);
        for (String mod : splits) {
            String modificationName = mod.substring(mod.indexOf(MODIFICATIONS_LOCATION_DELIMITER) + 1, mod.length());
            //try to find modification by name
            Modification modification = findModificationByName(modificationName);
            if (mod.contains(MODIFICATIONS_N_TERMINAL)) {
                modifiedPeptide.setNTermMod(modification);
            } else if (mod.contains(MODIFICATIONS_C_TERMINAL)) {
                modifiedPeptide.setCTermMod(modification);
            } else {
                int location = Integer.parseInt(mod.substring(0, mod.indexOf(MODIFICATIONS_LOCATION_DELIMITER))) - 1;
                modifiedPeptide.setNTModification(location, modification);
            }
        }
    }

    /**
     * loads pipeline modifications in order to map the modification names in
     * the result file to the right modification
     */
    private void loadModifications() {
        modifications = new HashMap<String, Modification>();
        try {
            for (Modification modification : modificationService.loadPipelineModifications(ResourceUtils.getResourceByRelativePath(PropertiesConfigurationHolder.getInstance().getString("modification.pipeline_modifications_file")))) {
                modifications.put(modification.getName(), modification);
            }
        } catch (JDOMException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    /**
     * Finds the modification by name in the pipeline modifications. Returns an
     * "unknown" modification if nothing was found.
     *
     * @param modificationName
     * @return
     */
    private Modification findModificationByName(String modificationName) {
        if (modifications.containsKey(modificationName)) {
            return modifications.get(modificationName);
        } else {
            return new Modification(0.0, Modification.Location.NON_TERMINAL, NOT_AVAILABLE, modificationName);
        }
    }
}
