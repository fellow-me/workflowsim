package com.qiujie.starter;

import com.qiujie.aop.ClockModifier;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.core.CloudSim;
import org.slf4j.MarkerFactory;

import static com.qiujie.Constants.*;

@Slf4j
public abstract class ExperimentStarter {


    public long seed;
    public final String name;


    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        STARTUP = MarkerFactory.getMarker("STARTUP");
        System.setProperty("startup.class", name);
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
