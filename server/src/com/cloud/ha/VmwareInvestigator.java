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
package com.cloud.ha;

import javax.ejb.Local;

import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.VMInstanceVO;

@Local(value=Investigator.class)
public class VmwareInvestigator extends AdapterBase implements Investigator {
    protected VmwareInvestigator() {
    }
    
    @Override
    public Status isAgentAlive(HostVO agent) {
    	if(agent.getHypervisorType() == HypervisorType.VMware)
    		return Status.Disconnected;
    	
    	return null;
    }
    
    @Override
    public Boolean isVmAlive(VMInstanceVO vm, HostVO host) {
    	if(vm.getHypervisorType() == HypervisorType.VMware)
    		return true;
    	
    	return null;
    }
}

