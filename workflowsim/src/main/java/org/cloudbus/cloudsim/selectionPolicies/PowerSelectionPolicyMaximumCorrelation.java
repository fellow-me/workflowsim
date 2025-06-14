/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2024, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.selectionPolicies;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.cloudbus.cloudsim.core.PowerGuestEntity;
import org.cloudbus.cloudsim.util.MathUtil;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A VM selection policy that selects for migration the VM with the Maximum Correlation Coefficient (MCC) among
 * a list of migratable VMs.
 *
 * <br/>If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:<br/>
 *
 * <ul>
 * <li><a href="http://dx.doi.org/10.1002/cpe.1867">Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley &amp; Sons, Ltd, New York, USA, 2012</a>
 * </ul>
 *
 * @author Anton Beloglazov
 * @author Remo Andreoli
 * @since CloudSim Toolkit 3.0
 */
public class PowerSelectionPolicyMaximumCorrelation implements SelectionPolicy<PowerGuestEntity> {

	/** The fallback VM selection policy to be used when
         * the  Maximum Correlation policy doesn't have data to be computed. */
	private SelectionPolicy<PowerGuestEntity> fallbackPolicy;

	/**
	 * Instantiates a new PowerSelectionPolicyMaximumCorrelation.
	 *
	 * @param fallbackPolicy the fallback policy
	 */
	public PowerSelectionPolicyMaximumCorrelation(final SelectionPolicy<PowerGuestEntity> fallbackPolicy) {
		super();
		setFallbackPolicy(fallbackPolicy);
	}

	@Override
	public PowerGuestEntity select(List<PowerGuestEntity> candidates, Object host, Set<PowerGuestEntity> excludedCandidates) {
		if (candidates.isEmpty()) {
			return null;
		}

		List<Double> metrics = null;
		try {
			metrics = getCorrelationCoefficients(getUtilizationMatrix(candidates));
		} catch (IllegalArgumentException e) { // the degrees of freedom must be greater than zero
			return getFallbackPolicy().select(candidates, host, excludedCandidates);
		}
		double maxMetric = Double.MIN_VALUE;
		int maxIndex = 0;
		for (int i = 0; i < metrics.size(); i++) {
			double metric = metrics.get(i);
			if (metric > maxMetric) {
				maxMetric = metric;
				maxIndex = i;
			}
		}
		return candidates.get(maxIndex);
	}

	/**
	 * Gets the CPU utilization percentage matrix for a given list of VMs.
	 *
	 * @param vmList the VM list
	 * @return the CPU utilization percentage matrix, where each line i
         * is a VM and each column j is a CPU utilization percentage history for that VM.
	 */
	protected double[][] getUtilizationMatrix(final List<PowerGuestEntity> vmList) {
		int n = vmList.size();
                /*//@TODO It gets the min size of the history among all VMs considering
                that different VMs can have different history sizes.
                However, the j loop is not using the m variable
                but the size of the vm list. If a VM list has
                a size greater than m, it will thow an exception.
                It as to be included a test case for that.*/
		int m = getMinUtilizationHistorySize(vmList);
		double[][] utilization = new double[n][m];
		for (int i = 0; i < n; i++) {
			ArrayDeque<Double> vmUtilization = vmList.get(i).getUtilizationHistory();
			int j = 0;
			for (double u : vmUtilization)
				utilization[i][j++] = u;
		}
		return utilization;
	}

	/**
	 * Gets the min CPU utilization percentage history size among a list of VMs.
	 *
	 * @param vmList the VM list
	 * @return the min CPU utilization percentage history size of the VM list
	 */
	protected int getMinUtilizationHistorySize(final List<PowerGuestEntity> vmList) {
		int minSize = Integer.MAX_VALUE;
		for (PowerGuestEntity vm : vmList) {
			int size = vm.getUtilizationHistory().size();
			if (size < minSize) {
				minSize = size;
			}
		}
		return minSize;
	}

	/**
	 * Gets the correlation coefficients.
	 *
	 * @param data the data
	 * @return the correlation coefficients
	 */
    public List<Double> getCorrelationCoefficients(final double[][] data) {
		int n = data.length;
		int m = data[0].length;
		List<Double> correlationCoefficients = new LinkedList<>();
		for (int i = 0; i < n; i++) {
			double[][] x = new double[n - 1][m];
			int k = 0;
			for (int j = 0; j < n; j++) {
				if (j != i) {
					x[k++] = data[j];
				}
			}

			// Transpose the matrix so that it fits the linear model
			double[][] xT = new Array2DRowRealMatrix(x).transpose().getData();

			// RSquare is the "coefficient of determination"
			correlationCoefficients.add(MathUtil.createLinearRegression(xT,
					data[i]).calculateRSquared());
		}
		return correlationCoefficients;
	}

	/**
	 * Gets the fallback policy.
	 *
	 * @return the fallback policy
	 */
	public SelectionPolicy<PowerGuestEntity> getFallbackPolicy() {
		return fallbackPolicy;
	}

	/**
	 * Sets the fallback policy.
	 *
	 * @param fallbackPolicy the new fallback policy
	 */
	public void setFallbackPolicy(final SelectionPolicy<PowerGuestEntity> fallbackPolicy) {
		this.fallbackPolicy = fallbackPolicy;
	}
}
