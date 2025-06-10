package com.qiujie.entity;

import java.lang.reflect.InvocationTargetException;

public abstract class ExperimentStarter {


    public long seed;
    public final String className;


    public ExperimentStarter() {
        this.className = getClass().getSimpleName();
        start();
    }


    private void start() {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        System.out.println("Experiment " + className + " starting...");
        this.seed = System.currentTimeMillis();
        try {
            run();
            System.out.printf("Experiment %s run %.2fs\n", className, (System.currentTimeMillis() - seed) / 1000.0);
        } catch (Exception e) {
            System.err.printf("Experiment %s failed: %s\n", className, e.getMessage());
            e.printStackTrace();
            System.exit(1); // exit when throw exception
        }
    }


    public abstract void run() throws Exception;

    public static void startExperiment() {
        try {
            String className = Thread.currentThread().getStackTrace()[2].getClassName();
            Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.printf("Failed to instantiate class: %s\n", e.getMessage());
            e.printStackTrace();
            System.exit(1); // exit when throw exception
        }
    }

}
