package com.qiujie.core;

import static com.qiujie.Constants.*;

import cn.hutool.log.StaticLog;
import com.qiujie.entity.Job;
import com.qiujie.util.ExperimentUtil;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.List;

public class DvfsCloudletSchedulerSpaceShared extends CloudletSchedulerSpaceShared {


    @Override
    public double updateCloudletsProcessing(double currentTime, List<Double> mipsShare) {
        setCurrentMipsShare(mipsShare);

        double timeSpan = currentTime - getPreviousTime(); // time since last update

        // Update cloudlets in exec list
        for (Cloudlet cl : getCloudletExecList()) {

            cl.updateCloudlet(null);

            Job job = (Job) cl;
            WorkflowDatacenter dc = (WorkflowDatacenter) job.getFv().getVm().getDatacenter();
            job.updateElecCost(ExperimentUtil.calculateElecCost(dc.getElecPrice(), getPreviousTime(), currentTime, job.getFv().getPower()));
            double totalCurrentAllocatedMips = getTotalCurrentAllocatedMipsForCloudlet(cl, currentTime);
            double prevFinishedLength = job.getCloudletFinishedSoFar();
            cl.updateCloudletFinishedSoFar((long) (timeSpan * totalCurrentAllocatedMips * Consts.MILLION));
            // assume no transient fault in the file transfer stage
            double transferLength = job.getFileTransferTime() * totalCurrentAllocatedMips * Consts.MILLION;
            double execTimeSpan = timeSpan;
            if (prevFinishedLength < transferLength) {
                execTimeSpan = Math.max(0, timeSpan - (transferLength - prevFinishedLength) / (totalCurrentAllocatedMips * Consts.MILLION));
            }
            double reliability = Math.exp(-job.getFv().getLambda() * execTimeSpan);
            if (RANDOM.sample() < 1 - reliability && job.canRetry()) {
                job.setCloudletLength(job.getCloudletFinishedSoFar() / Consts.MILLION + job.getLength());
                job.updateRetryCount();
                StaticLog.warn("{}: Retry {} for Job #{} {}", CloudSim.clock(), job.getRetryCount(), job.getCloudletId(), job.getName());
            }
        }

        // Remove finished cloudlets
        for (Cloudlet cl : getCloudletExecList()) {
            if (cl.isFinished()) {
                cloudletJustFinishedList.add(cl);
                cloudletFinish(cl);
            }
        }
        getCloudletExecList().removeAll(cloudletJustFinishedList);

        if (getCloudletExecList().isEmpty() && getCloudletWaitingList().isEmpty()) {
            setPreviousTime(currentTime);
            return 0.0;
        }


        // Update cloudlets in waiting list, if any
        updateWaitingCloudlets(currentTime, null);
        cloudletJustFinishedList.clear();

        // estimate finish time of cloudlets in the execution queue
        double nextEvent = Double.MAX_VALUE;
        for (Cloudlet cl : getCloudletExecList()) {
            double estimatedFinishTime = getEstimatedFinishTime(cl, currentTime);
            if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
                estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
            }
            if (estimatedFinishTime < nextEvent) {
                nextEvent = estimatedFinishTime;
            }
        }

        setPreviousTime(currentTime);
        return nextEvent;
    }


    @Override
    public double cloudletSubmit(Cloudlet cl, double fileTransferTime) {

        Job job = (Job) cl;

        // calculate the expected time for cloudlet completion
        double capacity = getCapacity(job);

        // use the current capacity to estimate the extra amount of
        // time to file transferring. It must be added to the cloudlet length
        double extraSize = capacity * fileTransferTime;
        long length = (long) (job.getLength() + extraSize);
        job.setCloudletLength(length);

        if ((getCurrentPEs() - usedPes) >= cl.getNumberOfPes()) { // it can go to the exec list
            cl.updateStatus(Cloudlet.CloudletStatus.INEXEC);
            getCloudletExecList().add(cl);
            usedPes += cl.getNumberOfPes();
        } else {// no enough free PEs: go to the waiting queue
            cl.updateStatus(Cloudlet.CloudletStatus.QUEUED);
            getCloudletWaitingList().add(cl);
            return 0.0;
        }

        return cl.getCloudletLength() / capacity;
    }


    @Override
    public double getTotalCurrentAvailableMipsForCloudlet(Cloudlet cl, List<Double> mipsShare) {
        return getCapacity((Job) cl) * cl.getNumberOfPes();
    }

    private double getCapacity(Job job) {
        return job.getFv().getMips();
    }
}
