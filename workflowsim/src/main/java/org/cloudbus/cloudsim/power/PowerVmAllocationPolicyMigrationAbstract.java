/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;

import java.util.*;

/**
 * An abstract power-aware VM allocation policy that dynamically optimizes the VM
 * allocation (placement) using migration.
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
 * @since CloudSim Toolkit 3.0
 */
public abstract class PowerVmAllocationPolicyMigrationAbstract extends VmAllocationPolicy {

	/** The vm selection policy. */
	private SelectionPolicy<GuestEntity> vmSelectionPolicy;

	/** A list of maps between a VM and the host where it is place. */
	private final List<GuestMapping> savedAllocation = new ArrayList<>();

	private void growIfNeeded(List<List<Double>> l, int idx) {
		for (int i = l.size(); i <= idx; i++)
			l.add(null);
	}

	/** A map of CPU utilization history (in percentage) for each host,
         where each key is a host id and each value is the CPU utilization percentage history.*/
    private final List<List<Double>> utilizationHistory = new ArrayList<>();

	/**
         * The metric history.
         * //@TODO the map stores different data. Sometimes it stores the upper threshold,
         * other it stores utilization threshold or predicted utilization, that
         * is very confusing.
         */
	private final List<List<Double>> metricHistory = new ArrayList<>();

	/** The time when entries in each history list was added.
         * All history lists are updated at the same time.
         */
	private final List<List<Double>> timeHistory = new ArrayList<>();

	/** The history of time spent in VM selection
         * every time the optimization of VM allocation method is called.
         * @see #optimizeAllocation(List)
         */
	private final List<Double> executionTimeHistoryVmSelection = new ArrayList<>();

	/** The history of time spent in host selection
         * every time the optimization of VM allocation method is called.
         * @see #optimizeAllocation(List)
         */
	private final List<Double> executionTimeHistoryHostSelection = new ArrayList<>();

	/** The history of time spent in VM reallocation
         * every time the optimization of VM allocation method is called.
         * @see #optimizeAllocation(List)
         */
	private final List<Double> executionTimeHistoryVmReallocation = new ArrayList<>();

	/** The history of total time spent in every call of the
         * optimization of VM allocation method.
         * @see #optimizeAllocation(List)
         */
	private final List<Double> executionTimeHistoryTotal = new ArrayList<>();

	/**
	 * Instantiates a new PowerVmAllocationPolicyMigrationAbstract.
	 *
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 */
	public PowerVmAllocationPolicyMigrationAbstract(
			List<? extends Host> hostList,
			SelectionPolicy<GuestEntity> vmSelectionPolicy) {
		super(hostList);
		setVmSelectionPolicy(vmSelectionPolicy);
	}

	/**
	 * Optimize allocation of the VMs according to current utilization.
	 *
	 * @param vmList the vm list
	 *
	 * @return the array list< hash map< string, object>>
	 */
	@Override
	public List<GuestMapping> optimizeAllocation(List<? extends GuestEntity> vmList) {
		ExecutionTimeMeasurer.start("optimizeAllocationTotal");

		ExecutionTimeMeasurer.start("optimizeAllocationHostSelection");
		List<PowerHost> overUtilizedHosts = getOverUtilizedHosts();
		getExecutionTimeHistoryHostSelection().add(
				ExecutionTimeMeasurer.end("optimizeAllocationHostSelection"));

		printOverUtilizedHosts(overUtilizedHosts);

		saveAllocation();

		ExecutionTimeMeasurer.start("optimizeAllocationVmSelection");
		List<GuestEntity> vmsToMigrate = getVmsToMigrateFromHosts(overUtilizedHosts);
		getExecutionTimeHistoryVmSelection().add(ExecutionTimeMeasurer.end("optimizeAllocationVmSelection"));

		Log.println("Reallocation of VMs from the over-utilized hosts:");
		ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
		List<GuestMapping> migrationMap = getNewVmPlacement(vmsToMigrate, new HashSet<>(overUtilizedHosts));
		getExecutionTimeHistoryVmReallocation().add(
				ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));
		Log.println();

		migrationMap.addAll(getMigrationMapFromUnderUtilizedHosts(overUtilizedHosts));

		restoreAllocation();

		getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));

		return migrationMap;
	}

	/**
	 * Gets the migration map from under utilized hosts.
	 *
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the migration map from under utilized hosts
	 */
	protected List<GuestMapping> getMigrationMapFromUnderUtilizedHosts(
			List<PowerHost> overUtilizedHosts) {
		List<GuestMapping> migrationMap = new LinkedList<>();
		List<PowerHost> switchedOffHosts = getSwitchedOffHosts();

		// over-utilized hosts + hosts that are selected to migrate VMs to from over-utilized hosts
		Set<PowerHost> excludedHostsForFindingUnderUtilizedHost = new HashSet<>();
		excludedHostsForFindingUnderUtilizedHost.addAll(overUtilizedHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(switchedOffHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(migrationMap));

		// over-utilized + under-utilized hosts
		Set<PowerHost> excludedHostsForFindingNewVmPlacement = new HashSet<>();
		excludedHostsForFindingNewVmPlacement.addAll(overUtilizedHosts);
		excludedHostsForFindingNewVmPlacement.addAll(switchedOffHosts);

		int numberOfHosts = getHostList().size();

		while (true) {
			if (numberOfHosts == excludedHostsForFindingUnderUtilizedHost.size()) {
				break;
			}

			PowerHost underUtilizedHost = getUnderUtilizedHost(excludedHostsForFindingUnderUtilizedHost);
			if (underUtilizedHost == null) {
				break;
			}

			Log.printlnConcat("Under-utilized host: host #", underUtilizedHost.getId(), "\n");

			excludedHostsForFindingUnderUtilizedHost.add(underUtilizedHost);
			excludedHostsForFindingNewVmPlacement.add(underUtilizedHost);

			List<? extends GuestEntity> vmsToMigrateFromUnderUtilizedHost = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilizedHost.isEmpty()) {
				continue;
			}

			Log.print("Reallocation of VMs from the under-utilized host: ");
			if (!Log.isDisabled()) {
				for (GuestEntity vm : vmsToMigrateFromUnderUtilizedHost) {
					Log.print(vm.getId() + " ");
				}
			}
			Log.println();

			List<GuestMapping> newVmPlacement = getNewVmPlacementFromUnderUtilizedHost(
					vmsToMigrateFromUnderUtilizedHost,
					excludedHostsForFindingNewVmPlacement);

			excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(newVmPlacement));

			migrationMap.addAll(newVmPlacement);
			Log.println();
		}

        excludedHostsForFindingUnderUtilizedHost.clear();
        excludedHostsForFindingNewVmPlacement.clear();
        return migrationMap;
    }

	/**
	 * Prints the over utilized hosts.
	 *
	 * @param overUtilizedHosts the over utilized hosts
	 */
	protected void printOverUtilizedHosts(List<PowerHost> overUtilizedHosts) {
		if (!Log.isDisabled()) {
			Log.println("Over-utilized hosts:");
			for (PowerHost host : overUtilizedHosts) {
				Log.printlnConcat("Host #", host.getId());
			}
			Log.println();
		}
	}

	/**
	 * Finds a PM that has enough resources to host a given VM
         * and that will not be overloaded after placing the VM on it.
         * The selected host will be that one with most efficient
         * power usage for the given VM.
	 *
	 * @param vm the VM
	 * @param excludedHosts the excluded hosts
	 * @return the host found to host the VM
	 */
	public PowerHost findHostForGuest(GuestEntity vm, Set<? extends HostEntity> excludedHosts) {
		double minPower = Double.MAX_VALUE;
		PowerHost allocatedHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForGuest(vm)) {
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}

				try {
					double powerAfterAllocation = getPowerAfterAllocation(host, vm);
					if (powerAfterAllocation != -1) {
						double powerDiff = powerAfterAllocation - host.getPower();
						if (powerDiff < minPower) {
							minPower = powerDiff;
							allocatedHost = host;
						}
					}
				} catch (Exception e) {
				}
			}
		}
		return allocatedHost;
	}

	/**
	 * Checks if a host will be over utilized after placing of a candidate VM.
	 *
	 * @param host the host to verify
	 * @param vm the candidate vm
	 * @return true, if the host will be over utilized after VM placement; false otherwise
	 */
	protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, GuestEntity vm) {
		boolean isHostOverUtilizedAfterAllocation = true;
		if (host.guestCreate(vm)) {
			isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
			host.guestDestroy(vm);
		}
		return isHostOverUtilizedAfterAllocation;
	}

	@Override
	public PowerHost findHostForGuest(GuestEntity vm) {
		Set<HostEntity> excludedHosts = new HashSet<>();
		if (vm.getHost() != null) {
			excludedHosts.add(vm.getHost());
		}
		return findHostForGuest(vm, excludedHosts);
	}

	/**
	 * Extracts the host list from a migration map.
	 *
	 * @param migrationMap the migration map
	 * @return the list
	 */
	protected List<PowerHost> extractHostListFromMigrationMap(List<GuestMapping> migrationMap) {
		List<PowerHost> hosts = new LinkedList<>();
		for (GuestMapping map : migrationMap) {
			hosts.add((PowerHost) map.host());
		}
		return hosts;
	}

	/**
	 * Gets a new vm placement considering the list of VM to migrate.
	 *
	 * @param vmsToMigrate the list of VMs to migrate
	 * @param excludedHosts the list of hosts that aren't selected as destination hosts
	 * @return the new vm placement map
	 */
	protected List<GuestMapping> getNewVmPlacement(
			List<GuestEntity> vmsToMigrate,
			Set<HostEntity> excludedHosts) {
		List<GuestMapping> migrationMap = new LinkedList<>();
		VmList.sortByCpuUtilization(vmsToMigrate);
		for (GuestEntity vm : vmsToMigrate) {
			PowerHost allocatedHost = findHostForGuest(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.guestCreate(vm);
				Log.printlnConcat("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());
				migrationMap.add(new GuestMapping(vm, allocatedHost));
			}
		}
		return migrationMap;
	}

	/**
	 * Gets the new vm placement from under utilized host.
	 *
	 * @param vmsToMigrate the list of VMs to migrate
	 * @param excludedHosts the list of hosts that aren't selected as destination hosts
	 * @return the new vm placement from under utilized host
	 */
	protected List<GuestMapping> getNewVmPlacementFromUnderUtilizedHost(
			List<? extends GuestEntity> vmsToMigrate,
			Set<? extends HostEntity> excludedHosts) {
		List<GuestMapping> migrationMap = new LinkedList<>();
		VmList.sortByCpuUtilization(vmsToMigrate);
		for (GuestEntity vm : vmsToMigrate) {
			PowerHost allocatedHost = findHostForGuest(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.guestCreate(vm);
				Log.printlnConcat("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());
				migrationMap.add(new GuestMapping(vm, allocatedHost));
			} else {
				Log.println("Not all VMs can be reallocated from the host, reallocation cancelled");
				for (GuestMapping map : migrationMap) {
					(map.host()).guestDestroy(map.vm());
				}
				migrationMap.clear();
				break;
			}
		}
		return migrationMap;
	}

	/**
	 * Gets the VMs to migrate from hosts.
	 *
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the VMs to migrate from hosts
	 */
	protected List<GuestEntity>
	  getVmsToMigrateFromHosts(List<PowerHost> overUtilizedHosts) {
		List<GuestEntity> vmsToMigrate = new LinkedList<>();
		for (PowerHost host : overUtilizedHosts) {
			while (true) {
				GuestEntity vm = getVmSelectionPolicy().select(host.getMigrableVms(), host, new HashSet<>());
				if (vm == null) {
					break;
				}
				vmsToMigrate.add(vm);
				host.guestDestroy(vm);
				if (!isHostOverUtilized(host)) {
					break;
				}
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the VMs to migrate from under utilized host.
	 *
	 * @param host the host
	 * @return the vms to migrate from under utilized host
	 */
	protected List<? extends GuestEntity> getVmsToMigrateFromUnderUtilizedHost(PowerHost host) {
		List<GuestEntity> vmsToMigrate = new LinkedList<>();
		for (GuestEntity vm : host.getGuestList()) {
			if (!vm.isInMigration()) {
				vmsToMigrate.add(vm);
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the over utilized hosts.
	 *
	 * @return the over utilized hosts
	 */
	protected List<PowerHost> getOverUtilizedHosts() {
		List<PowerHost> overUtilizedHosts = new LinkedList<>();
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (isHostOverUtilized(host)) {
				overUtilizedHosts.add(host);
			}
		}
		return overUtilizedHosts;
	}

	/**
	 * Gets the switched off hosts.
	 *
	 * @return the switched off hosts
	 */
	protected List<PowerHost> getSwitchedOffHosts() {
		List<PowerHost> switchedOffHosts = new LinkedList<>();
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (host.getUtilizationOfCpu() == 0) {
				switchedOffHosts.add(host);
			}
		}
		return switchedOffHosts;
	}

	/**
	 * Gets the most under utilized host.
	 *
	 * @param excludedHosts the excluded hosts
	 * @return the most under utilized host
	 */
	protected PowerHost getUnderUtilizedHost(Set<? extends Host> excludedHosts) {
		double minUtilization = 1;
		PowerHost underUtilizedHost = null;
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			double utilization = host.getUtilizationOfCpu();
			if (utilization > 0 && utilization < minUtilization
					&& !areAllVmsMigratingOutOrAnyVmMigratingIn(host)) {
				minUtilization = utilization;
				underUtilizedHost = host;
			}
		}
		return underUtilizedHost;
	}

	/**
	 * Checks whether all VMs of a given host are in migration.
	 *
	 * @param host the host
	 * @return true, if successful
	 */
	protected boolean areAllVmsMigratingOutOrAnyVmMigratingIn(PowerHost host) {
		for (PowerVm vm : host.<PowerVm>getGuestList()) {
			if (!vm.isInMigration()) {
				return false;
			}
			if (host.getGuestsMigratingIn().contains(vm)) {
				return true;
			}
		}
		return true;
	}

	/**
	 * Checks if host is over utilized.
	 *
	 * @param host the host
	 * @return true, if the host is over utilized; false otherwise
	 */
	protected abstract boolean isHostOverUtilized(PowerHost host);

	/**
	 * Adds an entry for each history map of a host.
	 *
	 * @param host the host to add metric history entries
	 * @param metric the metric to be added to the metric history map
	 */
	protected void addHistoryEntry(HostDynamicWorkload host, double metric) {
		int hostId = host.getId();
		growIfNeeded(timeHistory, hostId);
		if (timeHistory.get(hostId) == null) {
			timeHistory.set(hostId, new ArrayList<Double>());
		}
		growIfNeeded(utilizationHistory, hostId);
		if (utilizationHistory.get(hostId) == null) {
			utilizationHistory.set(hostId, new ArrayList<Double>());
		}
		growIfNeeded(metricHistory, hostId);
		if (metricHistory.get(hostId) == null) {
			metricHistory.set(hostId, new ArrayList<Double>());
		}
		if (timeHistory.get(hostId).isEmpty() || timeHistory.get(hostId).getLast() < CloudSim.clock()) {
			timeHistory.get(hostId).add(CloudSim.clock());
			utilizationHistory.get(hostId).add(host.getUtilizationOfCpu());
			metricHistory.get(hostId).add(metric);
		}
	}

	/**
	 * Updates the list of maps between a VM and the host where it is place.
         * @see #savedAllocation
	 */
	protected void saveAllocation() {
		getSavedAllocation().clear();
		for (HostEntity host : getHostList()) {
			for (GuestEntity vm : host.getGuestList()) {
				if (host.getGuestsMigratingIn().contains(vm)) {
					continue;
				}
				getSavedAllocation().add(new GuestMapping(vm, host));
			}
		}
	}

	/**
	 * Restore VM allocation from the allocation history.
         * @see #savedAllocation
	 */
	protected void restoreAllocation() {
		for (HostEntity host : getHostList()) {
			host.guestDestroyAll();
			host.reallocateMigratingInGuests();
		}
		for (GuestMapping map : getSavedAllocation()) {
			Vm vm = (Vm) map.vm();
			PowerHost host = (PowerHost) map.host();
			if (!host.guestCreate(vm)) {
				Log.printlnConcat("Couldn't restore VM #", vm.getId(), " on host #", host.getId());
				System.exit(0);
			}
			getGuestTable().put(vm.getUid(), host);
		}
	}

	/**
	 * Gets the power consumption of a host after placement of a candidate VM.
         * The VM is not in fact placed at the host.
	 *
	 * @param host the host
	 * @param vm the candidate vm
	 *
	 * @return the power after allocation
	 */
	protected double getPowerAfterAllocation(PowerHost host, GuestEntity vm) {
		double power = 0;
		try {
			power = host.getPowerModel().getPower(getMaxUtilizationAfterAllocation(host, vm));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		return power;
	}

	/**
	 * Gets the max power consumption of a host after placement of a candidate VM.
         * The VM is not in fact placed at the host.
         * We assume that load is balanced between PEs. The only
	 * restriction is: VM's max MIPS < PE's MIPS
	 *
	 * @param host the host
	 * @param vm the vm
	 *
	 * @return the power after allocation
	 */
	protected double getMaxUtilizationAfterAllocation(PowerHost host, GuestEntity vm) {
		double requestedTotalMips = vm.getCurrentRequestedTotalMips();
		double hostUtilizationMips = getUtilizationOfCpuMips(host);
		double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
        return hostPotentialUtilizationMips / host.getTotalMips();
	}

	/**
	 * Gets the utilization of the CPU in MIPS for the current potentially allocated VMs.
	 *
	 * @param host the host
	 *
	 * @return the utilization of the CPU in MIPS
	 */
	protected double getUtilizationOfCpuMips(PowerHost host) {
		double hostUtilizationMips = 0;
		for (GuestEntity vm2 : host.getGuestList()) {
			if (host.getGuestsMigratingIn().contains(vm2)) {
				// calculate additional potential CPU usage of a migrating in VM
				hostUtilizationMips += host.getTotalAllocatedMipsForGuest(vm2) * 0.9 / 0.1;
			}
			hostUtilizationMips += host.getTotalAllocatedMipsForGuest(vm2);
		}
		return hostUtilizationMips;
	}

	/**
	 * Gets the saved allocation.
	 *
	 * @return the saved allocation
	 */
	protected List<GuestMapping> getSavedAllocation() {
		return savedAllocation;
	}

	/**
	 * Sets the vm selection policy.
	 *
	 * @param vmSelectionPolicy the new vm selection policy
	 */
	protected void setVmSelectionPolicy(SelectionPolicy<GuestEntity> vmSelectionPolicy) {
		this.vmSelectionPolicy = vmSelectionPolicy;
	}

	/**
	 * Gets the vm selection policy.
	 *
	 * @return the vm selection policy
	 */
	protected SelectionPolicy<GuestEntity> getVmSelectionPolicy() {
		return vmSelectionPolicy;
	}

	/**
	 * Gets the utilization history.
	 *
	 * @return the utilization history
	 */
	public List<Double> getUtilizationHistory(int hostId) {
		if (hostId >= utilizationHistory.size())
			return null;
		return utilizationHistory.get(hostId);
	}

	/**
	 * Gets the metric history.
	 *
	 * @return the metric history
	 */
	public List<Double> getMetricHistory(int hostId) {
		if (hostId >= metricHistory.size())
			return null;
		return metricHistory.get(hostId);
	}

	/**
	 * Gets the time history.
	 *
	 * @return the time history
	 */
	public List<Double> getTimeHistory(int hostId) {
		if (hostId >= timeHistory.size())
			return null;
		return timeHistory.get(hostId);
	}

	/**
	 * Gets the execution time history vm selection.
	 *
	 * @return the execution time history vm selection
	 */
	public List<Double> getExecutionTimeHistoryVmSelection() {
		return executionTimeHistoryVmSelection;
	}

	/**
	 * Gets the execution time history host selection.
	 *
	 * @return the execution time history host selection
	 */
	public List<Double> getExecutionTimeHistoryHostSelection() {
		return executionTimeHistoryHostSelection;
	}

	/**
	 * Gets the execution time history vm reallocation.
	 *
	 * @return the execution time history vm reallocation
	 */
	public List<Double> getExecutionTimeHistoryVmReallocation() {
		return executionTimeHistoryVmReallocation;
	}

	/**
	 * Gets the execution time history total.
	 *
	 * @return the execution time history total
	 */
	public List<Double> getExecutionTimeHistoryTotal() {
		return executionTimeHistoryTotal;
	}

}
