package com.qiujie.planner;

import cn.hutool.log.StaticLog;
import com.qiujie.entity.*;
import com.qiujie.util.ExperimentUtil;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
import java.util.stream.Collectors;

import static com.qiujie.Constants.*;

/**
 * Energy-Aware Cloud Workflow Applications Scheduling With Geo-Distributed Data
 */

public class ECWSDPlanner extends WorkflowPlannerAbstract {

    private final double τ = 0.6;

    // record local data transfer time
    private Map<Job, Map<Vm, Double>> localDataTransferTimeMap;
    private Map<Job, Double> eftMap;
    private Map<Job, Double> aftMap;
    private Map<Job, Double> upwardRankMap;
    private Map<Job, Double> downwardRankMap;
    private Map<Job, Double> subDeadlineMap;
    private Map<Workflow, Double> workflowEftMap;
    private Map<Datacenter, List<Vm>> dcVmsMap;


    public void initialize() {
        dcVmsMap = getVmList().stream().collect(Collectors.groupingBy(Vm::getDatacenter));
        localDataTransferTimeMap = new HashMap<>();
        eftMap = new HashMap<>();
        workflowEftMap = new HashMap<>();
        upwardRankMap = new HashMap<>();
        downwardRankMap = new HashMap<>();
        for (Workflow workflow : getWorkflowList()) {
            Map<Job, Double> avgLocalDataTransferTimeMap = calculateAvgLocalDataTransferTime(workflow);
            Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = calculateAvgPredecessorDataTransferTime(workflow);
            double mips = getVmList().stream().mapToDouble(Vm::getMips).max().getAsDouble();
            double eft = calculateEFT(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, mips, workflow);
            workflowEftMap.put(workflow, eft);
            calculateUpwardRank(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, mips, workflow);
            calculateDownwardRank(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, mips, workflow);
        }
        // ssf
        getWorkflowList().sort(Comparator.comparingDouble(workflowEftMap::get));
    }


    /**
     * The main function
     */
    @Override
    public void run() {
        initialize();
        for (Workflow workflow : getWorkflowList()) {
            calculateDeadline(workflow);
            calculateExecutionTimeAndReliability(workflow);
            allocateJobs(workflow);
        }
    }


    /**
     * calculate predicted average local data transfer time
     */
    private Map<Job, Double> calculateAvgLocalDataTransferTime(Workflow workflow) {
        Map<Job, Double> avgLocalDataTransferTimeMap = new HashMap<>();
        int vmNum = getVmList().size();
        for (Job job : workflow.getJobList()) {
            double total = 0.0;
            localDataTransferTimeMap.put(job, new HashMap<>());
            for (Vm vm : getVmList()) {
                double temp = ExperimentUtil.calculateLocalDataTransferTime(job, (Host) vm.getHost());
                localDataTransferTimeMap.get(job).put(vm, temp);
                total += temp;
            }
            double avgTime = total / vmNum;
            avgLocalDataTransferTimeMap.put(job, avgTime);
        }
        return avgLocalDataTransferTimeMap;
    }

    /**
     * calculate predicted EFT
     *
     * @param avgLocalDataTransferTimeMap
     * @param avgPredecessorDataTransferTimeMap
     * @param mips
     * @param workflow
     * @return
     */
    private double calculateEFT(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double mips, Workflow workflow) {
        List<Job> list = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth)).toList();
        double maxEFT = 0;
        for (Job job : list) {
            double max = 0.0;
            for (Job parent : job.getParentList()) {
                // check whether the eft of parent job has been calculated
                if (!eftMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                }
                double temp = eftMap.get(parent) + avgPredecessorDataTransferTimeMap.get(job).get(parent);
                max = Math.max(max, temp);
            }
            double eft = max + avgLocalDataTransferTimeMap.get(job) + job.getLength() / mips;
            eftMap.put(job, eft);
            maxEFT = Math.max(maxEFT, eft);
        }
        return maxEFT;
    }

    /**
     * calculate upward rank
     *
     * @param avgLocalDataTransferTimeMap
     * @param avgPredecessorDataTransferTimeMap
     * @param mips
     * @param workflow
     */
    private void calculateUpwardRank(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double mips, Workflow workflow) {
        List<Job> list = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth).reversed()).toList();
        for (Job job : list) {
            double max = 0.0;
            for (Job child : job.getChildList()) {
                // check whether the upward rank of child job has been calculated
                if (!upwardRankMap.containsKey(child)) {
                    throw new IllegalStateException(String.format("Child job #%d upward rank has not been calculated!", child.getCloudletId()));
                }
                double temp = upwardRankMap.get(child) + avgPredecessorDataTransferTimeMap.get(child).get(job);
                max = Math.max(max, temp);
            }
            double upwardRank = max + avgLocalDataTransferTimeMap.get(job) + job.getLength() / mips;
            upwardRankMap.put(job, upwardRank);
        }
    }


    /**
     * calculate downward rank
     *
     * @param avgLocalDataTransferTimeMap
     * @param avgPredecessorDataTransferTimeMap
     * @param mips
     * @param workflow
     */
    private void calculateDownwardRank(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double mips, Workflow workflow) {
        List<Job> list = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth)).toList();
        for (Job job : list) {
            double max = 0.0;
            for (Job parent : job.getParentList()) {
                // check whether the downward rank of parent job has been calculated
                if (!downwardRankMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d downward rank has not been calculated!", parent.getCloudletId()));
                }
                double temp = downwardRankMap.get(parent) + avgPredecessorDataTransferTimeMap.get(job).get(parent) + avgLocalDataTransferTimeMap.get(parent) + parent.getLength() / mips;
                max = Math.max(max, temp);
            }
            downwardRankMap.put(job, max);
        }
    }

    /**
     * calculate deadline based on depth
     */
    private void calculateDeadline(Workflow workflow) {
        double eft = workflowEftMap.get(workflow);
//        double slackTime = (getFinishTime() + eft) * SLACK_TIME_FACTOR;
        double slackTime = eft * SLACK_TIME_FACTOR;
        workflow.setDeadline(getFinishTime() + eft + slackTime);
        subDeadlineMap = new HashMap<>();
        for (Job job : workflow.getJobList()) {
            double subDeadline = getFinishTime() + eftMap.get(job) + slackTime * (job.getDepth() + 1) / (workflow.getDepth() + 1);
            subDeadlineMap.put(job, subDeadline);
        }
    }


    /**
     * allocate jobs
     */
    private void allocateJobs(Workflow workflow) {
        StaticLog.info("{}: Starting planning workflow #{} {}, a total of {} Jobs...", CloudSim.clock(), workflow.getId(), workflow.getName(), workflow.getJobNum());
        // construct initial sequence
        List<Job> initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(job -> upwardRankMap.get(job) + workflowEftMap.get(workflow) - downwardRankMap.get(job)).reversed()).toList();
        List<Job> candiateJobList = initialSequence.stream().filter(job -> job.getChildList().size() > 1).toList();
        List<Job> sequence = new ArrayList<>(initialSequence);
        Solution bestSolution = null;
        Map<Vm, List<ExecWindow>> bestExecWindowMap = null;
        aftMap = new HashMap<>();
        int kmax = Math.min((int) (τ * sequence.size()), candiateJobList.size());
        int k = 0;
        while (k < kmax) {
            // copy execWindowMap
            Map<Vm, List<ExecWindow>> execWindowMap = new HashMap<>();
            for (Map.Entry<Vm, List<ExecWindow>> entry : getExecWindowMap().entrySet()) {
                execWindowMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            aftMap.clear();
            Solution solution = new Solution();
            double elecCost = 0;
            double reliability = 1;
            double finishTime = 0;
            List<Job> scheduleSequence = new ArrayList<>();
            while (scheduleSequence.size() < sequence.size()) {
                for (Job job : sequence) {
                    if (scheduleSequence.contains(job) || !new HashSet<>(scheduleSequence).containsAll(job.getParentList())) {
                        continue;
                    }
                    scheduleSequence.add(job);
                    elecCost += allocateJob(job, solution, execWindowMap);
                    reliability *= reliabilityMap.get(job).get(solution.getResult().get(job));
                    finishTime = Math.max(finishTime, aftMap.get(job));
                }
            }

            if (isNotTopologicalOrder(scheduleSequence)) {
                throw new IllegalStateException("Not a topological order!");
            }

            solution.setSequence(scheduleSequence);
            solution.setElecCost(elecCost);
            solution.setReliability(reliability);
            solution.setFinishTime(finishTime);
            boolean feasible = solution.getFinishTime() <= workflow.getDeadline();
            boolean isBetter = bestSolution == null || solution.getElecCost() < bestSolution.getElecCost();
            if (feasible && isBetter) {
                bestSolution = solution;
                bestExecWindowMap = execWindowMap;
            } else {
                k++;
            }
            StaticLog.debug(String.format("%.2f: %s: Deadline: %.2f, Kmax: %d, K: %d, %s", CloudSim.clock(), workflow.getName(), workflow.getDeadline(), kmax, k, solution));
            // VND start
            sequence = new ArrayList<>(bestSolution == null ? initialSequence : bestSolution.getSequence());
            List<Job> remainingCandiateJobList = new ArrayList<>(candiateJobList);
            for (int j = 0; j < k; j++) {
                int index = ExperimentUtil.getRandomValue(remainingCandiateJobList.size());
                remainingCandiateJobList.remove(index);
            }
            for (Job job : remainingCandiateJobList) {
                List<Job> childList = job.getChildList();
                Job child1 = childList.get(ExperimentUtil.getRandomValue(childList.size()));
                Job child2 = childList.get(ExperimentUtil.getRandomValue(childList.size()));
                while (child1.equals(child2)) {
                    child2 = childList.get(ExperimentUtil.getRandomValue(childList.size()));
                }
                Collections.swap(sequence, sequence.indexOf(child1), sequence.indexOf(child2));
            }
            // VND end
        }
        if (bestSolution == null) {
            throw new IllegalStateException(String.format(
                    "No feasible solution found for workflow #%d (%s). Consider relaxing SLACK_TIME_FACTOR (%.2f).",
                    workflow.getId(), workflow.getName(), SLACK_TIME_FACTOR));
        } else {
            for (Job job : bestSolution.getSequence()) {
                Fv fv = bestSolution.getResult().get(job);
                job.setFv(fv);
                job.setGuestId(fv.getVm().getId());
                job.setVm(fv.getVm());
            }
            getSequence().addAll(bestSolution.getSequence());
            setExecWindowMap(bestExecWindowMap);
            setElecCost(getElecCost() + bestSolution.getElecCost());
            setFinishTime(Math.max(getFinishTime(), bestSolution.getFinishTime()));
            StaticLog.debug(String.format("%.2f: %s: Best %s", CloudSim.clock(), workflow.getName(), bestSolution));
        }
    }


    /**
     * allocate job
     *
     * @return elecCost
     */
    private double allocateJob(Job job, Solution solution, Map<Vm, List<ExecWindow>> execWindowMap) {
        // 电费的开始计算时间
        double beginTime = job.getParentList().isEmpty() ? 0 : job.getParentList().stream().mapToDouble(aftMap::get).min().getAsDouble();
        List<Datacenter> datacenterList = constructDcSequence(beginTime);
        DvfsVm worstVm = (DvfsVm) getVmList().stream().min(Comparator.comparingDouble(vm -> {
            DvfsVm dvfsVm = (DvfsVm) vm;
            Fv fv = dvfsVm.getFvList().getFirst();
            return fv.getMips() / fv.getPower();
        })).get();
        Fv bestFv = worstVm.getFvList().getFirst();
        double bestReadyTime = 0;
        double max = 0;
        for (Job parent : job.getParentList()) {
            if (!aftMap.containsKey(parent)) {
                throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
            }
            max = Math.max(max, aftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) worstVm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
        }
        double readyTime = max + localDataTransferTimeMap.get(job).get(worstVm);
        double eft = findEFT(job, bestFv, readyTime, execTimeMap, false, execWindowMap);
        WorkflowDatacenter dc = (WorkflowDatacenter) worstVm.getDatacenter();
        List<Double> elecPrice = dc.getElecPrice();
        double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, readyTime, bestFv.getPower());
        double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(bestFv), eft, bestFv.getPower());
        double elecCost = transferElecCost + execElecCost;
        double bestElecCost = elecCost;
        for (Datacenter datacenter : datacenterList) {
            dc = (WorkflowDatacenter) datacenter;
            elecPrice = dc.getElecPrice();
            List<Vm> vmList = dcVmsMap.get(datacenter);
            for (Vm vm : vmList) {
                DvfsVm dvfsVm = (DvfsVm) vm;
                max = 0;
                for (Job parent : job.getParentList()) {
                    if (!aftMap.containsKey(parent)) {
                        throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                    }
                    max = Math.max(max, aftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) dvfsVm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
                }
                readyTime = max + localDataTransferTimeMap.get(job).get(dvfsVm);
                Fv fv = dvfsVm.getFvList().getFirst();
                eft = findEFT(job, fv, readyTime, execTimeMap, false, execWindowMap);
                if (eft <= subDeadlineMap.get(job)) {
                    transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, readyTime, fv.getPower());
                    execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(fv), eft, fv.getPower());
                    elecCost = transferElecCost + execElecCost;
                    if (elecCost < bestElecCost) {
                        bestFv = fv;
                        bestReadyTime = readyTime;
                        bestElecCost = elecCost;
                    }
                }
            }
        }
        aftMap.put(job, findEFT(job, bestFv, bestReadyTime, execTimeMap, true, execWindowMap));
        solution.bindJobToFv(job, bestFv);
        return bestElecCost;
    }

    private List<Datacenter> constructDcSequence(double startTime) {
        double currentTime = startTime / 3600;
        return dcVmsMap.keySet().stream().sorted(Comparator.comparingDouble(datacenter -> {
            WorkflowDatacenter dc = (WorkflowDatacenter) datacenter;
            List<Double> elecPrice = dc.getElecPrice();
            int hourIndex = (int) Math.floor(currentTime) % elecPrice.size();
            return elecPrice.get(hourIndex);
        })).toList();
    }
}
