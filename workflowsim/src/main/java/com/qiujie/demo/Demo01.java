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

import static com.qiujie.Constants.*;

import com.qiujie.planner.MyPlanner;
import com.qiujie.entity.*;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import com.qiujie.util.WorkflowParser;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.List;


/**
 * @author QIUJIE
 * <p>
 * schedule a workflow to mutil datacenters
 */
public class Demo01 {
    public static void main(String[] args) throws Exception {
        long send = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        Log.setLevel(Level.TRACE);
//            String daxPath = "data/dax/Inspiral_1000.xml";
//            String daxPath = "data/dax/Inspiral_100.xml";
//            String daxPath = "data/dax/Epigenomics_997.xml";
//        String daxPath = "data/dax/Montage_25.xml";
//            String daxPath = "data/dax/CyberShake_100.xml";
//            String daxPath = "data/dax/Epigenomics_46.xml";
            String daxPath = "data/dax/Montage_50.xml";
//        String daxPath = "data/dax/Montage_1000.xml";
//        String daxPath = "data/dax/Montage_50.xml";
//            String daxPath = "data/dax/Montage_25.xml";
//            String daxPath = "data/dax/Sipht_1000.xml";
        // basic parameters


        RANDOM = new UniformDistr(0, 1, send);
        LENGTH_FACTOR = 1e5;

        // create datacenters
        List<Datacenter> datacenterList = ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(MyPlanner.class);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(broker.getId());
        broker.submitGuestList(vmList);
        // submit workflows
        Workflow workflow = WorkflowParser.parse(daxPath);
        broker.submitWorkflow(workflow);
        // start simulation
        CloudSim.startSimulation();
        List<Job> cloudletReceivedList = broker.getCloudletReceivedList();

        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        // print result
        ExperimentUtil.printSimResult(cloudletReceivedList, broker.getName());
        // generate gantt chart data
        ExperimentUtil.generateSimGanttData(cloudletReceivedList, className + "_" + broker.getName());
        System.out.println(className + " task " + (System.currentTimeMillis() - send) / 1000.0 + "s");
    }
}
