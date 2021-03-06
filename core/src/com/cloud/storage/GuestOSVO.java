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
package com.cloud.storage;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.cloud.api.Identity;

@Entity
@Table(name="guest_os")
public class GuestOSVO implements GuestOS, Identity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    long id;
    
    @Column(name="category_id")
    private long categoryId;
    
    @Column(name="name")
    String name;
    
    @Column(name="display_name")
    String displayName;
    
    @Column(name="uuid")
    String uuid = UUID.randomUUID().toString();
    
    @Override
    public long getId() {
    	return id;
    }
    
    public long getCategoryId() {
    	return categoryId;
    }
    
    public void setCategoryId(long categoryId) {
    	this.categoryId = categoryId;
    }
    
    @Override
    public String getName() {
    	return name; 
    }
    
    public void setName(String name) {
    	this.name = name;
    }
    
    @Override
    public String getDisplayName() {
    	return displayName;
    }
    
    public void setDisplayName(String displayName) {
    	this.displayName = displayName;
    }
    
    @Override
    public String getUuid() {
    	return this.uuid; 
    }
    
    public void setUuid(String uuid) {
    	this.uuid = uuid;
    }
}
