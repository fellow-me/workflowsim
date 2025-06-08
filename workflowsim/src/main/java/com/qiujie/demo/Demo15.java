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
import com.qiujie.comparator.JobNumComparator;
import com.qiujie.entity.ClockModifier;
import com.qiujie.entity.SimStarter;
import com.qiujie.planner.*;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.List;


/**
 * @author QIUJIE
 * <p>
 * different workflow sort strategy
 */
public class Demo15 {
    public static void main(String[] args) throws Exception {
        long seed = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(Level.DEBUG);
        List<String> daxPathList = List.of(
//                "data/dax/Inspiral_50.xml",
//                "data/dax/Sipht_30.xml",
//                "data/dax/Sipht_60.xml",
                "data/dax/CyberShake_100.xml",
                "data/dax/CyberShake_50.xml",
                "data/dax/CyberShake_200.xml",
                "data/dax/CyberShake_500.xml",
//                "data/dax/SIPHT_200.xml",
//                "data/dax/Epigenomics_46.xml",
//                "data/dax/Inspiral_50.xml",
//                "data/dax/Epigenomics_24.xml",
                "data/dax/Montage_50.xml",
                "data/dax/Montage_100.xml",
                "data/dax/Montage_500.xml",
                "data/dax/Montage_200.xml");


        SimStarter simStarter = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, JobNumComparator.class, true);
        SimStarter simStarter1 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, ECWSDPlanner.class, JobNumComparator.class, true);
        SimStarter simStarter2 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, DEWSPlanner.class, JobNumComparator.class, true);
        SimStarter simStarter4 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, EPEEPlanner.class, JobNumComparator.class, true);
        SimStarter simStarter3 = new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class, JobNumComparator.class, true);

        List<SimStarter> simStarterList = List.of(simStarter, simStarter1, simStarter2, simStarter3, simStarter4);

        simStarterList.forEach(SimStarter::printSimResult);
        simStarterList.forEach(SimStarter::generateSimGanttData);

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        ExperimentUtil.printExperimentResult(simStarterList, className);

        System.out.println(className + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }
}
