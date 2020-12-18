package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

public class PageTable {

	private static Hashtable<Pair<Integer,Integer>, TranslationEntry> table = new Hashtable<>(); // TranslationEntry Against process id and virtual page number
	
//	private static Vector<Pair<Integer,TranslationEntry>> globalTable = initGlobalTable(); // Process id of each Translation Entry
	private static Vector<Pair<Integer,TranslationEntry>> globalTable = new Vector<>(Machine.processor().getNumPhysPages()); // Process id of each Translation Entry

	private static Vector<Pair<Integer, TranslationEntry>> initGlobalTable() {
		Vector<Pair<Integer, TranslationEntry>> v = new Vector<>(Machine.processor().getNumPhysPages());
		for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
			v.add(new Pair<>(0,null));
		}
		return v;
	}

	protected static final char dbgVM='v';

	private PageTable(){
	}

	public static boolean insertEntry(int pID, TranslationEntry entry){
		Pair<Integer,Integer> key=new Pair<>(pID,entry.vpn);
		if(table.containsKey(key)){
			return false;
		}
		table.put(key, entry);
		if(entry.valid){
			globalTable.set(entry.ppn, new Pair<>(pID, entry));
		}
		return true;

	}

	public static TranslationEntry deleteEntry(int pID, int vpn){
		Pair<Integer,Integer> key=new Pair<>(pID,vpn);
		TranslationEntry entry=table.get(key);
		if(entry==null){
			return null;
		}
		if(entry.valid){
			globalTable.set(entry.ppn, null);
		}
		return entry;

	}

	public static void setEntry(int pID, TranslationEntry newEntry){
		Pair<Integer,Integer> key=new Pair<>(pID,newEntry.vpn);
		if(!table.containsKey(key)){
			return;
		}
		TranslationEntry oldEntry=table.get(key);
		if(oldEntry.valid){
			if(globalTable.get(oldEntry.ppn) ==null){
				return;
			}
			globalTable.set(oldEntry.ppn, null);
		}
		if(newEntry.valid){
			if(globalTable.get(newEntry.ppn) !=null){
				return;
			}
			globalTable.set(newEntry.ppn, new Pair<>(pID, newEntry));
		}
		table.put(key, newEntry);

	}

	public static TranslationEntry getEntry(int pID, int vpn){
		Pair<Integer,Integer> key=new Pair<>(pID,vpn);
		TranslationEntry entry=null;
		if(table.containsKey(key)){
			entry=table.get(key);
		}
		return entry;
	}
	
	public static void updateEntry(int pID, TranslationEntry entry){
		Pair<Integer,Integer> key=new Pair<>(pID,entry.vpn);
		if(table.containsKey(key)){
			return;
		}
		TranslationEntry oldEntry=table.get(key);

		TranslationEntry newEntry=mix(entry,oldEntry);
		if(oldEntry!=null &&oldEntry.valid){
			if(globalTable.get(oldEntry.ppn) ==null){
				return;
			}
			globalTable.set(oldEntry.ppn, null);
		}
		if(newEntry.valid){
			if(globalTable.get(newEntry.ppn) !=null){
				return;
			}
			globalTable.set(newEntry.ppn, new Pair<>(pID, newEntry));
		}
		table.put(key, newEntry);
	}
	
	private static TranslationEntry mix(TranslationEntry entry1, TranslationEntry entry2){
		if(entry2 == null){
			return entry1;
		}
		if(entry1.dirty||entry2.dirty){
			entry1.dirty=true;
		}
		return entry1;
	}

	public static Pair<Integer,TranslationEntry> getVictim(){
		Pair<Integer,TranslationEntry> entry=null;
		do{
			int index= Lib.random(globalTable.size());
			entry= globalTable.get(index);
		}while(entry==null||!entry.getSecond().valid); //while Translation Entry is not valid
		return entry;
	}

}
