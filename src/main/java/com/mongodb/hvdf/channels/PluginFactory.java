package com.mongodb.hvdf.channels;

import java.util.HashMap;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.hvdf.api.FrameworkError;
import com.mongodb.hvdf.api.ServiceException;
import com.mongodb.hvdf.configuration.PluginConfiguration;

// Built in plugin packages
import com.mongodb.hvdf.interceptors.*;
import com.mongodb.hvdf.allocators.*;
import com.mongodb.hvdf.oid.*;
import com.mongodb.hvdf.rollup.*;
import com.mongodb.hvdf.tasks.*;

public class PluginFactory {

	public static final String CONFIG_KEY = "config";
	public static final String TYPE_KEY = "type";
	
	private static HashMap<String, String> registeredPlugins = 
			new HashMap<String, String>();
	
	static{
		
		// Register the "friendly" names of built in plugins so the
		// config does not need full class names like a custom plugin does
		
		// Id Factories
		registeredPlugins.put("time_only", HiDefTimeIdFactory.class.getName());
		registeredPlugins.put("source_time_document", SourceTimeDocumentIdFactory.class.getName());

		// Allocators
		registeredPlugins.put("periodic", PeriodicAllocator.class.getName());
		registeredPlugins.put("no_slicing", SingleCollectionAllocator.class.getName());

		// Interceptors
		registeredPlugins.put("retry", RetryInterceptor.class.getName());
		registeredPlugins.put("batching", BatchingInterceptor.class.getName());
		
		// Tasks
		registeredPlugins.put("ensure_indexes", IndexingTask.class.getName());
		registeredPlugins.put("limit_slices", LimitCollectionsTask.class.getName());
		
		// Storage
		registeredPlugins.put("rollup", RollupStorageInterceptor.class.getName());
		registeredPlugins.put("raw", RawStorageInterceptor.class.getName());
		
		// Rollup Operations
		registeredPlugins.put("max", MaxRollup.class.getName());
		registeredPlugins.put("min", MinRollup.class.getName());
		registeredPlugins.put("count", CountRollup.class.getName());
		registeredPlugins.put("total", TotalRollup.class.getName());
		registeredPlugins.put("group_count", GroupCountRollup.class.getName());
		
	}


	public static <T> T loadPlugin(Class<T> pluginType, PluginConfiguration config) {
		return loadPlugin(pluginType, config, null);
	}
	
	public static <T> T loadPlugin(Class<T> pluginType, 
			PluginConfiguration config, Map<String, Object> injectedConfig) {
		
		// If the config item is not a document, return nothing
		String className = config.get(TYPE_KEY, String.class);
		if(registeredPlugins.containsKey(className)){
			className = registeredPlugins.get(className);
		}

		// Get the plugin config and inject anything passed
		DBObject rawPluginConf = config.get(CONFIG_KEY, DBObject.class, new BasicDBObject());
		if(injectedConfig != null) rawPluginConf.putAll(injectedConfig);

		// Get the plugin instance
		T plugin = createPlugin(pluginType, className, rawPluginConf);
		
		return plugin;				
	}

	private static <T> T createPlugin(Class<T> pluginType, String className, DBObject rawConfig){
        
        T pluginInstance = null;

        try{
            // Find the plugin impl class and create instance
            Class<?> pluginClass = Class.forName(className);
            PluginConfiguration pConfig = new PluginConfiguration(rawConfig, pluginClass);
            Object instance = pluginClass.getConstructor(PluginConfiguration.class).newInstance(pConfig);

            // Cast to the request plugin type
            pluginInstance = pluginType.cast(instance);

        } catch (NoSuchMethodException nsmex) {
            throw new ServiceException(FrameworkError.FAILED_TO_LOAD_PLUGIN_CLASS).
            set(TYPE_KEY, className).set("plugin_type", pluginType.getName()).
            set("reason", "missing PluginConfiguration argument constructor");
        } catch (ClassNotFoundException cnfex) {
            throw new ServiceException(FrameworkError.FAILED_TO_LOAD_PLUGIN_CLASS).
            set(TYPE_KEY, className).set("plugin_type", pluginType.getName());
        } catch (ClassCastException cnfex) {
            throw new ServiceException(FrameworkError.PLUGIN_INCORRECT_TYPE).
            set(TYPE_KEY, className).set("plugin_type", pluginType.getName());
        } catch (Exception e) {
            throw ServiceException.wrap(e, FrameworkError.PLUGIN_ERROR).
            set(TYPE_KEY, className).set("plugin_type", pluginType.getName());
        }

        return pluginInstance;	
    }
	

}
