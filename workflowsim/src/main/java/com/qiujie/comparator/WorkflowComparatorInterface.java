package com.qiujie.comparator;

import com.qiujie.entity.Workflow;

import java.util.Comparator;

public interface WorkflowComparatorInterface {

    /**
     * @param ascending
     * @return
     */
    default Comparator<Workflow> get(boolean ascending) {
        Comparator<Workflow> comparator = Comparator.comparingInt(Workflow::getId);
        return ascending ? comparator : comparator.reversed();
    }
}
