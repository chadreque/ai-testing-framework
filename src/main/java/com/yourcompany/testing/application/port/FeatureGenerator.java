package com.yourcompany.testing.application.port;

import com.yourcompany.testing.domain.testcase.FeatureModel;
import com.yourcompany.testing.domain.testcase.TestSpecification;

public interface FeatureGenerator {
    FeatureModel generate(TestSpecification testSpecification);
}
