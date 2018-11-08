package com.ycloud.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;

import org.json.JSONObject;

import android.os.Build;

public class CPUMemUtils {
	
	public static String getCustomInfo() {
		return getCustomInfo(false);
	}
	
	public static String getCustomInfo(boolean data_only) {
		String info;
		if (data_only) {
			info = 	Build.MANUFACTURER + "\n" +
					Build.PRODUCT + "\n" +
					Build.BOARD + "\n" +
					Build.MODEL + "\n" +
					Build.VERSION.SDK_INT + "\n" +
					Build.VERSION.RELEASE + "\n" +
					CPUMemUtils.getCpuType() + "\n" +
					CPUMemUtils.getCPUInfo().mCPUMaxFreq/1000.0/1000.0 + "\n" +
					CPUMemUtils.getTotalMemory() + "\n";
		}
		else{
			info = 	"MANUFACTURER: " + Build.MANUFACTURER + "\n" +
					"PRODUCT: " + Build.PRODUCT + "\n" +
					"BOARD: " + Build.BOARD + "\n" +
					"MODEL: " +Build.MODEL + "\n" +
					"VERSION.SDK_INT: " + Build.VERSION.SDK_INT + "\n" +
					"VERSION.RELEASE: " + Build.VERSION.RELEASE + "\n" +
					"CPU:" + CPUMemUtils.getCpuType() + "\n" +
					"MaxCpuFreq:" + CPUMemUtils.getCPUInfo().mCPUMaxFreq/1000.0/1000.0 + "GHz\n" +
					"TotalMemory:" + CPUMemUtils.getTotalMemory() + "MB";
		}
		return info;
	}
	
	public static String getMobileInfo() { 
	    //StringBuffer sb = new StringBuffer(); 
	    JSONObject mbInfo = new JSONObject(); 
	    try { 
	    
	        Field[] fields = Build.class.getDeclaredFields(); 
	        for (Field field : fields) { 
	            field.setAccessible(true); 
	            String name = field.getName(); 
	            String value = field.get(null).toString(); 
	            //sb.append(name + "=" + value); 
	            //sb.append("n"); 
	            mbInfo.put(name, value); 
	        } 
	    } catch (Exception e) { 
	        e.printStackTrace(); 
	    }         
	    //return sb.toString(); 
	    return mbInfo.toString(); 
	} 
	
	public static String getBoard() { 
	    try { 
	    
	        Field[] fields = Build.class.getDeclaredFields(); 
	        for (Field field : fields) { 
	            field.setAccessible(true); 
	            String name = field.getName(); 
	            String value = field.get(null).toString(); 
	            if (name.equals("BOARD"))
	            	return value;
	        } 
	    } catch (Exception e) { 
	        e.printStackTrace(); 
	    }         
	    return "";
	} 
	
	public static String getMobileXInfo(String info_name) { 
	    try { 
	        Field[] fields = Build.class.getDeclaredFields(); 
	        for (Field field : fields) { 
	            field.setAccessible(true); 
	            String name = field.getName(); 
	            String value = field.get(null).toString(); 
	            if (name.equals(info_name))
	            	return value;
	        } 
	    } catch (Exception e) { 
	        e.printStackTrace(); 
	    }         
	    return "";
	} 
	
	static public String[] getCpuTypeAndFreq() {  
        String str1 = "/proc/cpuinfo";  
        String str2 = "";  
        String[] cpuInfo = {"", ""}; 
        String[] arrayOfString;  
        try {  
            FileReader fr = new FileReader(str1);  
            BufferedReader localBufferedReader = new BufferedReader(fr, 8192);  
            str2 = localBufferedReader.readLine();  
            arrayOfString = str2.split("\\s+");  
            for (int i = 2; i < arrayOfString.length; i++) {  
                cpuInfo[0] = cpuInfo[0] + arrayOfString[i] + " ";  
            }  
            str2 = localBufferedReader.readLine();  
            arrayOfString = str2.split("\\s+");  
            cpuInfo[1] += arrayOfString[2];  
            localBufferedReader.close();  
        } catch (IOException e) {  
        }  
        return cpuInfo;  
    }
	    
	    
	static public String getCpuString(){ 
	    if(Build.CPU_ABI.equalsIgnoreCase("x86")){ 
	        return "Intel"; 
	    } 
	        
	    String strInfo = ""; 
	    try
	    { 
	        byte[] bs = new byte[1024]; 
	        RandomAccessFile reader = new RandomAccessFile("/proc/cpuinfo", "r"); 
	        reader.read(bs); 
	        String ret = new String(bs); 
	        int index = ret.indexOf(0); 
	        if(index != -1) { 
	            strInfo = ret.substring(0, index); 
	        } else { 
	            strInfo = ret; 
	        } 
	        reader.close();
	    } 
	    catch (IOException ex){ 
	        ex.printStackTrace(); 
	    } 
	        
	    return strInfo; 
	} 
	    
	static public String getCpuType(){ 
	    String strInfo = getCpuString(); 
	    String strType = null; 
	        
	    if (strInfo.contains("ARMv5")) { 
	        strType = "armv5"; 
	    } else if (strInfo.contains("ARMv6")) { 
	        strType = "armv6"; 
	    } else if (strInfo.contains("ARMv7")) { 
	        strType = "armv7"; 
	    } else if (strInfo.contains("Intel")){ 
	        strType = "x86"; 
	    }else{ 
	        strType = "unknown"; 
	        return strType; 
	    } 
	        
	    if (strInfo.contains("neon")) { 
	        strType += "_neon"; 
	    }else if (strInfo.contains("vfpv3")) { 
	        strType += "_vfpv3"; 
	    }else if (strInfo.contains(" vfp")) { 
	        strType += "_vfp"; 
	    }else{ 
	        strType += "_none"; 
	    } 
	        
	    return strType; 
	} 
	    
	private final static String kCpuInfoMaxFreqFilePath = 
			"/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"; 
	
	private static int getMaxCpuFreq() { 
	    int result = 0; 
	    FileReader fr = null; 
	    BufferedReader br = null; 
	    try { 
	        fr = new FileReader(kCpuInfoMaxFreqFilePath); 
	        br = new BufferedReader(fr); 
	        String text = br.readLine(); 
	        if (text != null) { 
	            result = Integer.parseInt(text.trim()); 
	        } 
	    } catch (FileNotFoundException e) { 
	        e.printStackTrace(); 
	    } catch (IOException e) { 
	        e.printStackTrace(); 
	    } finally { 
	        if (fr != null) 
	            try { 
	                fr.close(); 
	            } catch (IOException e) { 
	                // TODO Auto-generated catch block 
	                e.printStackTrace(); 
	            } 
	        if (br != null) 
	            try { 
	                br.close(); 
	            } catch (IOException e) { 
	                // TODO Auto-generated catch block 
	                e.printStackTrace(); 
	            } 
	    } 
	    
	    return result; 
	} 
	        
	public static class CPUInfo{ 
	    public CPUInfo(){ 
	            
	    } 
	        
	    public static final int CPU_TYPE_UNKNOWN            =   0x00000000; 
	    public static final int CPU_TYPE_ARMV5TE            =   0x00000001; 
	    public static final int CPU_TYPE_ARMV6              =   0x00000010; 
	    public static final int CPU_TYPE_ARMV7              =   0x00000100; 
	        
	    public static final int CPU_FEATURE_UNKNOWS         =   0x00000000; 
	    public static final int CPU_FEATURE_VFP             =   0x00000001; 
	    public static final int CPU_FEATURE_VFPV3           =   0x00000010; 
	    public static final int CPU_FEATURE_NEON            =   0x00000100; 
	        
	    public int mCPUType; 
	    public int mCPUCount; 
	    public int mCPUFeature;      
	    public double mBogoMips; 
	    public long mCPUMaxFreq; 
	} 
	    
	/**
	 * 
	 * @param cpuInfo
	 * @return
	 * @hide
	 */
	private static CPUInfo parseCPUInfo(String cpuInfo) { 
	    if (cpuInfo == null || "".equals(cpuInfo)) { 
	        return null; 
	    } 
	    
	    CPUInfo ci = new CPUInfo(); 
	    ci.mCPUType = CPUInfo.CPU_TYPE_UNKNOWN; 
	    ci.mCPUFeature = CPUInfo.CPU_FEATURE_UNKNOWS; 
	    ci.mCPUCount = 1; 
	    ci.mBogoMips = 0; 
	    
	    if (cpuInfo.contains("ARMv5")) { 
	        ci.mCPUType = CPUInfo.CPU_TYPE_ARMV5TE; 
	    } else if (cpuInfo.contains("ARMv6")) { 
	        ci.mCPUType = CPUInfo.CPU_TYPE_ARMV6; 
	    } else if (cpuInfo.contains("ARMv7")) { 
	        ci.mCPUType = CPUInfo.CPU_TYPE_ARMV7; 
	    } 
	    
	    if (cpuInfo.contains("neon")) { 
	        ci.mCPUFeature |= CPUInfo.CPU_FEATURE_NEON; 
	    } 
	    
	    if (cpuInfo.contains("vfpv3")) { 
	        ci.mCPUFeature |= CPUInfo.CPU_FEATURE_VFPV3; 
	    } 
	    
	    if (cpuInfo.contains(" vfp")) { 
	        ci.mCPUFeature |= CPUInfo.CPU_FEATURE_VFP; 
	    } 
	    
	    String[] items = cpuInfo.split("n"); 
	    
	    for (String item : items) { 
	        if (item.contains("CPU variant")) { 
	            int index = item.indexOf(": "); 
	            if (index >= 0) { 
	                String value = item.substring(index + 2); 
	                try { 
	                    ci.mCPUCount = Integer.decode(value); 
	                    ci.mCPUCount = ci.mCPUCount == 0 ? 1 : ci.mCPUCount; 
	                } catch (NumberFormatException e) { 
	                    ci.mCPUCount = 1; 
	                } 
	            } 
	        } else if (item.contains("BogoMIPS")) { 
	            int index = item.indexOf(": "); 
	            if (index >= 0) { 
	                //String value = item.substring(index + 2); 
	            } 
	        } 
	    } 
	        
	    return ci; 
	} 
	    
	public static long getTotalMemory() {  
	    String str1 = "/proc/meminfo"; 
	    String str2;         
	    String[] arrayOfString; 
	    long initial_memory = 0; 
	    try { 
	        FileReader localFileReader = new FileReader(str1); 
	        BufferedReader localBufferedReader = new BufferedReader(localFileReader, 8192); 
	        str2 = localBufferedReader.readLine(); 
	        if (str2 != null) { 
	            arrayOfString = str2.split("\\s+");
	            initial_memory = Integer.valueOf(arrayOfString[1]).intValue()/1024; 
	        } 
	        localBufferedReader.close(); 
	        return initial_memory; 
	    }  
	    catch (IOException e)  
	    {        
	        return -1; 
	    } 
	} 
	    
	static public CPUInfo getCPUInfo() { 
	    String strInfo = null; 
	    try
	    { 
	        byte[] bs = new byte[1024]; 
	        RandomAccessFile reader = new RandomAccessFile("/proc/cpuinfo", "r"); 
	        reader.read(bs); 
	        String ret = new String(bs); 
	        int index = ret.indexOf(0); 
	        if(index != -1) { 
	            strInfo = ret.substring(0, index); 
	        } else { 
	            strInfo = ret; 
	        } 
	        reader.close();
	    } 
	    catch (IOException ex) 
	    { 
	        strInfo = ""; 
	        ex.printStackTrace(); 
	    } 
	        
	    CPUInfo info = parseCPUInfo(strInfo); 
	    info.mCPUMaxFreq = getMaxCpuFreq(); 
	                
	    return info; 
	} 

	public static String getCpuModel(){ 
	    String cpu_model = ""; 
	    
	    CPUInfo in = getCPUInfo(); 
	              
	    if ((in.mCPUType & CPUInfo.CPU_TYPE_ARMV5TE) == CPUInfo.CPU_TYPE_ARMV5TE) 
	        cpu_model="armv5"; 
	    else if ((in.mCPUType & CPUInfo.CPU_TYPE_ARMV6) == CPUInfo.CPU_TYPE_ARMV6) 
	        cpu_model="armv6"; 
	    else if ((in.mCPUType & CPUInfo.CPU_TYPE_ARMV7) == CPUInfo.CPU_TYPE_ARMV7) 
	        cpu_model="armv7"; 
	    else
	        cpu_model="unknown"; 
	    return cpu_model; 
	} 
	    
	public static String getCpuFeature(){ 
	    String cpu_feature = ""; 
	            
	    CPUInfo in = getCPUInfo(); 
	                
	    if ((in.mCPUFeature & CPUInfo.CPU_FEATURE_NEON ) == CPUInfo.CPU_FEATURE_NEON) 
	        cpu_feature="neon"; 
	    else if ((in.mCPUFeature & CPUInfo.CPU_FEATURE_VFP ) == CPUInfo.CPU_FEATURE_VFP) 
	        cpu_feature="vfp"; 
	    else if ((in.mCPUFeature & CPUInfo.CPU_FEATURE_VFPV3 ) == CPUInfo.CPU_FEATURE_VFPV3) 
	        cpu_feature="vfpv3"; 
	    else
	        cpu_feature="unknown";  
	    return cpu_feature; 
	} 
}
