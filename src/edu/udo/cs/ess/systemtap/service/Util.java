package edu.udo.cs.ess.systemtap.service;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import android.content.Context;
import android.os.Environment;
import edu.udo.cs.ess.logging.Eventlog;
import edu.udo.cs.ess.systemtap.Config;

public class Util
{
	private static final String TAG = Util.class.getSimpleName();
	
	/**
	 * Execute the command {@link pCmd} on a shell 
	 * @param pCmd
	 * @return 0 if all went fine, -1 otherwise 
	 */
	public static int runCmd(String pCmd)
	{
		Runtime runtime;
		Process process;
		int retval = -1;
		
		try
		{
			runtime = Runtime.getRuntime();
			process = runtime.exec(new String[]{"sh","-c",pCmd});
			retval = process.waitFor();
		}
		catch (Exception e)
		{
			Eventlog.printStackTrace(TAG, e);
		}
		
		return retval;
	}
	
	/**
	 * Execute the command {@link pCmd} on a shell as root sending each string in {@link pInput} as a separated line stdin of the new process.
	 * @param pCmd
	 * @param pInput
	 * @return 0 if all went fine, -1 otherwise
	 */
	public static int runCmdAsRoot(String pCmd, List<String> pInput)
	{
		Runtime runtime;
		Process process;
		int retval = -1;
		
		Eventlog.i(TAG, "Try to run \"" + pCmd + "\" as root");
		try
		{
			runtime = Runtime.getRuntime();
			process = runtime.exec(new String[]{"su","-c",pCmd});
			if (pInput != null && pInput.size() > 0)
			{
				PrintWriter out = new PrintWriter(process.getOutputStream(),true);
				Iterator<String> iter = pInput.iterator(); 
				while (iter.hasNext())
				{
					String next = iter.next();
					out.write(next + "\n");
				}
				out.flush();
				out.close();
			}
			retval = process.waitFor();
			if (retval != 0)
			{
				DataInputStream in = new DataInputStream(process.getErrorStream());
				String line = null;
				Eventlog.e(TAG, "Error while running \"" + pCmd + "\" as root:");
				while ((line = in.readLine()) != null)
				{
					Eventlog.e(TAG,line);
				}				
				in.close();
			}
			else
			{
				Eventlog.i(TAG, "\"" + pCmd + "\" terminated successfully.");
			}
		}
		catch (Exception e)
		{
			Eventlog.printStackTrace(TAG, e);
		}
		
		return retval;
	}
	
	/**
	 * Creates the direcotry specified by {@link pDirecotry}.
	 * It creates all parent dirs (behaves like "mkdir -p").
	 * @param pDirecotry
	 */
	public static boolean createDirecotryOnExternalStorage(String pDirectory)
	{
		File root = null, file = null;
		
		root = Environment.getExternalStorageDirectory();
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{
			file = new File(root + File.separator + pDirectory);
			if (!file.exists())
			{
				return file.mkdirs();
			}
			else
			{
				return true;
			}
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Copy the file included in the apk-file (identified by {@link pRAWID}) to the apps private data dir as file {@link pFilename}
	 * @param pContext the applicatins context get access to the raw resource
	 * @param pRAWID
	 * @param pFilename the filename the raw resource should be copied to
	 * @return true if all went fine, false otherwise
	 */
	public static boolean copyFileFromRAW(Context pContext, int pRAWID, String pFilename)
	{
		InputStream is = null;
		OutputStream os = null;
		File file = null;
		byte[] buffer = new byte[1024];
		int count  = 0;
		
		try
		{
			file = new File(pContext.getFilesDir().getParent() + File.separator + pFilename);
			if (!file.exists())
			{
				if (!file.createNewFile())
				{
					Eventlog.e(TAG, "Could not create file: " + pFilename);
					return false;
				}
				is = pContext.getApplicationContext().getResources().openRawResource(pRAWID);
				os = new FileOutputStream(file);
				while ((count = is.read(buffer)) > 0)
				{
					os.write(buffer, 0, count);
				}
				
				is.close();
				os.close();
				if (!file.setExecutable(true, false))
				{
					Eventlog.e(TAG, "Could not make file executeable: " + pFilename);
					return false;
				}
			}
			return true;
		}
		catch (Exception e)
		{
			Eventlog.printStackTrace(TAG, e);
			return false;
		}
	}
	
	/**
	 * Get all pids of {@link pCmd}.
	 * @param pContext 
	 * @param pCmd
	 * @return if no process with name {@link pCmd} is running, an empty array will be returned. otherwise the array contains all process ids of this command. if an error occurs, null will be returned.
	 */
	public static List<Integer> getProcessIDs(Context pContext, String pCmd)
	{
		Runtime runtime = null;
		Process process = null;
		
		LinkedList<Integer> list = new LinkedList<Integer>();
		
		try
		{
			runtime = Runtime.getRuntime();
			process = runtime.exec(new String[]{pContext.getFilesDir().getParent() + File.separator + Config.BUSYBOX_NAME,"pidof",pCmd});
			DataInputStream in = new DataInputStream(process.getInputStream());
			
			if (process.waitFor() != 0)
			{
				return null;
			}

			Eventlog.d(TAG,"Parsing pidof output...");
			String line = null;
			StringTokenizer tokenizer = null;
			
			while ((line = in.readLine()) != null)
			{
				tokenizer = new StringTokenizer(line, " ");
				while (tokenizer.hasMoreTokens())
				{
					list.add(Integer.valueOf(tokenizer.nextToken()));
				}
			}
			in.close();
			Eventlog.d(TAG,"Got " + list.size() + " pids from pidof");

			return list;
		}
		catch(Exception e)
		{
			Eventlog.printStackTrace(TAG, e);
		}		
		return null;
	}
	
	/**
	 * Reads {@link pPidFile}s content, which acutally should be a process id.   It checks wether it belongs to a running process.
	 * @param pContext the activities context
	 * @param pPidFile 
	 * @return true if the pid belongs to a running process
	 * @throws IOException
	 */
	public static boolean isPidFilePidValid(Context pContext, File pPidFile) throws IOException
	{
		int pid = -1;
		DataInputStream in = new DataInputStream(new FileInputStream(pPidFile));
		pid = Integer.valueOf(in.readLine());
		in.close();
		in = null;
		List<Integer> pids = Util.getProcessIDs(pContext,Config.STAP_IO_NAME);

		if (pids == null)
		{
			return false;
		}
		
		return pids.contains(pid);
	}
	
	/**
	 * First check if pid file exists. Second, read its content and query system wether the pid belongs a running process.
	 * @param pContext the activities context
	 * @param pModulename the modulename which status the caller wants to know
	 * @param pShouldRun true if the service expects this module running
	 * @return Returns new module state
	 */
	public static Module.Status checkModuleStatus(Context pContext, String pModulename, boolean pShouldRun)
	{
		File pidFile = new File(Config.STAP_RUN_ABSOLUTE_PATH + File.separator + pModulename + Config.PID_EXT);
		if (pidFile.exists())
		{
			try
			{
				if (Util.isPidFilePidValid(pContext,pidFile))
				{
					if (pShouldRun)
					{
						Eventlog.d(TAG,"module (" + pModulename + ") is running and pid file exists. all fine. :-)");
						return Module.Status.RUNNING;
					}
					else
					{
						Eventlog.e(TAG,"module (" + pModulename + ") is stopped, but stap is running. Updating status...");
						return Module.Status.RUNNING;
					}
				}
				else
				{
					if (pShouldRun)
					{
						Eventlog.e(TAG,"module (" + pModulename + ") is running, but stap is not running. Updating status....");
						Eventlog.e(TAG,"module (" + pModulename + ") is crashed. Removing pid file: " + pidFile.delete());
						return Module.Status.CRASHED;
					}
					else
					{
						Eventlog.d(TAG,"module (" + pModulename + ") is stopped, but pidfile exsits. Deleting it: " + pidFile.delete());
						return Module.Status.STOPPED;
					}
				}
			}
			catch (IOException e)
			{
				Eventlog.e(TAG,"Error reading pid file of module " + pModulename + ". Try again later.");
				Eventlog.printStackTrace(TAG, e);
				return Module.Status.CRASHED;
			}
		}
		else
		{
			if (pShouldRun)
			{
				Eventlog.e(TAG,"module (" + pModulename + ") is running, but stap (no pid file) is not running. Updating status....");
				return Module.Status.CRASHED;
			}
			else
			{
				Eventlog.d(TAG,"module (" + pModulename + ") is stopped and no pid file exists. all fine. :-)");
				return Module.Status.STOPPED;
			}
		}
	}
}
