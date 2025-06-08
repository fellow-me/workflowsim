package com.qiujie.comparator;

import com.qiujie.entity.Workflow;

import java.util.Comparator;

public class DepthComparator implements WorkflowComparatorInterface {
    @Override
    public Comparator<Workflow> get(boolean ascending) {
        Comparator<Workflow> comparator = Comparator.comparingInt(Workflow::getDepth);
        return ascending ? comparator : comparator.reversed();
    }
}
