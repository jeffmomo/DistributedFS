package com.CZ4013.marshalling;

/**
 * A class representing a marshalled object and its type
 */
public class MarshalledObject
{
    public byte[] bytes;
    public int type;

    public MarshalledObject(int type, byte[] bytes)
    {
        this.bytes = bytes;
        this.type = type;
    }
}
