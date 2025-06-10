package com.qiujie.planner;

import cn.hutool.log.StaticLog;
import com.qiujie.entity.*;
import com.qiujie.util.ExperimentUtil;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
import java.util.stream.Collectors;

import static com.qiujie.Constants.*;


/**
 * An electricity price and energy-efficient workflow scheduling in geographically distributed cloud data centers
 */

public class EPEEPlanner extends WorkflowPlannerAbstract {

    private final double β = 0.8;

    // record local data transfer time
    private Map<Job, Map<Vm, Double>> localDataTransferTimeMap;
    private Map<Job, Double> eftMap;
    private Map<Job, Double> upwardRankMap;
    private Map<Job, Double> subDeadlineMap;
    private Map<Vm, Double> vmIpwMap;
    private Map<Datacenter, List<Vm>> dcVmsMap;
    private Map<Datacenter, List<Vm>> dcVmTypesMap;
    private Map<Datacenter, Double> dcIpwMap;
    private Map<Datacenter, Double> dcIppMap;

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

        dcVmsMap = getVmList().stream().collect(Collectors.groupingBy(
                Vm::getDatacenter,
                Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> list.stream()
                                .sorted(Comparator.comparingDouble(vmIpwMap::get).reversed())
                                .collect(Collectors.toList())
                )
        ));


        dcVmTypesMap = dcVmsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .collect(Collectors.collectingAndThen(
                                        Collectors.toMap(
                                                vm -> ((DvfsVm) vm).getType(), // type 作为 key
                                                vm -> vm,                      // 保留 VM
                                                (v1, v2) -> v1                 // 冲突时保留第一个
                                        ),
                                        map -> new ArrayList<>(map.values()) // 取出唯一类型的 VM
                                ))
                ));


        dcIpwMap = dcVmTypesMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<Vm> vmList = entry.getValue(); // 获取每种类型唯一 VM 列表
                            return vmList.stream()
                                    .mapToDouble(vm -> vmIpwMap.getOrDefault(vm, 0.0))
                                    .average()
                                    .orElse(0.0); // 如果 VM 列表为空，则默认值为 0.0
                        }
                ));


        dcIppMap = new HashMap<>();
    }

    /**
     * The main function
     */
    @Override
    public void run() {
        initialize();
        for (Workflow workflow : getWorkflowList()) {
            calculateIpp();
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
                double temp = calculateLocalDataTransferTime(job, vm);
                localDataTransferTimeMap.get(job).put(vm, temp);
                total += temp;
            }
            double avgTime = total / vmNum;
            avgLocalDataTransferTimeMap.put(job, avgTime);
        }
        return avgLocalDataTransferTimeMap;
    }

    @Override
    protected Map<Job, Map<Job, Double>> calculateAvgPredecessorDataTransferTime(Workflow workflow) {
        Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = new HashMap<>();
        int vmNum = getVmList().size();
        for (Job job : workflow.getJobList()) {
            avgPredecessorDataTransferTimeMap.put(job, new HashMap<>());
            for (Job parentJob : job.getParentList()) {
                double total = 0.0;
                for (Vm vm : getVmList()) {
                    for (Vm parentVm : getVmList()) {
                        double temp = calculatePredecessorDataTransferTime(job, vm, parentJob, parentVm);
                        total += temp;
                    }
                }
                double avgTime = total / (vmNum * vmNum);
                avgPredecessorDataTransferTimeMap.get(job).put(parentJob, avgTime);
            }
        }
        return avgPredecessorDataTransferTimeMap;
    }

    /**
     * calculate  EFT
     */
    private double calculateEFT(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double mips, Workflow workflow) {
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
            double eft = max + avgLocalDataTransferTimeMap.get(job) + job.getLength() / mips;
            maxEFT = Math.max(maxEFT, eft);
            eftMap.put(job, eft);
        }
        return maxEFT;
    }

    /**
     * calculate upward rank
     */
    private void calculateUpwardRank(Map<Job, Double> avgLocalDataTransferTimeMap, Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap, double mips, Workflow workflow) {
        upwardRankMap = new HashMap<>();
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
     * calculate deadline
     */
    private void calculateDeadline(Workflow workflow) {
        Map<Job, Double> avgLocalDataTransferTimeMap = calculateAvgLocalDataTransferTime(workflow);
        Map<Job, Map<Job, Double>> avgPredecessorDataTransferTimeMap = calculateAvgPredecessorDataTransferTime(workflow);
        double mips = getVmList().stream().mapToDouble(Vm::getMips).max().getAsDouble();
        double eft = calculateEFT(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, mips, workflow);
        calculateUpwardRank(avgLocalDataTransferTimeMap, avgPredecessorDataTransferTimeMap, mips, workflow);
//        double slackTime = (getFinishTime() + eft) * SLACK_TIME_FACTOR;
        double slackTime = eft * SLACK_TIME_FACTOR;
        workflow.setDeadline(getFinishTime() + eft + slackTime);
        subDeadlineMap = new HashMap<>();
        List<Job> jobList = workflow.getJobList().stream().sorted(Comparator.comparingDouble(upwardRankMap::get).reversed()).toList();
        int size = jobList.size();
        for (int i = 0; i < size; i++) {
            Job job = jobList.get(i);
            double subDeadline = getFinishTime() + eftMap.get(job) + (i + 1) * slackTime / size;
            subDeadlineMap.put(job, subDeadline);
        }
    }


    private void calculateIpp() {
        dcIppMap.clear();
        dcVmsMap.forEach((datacenter, vms) -> {
            double est = vms.stream()
                    .map(vm -> getExecWindowMap().get(vm))
                    .filter(list -> list != null && !list.isEmpty())
                    .mapToDouble(list -> list.getLast().getFinishTime())
                    .min()
                    .orElse(0.0);
            WorkflowDatacenter dc = (WorkflowDatacenter) datacenter;
            List<Double> elecPrice = dc.getElecPrice();
            if (elecPrice == null || elecPrice.isEmpty()) return;
            int hourIndex = (int) (Math.floor(est / 3600)) % elecPrice.size();
            double price = elecPrice.get(hourIndex);
            if (price == 0) return;
            dcIppMap.put(dc, dcIpwMap.get(dc) / price);
        });
    }


    /**
     * allocate jobs
     *
     * @param workflow
     */
    private void allocateJobs(Workflow workflow) {
        StaticLog.info("{}: Starting planning workflow #{} {}, a total of {} Jobs...", CloudSim.clock(), workflow.getId(), workflow.getName(), workflow.getJobNum());
        List<Job> initialSequence = workflow.getJobList().stream().sorted(Comparator.comparingDouble(upwardRankMap::get).reversed()).toList();
        List<Job> sequence = new ArrayList<>(initialSequence);
        Solution bestSolution = null;
        Map<Vm, List<ExecWindow>> bestExecWindowMap = null;
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

        if (solution.getFinishTime() <= workflow.getDeadline()) {
            bestSolution = solution;
            bestExecWindowMap = execWindowMap;
        }

        StaticLog.debug(String.format("%.2f: %s: Deadline: %.2f, %s", CloudSim.clock(), workflow.getName(), workflow.getDeadline(), solution));


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
            setElecCost(getElecCost() + bestSolution.getElecCost());
            setExecWindowMap(bestExecWindowMap);
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
        Datacenter datacenter = selectDC(beginTime, job, solution);
        WorkflowDatacenter workflowDatacenter = (WorkflowDatacenter) datacenter;
        List<Double> elecPrice = workflowDatacenter.getElecPrice();
        Fv bestFv = null;
        double bestReadyTime = 0;
        double bestElecCost = Double.MAX_VALUE;
        List<Vm> vmList = dcVmsMap.get(datacenter);
        for (Vm vm : vmList) {
            DvfsVm dvfsVm = (DvfsVm) vm;
            double max = 0;
            Map<Job, Double> predecessorDataTransferTimeMap = new HashMap<>();
            for (Job parent : job.getParentList()) {
                if (!eftMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                }
                double predecessorDataTransferTime = calculatePredecessorDataTransferTime(job, dvfsVm, parent, solution.getResult().get(parent).getVm());
                predecessorDataTransferTimeMap.put(parent, predecessorDataTransferTime);
                max = Math.max(max, eftMap.get(parent) + predecessorDataTransferTime);
            }
            double readyTime = max + localDataTransferTimeMap.get(job).get(dvfsVm);
            double eft = findEFT(job, dvfsVm.getFvList().getFirst(), readyTime, execTimeMap, false, execWindowMap);
            if (eft <= subDeadlineMap.get(job)) {
                double temp = eft;
                List<Double> futureWindowStartTimes = execWindowMap.get(dvfsVm).stream().map(ExecWindow::getStartTime).filter(startTime -> startTime > temp).toList();
                double nextWindowStartTime = futureWindowStartTimes.isEmpty() ? Double.MAX_VALUE : futureWindowStartTimes.getFirst();
                double lft = Math.min(nextWindowStartTime, subDeadlineMap.get(job));
                double startTime = eft - execTimeMap.get(job).get(dvfsVm.getFvList().getFirst());
                double timeSpan = lft - β * (lft - eft) - startTime;
                List<Fv> fvList = dvfsVm.getFvList();
                Fv betterFv = fvList.getFirst();
                // adjust fv
                for (Fv fv : fvList) {
                    if (execTimeMap.get(job).get(fv) > timeSpan) {
                        break;
                    }
                    betterFv = fv;
                }
                double power = betterFv.getPower();
                double localDataTransferElecCost = ExperimentUtil.calculateElecCost(elecPrice, readyTime - localDataTransferTimeMap.get(job).get(dvfsVm), readyTime, power);
                double predDatatransferElecCost = job.getParentList().stream().mapToDouble(parent ->
                        ExperimentUtil.calculateElecCost(elecPrice, eftMap.get(parent), eftMap.get(parent) + predecessorDataTransferTimeMap.get(parent), power)).sum();
                double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, startTime, startTime + execTimeMap.get(job).get(betterFv), betterFv.getPower());
                double elecCost = localDataTransferElecCost + predDatatransferElecCost + execElecCost;
                if (elecCost < bestElecCost) {
                    bestFv = betterFv;
                    bestReadyTime = readyTime;
                    bestElecCost = elecCost;
                }
                break;
            }
        }
        if (bestFv == null) {
            DvfsVm vm = (DvfsVm) ExperimentUtil.getRandomElement(vmList);
            double max = 0;
            Map<Job, Double> predecessorDataTransferTimeMap = new HashMap<>();
            for (Job parent : job.getParentList()) {
                if (!eftMap.containsKey(parent)) {
                    throw new IllegalStateException(String.format("Parent job #%d eft has not been calculated!", parent.getCloudletId()));
                }
                double predecessorDataTransferTime = calculatePredecessorDataTransferTime(job, vm, parent, solution.getResult().get(parent).getVm());
                predecessorDataTransferTimeMap.put(parent, predecessorDataTransferTime);
                max = Math.max(max, eftMap.get(parent) + predecessorDataTransferTime);
            }
            double readyTime = max + localDataTransferTimeMap.get(job).get(vm);
            bestFv = vm.getFvList().getFirst();
            double eft = findEFT(job, bestFv, readyTime, execTimeMap, false, execWindowMap);
            double power = bestFv.getPower();
            double localDataTransferElecCost = ExperimentUtil.calculateElecCost(elecPrice, readyTime - localDataTransferTimeMap.get(job).get(vm), readyTime, power);
            double predDataTransferElecCost = job.getParentList().stream().mapToDouble(parent ->
                    ExperimentUtil.calculateElecCost(elecPrice, eftMap.get(parent), eftMap.get(parent) + predecessorDataTransferTimeMap.get(parent), power)).sum();
            double execElecCost = ExperimentUtil.calculateElecCost(elecPrice, eft - execTimeMap.get(job).get(bestFv), eft, bestFv.getPower());
            bestReadyTime = readyTime;
            bestElecCost = localDataTransferElecCost + predDataTransferElecCost + execElecCost;
        }
        eftMap.put(job, findEFT(job, bestFv, bestReadyTime, execTimeMap, true, execWindowMap));
        solution.bindJobToFv(job, bestFv);
        return bestElecCost;
    }

    private Datacenter selectDC(double startTime, Job job, Solution solution) {
        Datacenter bestDC = null;
        double bestElecCost = Double.MAX_VALUE;
        List<Datacenter> datacenterList = dcIppMap.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<Datacenter, Double> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .toList();
        List<Datacenter> localDataDacenterList = job.getLocalInputFileList().stream().map(file -> file.getHost().getDatacenter()).distinct().toList();
        List<Datacenter> predDataDacenterList = job.getParentList().stream().map(parent -> solution.getResult().get(parent).getVm().getDatacenter()).distinct().toList();
        for (int i = 0; i < datacenterList.size(); i++) {
            WorkflowDatacenter dc = (WorkflowDatacenter) datacenterList.get(i);
            if (i == 0 || localDataDacenterList.contains(dc) || predDataDacenterList.contains(dc)) {
                List<Double> elecPrice = dc.getElecPrice();
                double duration = 0;
                double avgMips = dcVmTypesMap.get(dc).stream().mapToDouble(Vm::getMips).average().orElse(0);
                duration += job.getLength() / avgMips;
                for (Job parent : job.getParentList()) {
                    if (!solution.getResult().get(parent).getVm().getDatacenter().equals(dc)) {
                        List<String> parentOutputFiles = parent.getOutputFileList().stream().map(File::getName).toList();
                        double dataSize = job.getPredInputFileList().stream().filter(file -> parentOutputFiles.contains(file.getName())).mapToDouble(File::getSize).sum();
                        duration += dataSize / INTER_BANDWIDTH;
                    }
                }
                duration += job.getLocalInputFileList().stream().mapToDouble(file -> dc.equals(file.getHost().getDatacenter()) ? 0 : file.getSize() / INTER_BANDWIDTH).sum();
                double avgPower = dcVmTypesMap.get(dc).stream().mapToDouble(vm -> ((DvfsVm) vm).getFvList().getFirst().getPower()).average().orElse(0);
                double elecCost = ExperimentUtil.calculateElecCost(elecPrice, startTime, startTime + duration, avgPower);
                if (elecCost < bestElecCost) {
                    bestElecCost = elecCost;
                    bestDC = dc;
                }
            }
        }
        return bestDC;
    }

    public double calculateLocalDataTransferTime(Job job, Vm vm) {
        return job.getLocalInputFileList().stream().mapToDouble(file -> {
            List<Vm> fileVmList = getVmList().stream().filter(v -> v.getHost().getId() == file.getHost().getId()).toList();
            Vm fileVm = ExperimentUtil.getRandomElement(fileVmList);
            // No data transfer time if the job and its parent are on the same vm.
            if (vm.equals(fileVm)) return 0;
            return vm.getDatacenter().getId() == fileVm.getDatacenter().getId() ? file.getSize() / INTRA_BANDWIDTH : file.getSize() / INTER_BANDWIDTH;
        }).sum();
    }

    public double calculatePredecessorDataTransferTime(Job job, Vm vm, Job parentJob, Vm parentVm) {
        // No data transfer time if the job and its parent are on the same vm.
        if (vm.equals(parentVm)) return 0;
        List<String> parentOutputFiles = parentJob.getOutputFileList().stream().map(File::getName).toList();
        double dataSize = job.getPredInputFileList().stream().filter(file -> parentOutputFiles.contains(file.getName())).mapToDouble(File::getSize).sum();
        return vm.getDatacenter().getId() == parentVm.getDatacenter().getId() ? dataSize / INTRA_BANDWIDTH : dataSize / INTER_BANDWIDTH;
    }
}
