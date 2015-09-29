package gr.iti.mklab.sfc.storages;

import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.Item;

import java.io.IOException;

public class StdoutStorage implements Storage {
	
	private String storageName = "StdOut";

	public StdoutStorage(Configuration config) {
		
	}


	@Override
	public void store(Item update) throws IOException {
		System.out.println(update.toString());	
	}
	
	

	@Override
	public boolean delete(String id) throws IOException {
		System.out.println("{ delete : " + id + "}");	
		return false;
	}


	@Override
	public boolean open(){
		return true;
	}

	@Override
	public void close() {
		
	}
	
	@Override
	public boolean checkStatus() {
		return true;
	}
	
	@Override
	public String getStorageName(){
		return this.storageName;
	}

}
