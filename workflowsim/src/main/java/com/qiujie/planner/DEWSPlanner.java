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
 * Deadline-constrained energy-aware workflow scheduling in geographically distributed cloud data centers
 */

public class DEWSPlanner extends WorkflowPlannerAbstract {

    private final double γ = 0.6;
    private final double β = 0.8;

    // record local data transfer time
    private Map<Job, Map<Vm, Double>> localDataTransferTimeMap;
    private Map<Job, Double> eftMap;
    private Map<Job, Double> subDeadlineMap;
    private Map<Vm, Double> vmIpwMap;
    private Map<Datacenter, List<Vm>> dcVmsMap;

    public void initialize() {
        vmIpwMap = getVmList().stream().collect(Collectors.toMap(
                vm -> vm,
                vm -> {
                    DvfsVm dvfsVm = (DvfsVm) vm;
                    List<Fv> fvList = dvfsVm.getFvList();
                    return fvList.stream()
                            .mapToDouble(fv -> calculateIPW(fv.getMips(), dvfsVm.getMips(), fv.getMips(), fv.getPower()))
                            .average()
                            .orElse(0.0);
                }
        ));
        dcVmsMap = getVmList().stream()
                .collect(Collectors.groupingBy(
                        Vm::getDatacenter,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparingDouble(vmIpwMap::get).reversed())
                                        .collect(Collectors.toList())
                        )
                ));

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
     */
    private void calculateEFT(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double avgMips, Workflow workflow) {
        eftMap = new HashMap<>();
        List<Job> list = workflow.getJobList().stream().sorted(Comparator.comparingDouble(Job::getDepth)).toList();
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
            eftMap.put(job, eft);
        }
    }

    /**
     * calculate deadline
     */
    private void calculateDeadline(Workflow workflow) {
        Map<Job, Double> avgLocalDataTransferTimeMap = calculateAvgLocalDataTransferTime(workflow);
        Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = calculateAvgPredecessorDataTransferTime(workflow);
        double avgMips = getVmList().stream().mapToDouble(Vm::getMips).average().getAsDouble();
        calculateEFT(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, avgMips, workflow);
        subDeadlineMap = new HashMap<>();
        for (Job job : workflow.getJobList()) {
            double subDeadline = getFinishTime() + eftMap.get(job) * (1 + SLACK_TIME_FACTOR);
            subDeadlineMap.put(job, subDeadline);
        }
    }

    /**
     * allocate jobs
     */
    private void allocateJobs(Workflow workflow) {
        StaticLog.info("{}: Starting planning workflow #{} {}, a total of {} Jobs...", CloudSim.clock(), workflow.getId(), workflow.getName(), workflow.getJobNum());
        // construct initial sequence
        List<Job> initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(eftMap::get)).toList();
        Map<Integer, List<Job>> depthJobsMap = initialSequence.stream().collect(Collectors.groupingBy(Job::getDepth));
        List<Integer> depthList = depthJobsMap.entrySet().stream().filter(entry -> entry.getValue().size() > 1).map(Map.Entry::getKey).toList();
        List<Job> candidateJobList = depthList.stream().map(depthJobsMap::get).flatMap(List::stream).toList();
        List<Job> sequence = new ArrayList<>(initialSequence);
        Solution bestSolution = null;
        Map<Vm, List<ExecWindow>> bestExecWindowMap = null;
        int kmax = (int) (γ * candidateJobList.size());
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
            List<Job> scheduleSequence = new ArrayList<>();
            while (scheduleSequence.size() < sequence.size()) {
                for (Job job : sequence) {
                    if (scheduleSequence.contains(job) || !new HashSet<>(scheduleSequence).containsAll(job.getParentList())) {
                        continue;
                    }
                    scheduleSequence.add(job);
                    elecCost += allocateJob(job, solution, execWindowMap);
                    reliability *= reliabilityMap.get(job).get(solution.getResult().get(job));
                    finishTime = Math.max(finishTime, eftMap.get(job));
                }
            }

            if (isNotTopologicalOrder(scheduleSequence)) {
                throw new IllegalStateException("Not a topological order!");
            }

            solution.setSequence(scheduleSequence);
            solution.setElecCost(elecCost);
            solution.setReliability(reliability);
            solution.setFinishTime(finishTime);
            if (bestSolution == null || solution.getElecCost() < bestSolution.getElecCost()) {
                bestSolution = solution;
                bestExecWindowMap = execWindowMap;
                k = 0;
            } else {
                k++;
            }
            StaticLog.debug(String.format("%.2f: %s: Kmax: %d, K: %d, %s", CloudSim.clock(), workflow.getName(), kmax, k, solution));
            // VND start
            sequence = new ArrayList<>(bestSolution.getSequence());
            for (int j = 0; j < k; j++) {
                int depth = ExperimentUtil.getRandomElement(depthList);
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
            throw new IllegalStateException(String.format("No feasible solution found for workflow #%d (%s). Consider relaxing SLACK_TIME_FACTOR (%.2f) or lowering RELIABILITY_FACTOR (%.4f).", workflow.getId(), workflow.getName(), SLACK_TIME_FACTOR, RELIABILITY_FACTOR));
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
     * @param job      任务
     * @param solution 解
     * @return 电费
     */
    private double allocateJob(Job job, Solution solution, Map<Vm, List<ExecWindow>> execWindowMap) {
        double beginTime = job.getParentList().isEmpty() ? 0 : job.getParentList().stream().mapToDouble(eftMap::get).min().getAsDouble();
        Fv bestFv = null;
        double bestReadyTime = 0;
        double bestElecCost = Double.MAX_VALUE;
        int Rmax = 3;
        int r = 0;
        while (r < Rmax) {
            Datacenter datacenter = selectDC(beginTime, job, r);
            WorkflowDatacenter dc = (WorkflowDatacenter) datacenter;
            List<Double> elecPrice = dc.getElecPrice();
            List<Vm> vmList = dcVmsMap.get(datacenter);
            Fv betterFv = null;
            double betterReadyTime = 0;
            double betterEft = 0;
            double betterElecCost = Double.MAX_VALUE;
            int i = 0;
            while (i < vmList.size()) {
                DvfsVm dvfsVm = (DvfsVm) vmList.get(i);
                double max = 0;
                for (Job parent : job.getParentList()) {
                    if (!eftMap.containsKey(parent)) {
                        throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                    }
                    max = Math.max(max, eftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) dvfsVm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
                }
                double readyTime = max + localDataTransferTimeMap.get(job).get(dvfsVm);
                Fv fv = dvfsVm.getFvList().getFirst();
                double eft = findEFT(job, fv, readyTime, execTimeMap, false, execWindowMap);
                if (eft <= subDeadlineMap.get(job)) {
                    double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, readyTime, fv.getPower());
                    double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(fv), eft, fv.getPower());
                    double elecCost = transferElecCost + execElecCost;
                    if (elecCost < betterElecCost) {
                        betterFv = fv;
                        betterReadyTime = readyTime;
                        betterEft = eft;
                        betterElecCost = elecCost;
                    }
                    break;
                }
                i++;
            }
            List<DvfsVm> candiateVmList = job.getLocalInputFileList().stream()
                    .flatMap(file -> file.getHost().getGuestList().stream())
                    .filter(guest -> guest instanceof DvfsVm)
                    .map(guest -> (DvfsVm) guest)
                    .filter(vm -> vm.getHost().getDatacenter().equals(datacenter))
                    .toList();
            while (i < vmList.size()) {
                DvfsVm dvfsVm = (DvfsVm) vmList.get(i);
                if (candiateVmList.contains(dvfsVm)) {
                    double max = 0;
                    for (Job parent : job.getParentList()) {
                        if (!eftMap.containsKey(parent)) {
                            throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                        }
                        max = Math.max(max, eftMap.get(parent) + ExperimentUtil.calculatePredecessorDataTransferTime(job, (Host) dvfsVm.getHost(), parent, (Host) solution.getResult().get(parent).getVm().getHost()));
                    }
                    double readyTime = max + localDataTransferTimeMap.get(job).get(dvfsVm);
                    Fv fv = dvfsVm.getFvList().getFirst();
                    double eft = findEFT(job, fv, readyTime, execTimeMap, false, execWindowMap);
                    if (eft <= subDeadlineMap.get(job)) {
                        double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, readyTime, fv.getPower());
                        double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(fv), eft, fv.getPower());
                        double elecCost = transferElecCost + execElecCost;
                        if (elecCost < betterElecCost) {
                            betterFv = fv;
                            betterReadyTime = readyTime;
                            betterEft = eft;
                            betterElecCost = elecCost;
                        }
                        break;
                    }
                }
                i++;
            }
            if (betterFv != null) {
                DvfsVm dvfsVm = (DvfsVm) betterFv.getVm();
                double temp = betterEft;
                List<Double> futureWindowStartTimes = execWindowMap.get(dvfsVm).stream().map(ExecWindow::getStartTime).filter(startTime -> startTime > temp).toList();
                double nextWindowStartTime = futureWindowStartTimes.isEmpty() ? Double.MAX_VALUE : futureWindowStartTimes.getFirst();
                double lft = Math.min(nextWindowStartTime, subDeadlineMap.get(job));
                double startTime = betterEft - execTimeMap.get(job).get(betterFv);
                double timeSpan = lft - β * (lft - betterEft) - startTime;
                List<Fv> fvList = dvfsVm.getFvList();
                betterFv = fvList.getFirst();
                // adjust fv
                for (Fv fv : fvList) {
                    if (execTimeMap.get(job).get(fv) > timeSpan) {
                        break;
                    }
                    betterFv = fv;
                }
                double transferElecCost = ExperimentUtil.calculateElecCost(elecPrice, beginTime, betterReadyTime, betterFv.getPower());
                double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, startTime, startTime + execTimeMap.get(job).get(betterFv), betterFv.getPower());
                betterElecCost = transferElecCost + execElecCost;
                if (betterElecCost < bestElecCost) {
                    bestFv = betterFv;
                    bestReadyTime = betterReadyTime;
                    bestElecCost = betterElecCost;
                    r = 0;
                } else {
                    r++;
                }
            } else {
                r++;
            }

        }
        if (bestFv == null) {
            WorkflowDatacenter datacenter = (WorkflowDatacenter) ExperimentUtil.getRandomElement(dcVmsMap.keySet().stream().toList());
            List<Double> elecPrice = datacenter.getElecPrice();
            DvfsVm vm = (DvfsVm) ExperimentUtil.getRandomElement(dcVmsMap.get(datacenter));
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
    private Datacenter selectDC(double startTime, Job job, int index) {
        Datacenter bestDC = null;
        List<Datacenter> datacenterList = dcVmsMap.keySet().stream().toList();
        if (index == 0) {
            bestDC = ExperimentUtil.getRandomElement(datacenterList);
        } else if (index == 1) {
            double minPrice = Double.MAX_VALUE;
            for (Datacenter datacenter : datacenterList) {
                WorkflowDatacenter dc = (WorkflowDatacenter) datacenter;
                List<Double> elecPrice = dc.getElecPrice();
                // according to avg elec price
                double avgElecPrice = calculateAvgElecPrice(elecPrice, startTime, subDeadlineMap.get(job));
                if (avgElecPrice < minPrice) {
                    minPrice = avgElecPrice;
                    bestDC = datacenter;
                }
            }
        } else {
            List<Datacenter> localDataDacenterList = job.getLocalInputFileList().stream().map(file -> file.getHost().getDatacenter()).distinct().toList();
            if (localDataDacenterList.isEmpty()) {
                bestDC = ExperimentUtil.getRandomElement(datacenterList);
            } else {
                bestDC = ExperimentUtil.getRandomElement(localDataDacenterList);
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
