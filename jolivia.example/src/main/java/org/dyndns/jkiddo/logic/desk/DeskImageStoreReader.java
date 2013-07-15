package org.dyndns.jkiddo.logic.desk;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.dyndns.jkiddo.logic.interfaces.IImageStoreReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeskImageStoreReader implements IImageStoreReader
{
	private static final Logger logger = LoggerFactory.getLogger(DeskImageStoreReader.class);
	private Map<IImageItem, File> mapOfImageToFile;
	private String path;

	public DeskImageStoreReader()
	{
		this(System.getProperty("user.dir") + System.getProperty("file.separator") + "etc");
		this.mapOfImageToFile = new HashMap<IImageItem, File>();
	}

	public DeskImageStoreReader(String path)
	{
		this.mapOfImageToFile = new HashMap<IImageItem, File>();
		this.path = path;
	}

	@Override
	public Set<IImageItem> readImages() throws Exception
	{
		try
		{
			traverseRootPathRecursively(new File(path));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return Collections.unmodifiableSet(mapOfImageToFile.keySet());
	}

	private static boolean isImage(File f)
	{
		if(f.getPath().endsWith(".jpg") || f.getPath().endsWith(".jpeg"))
			return true;
		return false;
	}

	private void traverseRootPathRecursively(File f)
	{
		if(f.isDirectory())
		{
			File[] contents = f.listFiles();
			for(int i = 0; i < contents.length; i++)
			{
				traverseRootPathRecursively(contents[i]);
			}
		}
		else if(isImage(f))
		{
			mapOfImageToFile.put(populateImage(f), f);
		}
	}

	private IImageItem populateImage(final File f)
	{
		return new IImageItem() {
			@Override
			public String getImageFilename()
			{
				return f.getName();
			}

			@Override
			public int getSize()
			{
				return (int) f.length();
			}

			@Override
			public String getFormat()
			{
				return "JPEG";
			}

			@Override
			public int getRating()
			{
				return 5;
			}

			@Override
			public Date getCreationDate()
			{
				return new Date();
			}
		};
	}
	@Override
	public URI getImage(IImageItem image) throws Exception
	{
		if(image != null)
		{
			logger.debug("Serving " + image.getImageFilename());
		}
		return mapOfImageToFile.get(image).toURI();
	}

}
