package com.CZ4013.marshalling;


import javax.naming.SizeLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

public class Marshaller
{

    public static final int IntegerType = 0;
    public static final int StringType = 1;
    public static final int IntArrayType = 2;
    public static final int ByteType = 3;



    private List<byte[]> _chunks;
    private byte[] _currentChunk;
    private int _currentChunkPosition = 0;
    public final int chunkSize;


    private byte[] _bytes;

    public Marshaller(int chunkByteSize)
    {
        chunkSize = chunkByteSize;

        _chunks = new LinkedList<>();
        _currentChunk = new byte[chunkSize];
    }

    public Marshaller(MarshalledObject[] values)
    {
        chunkSize = 0;

        int totalSize = 0;
        int position = 0;

        for(MarshalledObject val : values)
        {
            totalSize += val.bytes.length;

            switch (val.type)
            {
                case IntegerType:
                    totalSize++;
                    break;
                case StringType:
                    totalSize += 3;
                    break;
                case IntArrayType:
                    totalSize += 3;
            }
        }

        _bytes = new byte[totalSize];

        for(MarshalledObject val : values)
        {

            switch (val.type)
            {
                case IntegerType:
                    _bytes[position++] = (byte)Marshaller.IntegerType;
                    break;
                case StringType:
                {
                    _bytes[position++] = (byte)Marshaller.StringType;
                    int len = val.bytes.length;
                    byte msb = (byte) (len >>> 8);
                    byte lsb = (byte) len;
                    _bytes[position++] = msb;
                    _bytes[position++] = lsb;
                    break;
                }
                case IntArrayType:
                {
                    _bytes[position++] = (byte)Marshaller.IntArrayType;
                    int len = val.bytes.length;
                    byte msb = (byte) (len >>> 8);
                    byte lsb = (byte) len;
                    _bytes[position++] = msb;
                    _bytes[position++] = lsb;
                    break;
                }
            }

            System.arraycopy(val.bytes, 0, _bytes, position, val.bytes.length);
            position += val.bytes.length;
        }
    }

    public byte[] getBytes()
    {
        return _bytes;
    }

    // value includes the 1 byte type tag
    public void chunkable(byte[] value, int fromPos)
    {
        if(_currentChunkPosition + value.length - fromPos > chunkSize)
        {
            int spillOver = _currentChunkPosition + value.length - chunkSize - fromPos;
            int remainingSpace = chunkSize - _currentChunkPosition;

            // copies to the remainders of the current trunk
            System.arraycopy(value, fromPos, _currentChunk, _currentChunkPosition, remainingSpace);

            _chunks.add(_currentChunk);

            // reset
            _currentChunkPosition = 0;
            _currentChunk = new byte[chunkSize];

            chunkable(value, remainingSpace);
        }
        else
        {
            //

            System.arraycopy(value, fromPos, _currentChunk, _currentChunkPosition, value.length - fromPos);
            _currentChunkPosition += value.length - fromPos;
        }
    }

    public List getChunks()
    {
        if(_currentChunkPosition > 0)
        {
            _chunks.add(_currentChunk);

            _currentChunkPosition = 0;
            _currentChunk = new byte[chunkSize];
        }

        return _chunks;
    }

    public void appendInt(byte[] integer)
    {
        byte[] intBytes = new byte[5];
        intBytes[0] = Marshaller.IntegerType;
        System.arraycopy(integer, 0, intBytes, 1, integer.length);

        chunkable(intBytes, 0);
    }

    public void appendString(byte[] str)
    {
        byte[] strBytes = new byte[str.length + 2];
        strBytes[0] = Marshaller.StringType;
        strBytes[1] = str.length > 255 ? 0 : (byte)str.length;
        System.arraycopy(str, 0, strBytes, 1, str.length);

        chunkable(strBytes, 0);
    }


    /**
     * Format: (4_bit_type, 32_bit_value)
     * Most significant byte is closest to the 0 index of the byte array
     */
    public static MarshalledObject marshalInt(int value)
    {
        byte[] output = new byte[4];

        //output[0] = Marshaller.IntegerType;
        output[0] = (byte)(value >>> 24);
        output[1] = (byte)(value >>> 16);
        output[2] = (byte)(value >>> 8);
        output[3] = (byte)value;

        return new MarshalledObject(Marshaller.IntegerType, output);
    }

    public static int unMarshallInt(byte[] value)
    {
        return ((value[0] << 24) & 0xFF000000 | (value[1] << 16) & 0x00FF0000 | (value[2] << 8) & 0x0000FF00 | (value[3]) & 0xFF);
    }

    public static MarshalledObject marshalString(String value) throws SizeLimitExceededException
    {
        byte[] output = value.getBytes(StandardCharsets.UTF_16);

        if(output.length > 2 << 16)
        {
            throw new SizeLimitExceededException("String has length in bytes of longer than 2^16 bits");
        }

        return new MarshalledObject(Marshaller.StringType, output);


//        byte[] output = new byte[stringBytes.length + 1];
//        output[0] = Marshaller.StringType;
//
//        System.arraycopy(stringBytes, 0, output, 1, stringBytes.length);
//
//        return output;
    }

    public static String unMarshallString(byte[] value)
    {
        return new String(value, StandardCharsets.UTF_16);
    }


    public static MarshalledObject marshalIntArray(int[] array) throws SizeLimitExceededException
    {
        if(array.length > 2 << 16)
        {
            throw new SizeLimitExceededException("Array has length of longer than 2^16 bits");
        }

        byte[] output = new byte[array.length * 4];

        for(int i = 0; i < array.length; i++)
        {
            int value = array[i];
            byte[] integer = new byte[4];

            integer[0] = (byte)(value >>> 24);
            integer[1] = (byte)(value >>> 16);
            integer[2] = (byte)(value >>> 8);
            integer[3] = (byte)value;

            System.arraycopy(integer, 0, output, i * 4, 4);

        }

        return new MarshalledObject(Marshaller.IntArrayType, output);
    }

    public static int[] unMarshallIntArray(byte[] value)
    {
        int numInts = value.length / 4;
        int[] output = new int[numInts];

        for(int i = 0; i < numInts; i++)
        {
            int idx = i * 4;
            output[i] = ((value[idx] << 24) & 0xFF000000 | (value[idx + 1] << 16) & 0x00FF0000 | (value[idx + 2] << 8) & 0x0000FF00 | (value[idx + 3]) & 0xFF);
        }

        return output;
    }


}
