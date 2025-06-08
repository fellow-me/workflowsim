package com.qiujie.comparator;

import com.qiujie.entity.Workflow;

import java.util.Comparator;

public class LengthComparator implements WorkflowComparatorInterface {
    @Override
    public Comparator<Workflow> get(boolean ascending) {
        Comparator<Workflow> comparator = Comparator.comparingDouble(Workflow::getLength);
        return ascending ? comparator : comparator.reversed();
    }
}
