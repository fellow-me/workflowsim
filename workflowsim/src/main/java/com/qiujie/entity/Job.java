package com.qiujie.entity;

import com.qiujie.Constants;
import lombok.Getter;
import lombok.Setter;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


@Getter
public class Job extends Cloudlet {

    private static final AtomicInteger nextId = new AtomicInteger(0);

    private String name;

    private List<Job> parentList;

    private List<Job> childList;

    private List<File> predInputFileList;

    private List<File> localInputFileList;

    private List<File> outputFileList;

    // the original length of job
    private long length;

    @Setter
    private double fileTransferTime;

    private int retryCount;

    private double elecCost;

    @Setter
    private int depth;

    @Setter
    private Fv fv;

    @Setter
    private Vm vm;

    private Job(int cloudletId, long length, int pesNumber, long cloudletFileSize, long cloudletOutputSize, UtilizationModel utilizationModelCpu, UtilizationModel utilizationModelRam, UtilizationModel utilizationModelBw, boolean record) {
        super(cloudletId, length, pesNumber, cloudletFileSize, cloudletOutputSize, utilizationModelCpu, utilizationModelRam, utilizationModelBw, false);
    }

    public Job(String name, long length) {
        this(nextId.getAndIncrement(), length, 1, 1, 1, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull(), false);
        this.name = name;
        this.length = length;
        this.retryCount = 0;
        this.elecCost = 0;
        this.parentList = new ArrayList<>();
        this.childList = new ArrayList<>();
        this.predInputFileList = new ArrayList<>();
        this.localInputFileList = new ArrayList<>();
        this.outputFileList = new ArrayList<>();


        this.count = 0;
    }

    public void updateRetryCount() {
        this.retryCount++;
    }

    public void updateElecCost(double elecCost) {
        this.elecCost += elecCost;
    }

    private int count;

    @Override
    public boolean updateCloudlet(Object info) {
        count++;
        return true;
    }

    public boolean canRetry() {
        return this.retryCount < Constants.MAX_RETRY_COUNT;
    }

    public void addChild(Job job) {
        if (!this.childList.contains(job)) {
            this.childList.add(job);
        }
    }

    public void addParent(Job job) {
        if (!this.parentList.contains(job)) {
            this.parentList.add(job);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(getCloudletId(), job.getCloudletId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getCloudletId());
    }

    @Override
    public String toString() {
        return "Job{" +
                "cloudletId=" + getCloudletId() +
                ", name='" + name + '\'' +
                ", depth=" + depth +
                ", childList=" + childList +
                '}';
    }
}
