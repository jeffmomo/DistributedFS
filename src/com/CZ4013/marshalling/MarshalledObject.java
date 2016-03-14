package com.CZ4013.marshalling;

/**
 * Created by mdl94 on 14/03/2016.
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
