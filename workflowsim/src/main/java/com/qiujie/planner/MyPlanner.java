package com.qiujie.planner;

import cn.hutool.log.StaticLog;
import com.qiujie.entity.*;
import com.qiujie.util.ExperimentUtil;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.qiujie.Constants.*;

public class MyPlanner extends WorkflowPlannerAbstract {

    // record local data transfer time
    private Map<Job, Map<Vm, Double>> localDataTransferTimeMap;
    private Map<Job, Double> eftMap;
    private Map<Job, Double> upwardRankMap;
    private Map<Job, Double> subDeadlineMap;
    private Map<Datacenter, List<Vm>> dcVmsMap;

    public void initialize() {
        dcVmsMap = getVmList().stream().collect(Collectors.groupingBy(Vm::getDatacenter));
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
        localDataTransferTimeMap = new HashMap<>();
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
     * @param avgMips
     * @param workflow
     * @return
     */
    private double calculateEFT(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double avgMips, Workflow workflow) {
        eftMap = new HashMap<>();
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
            double eft = max + avgLocalDataTransferTimeMap.get(job) + job.getLength() / avgMips;
            maxEFT = Math.max(maxEFT, eft);
            eftMap.put(job, eft);
        }
        return maxEFT;
    }

    /**
     * calculate predicted upward rank
     *
     * @param avgLocalDataTransferTimeMap
     * @param avgPredecessorDataTransferTimeMap
     * @param avgMips
     * @param workflow
     * @return
     */
    private double calculateUpwardRank(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double avgMips, Workflow workflow) {
        upwardRankMap = new HashMap<>();
        List<Job> list = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth).reversed()).toList();
        double maxUpwardRank = 0;
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
            double upwardRank = max + avgLocalDataTransferTimeMap.get(job) + job.getLength() / avgMips;
            maxUpwardRank = Math.max(maxUpwardRank, upwardRank);
            upwardRankMap.put(job, upwardRank);
        }
        return maxUpwardRank;
    }

    /**
     * calculate deadline
     *
     * @param workflow
     */
    private void calculateDeadline(Workflow workflow) {
        Map<Job, Double> avgLocalDataTransferTimeMap = calculateAvgLocalDataTransferTime(workflow);
        Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = calculateAvgPredecessorDataTransferTime(workflow);
        double avgMips = getVmList().stream().mapToDouble(Vm::getMips).average().getAsDouble();
        double eft = calculateEFT(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, avgMips, workflow);
        double upwardRank = calculateUpwardRank(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, avgMips, workflow);
//        double slackTime = (getFinishTime() + eft) * SLACK_TIME_FACTOR;
        double slackTime = eft * SLACK_TIME_FACTOR;
        workflow.setDeadline(getFinishTime() + eft + slackTime);
        subDeadlineMap = new HashMap<>();
        for (Job job : workflow.getJobList()) {
            double subDeadline = getFinishTime() + eftMap.get(job) + (upwardRank - upwardRankMap.get(job) + job.getLength() / avgMips + avgLocalDataTransferTimeMap.get(job)) * slackTime / upwardRank;
            subDeadlineMap.put(job, subDeadline);
        }
    }

    /**
     * allocate jobs
     *
     * @param workflow
     */
    private void allocateJobs(Workflow workflow) {
        StaticLog.info("{}: Starting planning workflow #{} {}, a total of {} Jobs...", CloudSim.clock(), workflow.getId(), workflow.getName(), workflow.getJobNum());
        List<Job> initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingInt(Job::getDepth)).toList();
        Map<Integer, List<Job>> depthJobsMap = initialSequence.stream().collect(Collectors.groupingBy(Job::getDepth));
        List<Integer> depthList = depthJobsMap.entrySet().stream().filter(entry -> entry.getValue().size() > 1).map(Map.Entry::getKey).toList();
        List<Job> sequence = new ArrayList<>(initialSequence);
        double avgReliability = Math.pow(workflow.getReliGoal(), 1.0 / sequence.size());
        Solution bestSolution = null;
        Map<Vm, List<ExecWindow>> bestExecWindowMap = null;
        int kmax = depthList.size();
        int k = 0;
        while (k < kmax) {
            // copy execWindowMap
            Map<Vm, List<ExecWindow>> execWindowMap = new HashMap<>();
            for (Map.Entry<Vm, List<ExecWindow>> entry : getExecWindowMap().entrySet()) {
                execWindowMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            eftMap.clear();
            Solution solution = new Solution();
            double elecCost = 0;
            double reliability = 1;
            double finishTime = 0;
            for (int i = 0; i < sequence.size(); i++) {
                Job job = sequence.get(i);
                double subReliabilityGoal = workflow.getReliGoal() / (reliability * Math.pow(avgReliability, sequence.size() - i - 1));
                elecCost += allocateJob(job, subReliabilityGoal, solution, execWindowMap);
                reliability *= reliabilityMap.get(job).get(solution.getResult().get(job));
                finishTime = Math.max(finishTime, eftMap.get(job));
            }

            if (isNotTopologicalOrder(sequence)) {
                throw new IllegalStateException("Not a topological order!");
            }

            solution.setSequence(sequence);
            solution.setElecCost(elecCost);
            solution.setReliability(reliability);
            solution.setFinishTime(finishTime);
            boolean feasible = solution.getReliability() >= workflow.getReliGoal() && solution.getFinishTime() <= workflow.getDeadline();
            boolean isBetter = bestSolution == null || solution.getElecCost() < bestSolution.getElecCost();
            if (feasible) {
                if (isBetter) {
                    bestSolution = solution;
                    bestExecWindowMap = execWindowMap;
                    k = 0;
                } else {
                    k++;
                }
            }

            StaticLog.debug(String.format("%.2f: %s: ReliGoal: %.4f, Deadline: %.2f, Kmax: %d, K: %d, %s", CloudSim.clock(), workflow.getName(), workflow.getReliGoal(), workflow.getDeadline(), kmax, k, solution));
            // VND start
            sequence = new ArrayList<>(bestSolution == null ? initialSequence : bestSolution.getSequence());
            List<Integer> remainingDepthList = new ArrayList<>(depthList);
            for (int j = 0; j < k; j++) {
                int index = ExperimentUtil.getRandomValue(remainingDepthList.size());
                remainingDepthList.remove(index);
            }

            for (Integer depth : remainingDepthList) {
                List<Job> jobList = depthJobsMap.get(depth);
                Job job1 = ExperimentUtil.getRandomElement(jobList);
                Job job2 = ExperimentUtil.getRandomElement(jobList);
                while (job1.equals(job2)) {
                    job2 = ExperimentUtil.getRandomElement(jobList);
                }
                Collections.swap(sequence, sequence.indexOf(job1), sequence.indexOf(job2));
            }
            // VND end

        }
        if (bestSolution == null) {
            throw new IllegalStateException(String.format(
                    "No feasible solution found for workflow #%d (%s). Consider relaxing SLACK_TIME_FACTOR (%.2f) or lowering RELIABILITY_FACTOR (%.4f).",
                    workflow.getId(), workflow.getName(), SLACK_TIME_FACTOR, RELIABILITY_FACTOR));
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
     * 分配任务
     *
     * @param job                任务
     * @param subReliabilityGoal 任务可靠性目标
     * @param solution           解
     * @return 电费
     */
    private double allocateJob(Job job, double subReliabilityGoal, Solution solution, Map<Vm, List<ExecWindow>> execWindowMap) {
        double beginTime = job.getParentList().isEmpty() ? 0 : job.getParentList().stream().mapToDouble(eftMap::get).min().getAsDouble();
        Datacenter datacenter = selectDC(beginTime, job);
        WorkflowDatacenter workflowDatacenter = (WorkflowDatacenter) datacenter;
        List<Double> elecPrice = workflowDatacenter.getElecPrice();
        Fv bestFv = null;
        double bestReadyTime = 0;
        double bestElecCost = Double.MAX_VALUE;
        List<Vm> vmList = dcVmsMap.get(datacenter);
        for (Vm vm : vmList) {
            DvfsVm dvfsVm = (DvfsVm) vm;
            double max = 0;
            for (Job parent : job.getParentList()) {
                if (!eftMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                }
                max = Math.max(max, eftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) dvfsVm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
            }
            double readyTime = max + localDataTransferTimeMap.get(job).get(dvfsVm);
            for (Fv fv : dvfsVm.getFvList()) {
                double reliability = reliabilityMap.get(job).get(fv);
                if (reliability < subReliabilityGoal) {
                    break;
                }
                double eft = findEFT(job, fv, readyTime, execTimeMap, false, execWindowMap);
                if (eft > subDeadlineMap.get(job)) {
                    break;
                }
                double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, readyTime, fv.getPower());
                double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(fv), eft, fv.getPower());
                double elecCost = transferElecCost + execElecCost;
                if (elecCost < bestElecCost) {
                    bestFv = fv;
                    bestReadyTime = readyTime;
                    bestElecCost = elecCost;
                }
            }
        }
        if (bestFv == null) {
            DvfsVm vm = (DvfsVm) ExperimentUtil.getRandomElement(vmList);
            double max = 0;
            for (Job parent : job.getParentList()) {
                if (!eftMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                }
                max = Math.max(max, eftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) vm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
            }
            double readyTime = max + localDataTransferTimeMap.get(job).get(vm);
            bestFv = vm.getFvList().getFirst();
            double eft = findEFT(job, bestFv, readyTime, execTimeMap, false, execWindowMap);
            double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, readyTime, bestFv.getPower());
            double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(bestFv), eft, bestFv.getPower());
            bestReadyTime = readyTime;
            bestElecCost = transferElecCost + execElecCost;
        }
        eftMap.put(job, findEFT(job, bestFv, bestReadyTime, execTimeMap, true, execWindowMap));
        solution.bindJobToFv(job, bestFv);
        return bestElecCost;
    }


    /**
     * 选择DC
     *
     * @param startTime 电费的开始计费时间
     * @param job       任务
     * @return
     */
    private Datacenter selectDC(double startTime, Job job) {
        Datacenter bestDC = null;
        double minPrice = Double.MAX_VALUE;
        for (Datacenter datacenter : dcVmsMap.keySet()) {
            WorkflowDatacenter dc = (WorkflowDatacenter) datacenter;
            List<Double> elecPrice = dc.getElecPrice();

            // according to avg elec price
//            double avgElecPrice = calculateAvgElecPrice(elecPrice, startTime, subDeadlineMap.get(job));
//            if (avgElecPrice < minPrice) {
//                minPrice = avgElecPrice;
//                bestDC = datacenter;
//            }

            double startHour = startTime / 3600;
            int hourIndex = (int) Math.floor(startHour) % 12;
            Double price = elecPrice.get(hourIndex);
            if (price < minPrice) {
                minPrice = price;
                bestDC = datacenter;
            }

        }
        return bestDC;
    }


    /**
     * 计算一段时间内的平均电价
     *
     * @param elecPrice 电价
     * @param startTime 开始时间（s）
     * @param endTime   结束时间（s）
     * @return
     */
    private static double calculateAvgElecPrice(List<Double> elecPrice, double startTime, double endTime) {
        double totalCost = 0.0;
        double currentTime = startTime / 3600;
        double duration = (endTime - startTime) / 1000;
        double remainingDuration = duration;
        while (remainingDuration > 0) {
            int hourIndex = (int) Math.floor(currentTime) % elecPrice.size();
            double nextHourTime = Math.floor(currentTime) + 1.0;
            double availableDuration = Math.min(nextHourTime - currentTime, remainingDuration);
            totalCost += availableDuration * elecPrice.get(hourIndex);
            currentTime += availableDuration;
            remainingDuration -= availableDuration;
        }
        return totalCost / duration;
    }


}
