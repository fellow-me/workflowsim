package com.qiujie.starter;

import com.qiujie.aop.ClockModifier;

public abstract class ExperimentStarter {


    public long seed;
    public final String name;


    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        start();
    }


    private void start() {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        System.out.printf("Experiment %s starting...\n", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            System.out.printf("Experiment %s run %.2fs\n", name, (System.currentTimeMillis() - seed) / 1000.0);
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
