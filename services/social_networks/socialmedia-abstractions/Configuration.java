package eu.socialsensor.sfc.builder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import eu.socialsensor.framework.common.domain.JSONable;

/**
 * Class for the configuration of streams or storages 
 * reading a set of parameters
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 *
 */
public class Configuration implements Iterable<String>, JSONable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5070483137103099259L;
	
	@Expose
	@SerializedName(value = "parameters")
	private Map<String,String> params = new HashMap<String,String>();

	public String getParameter(String name) {
		return getParameter(name,null);
	}
	
	public String getParameter(String name, String defaultValue){
		String value = params.get(name);
		return value == null ? defaultValue : value;
	}
	
	public void setParameter(String name, String value) {
		params.put(name,value);
	}

	@Override
	public Iterator<String> iterator() {
		return params.keySet().iterator();
	}

	@Override
    public String toJSONString() {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        return gson.toJson(this);
    }

	
}
