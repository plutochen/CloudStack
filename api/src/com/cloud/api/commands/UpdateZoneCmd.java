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

package com.cloud.api.commands;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cloud.api.ApiConstants;
import com.cloud.api.BaseCmd;
import com.cloud.api.IdentityMapper;
import com.cloud.api.Implementation;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.ZoneResponse;
import com.cloud.dc.DataCenter;
import com.cloud.user.Account;
import com.cloud.user.UserContext;

@Implementation(description="Updates a Zone.", responseObject=ZoneResponse.class)
public class UpdateZoneCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateZoneCmd.class.getName());

    private static final String s_name = "updatezoneresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.DNS1, type=CommandType.STRING, description="the first DNS for the Zone")
    private String dns1;

    @Parameter(name=ApiConstants.DNS2, type=CommandType.STRING, description="the second DNS for the Zone")
    private String dns2;

    @Parameter(name=ApiConstants.GUEST_CIDR_ADDRESS, type=CommandType.STRING, description="the guest CIDR address for the Zone")
    private String guestCidrAddress;

    @IdentityMapper(entityTableName="data_center")
    @Parameter(name=ApiConstants.ID, type=CommandType.LONG, required=true, description="the ID of the Zone")
    private Long id;

    @Parameter(name=ApiConstants.INTERNAL_DNS1, type=CommandType.STRING, description="the first internal DNS for the Zone")
    private String internalDns1;

    @Parameter(name=ApiConstants.INTERNAL_DNS2, type=CommandType.STRING, description="the second internal DNS for the Zone")
    private String internalDns2;

    @Parameter(name=ApiConstants.NAME, type=CommandType.STRING, description="the name of the Zone")
    private String zoneName;

    @Parameter(name=ApiConstants.IS_PUBLIC, type=CommandType.BOOLEAN, description="updates a private zone to public if set, but not vice-versa")
    private Boolean isPublic;
    
    @Parameter(name=ApiConstants.ALLOCATION_STATE, type=CommandType.STRING, description="Allocation state of this cluster for allocation of new resources")
    private String allocationState;
    
    @Parameter(name=ApiConstants.DETAILS, type=CommandType.MAP, description="the details for the Zone")
    private Map details;
    
    @Parameter(name=ApiConstants.DHCP_PROVIDER, type=CommandType.STRING, description="the dhcp Provider for the Zone")
    private String dhcpProvider;
    
    @Parameter(name=ApiConstants.DOMAIN, type=CommandType.STRING, description="Network domain name for the networks in the zone; empty string will update domain with NULL value")
    private String domain;
    
    @Parameter(name=ApiConstants.DNS_SEARCH_ORDER, type=CommandType.LIST, collectionType = CommandType.STRING, description="the dns search order list")
    private List<String> dnsSearchOrder;   
    
    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getDns1() {
        return dns1;
    }

    public String getDns2() {
        return dns2;
    }

    public String getGuestCidrAddress() {
        return guestCidrAddress;
    }

    public Long getId() {
        return id;
    }

    public String getInternalDns1() {
        return internalDns1;
    }

    public String getInternalDns2() {
        return internalDns2;
    }

    public String getZoneName() {
        return zoneName;
    }

    public Boolean isPublic() {
        return isPublic;
    }
    
    public String getAllocationState() {
    	return allocationState;
    }
    
    public Map getDetails() {
        return details;
    }
    
    public String getDhcpProvider() {
        return dhcpProvider;
    }
    
    public String getDomain() {
        return domain;
    }

    public List<String> getDnsSearchOrder() {
        return dnsSearchOrder;
    }   
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }
    
    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
    
    @Override
    public void execute(){
    	UserContext.current().setEventDetails("Zone Id: "+getId());
    	DataCenter result = _configService.editZone(this);
        if (result != null) {
            ZoneResponse response = _responseGenerator.createZoneResponse(result, false);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update zone; internal error.");
        }
    }
}
