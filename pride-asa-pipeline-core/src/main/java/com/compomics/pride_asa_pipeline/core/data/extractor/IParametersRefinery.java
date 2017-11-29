package com.compomics.pride_asa_pipeline.core.data.extractor;

import com.compomics.util.preferences.IdentificationParameters;

/**
 * This code is licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * ==Overview==
 * <p>
 * This Interface will be use to define when some external method would like to force some parameters to
 * work in some specific way. For example the check that all search engines use the same enzyme.
 * <p>
 * Created by ypriverol (ypriverol@gmail.com) on 29/11/2017.
 */
public interface IParametersRefinery {

    /**
     * Refine the Identification Parameters.
     * @param identificationParameters identification parameters
     * @return new Identification Parameters.
     */
    IdentificationParameters refineIdentificationParameters(IdentificationParameters identificationParameters);

}
