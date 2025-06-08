package com.qiujie.test;


import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.junit.Test;

import java.io.File;
import java.util.Map;

public class Test01 {


    @Test
    public void test01() {
        long l = System.currentTimeMillis();
        UniformDistr random = new UniformDistr(0, 1, l);
        UniformDistr random1 = new UniformDistr(0, 1, l);
        for (int i = 0; i < 100000; i++) {
            double sample = random.sample();
            double sample1 = random1.sample();
            if (sample != sample1) {
                System.out.println("different !");
                break;
            }
            System.out.println(sample + " " + sample1);
        }
    }


    @Test
    public void test02() {
        String path = System.getProperty("user.dir") + "\\data\\gantt\\_pln.json";
        System.out.println(path);
        System.out.println(System.getProperty("user.dir"));
    }


    @Test
    public void test03() {
        UniformDistr rand = new UniformDistr(0, 1);
        while (true) {
            double sample = rand.sample();
            System.out.println(sample);
            if (sample == 0) {
                break;
            }
        }
    }


    @Test
    public void test04() {
        double exp = Math.exp(-1e-6 * 10);
        System.out.println(exp);
    }


    @Test
    public void test05() {
        double var = Math.pow(0.99, 100);
        System.out.println(var);
    }
}
