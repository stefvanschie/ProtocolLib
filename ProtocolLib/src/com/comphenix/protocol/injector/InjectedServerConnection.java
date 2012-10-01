package com.comphenix.protocol.injector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;

import com.comphenix.protocol.reflect.FieldUtils;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.VolatileField;

/**
 * Used to ensure that the 1.3 server is referencing the correct server handler.
 * 
 * @author Kristian
 */
class InjectedServerConnection {
	
	private static Field listenerThreadField;
	private static Field minecraftServerField;
	private static Method serverConnectionMethod;
	private static Field listField;
	
	private List<VolatileField> listFields;
	private List<ReplacedArrayList<Object>> replacedLists;
	
	private Server server;
	private Logger logger;
	private boolean hasAttempted;
	private boolean hasSuccess;
	
	private Object minecraftServer = null;
	
	public InjectedServerConnection(Logger logger, Server server) {
		this.listFields = new ArrayList<VolatileField>();
		this.replacedLists = new ArrayList<ReplacedArrayList<Object>>();
		this.logger = logger;
		this.server = server;
	}

	public void injectList() {

		// Only execute this method once
		if (!hasAttempted)
			hasAttempted = true;
		else
			return;
		
		if (minecraftServerField == null)
			minecraftServerField = FuzzyReflection.fromObject(server, true).getFieldByType(".*MinecraftServer");

		try {
			minecraftServer = FieldUtils.readField(minecraftServerField, server, true);
		} catch (IllegalAccessException e1) {
			logger.log(Level.WARNING, "Cannot extract minecraft server from Bukkit.");
			return;
		}
		
		try {
			if (serverConnectionMethod == null)
				serverConnectionMethod = FuzzyReflection.fromClass(minecraftServerField.getType()).
											getMethodByParameters("getServerConnection", ".*ServerConnection", new String[] {});
			// We're using Minecraft 1.3.1
			injectServerConnection();
		
		} catch (RuntimeException e) {
			
			// Minecraft 1.2.5 or lower
			injectListenerThread();
		}
	}
	
	private void injectListenerThread() {
	
		try {
		
		if (listenerThreadField == null)
			listenerThreadField = FuzzyReflection.fromClass(minecraftServerField.getType()).
				getFieldByType(".*NetworkListenThread");
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE, "Cannot find listener thread in MinecraftServer.");
			return;
		}

		Object listenerThread = null;
		
		// Attempt to get the thread
		try {
			listenerThread = listenerThreadField.get(minecraftServer);
		} catch (Exception e) {
			logger.log(Level.WARNING, "Unable to read the listener thread.");
			return;
		}
		
		// Ok, great. Get every list field
		List<Field> lists = FuzzyReflection.fromClass(listenerThreadField.getType()).getFieldListByType(List.class);
		
		for (Field list : lists) {
			injectIntoList(listenerThread, list);
		}
		
		hasSuccess = true;
	}
	
	private void injectServerConnection() {
		
		Object serverConnection = null;
		
		// Careful - we might fail
		try {
			serverConnection = serverConnectionMethod.invoke(minecraftServer);
		} catch (Exception ex) {
			logger.log(Level.WARNING, "Unable to retrieve server connection", ex);
			return;
		}
		
		if (listField == null)
			listField = FuzzyReflection.fromClass(serverConnectionMethod.getReturnType(), true).
							getFieldByType("serverConnection", List.class);
		injectIntoList(serverConnection, listField);
		hasSuccess = true;
	}
	
	@SuppressWarnings("unchecked")
	private void injectIntoList(Object instance, Field field) {
		VolatileField listFieldRef = new VolatileField(listField, instance, true);
		List<Object> list = (List<Object>) listFieldRef.getValue();

		// Careful not to inject twice
		if (list instanceof ReplacedArrayList) {
			replacedLists.add((ReplacedArrayList<Object>) list);
		} else {
			replacedLists.add(new ReplacedArrayList<Object>(list));
			listFieldRef.setValue(replacedLists.get(0));
			listFields.add(listFieldRef);
		}
	}
	
	/**
	 * Replace the server handler instance kept by the "keep alive" object.
	 * @param oldHandler - old server handler.
	 * @param newHandler - new, proxied server handler.
	 */
	public void replaceServerHandler(Object oldHandler, Object newHandler) {
		if (!hasAttempted) {
			injectList();
		}
		
		if (hasSuccess) {
			for (ReplacedArrayList<Object> replacedList : replacedLists) {
				replacedList.addMapping(oldHandler, newHandler);
			}
		}
	}
	
	/**
	 * Revert to the old vanilla server handler, if it has been replaced.
	 * @param oldHandler - old vanilla server handler.
	 */
	public void revertServerHandler(Object oldHandler) {
		if (hasSuccess) {
			for (ReplacedArrayList<Object> replacedList : replacedLists) {
				replacedList.removeMapping(oldHandler);
			}
		}
	}
	
	/**
	 * Undoes everything.
	 */
	public void cleanupAll() {
		if (replacedLists.size() > 0) {
			// Repair the underlying lists
			for (ReplacedArrayList<Object> replacedList : replacedLists) {
				replacedList.revertAll();
			}
			for (VolatileField field : listFields) {
				field.revertValue();
			}
			
			listFields.clear();
			replacedLists.clear();
		}
	}
}