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
import com.qiujie.comparator.JobNumComparator;
import com.qiujie.comparator.WorkflowComparatorInterface;
import com.qiujie.entity.ClockModifier;
import com.qiujie.entity.Workflow;
import com.qiujie.entity.WorkflowBroker;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import com.qiujie.util.WorkflowParser;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

import static com.qiujie.Constants.*;


/**
 * @author QIUJIE
 * <p>
 * different workflow sort strategy
 */
public class Demo08 {
    public static void main(String[] args) throws Exception {
        long seed = System.currentTimeMillis();
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(Level.DEBUG);
        List<String> daxPathList = List.of(
//                "data/dax/Inspiral_1000.xml",
                "data/dax/Inspiral_100.xml",
//                "data/dax/Epigenomics_997.xml",
//                "data/dax/Sipht_30.xml",
                "data/dax/Sipht_100.xml",
//                "data/dax/Montage_1000.xml"
                "data/dax/CyberShake_100.xml",
//                "data/dax/CyberShake_30.xml",
//                "data/dax/CyberShake_200.xml",
//                "data/dax/Epigenomics_100.xml",
//                "data/dax/Montage_200.xml",
//                "data/dax/Montage_1000.xml",
                "data/dax/Montage_100.xml");


        WorkflowBroker broker = run(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, JobNumComparator.class, true);
//        WorkflowBroker broker1 = run(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, CloudletLengthComparator.class, true);
//        WorkflowBroker broker2 = run(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, DepthComparator.class, true);
//        WorkflowBroker broker3 = run(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, JobNumComparator.class, false);
//        WorkflowBroker broker4 = run(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, CloudletLengthComparator.class, false);
//        WorkflowBroker broker5 = run(new UniformDistr(0, 1, seed), daxPathList, MyPlanner.class, DepthComparator.class, false);

        ExperimentUtil.printSimResult(broker.getCloudletReceivedList(), "broker");
//        ExperimentUtil.printSimResult(broker1.getCloudletReceivedList(), "broker1");
//        ExperimentUtil.printSimResult(broker2.getCloudletReceivedList(), "broker2");
//        ExperimentUtil.printSimResult(broker3.getCloudletReceivedList(), "broker3");
//        ExperimentUtil.printSimResult(broker4.getCloudletReceivedList(), "broker4");
//        ExperimentUtil.printSimResult(broker5.getCloudletReceivedList(), "broker5");


//        System.out.printf("%s elecCost: %.2f\n", "broker", broker.getPlnElecCost());
//        System.out.printf("%s elecCost: %.2f\n", "broker1", broker1.getPlnElecCost());
//        System.out.printf("%s elecCost: %.2f\n", "broker2", broker2.getPlnElecCost());
//        System.out.printf("%s elecCost: %.2f\n", "broker3", broker3.getPlnElecCost());
//        System.out.printf("%s elecCost: %.2f\n", "broker4", broker4.getPlnElecCost());
//        System.out.printf("%s elecCost: %.2f\n", "broker5", broker5.getPlnElecCost());



        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();

        ExperimentUtil.generateSimGanttData(broker.getCloudletReceivedList(), className + "_" + broker.getName());

        System.out.println(className + " run " + (System.currentTimeMillis() - seed) / 1000.0 + "s");
    }

    public static WorkflowBroker run(ContinuousDistribution random, List<String> daxPathList, Class<? extends WorkflowPlannerAbstract> plannerClass, Class<? extends WorkflowComparatorInterface> comparatorClass, boolean ascending) throws Exception {
        RANDOM = random;
        // init cloudsim
        CloudSim.init(USERS, Calendar.getInstance(), TRACE_FLAG);
        // create datacenters
        ExperimentUtil.createDatacenters();
        // create broker
        WorkflowBroker broker = new WorkflowBroker(plannerClass);
        // submit vms
        List<Vm> vmList = ExperimentUtil.createVms(broker.getId());
        broker.submitGuestList(vmList);
        // create workflow comparator
        Comparator<Workflow> comparator = comparatorClass.getDeclaredConstructor().newInstance().get(ascending);
        // submit workflows
        List<Workflow> workflowList = daxPathList.stream().map(WorkflowParser::parse).sorted(comparator).toList();
        broker.submitWorkflowList(workflowList);
        // start simulation
        CloudSim.startSimulation();
        System.out.println();
        return broker;
    }
}
