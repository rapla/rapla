package org.rapla.rest;

/**
 * Marker interface for serialization to use the generic object serialzer instead of a special one like MapSerializer or ListSerialzer
 * E.g. this is to overcome a behaviour of gson, that always use a MapSerializer when the class extends java.util.Map
 */
public interface GenericObjectSerializable
{
}
