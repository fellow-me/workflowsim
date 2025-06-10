
package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.entity.ClockModifier;
import com.qiujie.entity.Job;
import com.qiujie.entity.Workflow;
import com.qiujie.entity.WorkflowBroker;
import com.qiujie.planner.ECWSDPlanner;
import com.qiujie.planner.HEFTPlanner;
import com.qiujie.util.ExperimentUtil;
import com.qiujie.util.Log;
import com.qiujie.util.WorkflowParser;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.distributions.UniformDistr;

import java.util.Calendar;
import java.util.List;

import static com.qiujie.Constants.*;


/**
 * multiple brokers, each broker schedule multiple workflows
 */
public class Demo03 {
    public static void main(String[] args) throws Exception {
        long send = System.currentTimeMillis();
        RANDOM = new UniformDistr(0, 1, send);
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        CloudSim.init(2, Calendar.getInstance(), TRACE_FLAG);
        Log.setLevel(Level.TRACE);
        List<String> daxPathList = List.of(
//                "data/dax/Inspiral_1000.xml",
//                "data/dax/Inspiral_100.xml",
                "data/dax/Inspiral_50.xml",
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

        List<String> daxPathList1 = List.of(
//                "data/dax/Inspiral_1000.xml",
//                "data/dax/Inspiral_100.xml",
                "data/dax/Inspiral_50.xml",
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

        VMS = 100;

        List<Datacenter> datacenterList = ExperimentUtil.createDatacenters();

        WorkflowBroker broker = new WorkflowBroker(HEFTPlanner.class);
        List<Vm> vmList = ExperimentUtil.createVms(broker.getId());
        broker.submitGuestList(vmList);
        List<Workflow> workflowList = daxPathList.stream().map(WorkflowParser::parse).toList();
        broker.submitWorkflowList(workflowList);

        WorkflowBroker broker1 = new WorkflowBroker(HEFTPlanner.class);
        List<Vm> vmList1 = ExperimentUtil.createVms(broker1.getId());
        broker1.submitGuestList(vmList1);
        List<Workflow> workflowList1 = daxPathList1.stream().map(WorkflowParser::parse).toList();
        broker1.submitWorkflowList(workflowList1);

        CloudSim.startSimulation();

        List<Job> cloudletReceivedList = broker.getCloudletReceivedList();
        ExperimentUtil.printSimResult(cloudletReceivedList,  broker.getName());
        ExperimentUtil.generateSimGanttData(cloudletReceivedList, broker.getName());

        List<Job> cloudletReceivedList1 = broker1.getCloudletReceivedList();
        ExperimentUtil.printSimResult(cloudletReceivedList1,  broker1.getName());
        ExperimentUtil.generateSimGanttData(cloudletReceivedList1, broker1.getName());



        String className = new Object() {
        }.getClass().getEnclosingClass().getSimpleName();
        System.out.println(className + " task " + (System.currentTimeMillis() - send) / 1000.0 + "s");
    }
}
