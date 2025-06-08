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
import com.qiujie.entity.*;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import com.qiujie.util.WorkflowParser;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.List;

import static com.qiujie.Constants.*;


/**
 * schedule multiple workflows
 */
public class Demo02 {
    public static void main(String[] args) throws Exception {
        long send = System.currentTimeMillis();
        RANDOM = new UniformDistr(0, 1, send);
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        Log.setLevel(Level.INFO);
        List<String> daxPathList = List.of(
//                "data/dax/Inspiral_1000.xml",
//                "data/dax/Inspiral_100.xml",
//                "data/dax/Inspiral_50.xml",
//                "data/dax/Epigenomics_997.xml",
                "data/dax/Sipht_30.xml",
//                "data/dax/Montage_1000.xml",
//                "data/dax/CyberShake_100.xml",
//                "data/dax/CyberShake_30.xml",
//                "data/dax/Epigenomics_46.xml",
//                "data/dax/Epigenomics_24.xml",
                "data/dax/Montage_100.xml",
//                "data/dax/Montage_1000.xml",
                "data/dax/Montage_50.xml"
        );
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(MyPlanner.class);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        List<Workflow> workflowList = daxPathList.stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
        List<Job> cloudletReceivedList = broker.getCloudletReceivedList();
        // plot dc electricity price chart
//            ExperimentUtil.plotElecPriceChart(datacenterList);
        // print result
        ExperimentUtil.printSimResult(cloudletReceivedList);
        // generate gantt chart data

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        ExperimentUtil.generateSimGanttData(cloudletReceivedList, className + "_" + broker.getName());
        ExperimentUtil.generateSimGanttData(cloudletReceivedList, className + "_" + broker.getName());

        System.out.println(className + " task " + (System.currentTimeMillis() - send) / 1000.0 + "s");
    }
}
