package com.CZ4013.marshalling;


import javax.naming.SizeLimitExceededException;
import java.nio.charset.StandardCharsets;

/**
 * Performs marshalling towards a byte array
 * Can marshall bytes, integers, strings(up to 2^16-1 length), int array(2^16-1 length), byte array(2^16-1 length)
 */
public class Marshaller
{

    public static final int IntegerType = 0;
    public static final int StringType = 1;
    public static final int IntArrayType = 2;
    public static final int ByteArrayType = 3;
    public static final int ByteType = 4;

    public static final int MAX_PACKET_BYTES = 60000;

    private byte[] _bytes;


    /**
     * Easy to use marshaller. Directly input the arguments into it, making sure to cast it to the appropriate type
     * @param arguments Arguments of any type
     * @throws SizeLimitExceededException If the length of any string/arrays exceed the max possible length allowed by the 2-byte length tag
     */
    public Marshaller(Object... arguments) throws SizeLimitExceededException
    {
        // First transforms all objects in argument into the relevant MarshalledObject
        for(int i = 0; i < arguments.length; i++)
        {
            Class c = arguments[i].getClass();

            if(c == Integer.class)
            {
                arguments[i] = marshalInt((int)arguments[i]);
            }
            else if(c == String.class)
            {
                arguments[i] = marshalString((String)arguments[i]);

            }
            else if(c == byte[].class)
            {
                arguments[i] = marshallByteArray((byte[])arguments[i]);

            }
            else if(c == int[].class)
            {
                arguments[i] = marshalIntArray((int[])arguments[i]);

            }
            else if(c == Byte.class)
            {
                arguments[i] = marshalByte((byte)arguments[i]);
            }
        }


        // Records the total size of the byte array
        int totalSize = 0;
        int position = 0;

        // Calculates the total size of the bytes
        for(Object obj : arguments)
        {
            MarshalledObject val = (MarshalledObject)obj;

            totalSize += val.bytes.length;

            // Each type requires a different overhead in the byte array
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
                    break;
                case ByteArrayType:
                    totalSize += 3;
                    break;
                case ByteType:
                    totalSize += 0;
            }
        }

        // Create byte array with the calculated size
        _bytes = new byte[totalSize];

        for(Object obj : arguments)
        {
            MarshalledObject val = (MarshalledObject) obj;

            // Writes the type and length information(if any) of the marshalled object
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
                case ByteArrayType:
                {
                    _bytes[position++] = (byte)Marshaller.ByteArrayType;
                    int len = val.bytes.length;
                    byte msb = (byte) (len >>> 8);
                    byte lsb = (byte) len;
                    _bytes[position++] = msb;
                    _bytes[position++] = lsb;
                    break;
                }
                case ByteType:
                {

                    break;
                }
            }

            // Copies the actual byte of the object into the bytearray
            System.arraycopy(val.bytes, 0, _bytes, position, val.bytes.length);
            position += val.bytes.length;
        }
    }

    /**
     * Returns the marshalled byte array
     * @return The byte array
     */
    public byte[] getBytes()
    {
        return _bytes;
    }


    /**
     *
     * @param value An integer to marshal
     * @return Returns a MarshalledObject representing the int
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

    /**
     * Marshalls a string
     * @param value A string to marshal
     * @return
     * @throws SizeLimitExceededException
     */
    public static MarshalledObject marshalString(String value) throws SizeLimitExceededException
    {
        byte[] output = value.getBytes(StandardCharsets.UTF_16);

        // Checks if length is not too long for the header
        if(output.length > 2 << 16)
        {
            throw new SizeLimitExceededException("Length is longer than 2 ^ 16. The header only allocates 2 bytes to store length");
        }

        return new MarshalledObject(Marshaller.StringType, output);
    }

    /**
     * Marshalls a byte
     * @param b
     * @return
     */
    public static MarshalledObject marshalByte(byte b)
    {
        byte[] output = new byte[]{b};

        return new MarshalledObject(Marshaller.ByteType, output);
    }


    /**
     * Marshalls a byte array
     * @param array
     * @return
     * @throws SizeLimitExceededException
     */
    public static MarshalledObject marshallByteArray(byte[] array) throws SizeLimitExceededException
    {
        // Checks if length is not too long for the header
        if(array.length > 2 << 16)
        {
            throw new SizeLimitExceededException("Length is longer than 2 ^ 16. The header only allocates 2 bytes to store length");
        }

        byte[] output = new byte[array.length];
        System.arraycopy(array, 0, output, 0, array.length);

        return new MarshalledObject(Marshaller.ByteArrayType, output);
    }


    /**
     * Marshalls an integer array
     * @param array
     * @return
     * @throws SizeLimitExceededException
     */
    public static MarshalledObject marshalIntArray(int[] array) throws SizeLimitExceededException
    {
        // Checks if length is not too long for the header
        if(array.length > 2 << 16)
        {
            throw new SizeLimitExceededException("Length is longer than 2 ^ 16. The header only allocates 2 bytes to store length");
        }

        // Allocates enough space for the int arraay
        byte[] output = new byte[array.length * 4];

        // Marshalls each int
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

    /**
     * Takes in an array of MarshalledObjects and converts it into an internal byte array. These MarshalledObjects can be obtained from the static functions on the Marshaller
     * Has greater type safety than the other method
     * @param values An array of marshalled objects
     */
    @Deprecated
    public Marshaller(MarshalledObject[] values)
    {
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
                    break;
                case ByteArrayType:
                    totalSize += 3;
                    break;
                case ByteType:
                    totalSize += 0;
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
                case ByteArrayType:
                {
                    _bytes[position++] = (byte)Marshaller.ByteArrayType;
                    int len = val.bytes.length;
                    byte msb = (byte) (len >>> 8);
                    byte lsb = (byte) len;
                    _bytes[position++] = msb;
                    _bytes[position++] = lsb;
                    break;
                }
                case ByteType:
                {

                    break;
                }
            }

            System.arraycopy(val.bytes, 0, _bytes, position, val.bytes.length);
            position += val.bytes.length;
        }
    }



}
