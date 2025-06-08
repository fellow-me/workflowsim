package com.qiujie.entity;

public abstract class ExperimentStarter  {


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
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Experiment " + className + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }


    public abstract void run() throws Exception;

    public static void startExperiment() {
        String className = Thread.currentThread().getStackTrace()[2].getClassName();
        try {
            Class<?> clazz = Class.forName(className);
            clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
