package com.yourcompany.testing.hooks;

import com.yourcompany.testing.infrastructure.runtime.TestRuntime;
import io.cucumber.java.After;
import io.cucumber.java.Before;

public final class CucumberHooks {
    @Before
    public void startRuntime() {
        TestRuntime.start();
    }

    @After
    public void stopRuntime() {
        TestRuntime.closeCurrent();
    }
}
