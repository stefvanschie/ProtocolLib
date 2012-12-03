/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */

package com.comphenix.protocol.events;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import com.comphenix.protocol.injector.StructureCache;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.ChunkPosition;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.google.common.io.Closeables;

import net.minecraft.server.Packet;

/**
 * Represents a Minecraft packet indirectly.
 * 
 * @author Kristian
 */
public class PacketContainer implements Serializable {

	/**
	 * Generated by Eclipse.
	 */
	private static final long serialVersionUID = 2074805748222377230L;
	
	protected int id;
	protected transient Packet handle;

	// Current structure modifier
	protected transient StructureModifier<Object> structureModifier;
		
	// Support for serialization
	private static Method writeMethod;
	private static Method readMethod;
		
	/**
	 * Creates a packet container for a new packet.
	 * @param id - ID of the packet to create.
	 */
	public PacketContainer(int id) {
		this(id, StructureCache.newPacket(id));
	}
	
	/**
	 * Creates a packet container for an existing packet.
	 * @param id - ID of the given packet.
	 * @param handle - contained packet.
	 */
	public PacketContainer(int id, Packet handle) {
		this(id, handle, StructureCache.getStructure(id).withTarget(handle));
	}
	
	/**
	 * Creates a packet container for an existing packet.
	 * @param id - ID of the given packet.
	 * @param handle - contained packet.
	 * @param structure - structure modifier.
	 */
	public PacketContainer(int id, Packet handle, StructureModifier<Object> structure) {
		if (handle == null)
			throw new IllegalArgumentException("handle cannot be null.");
		
		this.id = id;
		this.handle = handle;
		this.structureModifier = structure;
	}
	
	/**
	 * Retrieves the underlying Minecraft packet. 
	 * @return Underlying Minecraft packet.
	 */
	public Packet getHandle() {
		return handle;
	}
	
	/**
	 * Retrieves the generic structure modifier for this packet.
	 * @return Structure modifier.
	 */
	public StructureModifier<Object> getModifier() {
		return structureModifier;
	}
	
	/**
	 * Retrieves a read/write structure for every field with the given type.
	 * @param primitiveType - the type to find.
	 * @return A modifier for this specific type.
	 */
	public <T> StructureModifier<T> getSpecificModifier(Class<T> primitiveType) {
		return structureModifier.withType(primitiveType);
	}
	
	/**
	 * Retrieves a read/write structure for every byte field.
	 * @return A modifier for every byte field.
	 */
	public StructureModifier<Byte> getBytes() {
		return structureModifier.withType(byte.class);
	}
	
	/**
	 * Retrieves a read/write structure for every short field.
	 * @return A modifier for every short field.
	 */
	public StructureModifier<Short> getShorts() {
		return structureModifier.withType(short.class);
	}
	
	/**
	 * Retrieves a read/write structure for every integer field.
	 * @return A modifier for every integer field.
	 */
	public StructureModifier<Integer> getIntegers() {
		return structureModifier.withType(int.class);
	}
	/**
	 * Retrieves a read/write structure for every long field.
	 * @return A modifier for every long field.
	 */
	public StructureModifier<Long> getLongs() {
		return structureModifier.withType(long.class);
	}
	
	/**
	 * Retrieves a read/write structure for every float field.
	 * @return A modifier for every float field.
	 */
	public StructureModifier<Float> getFloat() {
		return structureModifier.withType(float.class);
	}
	
	/**
	 * Retrieves a read/write structure for every double field.
	 * @return A modifier for every double field.
	 */
	public StructureModifier<Double> getDoubles() {
		return structureModifier.withType(double.class);
	}
	
	/**
	 * Retrieves a read/write structure for every String field.
	 * @return A modifier for every String field.
	 */
	public StructureModifier<String> getStrings() {
		return structureModifier.withType(String.class);
	}
	
	/**
	 * Retrieves a read/write structure for every String array field.
	 * @return A modifier for every String array field.
	 */
	public StructureModifier<String[]> getStringArrays() {
		return structureModifier.withType(String[].class);
	}
	
	/**
	 * Retrieves a read/write structure for every byte array field.
	 * @return A modifier for every byte array field.
	 */
	public StructureModifier<byte[]> getByteArrays() {
		return structureModifier.withType(byte[].class);
	}
	
	/**
	 * Retrieves a read/write structure for ItemStack.
	 * <p>
	 * This modifier will automatically marshall between the Bukkit ItemStack and the
	 * internal Minecraft ItemStack.
	 * @return A modifier for ItemStack fields.
	 */
	public StructureModifier<ItemStack> getItemModifier() {
		// Convert to and from the Bukkit wrapper
		return structureModifier.<ItemStack>withType(
				net.minecraft.server.ItemStack.class, BukkitConverters.getItemStackConverter());
	}
	
	/**
	 * Retrieves a read/write structure for arrays of ItemStacks.
	 * <p>
	 * This modifier will automatically marshall between the Bukkit ItemStack and the
	 * internal Minecraft ItemStack.
	 * @return A modifier for ItemStack array fields.
	 */
	public StructureModifier<ItemStack[]> getItemArrayModifier() {
		
		final EquivalentConverter<ItemStack> stackConverter = BukkitConverters.getItemStackConverter();
		
		// Convert to and from the Bukkit wrapper
		return structureModifier.<ItemStack[]>withType(
				net.minecraft.server.ItemStack[].class, 
				BukkitConverters.getIgnoreNull(new EquivalentConverter<ItemStack[]>() {
					
			public Object getGeneric(Class<?>genericType, ItemStack[] specific) {
				net.minecraft.server.ItemStack[] result = new net.minecraft.server.ItemStack[specific.length];
				
				// Unwrap every item
				for (int i = 0; i < result.length; i++) {
					result[i] = (net.minecraft.server.ItemStack) stackConverter.getGeneric(
							net.minecraft.server.ItemStack.class, specific[i]); 
				}
				return result;
			}
			
			@Override
			public ItemStack[] getSpecific(Object generic) {
				net.minecraft.server.ItemStack[] input = (net.minecraft.server.ItemStack[]) generic;
				ItemStack[] result = new ItemStack[input.length];
				
				// Add the wrapper
				for (int i = 0; i < result.length; i++) {
					result[i] = stackConverter.getSpecific(input[i]);
				}
				return result;
			}
			
			@Override
			public Class<ItemStack[]> getSpecificType() {
				return ItemStack[].class;
			}
		}));
	}
	
	/**
	 * Retrieves a read/write structure for the world type enum.
	 * <p>
	 * This modifier will automatically marshall between the Bukkit world type and the
	 * internal Minecraft world type.
	 * @return A modifier for world type fields.
	 */
	public StructureModifier<WorldType> getWorldTypeModifier() {
		// Convert to and from the Bukkit wrapper
		return structureModifier.<WorldType>withType(
				net.minecraft.server.WorldType.class, 
				BukkitConverters.getWorldTypeConverter());
	}
	
	/**
	 * Retrieves a read/write structure for data watchers.
	 * @return A modifier for data watchers.
	 */
	public StructureModifier<WrappedDataWatcher> getDataWatcherModifier() {
		// Convert to and from the Bukkit wrapper
		return structureModifier.<WrappedDataWatcher>withType(
				net.minecraft.server.DataWatcher.class, 
				BukkitConverters.getDataWatcherConverter());
	}
	
	/**
	 * Retrieves a read/write structure for entity objects.
	 * <p>
	 * Note that entities are transmitted by integer ID, and the type may not be enough
	 * to distinguish between entities and other values. Thus, this structure modifier
	 * MAY return null or invalid entities for certain fields. Using the correct index 
	 * is essential.
	 * 
	 * @return A modifier entity types.
	 */
	public StructureModifier<Entity> getEntityModifier(World world) {
		// Convert to and from the Bukkit wrapper
		return structureModifier.<Entity>withType(
				int.class, BukkitConverters.getEntityConverter(world));
	}
	
	/**
	 * Retrieves a read/write structure for chunk positions.
	 * @return A modifier for a ChunkPosition.
	 */
	public StructureModifier<ChunkPosition> getPositionModifier() {
		// Convert to and from the Bukkit wrapper
		return structureModifier.withType(
				net.minecraft.server.ChunkPosition.class,
				ChunkPosition.getConverter());
	}
	
	/**
	 * Retrieves a read/write structure for collections of chunk positions.
	 * <p>
	 * This modifier will automatically marshall between the visible ProtocolLib ChunkPosition and the
	 * internal Minecraft ChunkPosition.
	 * @return A modifier for ChunkPosition list fields.
	 */
	public StructureModifier<List<ChunkPosition>> getPositionCollectionModifier() {
		// Convert to and from the ProtocolLib wrapper
		return structureModifier.withType(
			Collection.class,
			BukkitConverters.getListConverter(
					net.minecraft.server.ChunkPosition.class, 
					ChunkPosition.getConverter())
		);
	}
	
	/**
	 * Retrieves a read/write structure for collections of watchable objects.
	 * <p>
	 * This modifier will automatically marshall between the visible WrappedWatchableObject and the
	 * internal Minecraft WatchableObject.
	 * @return A modifier for watchable object list fields.
	 */
	public StructureModifier<List<WrappedWatchableObject>> getWatchableCollectionModifier() {
		// Convert to and from the ProtocolLib wrapper
		return structureModifier.withType(
			Collection.class,
			BukkitConverters.getListConverter(
					net.minecraft.server.WatchableObject.class, 
					BukkitConverters.getWatchableObjectConverter())
		);
	}
	
	/**
	 * Retrieves the ID of this packet.
	 * @return Packet ID.
	 */
	public int getID() {
		return id;
	}
	
	/**
	 * Create a deep copy of the current packet.
	 * @return A deep copy of the current packet.
	 */
	public PacketContainer deepClone() {
		ObjectOutputStream output = null;
		ObjectInputStream input = null;
		
		try {
			// Use a small buffer of 32 bytes initially.
			ByteArrayOutputStream bufferOut = new ByteArrayOutputStream(); 
			output = new ObjectOutputStream(bufferOut);
			output.writeObject(this);
			
			ByteArrayInputStream bufferIn = new ByteArrayInputStream(bufferOut.toByteArray());
			input = new ObjectInputStream(bufferIn);
			return (PacketContainer) input.readObject();
			
		} catch (IOException e) {
			throw new IllegalStateException("Unexpected error occured during object cloning.", e);
		} catch (ClassNotFoundException e) {
			// Cannot happen
			throw new IllegalStateException("Unexpected failure with serialization.", e);
		} finally {
			try {
				if (output != null)
					output.close();
				if (input != null)
					input.close();
				
			} catch (IOException e) {
				// STOP IT
			}
		}
	}
	
	private void writeObject(ObjectOutputStream output) throws IOException {
	    // Default serialization 
		output.defaultWriteObject();

		// We'll take care of NULL packets as well
		output.writeBoolean(handle != null);
		
		// Retrieve the write method by reflection
		if (writeMethod == null)
			writeMethod = FuzzyReflection.fromObject(handle).getMethodByParameters("write", DataOutputStream.class);
		
		try {
			// Call the write-method
			writeMethod.invoke(handle, new DataOutputStream(output));
		} catch (IllegalArgumentException e) {
			throw new IOException("Minecraft packet doesn't support DataOutputStream", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Insufficient security privileges.", e);
		} catch (InvocationTargetException e) {
			throw new IOException("Could not serialize Minecraft packet.", e);
		}
	}

	private void readObject(ObjectInputStream input) throws ClassNotFoundException, IOException {
	    // Default deserialization
		input.defaultReadObject();
		
		// Get structure modifier
		structureModifier = StructureCache.getStructure(id);

	    // Don't read NULL packets
	    if (input.readBoolean()) {
	    	
	    	// Create a default instance of the packet
	    	handle = StructureCache.newPacket(id);
	    	
			// Retrieve the read method by reflection
			if (readMethod == null)
				readMethod = FuzzyReflection.fromObject(handle).getMethodByParameters("read", DataInputStream.class);
	    	
			// Call the read method
			try {
				readMethod.invoke(handle, new DataInputStream(input));
			} catch (IllegalArgumentException e) {
				throw new IOException("Minecraft packet doesn't support DataInputStream", e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Insufficient security privileges.", e);
			} catch (InvocationTargetException e) {
				throw new IOException("Could not deserialize Minecraft packet.", e);
			}
			
			// And we're done
			structureModifier = structureModifier.withTarget(handle);
	    }
	}
}
