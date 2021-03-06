/*******************************************************************************
 * Copyright (c) 2013 Jens Kristian Villadsen.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jens Kristian Villadsen - Lead developer, owner and creator
 ******************************************************************************/
package org.dyndns.jkiddo.service.dpap.server;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.dyndns.jkiddo.service.dmap.ILibraryResource;

public interface IImageLibrary extends ILibraryResource
{
	public static final String DPAP_SERVICE_TYPE = "_dpap._tcp.local.";

	
	
	@Path("this_request_is_simply_to_send_a_close_connection_header")
	@GET
	public Response closeConnection() throws IOException;
}
