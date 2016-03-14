package com.CZ4013.marshalling;

import java.nio.charset.StandardCharsets;

/**
 * Created by mdl94 on 14/03/2016.
 */
public class UnMarshaller
{
    private byte[] _bytes;
    private int _position;

    public UnMarshaller(byte[] bytes)
    {
        _bytes = bytes;
    }

    public Object getNext() throws Exception
    {
        if(_position + 1 >= _bytes.length)
        {
            return null;
        }

        try
        {
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
        catch (ArrayIndexOutOfBoundsException ex)
        {
            throw new Exception("Byte array is not well formed");
        }

        throw new Exception("Byte array is not well formed");
    }
}