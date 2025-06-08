package com.qiujie.comparator;

import com.qiujie.entity.Workflow;

import java.util.Comparator;

public class JobNumComparator implements WorkflowComparatorInterface {
    @Override
    public Comparator<Workflow> get(boolean ascending) {
        Comparator<Workflow> comparator = Comparator.comparingInt(Workflow::getJobNum);
        return ascending ? comparator : comparator.reversed();
    }
}
