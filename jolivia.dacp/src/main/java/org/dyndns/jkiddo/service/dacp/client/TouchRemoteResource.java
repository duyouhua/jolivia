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
package org.dyndns.jkiddo.service.dacp.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.jmdns.JmDNS;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.dyndns.jkiddo.IDiscoverer;
import org.dyndns.jkiddo.dmcp.chunks.media.DeviceName;
import org.dyndns.jkiddo.dmcp.chunks.media.DeviceType;
import org.dyndns.jkiddo.dmcp.chunks.media.PairingContainer;
import org.dyndns.jkiddo.dmcp.chunks.media.PairingGuid;
import org.dyndns.jkiddo.dmp.DmapUtil;
import org.dyndns.jkiddo.service.dacp.server.ITouchAbleServerResource;
import org.dyndns.jkiddo.service.dmap.MDNSResource;
import org.dyndns.jkiddo.service.dmap.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.core.spi.factory.ResponseBuilderImpl;

@Singleton
public class TouchRemoteResource extends MDNSResource implements ITouchRemoteResource, IDiscoverer
{
	public static final String DACP_CLIENT_PORT_NAME = "DACP_CLIENT_PORT_NAME";
	public static final String DACP_CLIENT_PAIRING_CODE = "DACP_CLIENT_PAIRING_CODE";

	public final static Logger logger = LoggerFactory.getLogger(TouchRemoteResource.class);

	private final IPairingDatabase database;
	private final Integer actualCode;
	private final String name;
	private final JmmDNS mDNS;

	private static MessageDigest md5;

	static
	{
		try
		{
			md5 = MessageDigest.getInstance("MD5");
		}
		catch(NoSuchAlgorithmException e)
		{
			logger.error(e.getMessage(), e);
		}
	}

	@Inject
	public TouchRemoteResource(JmmDNS mDNS, @Named(DACP_CLIENT_PORT_NAME) Integer port, IPairingDatabase database, @Named(DACP_CLIENT_PAIRING_CODE) Integer code, @Named(Util.APPLICATION_NAME) String applicationName) throws IOException
	{
		super(mDNS, port);
		this.mDNS = mDNS;
		this.actualCode = code;
		this.database = database;
		this.name = applicationName;
		this.mDNS.addServiceListener(ITouchAbleServerResource.TOUCH_ABLE_SERVER, this);
		this.mDNS.addNetworkTopologyListener(this);
		this.register();
	}

	@Override
	@GET
	@Path("pair")
	public Response pair(@Context HttpServletRequest httpServletRequest, @Context HttpServletResponse httpServletResponse, @QueryParam("pairingcode") String pairingcode, @QueryParam("servicename") String servicename) throws IOException
	{
		byte[] code = new byte[] { 0, 0, 0, 0, 0, 0, 0, 1 };

		String match = expectedPairingCode(actualCode, database.getPairCode());
		if(match.equals(pairingcode))
		{
			database.updateCode(servicename, Util.toHex(code));

			PairingContainer pc = new PairingContainer();
			pc.add(new PairingGuid(code));
			pc.add(new DeviceName("Jolivia’s iPhone"));
			pc.add(new DeviceType("iPhone"));

			return new ResponseBuilderImpl().entity(DmapUtil.serialize(pc, false)).status(Status.OK).build();
		}

		// TODO Response is not verified to be correct in iTunes regi - it is however better than nothing.
		return new ResponseBuilderImpl().status(Status.UNAUTHORIZED).build();
	}

	@Override
	protected ServiceInfo getServiceInfoToRegister()
	{
		final Map<String, String> values = new HashMap<String, String>();
		values.put("DvNm", "Use " + actualCode + " as code for " + name);
		values.put("RemV", "10000");
		values.put("DvTy", "iPod");
		values.put("RemN", "Remote");
		values.put("txtvers", "1");
		values.put("Pair", database.getPairCode());

		return ServiceInfo.create(TOUCH_REMOTE_CLIENT, Util.toHex("JoliviaRemote"), this.port, 0, 0, values);
	}

	public static String expectedPairingCode(Integer actualCode, String databaseCode) throws IOException
	{
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		os.write(databaseCode.getBytes("UTF-8"));

		byte codeAsBytes[] = String.format("%04d", actualCode).getBytes("UTF-8");
		for(int c = 0; c < codeAsBytes.length; c++)
		{
			os.write(codeAsBytes[c]);
			os.write(0);
		}

		return Util.toHex(md5.digest(os.toByteArray()));
	}

	@Override
	public void serviceAdded(ServiceEvent event)
	{}

	@Override
	public void serviceRemoved(ServiceEvent event)
	{
		logger.info("REMOVE: " + event.getDNS().getServiceInfo(event.getType(), event.getName()));
		try
		{
			final String code = database.findCode(event.getInfo().getName());
			if(code != null)
			{
				database.updateCode(event.getInfo().getName(), null);
				// Seems like someone removed our pairing ... make a re-announcement
				this.register();
			}
		}
		catch(IOException e)
		{
			logger.debug(e.getMessage(), e);
		}
	}

	@Override
	public void serviceResolved(ServiceEvent event)
	{}

	@Override
	public void inetAddressAdded(NetworkTopologyEvent event)
	{
		JmDNS mdns = event.getDNS();
		InetAddress address = event.getInetAddress();
		logger.info("Registered PairedRemoteDiscoverer @ " + address.getHostAddress());
		mdns.addServiceListener(ITouchAbleServerResource.TOUCH_ABLE_SERVER, this);
	}

	@Override
	public void inetAddressRemoved(NetworkTopologyEvent event)
	{
		JmDNS mdns = event.getDNS();
		mdns.removeServiceListener(ITouchAbleServerResource.TOUCH_ABLE_SERVER, this);
		mdns.unregisterAllServices();
	}
}
