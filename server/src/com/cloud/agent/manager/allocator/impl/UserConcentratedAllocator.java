/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.cloud.agent.manager.allocator.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.manager.allocator.PodAllocator;
import com.cloud.capacity.CapacityVO;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.Pod;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.offering.ServiceOffering;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VMTemplateHostDao;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.utils.DateUtil;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Inject;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

@Local(value = PodAllocator.class)
public class UserConcentratedAllocator implements PodAllocator {
    private final static Logger s_logger = Logger.getLogger(UserConcentratedAllocator.class);

    String _name;

    @Inject
    UserVmDao _vmDao;
    @Inject
    VolumeDao _volumeDao;
    @Inject
    HostPodDao _podDao;
    @Inject
    VMTemplateHostDao _templateHostDao;
    @Inject
    VMTemplatePoolDao _templatePoolDao;
    @Inject
    ServiceOfferingDao _offeringDao;
    @Inject
    CapacityDao _capacityDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    VMInstanceDao _vmInstanceDao;

    Random _rand = new Random(System.currentTimeMillis());
    private int _secondsToSkipStoppedVMs = 86400;
    private int _secondsToSkipDestroyedVMs = 0;

    private int _secStorageVmRamSize = 1024;
    private int _proxyRamSize = 256;
    private int _routerRamSize = 128;

    @Override
    public Pair<HostPodVO, Long> allocateTo(VirtualMachineTemplate template, ServiceOfferingVO offering, DataCenterVO zone, long accountId, Set<Long> avoids) {
        long zoneId = zone.getId();
        List<HostPodVO> podsInZone = _podDao.listByDataCenterId(zoneId);

        if (podsInZone.size() == 0) {
            s_logger.debug("No pods found in zone " + zone.getName());
            return null;
        }

        // Find pods that have enough CPU/memory capacity
        List<HostPodVO> availablePods = new ArrayList<HostPodVO>();
        Map<Long, Long> podHostCandidates = new HashMap<Long, Long>();
        for (HostPodVO pod : podsInZone) {
            long podId = pod.getId();
            if (!avoids.contains(podId)) {
                if (template != null && !templateAvailableInPod(template.getId(), pod.getDataCenterId(), podId)) {
                    continue;
                }

                if (offering != null) {
                    // test for enough memory in the pod (make sure to check for enough memory for the service offering, plus
                    // some extra padding for xen overhead
                    long[] hostCandiates = new long[1];
                    boolean enoughCapacity = dataCenterAndPodHasEnoughCapacity(zoneId, podId, (offering.getRamSize()) * 1024L * 1024L, CapacityVO.CAPACITY_TYPE_MEMORY, hostCandiates);

                    if (!enoughCapacity) {
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("Not enough RAM available in zone/pod to allocate storage for user VM (zone: " + zoneId + ", pod: " + podId + ")");
                        }
                        continue;
                    }

                    // test for enough CPU in the pod
                    enoughCapacity = dataCenterAndPodHasEnoughCapacity(zoneId, podId, (offering.getCpu() * offering.getSpeed()), CapacityVO.CAPACITY_TYPE_CPU, hostCandiates);
                    if (!enoughCapacity) {
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("Not enough cpu available in zone/pod to allocate storage for user VM (zone: " + zoneId + ", pod: " + podId + ")");
                        }
                        continue;
                    }

                    podHostCandidates.put(podId, hostCandiates[0]);
                }

                // If the pod has VMs or volumes in it, return this pod
                List<UserVmVO> vmsInPod = _vmDao.listByAccountAndPod(accountId, pod.getId());
                if (!vmsInPod.isEmpty()) {
                    return new Pair<HostPodVO, Long>(pod, podHostCandidates.get(podId));
                }

                List<VolumeVO> volumesInPod = _volumeDao.findByAccountAndPod(accountId, pod.getId());
                if (!volumesInPod.isEmpty()) {
                    return new Pair<HostPodVO, Long>(pod, podHostCandidates.get(podId));
                }

                availablePods.add(pod);
            }
        }

        if (availablePods.size() == 0) {
            s_logger.debug("There are no pods with enough memory/CPU capacity in zone " + zone.getName());
            return null;
        } else {
            // Return a random pod
            int next = _rand.nextInt(availablePods.size());
            HostPodVO selectedPod = availablePods.get(next);
            s_logger.debug("Found pod " + selectedPod.getName() + " in zone " + zone.getName());
            return new Pair<HostPodVO, Long>(selectedPod, podHostCandidates.get(selectedPod.getId()));
        }
    }

    private boolean dataCenterAndPodHasEnoughCapacity(long dataCenterId, long podId, long capacityNeeded, short capacityType, long[] hostCandidate) {
        List<CapacityVO> capacities = null;

        SearchCriteria<CapacityVO> sc = _capacityDao.createSearchCriteria();
        sc.addAnd("capacityType", SearchCriteria.Op.EQ, capacityType);
        sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, dataCenterId);
        sc.addAnd("podId", SearchCriteria.Op.EQ, podId);
        s_logger.trace("Executing search");
        capacities = _capacityDao.search(sc, null);
        s_logger.trace("Done with a search");

        boolean enoughCapacity = false;
        if (capacities != null) {
            for (CapacityVO capacity : capacities) {
                if (capacityType == CapacityVO.CAPACITY_TYPE_CPU || capacityType == CapacityVO.CAPACITY_TYPE_MEMORY) {
                    //
                    // for CPU/Memory, we now switch to static allocation
                    //
                    if ((capacity.getTotalCapacity() - calcHostAllocatedCpuMemoryCapacity(capacity.getHostOrPoolId(), capacityType)) >= capacityNeeded) {

                        hostCandidate[0] = capacity.getHostOrPoolId();
                        enoughCapacity = true;
                        break;
                    }
                } else {
                    if ((capacity.getTotalCapacity() - capacity.getUsedCapacity()) >= capacityNeeded) {
                        hostCandidate[0] = capacity.getHostOrPoolId();
                        enoughCapacity = true;
                        break;
                    }
                }
            }
        }
        return enoughCapacity;
    }

    private boolean skipCalculation(VMInstanceVO vm) {
        if (vm.getState() == State.Expunging) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Skip counting capacity for Expunging VM : " + vm.getInstanceName());
            }
            return true;
        }

        if (vm.getState() == State.Destroyed && vm.getType() != VirtualMachine.Type.User) {
            return true;
        }

        if (vm.getState() == State.Stopped || vm.getState() == State.Destroyed) {
            // for Stopped/Destroyed VMs, we will skip counting it if it hasn't been used for a while
            int secondsToSkipVMs = _secondsToSkipStoppedVMs;

            if (vm.getState() == State.Destroyed) {
                secondsToSkipVMs = _secondsToSkipDestroyedVMs;
            }

            long millisecondsSinceLastUpdate = DateUtil.currentGMTTime().getTime() - vm.getUpdateTime().getTime();
            if (millisecondsSinceLastUpdate > secondsToSkipVMs * 1000L) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Skip counting " + vm.getState().toString() + " vm " + vm.getInstanceName() + " in capacity allocation as it has been " + vm.getState().toString().toLowerCase()
                            + " for " + millisecondsSinceLastUpdate / 60000 + " minutes");
                }
                return true;
            }
        }

        return false;
    }

    /**
     * 
     * @param hostId
     *            Host id to calculate against
     * @param capacityType
     *            CapacityVO.CAPACITY_TYPE_MEMORY or CapacityVO.CAPACITY_TYPE_CPU
     * @return
     */
    private long calcHostAllocatedCpuMemoryCapacity(long hostId, short capacityType) {
        assert (capacityType == CapacityVO.CAPACITY_TYPE_MEMORY || capacityType == CapacityVO.CAPACITY_TYPE_CPU) : "Invalid capacity type passed in calcHostAllocatedCpuCapacity()";

        // List<VMInstanceVO> vms = _vmInstanceDao.listByLastHostId(hostId);
        List<VMInstanceVO> vms = null;
        long usedCapacity = 0;
        for (VMInstanceVO vm : vms) {
            if (skipCalculation(vm)) {
                continue;
            }

            ServiceOffering so = null;

            if (vm.getType() == VirtualMachine.Type.User) {
                UserVmVO userVm = _vmDao.findById(vm.getId());
                if (userVm == null) {
                    continue;
                }
                so = _offeringDao.findById(userVm.getServiceOfferingId());
            } else if (vm.getType() == VirtualMachine.Type.ConsoleProxy) {
                so = new ServiceOfferingVO("Fake Offering For DomP", 1, _proxyRamSize, 0, 0, 0, false, null, false, true, null, true, VirtualMachine.Type.ConsoleProxy, false);
            } else if (vm.getType() == VirtualMachine.Type.SecondaryStorageVm) {
                so = new ServiceOfferingVO("Fake Offering For Secondary Storage VM", 1, _secStorageVmRamSize, 0, 0, 0, true, null, false, true, null, true, VirtualMachine.Type.SecondaryStorageVm, false);
            } else if (vm.getType() == VirtualMachine.Type.DomainRouter) {
                so = new ServiceOfferingVO("Fake Offering For DomR", 1, _routerRamSize, 0, 0, 0, true, null, false, true, null, true, VirtualMachine.Type.DomainRouter, false);
            } else {
                assert (false) : "Unsupported system vm type";
                so = new ServiceOfferingVO("Fake Offering For unknow system VM", 1, 128, 0, 0, 0, false, null, false, true, null, true, null, false);
            }

            if (capacityType == CapacityVO.CAPACITY_TYPE_MEMORY) {
                usedCapacity += so.getRamSize() * 1024L * 1024L;

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Counting memory capacity used by vm: " + vm.getId() + ", size: " + so.getRamSize() + "MB, host: " + hostId + ", currently counted: " + usedCapacity + " Bytes");
                }
            } else if (capacityType == CapacityVO.CAPACITY_TYPE_CPU) {
                usedCapacity += so.getCpu() * so.getSpeed();

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Counting cpu capacity used by vm: " + vm.getId() + ", cpu: " + so.getCpu() + ", speed: " + so.getSpeed() + ", currently counted: " + usedCapacity + " Bytes");
                }
            }
        }

        return usedCapacity;
    }

    private boolean templateAvailableInPod(long templateId, long dcId, long podId) {
        return true;
        /*
         * List<VMTemplateHostVO> thvoList = _templateHostDao.listByTemplateStatus(templateId, dcId, podId, Status.DOWNLOADED);
         * List<VMTemplateStoragePoolVO> tpvoList = _templatePoolDao.listByTemplateStatus(templateId, dcId, podId,
         * Status.DOWNLOADED);
         * 
         * if (thvoList != null && thvoList.size() > 0) { if (s_logger.isDebugEnabled()) { s_logger.debug("Found " +
         * thvoList.size() + " storage hosts in pod " + podId + " with template " + templateId); } return true; } else if
         * (tpvoList != null && tpvoList.size() > 0) { if (s_logger.isDebugEnabled()) { s_logger.debug("Found " +
         * tpvoList.size() + " storage pools in pod " + podId + " with template " + templateId); } return true; }else { return
         * false; }
         */
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;

        Map<String, String> configs = _configDao.getConfiguration("management-server", params);
        String stoppedValue = configs.get("vm.resource.release.interval");
        // String destroyedValue = configs.get("capacity.skipcounting.destroyed.hours");
        String destroyedValue = null;
        _secondsToSkipStoppedVMs = NumbersUtil.parseInt(stoppedValue, 86400);
        _secondsToSkipDestroyedVMs = NumbersUtil.parseInt(destroyedValue, 0);

        // TODO this is not good, there should be one place to get these values
        _secStorageVmRamSize = NumbersUtil.parseInt(configs.get("secstorage.vm.ram.size"), 256);
        _routerRamSize = NumbersUtil.parseInt(configs.get("router.ram.size"), 128);
        _proxyRamSize = NumbersUtil.parseInt(configs.get("consoleproxy.ram.size"), 1024);

        /*
         * ComponentLocator locator = ComponentLocator.getCurrentLocator(); _vmDao = locator.getDao(UserVmDao.class); if (_vmDao
         * == null) { throw new ConfigurationException("Unable to find UserVMDao."); }
         * 
         * _volumeDao = locator.getDao(VolumeDao.class); if (_volumeDao == null) { throw new
         * ConfigurationException("Unable to find VolumeDao."); }
         * 
         * _templateHostDao = locator.getDao(VMTemplateHostDao.class); if (_templateHostDao == null) { throw new
         * ConfigurationException("Unable to get template host dao."); }
         * 
         * _templatePoolDao = locator.getDao(VMTemplatePoolDao.class); if (_templatePoolDao == null) { throw new
         * ConfigurationException("Unable to get template pool dao."); }
         * 
         * _podDao = locator.getDao(HostPodDao.class); if (_podDao == null) { throw new
         * ConfigurationException("Unable to find HostPodDao."); }
         * 
         * _capacityDao = locator.getDao(CapacityDao.class); if (_capacityDao == null) { throw new
         * ConfigurationException("Unable to retrieve " + CapacityDao.class); }
         */
        return true;
    }

    @Override
    public Pod allocateTo(VirtualMachineProfile vm, DataCenter dc, Set<? extends Pod> avoids) {
        return null;
    }
}
