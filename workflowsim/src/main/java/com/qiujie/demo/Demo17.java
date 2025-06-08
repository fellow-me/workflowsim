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
import com.qiujie.entity.ClockModifier;
import com.qiujie.entity.SimStarter;
import com.qiujie.planner.*;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.List;

import static com.qiujie.Constants.*;


/**
 * @author QIUJIE
 * <p>
 * different planner comparison
 */
public class Demo17 {
    public static void main(String[] args) throws Exception {
        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();
        System.out.println("Experiment " + className + " starting...");
        long seed = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(Level.DEBUG);
        List<String> daxPathList = List.of(
                "data/dax/CyberShake_25.xml"
                , "data/dax/CyberShake_50.xml"
                , "data/dax/CyberShake_100.xml"
                , "data/dax/CyberShake_200.xml"
//                , "data/dax/CyberShake_400.xml"
//                , "data/dax/CyberShake_500.xml"
                , "data/dax/Montage_25.xml"
                , "data/dax/Montage_50.xml"
                , "data/dax/Montage_100.xml"
                , "data/dax/Montage_200.xml"
//                , "data/dax/Montage_400.xml"
        );

        MAX_RETRY_COUNT = 10;

        LENGTH_FACTOR = 5000;

        List<SimStarter> simStarterList = List.of(
                new SimStarter(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class)
                , new SimStarter(new UniformDistr(0, 1, seed), daxPathList, ECWSDPlanner.class)
                , new SimStarter(new UniformDistr(0, 1, seed), daxPathList, DEWSPlanner.class)
                , new SimStarter(new UniformDistr(0, 1, seed), daxPathList, EPEEPlanner.class)
                , new SimStarter(new UniformDistr(0, 1, seed), daxPathList, HEFTPlanner.class)
        );

        simStarterList.forEach(SimStarter::printSimResult);
        simStarterList.forEach(SimStarter::generateSimGanttData);

        ExperimentUtil.printExperimentResult(simStarterList, className);

        System.out.println("Experiment " + className + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }
}
