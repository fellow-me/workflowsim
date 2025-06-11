package com.qiujie.example;

import ch.qos.logback.classic.Level;
import com.qiujie.entity.ClockModifier;
import com.qiujie.entity.Job;
import com.qiujie.entity.Workflow;
import com.qiujie.entity.WorkflowBroker;
import com.qiujie.planner.RandomPlanner;
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
 * @author QIUJIE
 * <p>
 * schedule a workflow to mutil datacenters
 */
public class Example01 {
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
            String daxPath = "data/dax/CyberShake_100.xml";
//            String daxPath = "data/dax/Epigenomics_46.xml";
//            String daxPath = "data/dax/Montage_50.xml";
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
        WorkflowBroker broker = new WorkflowBroker(RandomPlanner.class);
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
