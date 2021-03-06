package com.mnetlab.aaiot.device;

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;

public class Selection {
	private Selection() {
	}

	public static Set<Device> devicesSelection(Devices devices, Locations locations) {
		Set<Device> selectedDevices = new HashSet<>();
		Set<Location> unsatisfiedLocations = new HashSet<>(locations.values());
		Set<Location> satisfiedLocations = new HashSet<>();
		for (Location location : locations.values()) {
			location.setSatisfied(false);
		}
		while (!unsatisfiedLocations.isEmpty()) {
			// find an unsatisfied location l with maximum number of groups
			Location locationToSatisfy = findMaxGroupsLocation(unsatisfiedLocations);
			Group selectedGroup = null;
			// l is satisfied
			locationToSatisfy.setSatisfied(true);
			unsatisfiedLocations.remove(locationToSatisfy);
			satisfiedLocations.add(locationToSatisfy);

			if ((selectedGroup = selectMaxTotalSatisfy(locationToSatisfy, unsatisfiedLocations,
					selectedDevices)) != null) {
				locationToSatisfy.setSelectedGroup(selectedGroup);
				satisfyUnsatisfiedLocations(selectedGroup, unsatisfiedLocations, satisfiedLocations, selectedDevices);
			} else if ((selectedGroup = selectMaxTotalInvolve(locationToSatisfy, unsatisfiedLocations,
					selectedDevices)) != null) {
				locationToSatisfy.setSelectedGroup(selectedGroup);
			} else if ((selectedGroup = selectMinEnergy(locationToSatisfy, selectedDevices)) != null) {
				locationToSatisfy.setSelectedGroup(selectedGroup);
			} else {
				System.err.println("No Group is selected!!");
			}

			// Z = Z ∪ g
			selectedDevices.addAll(selectedGroup.getMembers());

			// adjustment
			adjust(satisfiedLocations, selectedDevices);

			// Z = the union of selected groups for all satisfied locations
			// devices that are not in use are deleted
			selectedDevices.clear();
			for (Location satisfiedLocation : satisfiedLocations) {
				selectedDevices.addAll(satisfiedLocation.getSelectedGroup().getMembers());
			}

		} // end while
		return selectedDevices;
	} // end method devicesSelection

	private static Location findMaxGroupsLocation(Set<Location> unsatisfiedLocations) {
		Location MaxGroupsLocation = null;
		int max = Integer.MIN_VALUE;
		for (Location location : unsatisfiedLocations) {
			if (location.getGroupsSize() >= max) {
				max = location.getGroupsSize();
				MaxGroupsLocation = location;
			}
		}
		return MaxGroupsLocation;
	} // end method findMaxGroupsLocation

	private static Group selectMaxTotalSatisfy(Location locationToSatisfy, Set<Location> unsatisfiedLocations,
			Set<Device> selectedDevices) {
		Group maxGroup = null;
		double max = 0;
		for (Group selecting : locationToSatisfy.getGroups()) {
			final int totalSatisfy = totalSatisfy(selecting, unsatisfiedLocations, selectedDevices);
			final double energy = computeDevicesEnergy(Sets.difference(selecting.getMembers(),
					Sets.intersection(selecting.getMembers(), selectedDevices)));
			// not satisfying any location which is originally unsatisfied
			if (totalSatisfy == 0) {
				continue;
			}
			if ((totalSatisfy / energy) >= max) {
				max = totalSatisfy / energy;
				maxGroup = selecting;
			}
		}
		return maxGroup;
	} // end method selectMaxTotalSatisfy

	/**
	 * To satisfy other locations after satisfying MaxGroupsLocation.
	 * 
	 * Iterate through Sets.intersection(unsatisfiedLocations,
	 * locationsSelectedGroupCovers) and remove location from
	 * unsatisfiedLocations causes Exception java.util.HashMap$KeyIterator.next.
	 * Therefore, Sets.intersection(unsatisfiedLocations,
	 * locationsSelectedGroupCovers) is removed and use
	 * locationsSelectedGroupCovers instead.
	 */
	private static void satisfyUnsatisfiedLocations(Group selected, Set<Location> unsatisfiedLocations,
			Set<Location> satisfiedLocations, Set<Device> selectedDevices) {
		Set<Location> locationsSelectedGroupCovers = new HashSet<>();
		// the union of locations that each member in the group covers
		for (Device member : selected.getMembers()) {
			locationsSelectedGroupCovers.addAll(member.getCoverage());
		}
		for (Location location : locationsSelectedGroupCovers) {
			if (location.isSatisfied()) {
				continue;
			}
			for (Group unsatisfiedGroup : location.getGroups()) {
				if (isSatisfy(selected, unsatisfiedGroup, selectedDevices) == 1) {
					location.setSatisfied(true);
					unsatisfiedLocations.remove(location);
					satisfiedLocations.add(location);
					// the location is now satisfied by selecting this group
					location.setSelectedGroup(unsatisfiedGroup);
					break;
				}
			}
		}
	} // end method satisfyUnsatisfiedLocations

	private static int totalSatisfy(Group selecting, Set<Location> unsatisfiedLocations, Set<Device> selectedDevices) {
		int totalSatisfy = 0;
		Set<Location> locationsSelectingGroupCovers = new HashSet<>();
		// the union of locations that each member in the group covers
		for (Device member : selecting.getMembers()) {
			locationsSelectingGroupCovers.addAll(member.getCoverage());
		}
		for (Location unsatisfiedLocation : Sets.intersection(unsatisfiedLocations, locationsSelectingGroupCovers)) {
			for (Group unsatisfied : unsatisfiedLocation.getGroups()) {
				if (isSatisfy(selecting, unsatisfied, selectedDevices) == 1) {
					totalSatisfy += 1;
					break;
				}
			}
		}
		return totalSatisfy;
	} // end method totalSatisfy

	private static Group selectMaxTotalInvolve(Location locationToSatisfy, Set<Location> unsatisfiedLocations,
			Set<Device> selectedDevices) {
		Group maxGroup = null;
		double max = 0;
		for (Group selecting : locationToSatisfy.getGroups()) {
			final int totalInvolve = totalInvolve(selecting, unsatisfiedLocations, selectedDevices);
			final double energy = computeDevicesEnergy(Sets.difference(selecting.getMembers(),
					Sets.intersection(selecting.getMembers(), selectedDevices)));
			// not involving in any group
			if (totalInvolve == 0) {
				continue;
			}
			if ((totalInvolve / energy) >= max) {
				max = totalInvolve / energy;
				maxGroup = selecting;
			}
		}
		return maxGroup;
	} // end method selectMaxTotalInvolve

	private static int totalInvolve(Group selecting, Set<Location> unsatisfiedLocations, Set<Device> selectedDevices) {
		int totalInvolve = 0;
		Set<Location> locationsSelectingGroupCovers = new HashSet<>();
		// the union of locations that each member in the group covers
		for (Device member : selecting.getMembers()) {
			locationsSelectingGroupCovers.addAll(member.getCoverage());
		}
		for (Location unsatisfiedLocation : Sets.intersection(unsatisfiedLocations, locationsSelectingGroupCovers)) {
			for (Group unsatisfied : unsatisfiedLocation.getGroups()) {
				totalInvolve += isInvolve(selecting, unsatisfied, selectedDevices);
			}
		}
		return totalInvolve;
	} // end method totalInvolve

	private static int isSatisfy(Group selecting, Group unsatisfied, Set<Device> selectedDevices) {
		if (Sets.union(selecting.getMembers(), selectedDevices).containsAll(unsatisfied.getMembers())) {
			return 1;
		} else {
			return 0;
		}
	} // end method satisfy

	private static int isInvolve(Group selecting, Group unsatisfied, Set<Device> selectedDevices) {
		if (!Sets.intersection(unsatisfied.getMembers(),
				Sets.difference(selecting.getMembers(), Sets.intersection(selecting.getMembers(), selectedDevices)))
				.isEmpty()) {
			return 1;
		} else {
			return 0;
		}
	} // end method involve

	private static Group selectMinEnergy(Location locationToSatisfy, Set<Device> selectedDevices) {
		Group minGroup = null;
		double min = Double.POSITIVE_INFINITY;
		for (Group selecting : locationToSatisfy.getGroups()) {
			final double energy = computeDevicesEnergy(Sets.difference(selecting.getMembers(),
					Sets.intersection(selecting.getMembers(), selectedDevices)));
			if (energy <= min) {
				min = energy;
				minGroup = selecting;
			}
		}
		return minGroup;
	} // end method selectMinEnergy

	/**
	 * The energy of connecting a device to its default associated MEC is
	 * calculated. Then, total energy consumed by input devices is returned.
	 */
	private static double computeDevicesEnergy(Set<Device> devices) {
		double energy = 0;
		for (Device device : devices) {
			energy += device.getConnectionEnergy().get(device.getAssociatedMEC());
		}
		return energy;
	} // end method computeDevicesEnergy

	private static void adjust(Set<Location> satisfiedLocations, Set<Device> selectedDevices) {
		for (Location location : satisfiedLocations) {
			final Group selectedGroup = location.getSelectedGroup();
			double currentEnergy = computeDevicesEnergy(selectedGroup.getMembers());
			for (Group candidateGroup : location.getGroups()) {
				if (selectedGroup.equals(candidateGroup)) {
					continue;
				}
				if (selectedDevices.containsAll(candidateGroup.getMembers())
						&& computeDevicesEnergy(candidateGroup.getMembers()) < currentEnergy) {
					currentEnergy = computeDevicesEnergy(candidateGroup.getMembers());
					location.setSelectedGroup(candidateGroup);
				}
			}
		}
	} // end method adjust

	public static Set<Device> greedyMSC(Devices devices, Locations locations) {
		Set<Location> unsatisfiedLocations = new HashSet<>(locations.values());
		Set<Device> selectedDevices = new HashSet<>();
		Set<Device> availableDevices = new HashSet<>(devices.values());
		while (!unsatisfiedLocations.isEmpty()) {
			Location minCovered = selectMinCovered(unsatisfiedLocations);
			Set<Location> newlyCovered = selectGmscMax(minCovered, availableDevices, unsatisfiedLocations,
					selectedDevices);
			remove(unsatisfiedLocations, newlyCovered, selectedDevices);
		}
		return selectedDevices;
	} // end method greegyMSC

	private static Location selectMinCovered(Set<Location> unsatisfiedLocations) {
		Location minTarget = null;
		int min = Integer.MAX_VALUE;
		for (Location location : unsatisfiedLocations) {
			if (location.getCoveredBy().size() <= min) {
				min = location.getCoveredBy().size();
				minTarget = location;
			}
		}
		return minTarget;
	} // end method selectMinCovered

	private static Set<Location> selectGmscMax(Location minCovered, Set<Device> availableDevices,
			Set<Location> unsatisfiedLocations, Set<Device> selectedDevices) {
		Device maxDevice = null;
		int max = -1;
		Set<Location> newlyCovered = null;
		for (Device device : Sets.intersection(availableDevices, minCovered.getCoveredBy())) {
			Set<Location> intersection = Sets.intersection(device.getCoverage(), unsatisfiedLocations);
			if (intersection.size() >= max) {
				max = intersection.size();
				newlyCovered = intersection;
				maxDevice = device;
			}
		}
		selectedDevices.add(maxDevice);
		availableDevices.remove(maxDevice);
		return newlyCovered;
	} // end method selectGmscMax

	private static void remove(Set<Location> unsatisfiedLocations, Set<Location> newlyCovered,
			Set<Device> selectedDevices) {
		for (Location location : newlyCovered) {
			for (Group unsatisfiedGroup : location.getGroups()) {
				if (selectedDevices.containsAll(unsatisfiedGroup.getMembers())) {
					location.setSatisfied(true);
					unsatisfiedLocations.remove(location);
					location.setSelectedGroup(unsatisfiedGroup);
					break;
				}
			}
		}
	} // end method remove

	public static Set<Device> ESR(Devices devices, Locations locations) {
		Set<Location> unsatisfiedLocations = new HashSet<>(locations.values());
		Set<Device> selectedDevices = new HashSet<>();
		Set<Device> availableDevices = new HashSet<>(devices.values());
		while (!unsatisfiedLocations.isEmpty()) {
			Set<Location> newlyCovered = selectMinCost(availableDevices, unsatisfiedLocations, selectedDevices);
			remove(unsatisfiedLocations, newlyCovered, selectedDevices);
		}
		return selectedDevices;
	} // end method ESR

	private static Set<Location> selectMinCost(Set<Device> availableDevices, Set<Location> unsatisfiedLocations,
			Set<Device> selectedDevices) {
		Device minDevice = null;
		double min = Double.POSITIVE_INFINITY;
		Set<Location> newlyCovered = null;
		for (Device device : availableDevices) {
			Set<Location> intersection = Sets.intersection(device.getCoverage(), unsatisfiedLocations);
			double energy = device.getConnectionEnergy().get(device.getAssociatedMEC());
			if (energy / intersection.size() <= min) {
				min = energy / intersection.size();
				newlyCovered = intersection;
				minDevice = device;
			}
		}
		selectedDevices.add(minDevice);
		availableDevices.remove(minDevice);
		return newlyCovered;
	} // end method selectMinCost

}
