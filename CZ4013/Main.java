package com.CZ4013;

import com.CZ4013.marshalling.MarshalledObject;
import com.CZ4013.marshalling.Marshaller;
import com.CZ4013.marshalling.UnMarshaller;

import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws Exception {


//        System.out.println(Marshaller.unMarshallInt(Marshaller.marshalInt(Integer.MAX_VALUE)));
//
//        System.out.println(Marshaller.unMarshallString(Marshaller.marshalString("bob lu")));
//
//        System.out.println(arrToStr(Marshaller.unMarshallIntArray(Marshaller.marshalIntArray(new int[]{1,2,3,4,5}))));
//
//        Marshaller m = new Marshaller(8);
//        m.appendString("bob lu is a loser".getBytes(StandardCharsets.UTF_16));
//
//        m.getChunks();
        // write your code here

        Marshaller m = new Marshaller(new MarshalledObject[]{Marshaller.marshalInt(5), Marshaller.marshalString("jello"), Marshaller.marshalInt(-1), Marshaller.marshalInt(Integer.MAX_VALUE), Marshaller.marshalIntArray(new int[]{-1,0,1})});

        UnMarshaller um = new UnMarshaller(m.getBytes());

        int i = (Integer) um.getNext();
        String j = (String) um.getNext();
        int neg = (Integer) um.getNext();
        int max = (Integer) um.getNext();
        int[] arr = (int[]) um.getNext();
//        String arsr = arrToStr(arr);

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
