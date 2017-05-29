package com.compomics.pride_asa_pipeline.core.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.Logger;
import uk.ac.ebi.pride.utilities.data.controller.impl.ControllerImpl.CachedDataAccessController;
import uk.ac.ebi.pride.utilities.data.controller.impl.ControllerImpl.MzIdentMLControllerImpl;
import uk.ac.ebi.pride.utilities.data.controller.impl.ControllerImpl.PrideXmlControllerImpl;

/**
 * A cache that holds the parsers so they don't have to be run again
 *
 * @author Kenneth Verheggen
 */
public class ParserCache {

    /**
     * The cache containing filename and parser (so duplicate files cannot be
     * added)
     */
    private static final HashMap<String, CachedDataAccessController> parserCache = new HashMap<>();
    /**
     * The cache containing loaded files
     */
    private static final HashMap<String, File> loadedFiles = new HashMap<>();
    /**
     * The cache containing peak files
     */
    private static final HashMap<String, List<File>> peakFileCache = new HashMap();

    /**
     * The Logging instance
     */
    private static final Logger LOGGER = Logger.getLogger(ParserCache.class);

    /**
     * The ParserCache instance
     *
     */
    private static ParserCache instance;
    public static int TIME_OUT_MINUTES;

    public static ParserCache getInstance() {
        if (instance == null) {
            instance = new ParserCache();
        }
        return instance;
    }

    private ParserCache() {

    }

    /**
     * Returns an existing or creates a new FileParser for the given input file
     *
     * @param experimentAccession the experiment accession
     * @param identificationsFile the input identifications file
     * @param boolean indicating if the file is to be parsed in memory (only for
     * mzid)
     * @return a fileparser for the file
     * @throws IOException if the fileparser can not be constructed
     */
    public CachedDataAccessController getParser(String experimentAccession, File identificationsFile, boolean inMemory) throws TimeoutException, InterruptedException, ExecutionException {

        if (!parserCache.containsKey(experimentAccession)) {
            ExecutorService service = Executors.newSingleThreadExecutor();
            Future<CachedDataAccessController> future = service.submit(new Callable<CachedDataAccessController>() {
                @Override
                public CachedDataAccessController call() throws Exception {
                    LOGGER.info("Parsing file using PRIDE ms-data-core-api");
                    CachedDataAccessController parser;
                    if (identificationsFile.getName().toUpperCase().endsWith(".XML")) {
                        parser = new PrideXmlControllerImpl(identificationsFile);
                        peakFileCache.put(experimentAccession, Arrays.asList(new File[]{identificationsFile}));
                    } else {
                        parser = new MzIdentMLControllerImpl(identificationsFile, true, true);
                    }
                    LOGGER.debug("DataAccessController for " + experimentAccession + " : " + identificationsFile.getAbsolutePath() + " was cached");
                    return parser;
                }
            });
            //time limit for parsing is?
            if(TIME_OUT_MINUTES==0){
            TIME_OUT_MINUTES=15;
        }
            parserCache.put(experimentAccession, future.get(TIME_OUT_MINUTES, TimeUnit.MINUTES));
            loadedFiles.put(experimentAccession, identificationsFile);
        }
        return parserCache.get(experimentAccession);
    }

    /**
     * Returns an existing or creates a new FileParser for the given input file
     *
     * @param experimentAccession
     * @param identificationsFileName the input identifications file name
     * @param boolean indicating if the file is to be parsed in memory (only for
     * mzid)
     * @return a fileparser for the file
     * @throws java.util.concurrent.TimeoutException
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws IOException if the fileparser can not be constructed
     */
    public CachedDataAccessController getParser(String experimentAccession, boolean inMemory) throws TimeoutException, InterruptedException, ExecutionException {
        return getParser(experimentAccession, loadedFiles.get(experimentAccession), inMemory);
    }

    /**
     * Returns a boolean if the given accession is already loaded
     *
     * @param experimentAccession the experiment accession
     * @return a boolean whether the accession already is loaded
     */
    public boolean containsParser(String experimentAccession) {
        return loadedFiles.containsKey(experimentAccession);
    }

    /**
     * Deletes a parser from the cache
     *
     * @param identificationsFileName the name of the file to be removed from
     * the cache
     */
    public void deleteParser(String experimentAccession) {
        parserCache.get(experimentAccession).close();
        parserCache.remove(experimentAccession);
    }

    /**
     * Returns a hashmap of all filenames and their paths in the cache
     *
     * @return a hashmap of all filenames and their paths in the cache
     */
    public HashMap<String, String> getLoadedFiles() {
        HashMap<String, String> experimentAccessionMap = new HashMap<>();
        for (Map.Entry<String, File> anEntry : loadedFiles.entrySet()) {
            experimentAccessionMap.put(anEntry.getKey(), anEntry.getValue().getAbsolutePath());
        }
        return experimentAccessionMap;
    }

    /**
     * Adds peakfiles to a cached parser (only for mzid !)
     *
     * @param experimentName the name of the experiment (filename)
     * @param peakFiles a list of peakfiles related to this mzid file
     */
    public void addPeakFiles(String experimentName, List<File> peakFiles) {
        CachedDataAccessController controller = parserCache.get(experimentName);
        if (controller instanceof MzIdentMLControllerImpl) {
            MzIdentMLControllerImpl temp = (MzIdentMLControllerImpl) controller;
            temp.addMSController(peakFiles);
            List<File> peakFileList = peakFileCache.getOrDefault(experimentName, new ArrayList<File>());
            peakFileList.addAll(peakFiles);
            peakFileCache.put(experimentName, peakFileList);
        }
    }

    /**
     * Returns a list of cached peakfiles for an experiment
     *
     * @param experimentName the experiment identifier
     * @return a list of cached peakfiles for an experiment
     */
    public List<File> getPeakFiles(String experimentName) {
        return peakFileCache.get(experimentName);
    }

    /**
     * Returns a list of cached peakfiles for an experiment
     *
     * @param experimentFile the identiications file for the experiment
     * @return a list of cached peakfiles for an experiment
     */
    public List<File> getPeakFiles(File experimentFile) {
        return peakFileCache.get(experimentFile.getName());
    }

    /**
     * Adds peakfiles to a cached parser (only for mzid !)
     *
     * @param identificationsFile the mzid file for the experiment
     * @param peakFiles a list of peakfiles related to this mzid file
     */
    public void addPeakFiles(String experimentAccession, File identificationsFile, List<File> peakFiles) {
        addPeakFiles(experimentAccession, peakFiles);
    }

    /**
     * Clears the cache and closes all parser before removing them
     */
    public void clear() {
        for (String anExperiment : parserCache.keySet()) {
            deleteParser(anExperiment);
        }
    }

}
