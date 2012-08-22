/*
 * Session.java
 *
 * Copyright (C) 2012 LucasEasedUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gmail.lucaseasedup.logit;

/**
 * @author LucasEasedUp
 */
public class Session
{
    public Session()
    {
    }
    
    public long getStatus()
    {
        return status;
    }
    
    public void updateStatus(long update)
    {
        status += update;
    }
    
    public boolean isAlive()
    {
        return status >= 0L;
    }
    
    public void start()
    {
        status = 0L;
    }
    
    public void end()
    {
        status = -1L;
    }
    
    public void reset()
    {
        start();
    }
    
    private long status = -1L;
}
