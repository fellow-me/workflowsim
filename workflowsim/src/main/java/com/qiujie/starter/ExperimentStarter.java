package com.qiujie.starter;

import com.qiujie.aop.ClockModifier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {


    public long seed;
    public final String name;
    private final Logger log;


    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(getClass());
        STARTUP = MarkerFactory.getMarker("STARTUP");

        start();
    }


    private void start() {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        log.info(STARTUP, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            log.info(STARTUP, String.format("%s: Running %.2fs\n", name, (System.currentTimeMillis() - seed) / 1000.0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public abstract void run() throws Exception;

    public static void startExperiment() {
        try {
            String className = Thread.currentThread().getStackTrace()[2].getClassName();
            Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
