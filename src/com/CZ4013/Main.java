package com.CZ4013;

import com.CZ4013.marshalling.MarshalledObject;
import com.CZ4013.marshalling.Marshaller;
import com.CZ4013.marshalling.UnMarshaller;

import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws Exception {


        // To marshal variables into bytes, we use the Marshaller.
        // Provide to the marshaller an array of MarshalledObjects
        Marshaller m = new Marshaller(
                new MarshalledObject[]
                {
                        Marshaller.marshalInt(5),
                        Marshaller.marshalString("jello"),
                        Marshaller.marshalInt(-1),
                        Marshaller.marshalInt(Integer.MAX_VALUE),
                        Marshaller.marshalIntArray(new int[]{-1,0,1})
                });

        // This gets the marshalled bytes which is ready for transfer
        byte[] marshalledBytes = m.getBytes();

        // To unmarshall construct a new Unmarshaller on the bytes
        UnMarshaller um = new UnMarshaller(marshalledBytes);

        // Use the getNext function and a cast to get the parameters
        // Note that getNext() returns null if there are no more parameters
        // Exceptions may be thrown if the bytes are not well formed
        int i = (Integer) um.getNext();
        String j = (String) um.getNext();
        int neg = (Integer) um.getNext();
        int max = (Integer) um.getNext();
        int[] arr = (int[]) um.getNext();
        Object n = um.getNext();

        int z = 0;
    }

    static String arrToStr(int[] arr)
    {
        StringBuilder sb = new StringBuilder();

        for(int i : arr)
        {
            sb.append(i);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }
}
