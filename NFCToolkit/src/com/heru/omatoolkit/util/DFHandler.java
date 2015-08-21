package com.heru.omatoolkit.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.os.Environment;

import com.devicefidelity.lib.AsciiUtils;
import com.devicefidelity.lib.DebugUtil;
import com.devicefidelity.lib.SDAPDUConnection;
import com.heru.process.Process;

public class DFHandler {
	
	private SDAPDUConnection sacn = SDAPDUConnection.getInstance();
	private boolean sessionOpen = false;
	private boolean enabled7816 = false;
	
	private Process mProcess;
	private String mResponse;
	
	public DFHandler(Process _process)
	{
		mProcess = _process;
	}
	
/* open session with SD Card */
	
	
	private String getCardPathUsingMount ()
	{
		String state = Environment.getExternalStorageState();
		DebugUtil.logD("mounted " + state);
		if(!Environment.MEDIA_MOUNTED.equals(state))
		{
			return "";
		}
		DebugUtil.logD("get path card mounted");
		ArrayList<String> mountPoints = new ArrayList<String>();
		try
		{
			// the mount command with no args will return a list of the mounted drives
			// we then look mounted partitions with a filetype of vfat, the microSD
			// will always have this format.
			java.lang.Process ps = Runtime.getRuntime().exec("mount");
			InputStream is = ps.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line;
			while ((line = br.readLine()) != null)
			{
				DebugUtil.logD("mounts " + line);
				if(line.indexOf("vfat") > -1)
				{
	        		String[] parts = line.split(" ");
	        		if (parts.length > 2)
	        		{
	        			if (parts[2].equals("vfat"))
	        			{
	    					mountPoints.add(parts[1]);	        				
	        			}
	        		}
				}
			}
			
		}
		catch (Exception e)
		{
			
		}
        try 
        {
        	File file;
        	for(String mountPoint : mountPoints)
        	{
    			String filePath = mountPoint + "/TEMP_SYS.DFI";
    			String cupFilePath = mountPoint + "/MPAYSSD0.SYS";
    			file = new File(filePath);
    			if(file.exists())
    			{
    				return mountPoint;
    			}
    			else 
    			{
    				file = new File(cupFilePath);
    				if(file.exists())
    				{		
    					return mountPoint;
    				}
    				
    			}
        	}
        	return "";
	    }
	    catch (Exception e) 
	    {
	    }		   
		return "";
    }
	
	/**
	 * @return the contents of the Vold file system table or 
	 * null if the file could not be successfully read.
	 */
	private String ReadVold()
    {
		final  String VoldPath = "/etc/vold.fstab";
    	InputStream is = null;
    	
    	String output = null;
    	try{
    		File file = new File(VoldPath);
    		if(file.exists() && file.canRead()){
    			int len = (int)file.length();
    			
    			is = new FileInputStream(VoldPath);
    			byte buffer[] = new byte[len];
    			
    			int size = 0; 
    			int offset = 0;
    			while(offset != len && 
    					(size = is.read(buffer, offset, len - offset)) != -1)
    			{
    				offset += size;
    			}
    			if(size != -1) // don't pass on only partial files.
    				output = new String(buffer);
    		}
    		else{
    			DebugUtil.logD("Unable to load Vold file " + VoldPath);
    		}
    	}
    	catch(Exception ex){
    		DebugUtil.logD(ex.getMessage());
    	}
    	finally{
    		try{
    			if(is != null){
    				is.close();
    			}
    		}
    		catch(Exception ex){
    			
    		}
    	}
    	
    	return output;
    }
	
	/**
	 * In android 2.2 the file /etc/Vold.fstab was added. This file lists where devices
	 * WILL BE mounted, even if they are not currently present. 
	 * 
	 * NOTE: This will not work with phones that have no external storage since there internal 
	 * will almost always match sdCard
	 * 
	 * @return null if the file could not be loaded or a path that matches the search criteria could not be found.
	 * 			Otherwise, returns our best guess at what the mount point will be.
	 */
	private String LocatePath()
    {
		/*	This file seems to take on 
		 *	 one of the following formats. Note however there is some variation between manufactures 
		 * 	and even deviced from the same manufacturer
			# internal sdcard
			{
				 	mount_deep = 0
				ums_path = /sys/devices/platform/s3c-usbgadget/gadget/lun1/file
				asec = disable
				mbr_policy = skip
			}
			dev_mount sdcard /mnt/sdcard 1 /devices/platform/s3c-sdhci.0/mmc_host/mmc0
			
			# externel sdcard
			{
				mount_deep = 1
				ums_path = /sys/devices/platform/s3c-usbgadget/gadget/lun2/file
				asec = enable
				mbr_policy = overwrite
			}
			dev_mount sdcard1 /mnt/sdcard/external_sd auto /devices/platform/s3c-sdhci.2/mmc_host/mmc2
		
		* 
		* - OR -
		* 
			dev_mount sdcard /mnt/sdcard 37 /devices/platform/msm_sdcc.1/mmc_host/mmc0
			dev_mount sdcard2 /mnt/sdcard/ext_sd auto /devices/platform/msm_sdcc.3/mmc_host/mmc2
		*/
		
		final int Flags = Pattern.MULTILINE | Pattern.CASE_INSENSITIVE;
    	final String SimplePattern = "^\\s*dev_mount\\s*\\S*\\s*(\\S*)";
    	final String DescriptivePattern = "^#\\s*((?:in|ex)tern(?:a|e)l)\\s*sdcard[^}]*\\}\\s*" + SimplePattern;
    
    	String file = ReadVold();
    	
    	if(file == null){
    		return null;
    	}
    	
    	// first check for the more restrictive pattern, one that actually says "External sdCard"
    	Pattern pattern = Pattern.compile(DescriptivePattern, Flags);
    	
    	Matcher m = pattern.matcher(file);

		while(m.find()){
			String type = m.group(1);
			DebugUtil.logD(String.format("Type: %s => MountPoint: %s", type, m.group(2)));
			if(type.compareToIgnoreCase("external") == 0 ||
					type.compareToIgnoreCase("externel") == 0){ // some samsung spell external incorrectly
				return m.group(2);
			}
		}
		
		// the specific test failed so examine all of the mount points.
		// Look for any that contain "ex"; External, ext_sdCard, etc....
		// If that is not present then check for the standard sdCard.
		// Most modern phones with internal storage will mount that to sdCard,
		// older phones will mount the micro here
		pattern = Pattern.compile(SimplePattern, Flags);
		m = pattern.matcher(file);
		String sdCard =  null;
		
		while(m.find()){
			file = m.group(1);
			DebugUtil.logD("Brute force search on " + file);
			
			int index = file.lastIndexOf('/');
			String fileName = file.substring(index+1, file.length()).toLowerCase();
			if(fileName.indexOf("ex") >= 0){
				return file;
			}
			else if(fileName.compareToIgnoreCase("sdcard") == 0){
				// we found standart /sdCard but we want to keep looking for a better match
				sdCard = file;
			}
		}
			
		return sdCard;
		
    }
	/*
	 * This helper function returns which path we think the microSD is located at.
	 * For android 2.2 and above we will return where we think an external sd card will
	 * be mounted, whether it is present or not. On previous versions of android we will
	 * return which of the MOUNTED paths is the most likely to be the microSD
	 *
	 */
	public String getCardPath()
	{
		String path = LocatePath();
		if(path == null){
			path = getCardPathUsingMount();
		}
		return path+"/";
	}
	
	public String getCardPath(Context ctx)
	{
		String path = "";
		File fileList2[] = ctx.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS);

		if(fileList2.length == 1) {
			mProcess.showMessageToUI("external device is not mounted.");
		} else {
			mProcess.showMessageToUI("external device is mounted.");
		    File extFile = fileList2[1];
		    String absPath = extFile.getAbsolutePath(); 
		    path = absPath;
		    mProcess.showMessageToUI("external device download : "+path);
		}
		return path+"/";
	}
	
	public int openSession() 
    {
    	String sdCardPath = getCardPath();
    	if(sdCardPath.equals(""))
    	{
    		mProcess.showMessageToUI("OpenSession failed: Could not find path to external SD card");
    		return -1;
    	}
        try 
        {
     	   sacn.openSession(sdCardPath);  		
    	   mProcess.showMessageToUI("OpenSession successful");
	    }
	    catch (Exception e) 
	    {
	    	mProcess.showMessageToUI("OpenSession failed:" + e.getMessage() + " at path : " + sdCardPath);
            if (e.getMessage().compareTo("Invalid session") == 0) 
            {
                openSession();
            }
            sessionOpen = false;
            return -1;
	    }
	    sessionOpen = true;
	    return 0;
    }
	
	public int openSession(Context ctx)
	{
		String sdCardPath = getCardPath(ctx);
    	if(sdCardPath.equals(""))
    	{
    		mProcess.showMessageToUI("OpenSession failed: Could not find path to external SD card");
    		return -1;
    	}
        try 
        {
     	   sacn.openSession(sdCardPath);  		
    	   mProcess.showMessageToUI("OpenSession successful");
	    }
	    catch (Exception e) 
	    {
	    	mProcess.showMessageToUI("OpenSession failed:" + e.getMessage() + " at path : " + sdCardPath);
            if (e.getMessage().compareTo("Invalid session") == 0) 
            {
                openSession();
            }
            sessionOpen = false;
            return -1;
	    }
	    sessionOpen = true;
	    return 0;
	}
	
	
	
	 /* close session with SD card */
    public int closeSession() 
    {
    	sessionOpen = false;
        try 
        {
            sacn.closeSession();
            mProcess.showMessageToUI("CloseSession successful");
        }
        catch (Exception e) 
        {
            mProcess.showMessageToUI("CloseSession failed:" + e.getMessage());
            return -1;
        }
        return 0;
    }
    
    /**
     * Sends APDU and get response.
     * In case of failure, it will keep session open.
     * @param APDUCommandHexStr string contains a String of APDU command to be send to the smart chip
     * @return error flag
     */
    public boolean sendAPDU(String APDUCommandHexStr, boolean showMessage)    {

        if ( APDUCommandHexStr == null || APDUCommandHexStr.length() == 0 )
            return false;

        byte [] APDUCommand = AsciiUtils.hexStringToByteArray(APDUCommandHexStr);
        if(showMessage)
        {
        	mProcess.showMessageToUI(">> " + APDUCommandHexStr);
        }
        else
        {
        	mProcess.logMessageToUI(">> " + APDUCommandHexStr);
        }

        try 
        {
            //  ...send APDU and get response
            byte [] response = sacn.exchangeData(APDUCommand);
            String sResponse = AsciiUtils.getAsciiHex(response) + "\r\n";
            mResponse = sResponse;
            if (response.length < 2)
            	mProcess.showMessageToUI("<< " + sResponse + ". Invalid Response." + "\r\n");
            else 
            	mProcess.showMessageToUI("<< " + sResponse + "\r\n");
        }
        catch (Exception e)  
        {
        	mProcess.showMessageToUI("Exchange Data Failed: " + e.getMessage());
            if (e.getMessage().compareTo("Invalid session") == 0) 
            {
                openSession();
            }
            return false;
        }

        return true;
    }
    
    public String getResponse()
    {
    	String resp = mResponse.replaceAll(" ", "");
    	resp = resp.trim();
    	return resp;
    }
    
    /**
     * Demostrates usage of API function enable 7816 interface.
     * Turns on power and reset the 7816 interface.
     * @return error code of API openSession
     */
    public int enable7816() 
    {
        try 
        {
            byte [] ATRresponse = sacn.enable7816Interface();

            mProcess.showMessageToUI("Enable 7816 Interface successful");
            mProcess.showMessageToUI("<< " + AsciiUtils.getAsciiHex(ATRresponse));
            enabled7816 = true;
        }
        catch (Exception e) {
        	mProcess.showMessageToUI("Enable 7816 Interface Failed: " + e.getMessage());
            if (e.getMessage().compareTo("Invalid session") == 0) 
            {
                openSession();
            }
            enabled7816 = false;
            return -1;
        }
        return 0;
    }
    
    public int disable7816() 
    {
        enabled7816 = false;
        try 
        {
            sacn.disable7816Interface();
            mProcess.showMessageToUI("disable 7816 successful");
        }
        catch (Exception e) 
        {
            mProcess.showMessageToUI("disable 7816 Failed: " + e.getMessage());
            if (e.getMessage().compareTo("Invalid session") == 0) 
            {
                openSession();
            }
            return -1;
        }
        return 0;
    }
}
