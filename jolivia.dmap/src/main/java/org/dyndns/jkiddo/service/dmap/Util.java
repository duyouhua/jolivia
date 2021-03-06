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
package org.dyndns.jkiddo.service.dmap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.dyndns.jkiddo.NotImplementedException;
import org.dyndns.jkiddo.dmp.chunks.Chunk;
import org.dyndns.jkiddo.dmp.util.DmapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.sun.jersey.core.spi.factory.ResponseBuilderImpl;

public class Util
{
	public static final String APPLICATION_NAME = "APPLICATION_NAME";
	public static final String APPLICATION_X_DMAP_TAGGED = "application/x-dmap-tagged";
	private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

	private static final int PARTIAL_CONTENT = 206;

	public static Response buildResponse(final Chunk chunk, final String dmapKey, final String dmapServiceName) throws IOException
	{
		final byte[] binaryChunk = DmapUtil.serialize(chunk, false);
		return buildResponse(dmapKey, dmapServiceName).entity(binaryChunk).header(HttpHeaders.CONTENT_LENGTH, String.valueOf(binaryChunk.length)).build();// .header("Content-Encoding", "gzip").build();
	}

	public static Response buildAudioResponse(final byte[] buffer, final long position, final String dmapKey, final String dmapServiceName)
	{
		final ResponseBuilder response = new ResponseBuilderImpl().header("Accept-Ranges", "bytes").header(HttpHeaders.DATE, DmapUtil.now()).header(dmapKey, dmapServiceName).header(HttpHeaders.CONTENT_TYPE, APPLICATION_X_DMAP_TAGGED).header("Connection", "close");

		if(position == 0)
		{
			response.status(Response.Status.OK);
			response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(buffer.length));
		}
		else
		{
			response.status(PARTIAL_CONTENT);
			response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(buffer.length - position));
			response.header("Content-Range", "bytes " + position + "-" + (buffer.length - 1) + "/" + buffer.length);
		}
		response.entity(buffer);
		return response.build();
	}
	
	public static Response buildBinaryResponse(final byte[] buffer, final String dmapKey, final String dmapServiceName)
	{
		
		final ResponseBuilder response = new ResponseBuilderImpl().header(HttpHeaders.DATE, DmapUtil.now()).header(dmapKey, dmapServiceName).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_ENCODING, "gzip");
		response.status(Response.Status.OK);
		response.entity(buffer);
		response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(buffer.length));
		return response.build();
	}

	private static ResponseBuilder buildResponse(final String dmapKey, final String dmapServiceName)
	{
		return new ResponseBuilderImpl().header(HttpHeaders.DATE, DmapUtil.now()).header(dmapKey, dmapServiceName).header(HttpHeaders.CONTENT_TYPE, APPLICATION_X_DMAP_TAGGED).header("Connection", "Keep-Alive").status(Response.Status.OK);
	}

	enum SecurityType
	{
		BASIC, DIGEST
	}

	public static Response buildAuthenticationResponse(final String dmapKey, final String dmapServiceName, final SecurityType sm) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		final ResponseBuilder builder = new ResponseBuilderImpl().header(HttpHeaders.DATE, DmapUtil.now()).header(dmapKey, dmapServiceName).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML).header(HttpHeaders.CONTENT_LENGTH, "0").header("Connection", "Keep-Alive").status(Response.Status.UNAUTHORIZED);

		switch(sm)
		{
			case BASIC:
				builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + DmapUtil.DAAP_REALM + "\"");
				break;
			case DIGEST:
				builder.header(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"" + DmapUtil.DAAP_REALM + "\", nonce=\"" + DmapUtil.nonce() + "\"");
				break;
			default:
				throw new NotImplementedException();
		}
		return builder.build();

	}

	public static Response buildEmptyResponse(final String dmapKey, final String dmapServiceName)
	{
		return buildResponse(dmapKey, dmapServiceName).status(Response.Status.NO_CONTENT).build();
	}

	public static String toHex(final String value)
	{
		try
		{
			return toHex(value.getBytes("UTF-8"));
		}
		catch(final UnsupportedEncodingException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static String toHex(final byte[] code)
	{
		final StringBuilder sb = new StringBuilder();
		for(final byte b : code)
		{
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString().toUpperCase();
	}

	public static String toServiceGuid(final String name)
	{
		try
		{
			return toHex((name + "1111111111111111").getBytes("UTF-8")).substring(0, 16);
		}
		catch(final UnsupportedEncodingException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static String fromHex(final String hex)
	{
		final StringBuilder str = new StringBuilder();
		for(int i = 0; i < hex.length(); i += 2)
		{
			str.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
		}
		return str.toString();
	}

	/**
	 * Converts an array of bytes to a hexadecimal string
	 * 
	 * @param bytes
	 *            array of bytes
	 * @return hexadecimal representation
	 */
	public static String toHexString(final byte[] bytes)
	{
		final StringBuilder s = new StringBuilder();
		for(final byte b : bytes)
		{
			final String h = Integer.toHexString(0x100 | b);
			s.append(h.substring(h.length() - 2, h.length()).toUpperCase());
		}
		return s.toString();
	}

	public static String toMacString(final byte[] bytes)
	{
		final String hex = toHexString(bytes);
		return hex.substring(0, 2) + ":" + hex.substring(2, 4) + ":" + hex.substring(4, 6) + ":" + hex.substring(6, 8) + ":" + hex.substring(8, 10) + ":" + hex.substring(10, 12);
	}

	/**
	 * Returns a suitable hardware address.
	 * 
	 * @return a MAC address
	 */
	public static byte[] getHardwareAddress()
	{
		try
		{
			/* Search network interfaces for an interface with a valid, non-blocked hardware address */
			for(final NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces()))
			{
				if(iface.isLoopback())
					continue;
				if(iface.isPointToPoint())
					continue;
				if(!iface.isUp())
					continue;
				try
				{
					final byte[] ifaceMacAddress = iface.getHardwareAddress();
					if((ifaceMacAddress != null) && (ifaceMacAddress.length == 6) && !isBlockedHardwareAddress(ifaceMacAddress))
					{
						LOGGER.info("Hardware address is " + toHexString(ifaceMacAddress) + " (" + iface.getDisplayName() + ")");
						return Arrays.copyOfRange(ifaceMacAddress, 0, 6);
					}
				}
				catch(final Throwable e)
				{
					/* Ignore */
				}
			}
		}
		catch(final Throwable e)
		{
			/* Ignore */
		}

		/* Fallback to the IP address padded to 6 bytes */
		try
		{
			final byte[] hostAddress = Arrays.copyOfRange(InetAddress.getLocalHost().getAddress(), 0, 6);
			LOGGER.info("Hardware address is " + toHexString(hostAddress) + " (IP address)");
			return hostAddress;
		}
		catch(final Throwable e)
		{
			/* Ignore */
		}

		/* Fallback to a constant */
		LOGGER.info("Hardware address is 00DEADBEEF00 (last resort)");
		return new byte[] { (byte) 0x00, (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, (byte) 0x00 };
	}

	/**
	 * Decides whether or nor a given MAC address is the address of some virtual interface, like e.g. VMware's host-only interface (server-side).
	 * 
	 * @param addr
	 *            a MAC address
	 * @return true if the MAC address is unsuitable as the device's hardware address
	 */
	public static boolean isBlockedHardwareAddress(final byte[] addr)
	{
		if((addr[0] & 0x02) != 0)
			/* Locally administered */
			return true;
		else if((addr[0] == 0x00) && (addr[1] == 0x50) && (addr[2] == 0x56))
			/* VMware */
			return true;
		else if((addr[0] == 0x00) && (addr[1] == 0x1C) && (addr[2] == 0x42))
			/* Parallels */
			return true;
		else if((addr[0] == 0x00) && (addr[1] == 0x25) && (addr[2] == (byte) 0xAE))
			/* Microsoft */
			return true;
		else
			return false;
	}
	
	public static NSDictionary requestPList(final String username, final String password) throws Exception
	{
		final HttpURLConnection connection = (HttpURLConnection) new URL("https://homesharing.itunes.apple.com" + "/WebObjects/MZHomeSharing.woa/wa/getShareIdentifiers").openConnection();
		connection.setAllowUserInteraction(false);
		connection.setDoInput(true);
		connection.setDoOutput(true);
		
		connection.setRequestProperty("Viewer-Only-Client", "1");
		connection.setRequestProperty("User-Agent", "Remote/2.0");
		connection.setRequestProperty("Accept-Encoding", "gzip");
		connection.setRequestProperty("Connection", "keep-alive");
		connection.setRequestProperty("Content-Type", "text/xml");
		connection.setReadTimeout(0);
		
		final NSDictionary root = new NSDictionary();
		root.put("appleId", username);
		root.put("guid", "empty");
		root.put("password", password);
		final String xml = root.toXMLPropertyList();
		connection.connect();
		
		final OutputStream os = connection.getOutputStream();
		final BufferedWriter writer = new BufferedWriter(
		        new OutputStreamWriter(os, "UTF-8"));
		writer.write(xml);
		writer.flush();
		writer.close();
		os.close();


		if(connection.getResponseCode() >= HttpURLConnection.HTTP_UNAUTHORIZED)
			throw new Exception("HTTP Error Response Code: " + connection.getResponseCode());

		// obtain the encoding returned by the server
		final String encoding = connection.getContentEncoding();

		final InputStream inputStream;

		// create the appropriate stream wrapper based on the encoding type
		if(encoding != null && encoding.equalsIgnoreCase("gzip"))
		{
			inputStream = new GZIPInputStream(connection.getInputStream());
		}
		else if(encoding != null && encoding.equalsIgnoreCase("deflate"))
		{
			inputStream = new InflaterInputStream(connection.getInputStream(), new Inflater(true));
		}
		else
		{
			inputStream = connection.getInputStream();
		}
		final NSDictionary dictionary = (NSDictionary) PropertyListParser.parse(inputStream);
		final NSString o1 = (NSString) dictionary.get("spid");
		final NSNumber o2 = (NSNumber) dictionary.get("status");
		final NSNumber o3 = (NSNumber) dictionary.get("dsid");
		final NSString o4 = (NSString) dictionary.get("sgid");
		if(o1 == null && o3 == null && o4 == null && o2.intValue() == 5505)
			throw new Exception("bad password");
		return dictionary;
	}
}
