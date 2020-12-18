package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.ThreadedKernel;

import java.util.*;

public class Swapper {

	private static int pageSize = Processor.pageSize;

	private static String swapFileName = "Hard Disk";

	private static OpenFile swapFile = ThreadedKernel.fileSystem.open(swapFileName, true);

	
	private static Hashtable<Pair<Integer,Integer>,Integer> swapTable = new Hashtable<>(); // Location Against process id and virtual page number
	
	private static HashSet<Pair<Integer,Integer>> unallocated = new HashSet<>(); // unallocated process id and virtual page number
	
	private static LinkedList<Integer> availableLocations = new LinkedList<>();
	
	protected final static char dbgVM='v';

	private Swapper(String swapFileName){
	}

	public static void insertUnallocatedPage(int pid,int vpn){
		Pair<Integer,Integer> key= new Pair<>(pid, vpn);
		unallocated.add(key);
	}

	public static int allocatePosition(int pid,int vpn){
		Pair<Integer,Integer> key= new Pair<>(pid, vpn);
		if(unallocated.contains(key)){
			unallocated.remove(key);
			if(availableLocations.isEmpty()){
				availableLocations.add(swapTable.size());
			}
			int index=availableLocations.removeFirst();
			swapTable.put(key, index);
			return index;
		}else{
			int index=-1;
			index=swapTable.get(key);
			if(index==-1){
				Lib.debug(dbgVM, "allocatePosition:unallocated is inconsistent with swapTable");
			}
			return index;
		}
	}
	
	public static void deletePosition(int pid,int vpn){
		Pair<Integer,Integer> key= new Pair<>(pid, vpn);
		if(!swapTable.containsKey(key))return;
		int availableLocation=swapTable.remove(key);
		availableLocations.add(availableLocation);
	}
	
	public static byte[] readFromSwapFile(int pid,int vpn){
		int position=findEntry(pid,vpn);
		if(position==-1){
			Lib.debug(dbgVM, "readFromSwapFile:key doesn't exist in swapTable");
			return new byte[pageSize];
		}
		byte[] reader=new byte[pageSize];
		int length=swapFile.read(position*pageSize, reader, 0, pageSize);
		if(length==-1){
			Lib.debug(dbgVM, "readFromSwapFile:fail to read swapfile");
			return new byte[pageSize];
		}
		return reader;
	}
	
	private static int findEntry(int pid, int vpn) {
        Integer position = swapTable.get(new Pair<>(pid, vpn));
		return Objects.requireNonNullElse(position, -1);
    }
	
	
	public static int writeToSwapFile(int pid,int vpn,byte[] page,int offset){
		int position=allocatePosition(pid,vpn);
		if(position==-1){
			Lib.debug(dbgVM, "writeToSwapFile:fail to allocate position");
			return -1;
		}
		swapFile.write(position*pageSize, page, offset, pageSize);
		return position;
	}
	
	public static void removeSwapFile(){
		if(swapFile!=null){
			swapFile.close();
			ThreadedKernel.fileSystem.remove(swapFileName);
			swapFile=null;
		}
	}


}
