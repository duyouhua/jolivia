package org.dyndns.jkiddo.dmap.chunks.audio.extension;

import org.dyndns.jkiddo.dmp.chunks.BooleanChunk;

import org.dyndns.jkiddo.dmp.IDmapProtocolDefinition.DmapChunkDefinition;
import org.dyndns.jkiddo.dmp.DMAPAnnotation;

@DMAPAnnotation(type=DmapChunkDefinition.aeMQ)
public class UnknownMQ extends BooleanChunk
{
	public UnknownMQ()
	{
		this(false);
	}

	public UnknownMQ(boolean i)
	{
		super("aeMQ", "com.apple.itunes.unknown-MQ", i);
	}
}
