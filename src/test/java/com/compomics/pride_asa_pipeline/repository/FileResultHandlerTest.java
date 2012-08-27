/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.compomics.pride_asa_pipeline.repository;

import com.compomics.pride_asa_pipeline.model.AASequenceMassUnknownException;
<<<<<<< .mine
=======
import com.compomics.pride_asa_pipeline.model.AminoAcid;
import com.compomics.pride_asa_pipeline.model.AminoAcidSequence;
import com.compomics.pride_asa_pipeline.model.AnnotationData;
import com.compomics.pride_asa_pipeline.model.FragmentIonAnnotation;
import com.compomics.pride_asa_pipeline.model.Identification;
import com.compomics.pride_asa_pipeline.model.IdentificationScore;
import com.compomics.pride_asa_pipeline.model.Modification;
import com.compomics.pride_asa_pipeline.model.ModifiedPeptide;
import com.compomics.pride_asa_pipeline.model.Peptide;
>>>>>>> .r42
import com.compomics.pride_asa_pipeline.model.UnknownAAException;
import org.jdom2.JDOMException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

/**
 *
 * @author niels
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:springXMLConfig.xml")
public class FileResultHandlerTest {

    @Autowired
//    private FileResultHandler fileResultHandler;
    @Rule
//    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testWriteResult() throws IOException, UnknownAAException, JDOMException, AASequenceMassUnknownException {
        File tempResultFile = temporaryFolder.newFile("tempResultFile.txt");

        //add identifications
        List<Identification> identifications = new ArrayList<Identification>();

        //create identification with annotation data
        Peptide peptide = new Peptide(1, 1649.8D, new AminoAcidSequence("AAKENNYLENNART"));
        Identification identification_1 = new Identification(peptide, null, 1L, 1L);
        identifications.add(identification_1);

        List<FragmentIonAnnotation> fragmentIonAnnotations = new ArrayList<FragmentIonAnnotation>();
        FragmentIonAnnotation fragmentIonAnnotation_1 = new FragmentIonAnnotation(1L, FragmentIonAnnotation.IonType.Y_ION, 2, 1L, 1L, 1);
        fragmentIonAnnotations.add(fragmentIonAnnotation_1);

        FragmentIonAnnotation fragmentIonAnnotation_2 = new FragmentIonAnnotation(1L, FragmentIonAnnotation.IonType.Y_ION, 5, 1L, 1L, 2);
        fragmentIonAnnotations.add(fragmentIonAnnotation_2);

        FragmentIonAnnotation fragmentIonAnnotation_3 = new FragmentIonAnnotation(1L, FragmentIonAnnotation.IonType.B_ION, 4, 1L, 1L, 1);
        fragmentIonAnnotations.add(fragmentIonAnnotation_3);

        FragmentIonAnnotation fragmentIonAnnotation_4 = new FragmentIonAnnotation(1L, FragmentIonAnnotation.IonType.B_ION, 2, 1L, 1L, 1);
        fragmentIonAnnotations.add(fragmentIonAnnotation_4);

        IdentificationScore identificationScore = new IdentificationScore(4, 20, 1023L, 7502L, peptide.length());
        
        AnnotationData annotationData = new AnnotationData();
        annotationData.setFragmentIonAnnotations(fragmentIonAnnotations);                
        annotationData.setIdentificationScore(identificationScore);

        //add annotation data to identification
        identification_1.setAnnotationData(annotationData);

        //create identification with modified peptide
        Set<AminoAcid> modifiedAAs = new HashSet<AminoAcid>();
        ModifiedPeptide modifiedPeptide = new ModifiedPeptide(1, new AminoAcidSequence("AAKENNYLENNART").getSequenceMass(), new AminoAcidSequence("AAKENNYLENNART"), 2L);
        modifiedPeptide.setNTModification(3, new Modification("testModification_1", 0.0, 0.0, Modification.Location.NON_TERMINAL, modifiedAAs, "mod_1", "mod_1"));
        modifiedPeptide.setNTModification(6, new Modification("testModification_2", 0.0, 0.0, Modification.Location.NON_TERMINAL, modifiedAAs, "mod_2", "mod_2"));
        modifiedPeptide.setNTModification(0, new Modification("testModification_3", 0.0, 0.0, Modification.Location.NON_TERMINAL, modifiedAAs, "mod_3", "mod_3"));
        modifiedPeptide.setNTermMod(new Modification("testModification_4", 0.0, 0.0, Modification.Location.N_TERMINAL, modifiedAAs, "mod_4", "mod_4"));

        Identification identification_2 = new Identification(modifiedPeptide, null, 2L, 2L);
        identifications.add(identification_2);

        //write identifications to file
        fileResultHandler.writeResult(tempResultFile, identifications);

        //read file
        BufferedReader bufferedReader = new BufferedReader(new FileReader(tempResultFile));
        String line = null;

        while ((line = bufferedReader.readLine()) != null) {
            String[] splitArray = line.split("\t");

            if (line.startsWith("1")) {
                assertEquals(Long.toString(identification_1.getSpectrumId()), splitArray[0]);
                assertEquals(identification_1.getPeptide().getSequenceString(), splitArray[1]);
                assertEquals(0.034, Double.parseDouble(splitArray[2]), 0.01);
                assertEquals("ions[b ion_1+(2|4);y ion_1+(2);y ion_2+(5)]", splitArray[3]);
                assertEquals("N/A", splitArray[4]);
            } else if (line.startsWith("2")) {
                assertEquals(Long.toString(identification_2.getSpectrumId()), splitArray[0]);
                assertEquals(identification_2.getPeptide().getSequenceString(), splitArray[1]);
                assertEquals("N/A", splitArray[2]);
                assertEquals("N/A", splitArray[3]);
                assertEquals("mods[NT_testModification_4;0_testModification_3;3_testModification_1;6_testModification_2]", splitArray[4]);
            }
        }
    }
}
