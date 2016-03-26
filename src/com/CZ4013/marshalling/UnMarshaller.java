package com.CZ4013.marshalling;


import java.nio.charset.StandardCharsets;

/**
 * Created by mdl94 on 14/03/2016.
 */
public class UnMarshaller
{
    private byte[] _bytes;
    private int _position;

    /**
     * Creates an Unmarshaller object given a byte array
     * @param bytes
     */
    public UnMarshaller(byte[] bytes)
    {
        _bytes = bytes;
    }

    /**
     * Gets the next parameter. Need to cast this into the desired object.
     * Returns null if there are no more parameters
     * DO NOT GET A BYTE WITH THIS METHOD, USE getByte() INSTEAD
     * This is as a byte do not have an associated type byte stored before it
     * @return An object representing the parameter to be extracted from the byte array
     * @throws Exception is thrown when an index out of bounds error occurs when extracting data. This usually means the byte array is not well formed.
     */
    public Object getNext() throws Exception
    {
        // If there is no more bytes to go through, return a null object
        // This can be handled thru null checking
        if(_position + 1 >= _bytes.length)
        {
            return null;
        }

        try
        {
            // Checks type of the next object to extract
            // Depending on the type performs the appropirate unmarshalling from bytes
            switch (_bytes[_position++])
            {
                case Marshaller.IntegerType:
                    return ((_bytes[_position++] << 24) & 0xFF000000 | (_bytes[_position++] << 16) & 0x00FF0000 | (_bytes[_position++] << 8) & 0x0000FF00 | (_bytes[_position++]) & 0xFF);
                case Marshaller.StringType:
                    int stringLen = (_bytes[_position++] << 8) & 0x0000FF00 | (_bytes[_position++]) & 0xFF;

                    byte[] stringData = new byte[stringLen];
                    System.arraycopy(_bytes, _position, stringData, 0, stringLen);

                    _position += stringLen;

                    return new String(stringData, StandardCharsets.UTF_16);
                case Marshaller.ByteArrayType:
                    int byteLen = (_bytes[_position++] << 8) & 0x0000FF00 | (_bytes[_position++]) & 0xFF;

                    byte[] bytes = new byte[byteLen];
                    System.arraycopy(_bytes, _position, bytes, 0, byteLen);

                    _position += byteLen;
                    return bytes;

                case Marshaller.IntArrayType:

                    int len = ((_bytes[_position++] << 8) & 0x0000FF00 | (_bytes[_position++]) & 0xFF) / 4;

                    //int numInts = value.length / 4;
                    int[] output = new int[len];

                    for(int i = 0; i < len; i++)
                    {
                        output[i] = ((_bytes[_position++] << 24) & 0xFF000000 | (_bytes[_position++] << 16) & 0x00FF0000 | (_bytes[_position++] << 8) & 0x0000FF00 | (_bytes[_position++]) & 0xFF);
                    }
                    return output;
            }
        }
        // If there is any exceptions thrown, that means the byte array has errors or wrong extraction is performed
        catch (ArrayIndexOutOfBoundsException ex)
        {
            throw new Exception("Byte array is not well formed");
        }

        // If nothing is returned, means the type byte is incorrect and thus byte array is corrupted or wrong extraction is performed
        throw new Exception("Byte array is not well formed");
    }

    /**
     * Gets the next byte
     * This must be used if we want to get a byte as a byte do not have an associated type information stored
     * @return
     * @throws Exception
     */
    public byte getNextByte() throws Exception
    {
        if(_position + 1 >= _bytes.length)
        {
            throw new Exception("Byte buffer length exceeded");
        }

        return _bytes[_position++];
    }


    /**
     *  Resets the position of the unmarshaller
     */
    public void resetPosition()
    {
        _position = 0;
    }


    /**
     * Returns the bytes used in this unmarshaller
     * @return
     */
    public byte[] getBytes()
    {
        return _bytes;
    }

    /**
     * Modifies a specific byte at a specific location
     * @param b
     * @param pos
     */
    public void modifyByteAt(byte b, int pos)
    {
        _bytes[pos] = b;
    }
}
