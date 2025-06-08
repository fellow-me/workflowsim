/**
 * Copyright 2012-2013 University Of Southern California
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.qiujie.demo;

import ch.qos.logback.classic.Level;
import com.qiujie.planner.MyPlanner;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.comparator.LengthComparator;
import com.qiujie.comparator.DepthComparator;
import com.qiujie.comparator.JobNumComparator;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.entity.ClockModifier;
import com.qiujie.entity.SimStarter;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;

import static com.qiujie.Constants.*;

import generator.app.Application;
import generator.app.CyberShake;
import generator.app.Montage;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.ArrayList;
import java.util.List;


/**
 * @author qiujie
 * <p>
 * different workflow sort strategy
 */
public class Demo10 {
    public static void main(String[] args) throws Exception {
        long seed = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(Level.OFF);
        List<String> daxPathList = new ArrayList<>();

        APP_LIST = List.of(Montage.class, CyberShake.class);
        JOB_NUM_LIST = List.of(50,100);
        INSTANCE_NUM_LIST = List.of(20);

        for (Class<? extends Application> app : APP_LIST) {
            for (Integer jobNum : JOB_NUM_LIST) {
                for (Integer instanceNum : INSTANCE_NUM_LIST) {
                    for (int i = 0; i < instanceNum; i++) {
                        String name = app.getSimpleName();
                        daxPathList.add("data/dax/" + name + "/" + jobNum + "/" + instanceNum + "/" + name + "_" + jobNum + "_" + instanceNum + "_" + i + ".xml");
                    }
                }
            }
        }

        List<Class<? extends WorkflowPlannerAbstract>> plannerClassList = List.of(MyPlanner.class);
        List<Class<? extends WorkflowComparatorInterface>> comparatorClassList = List.of(JobNumComparator.class, DepthComparator.class, LengthComparator.class);
        List<Boolean> booleanList = List.of(true, false);

        List<SimStarter> simStarterList = new ArrayList<>();
        for (Class<? extends WorkflowPlannerAbstract> plannerClass : plannerClassList) {
            for (Class<? extends WorkflowComparatorInterface> comparatorClass : comparatorClassList) {
                for (Boolean ascending : booleanList) {
                    simStarterList.add(new SimStarter(new UniformDistr(0, 1, seed), daxPathList, plannerClass, comparatorClass, ascending));
                }
            }
        }

//        runnerList.forEach(Runner::printSimResult);
        ExperimentUtil.printExperimentResult(simStarterList);

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        System.out.println(className + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }
}
