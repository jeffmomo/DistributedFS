package com.CZ4013;

import com.CZ4013.marshalling.Marshaller;
import com.CZ4013.marshalling.UnMarshaller;
import com.CZ4013.server.FileServer;

public class Main {

    public static void main(String[] args) throws Exception {


        // WHEN MARSHALLING ARRAYS USE THE PRIMITIVE values and arrays.
        // Make sure to cast ambiguous args (e.g. integer as byte) so that the Marshaller knows what to treat each argument as
        Marshaller m = new Marshaller((byte)3, (int)5, "jello", -1, Integer.MAX_VALUE, new int[]{-1,0,1});

        // This gets the marshalled bytes which is ready for transfer
        byte[] marshalledBytes = m.getBytes();

        // To unmarshall construct a new Unmarshaller on the bytes
        UnMarshaller um = new UnMarshaller(marshalledBytes);

        // Use the getNext function and a cast to get the parameters
        // Note that getNext() returns null if there are no more parameters
        // Exceptions may be thrown if the bytes are not well formed
        byte b = um.getNextByte();
        int i = (Integer) um.getNext();
        String j = (String) um.getNext();
        int neg = (Integer) um.getNext();
        int max = (Integer) um.getNext();
        int[] arr = (int[]) um.getNext();
        Object n = um.getNext();

        FileServer fs = new FileServer(Integer.parseInt(args[0]));


        // An arbitrary packet used for testing purposes
//        DatagramPacket testPkt = new DatagramPacket(new byte[5], 0, InetAddress.getByName("127.0.0.1"), 0);
//
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.MONITOR_FILE, "test", 100).getBytes());
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.MONITOR_FILE, "test", 100).getBytes());
//
//
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.READ_FILE, "test", 0, -1).getBytes());
//
//
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.INSERT_FILE, "test", 1, "b".getBytes()).getBytes());
//        Thread.sleep(50);
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.INSERT_FILE, "test", 1, "b".getBytes()).getBytes());
//        Thread.sleep(500);
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.INSERT_FILE, "test", 1, "b".getBytes()).getBytes());
//
//
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.READ_FILE, "test", 0, -1).getBytes());
//
//        fs.processQuery(testPkt, new Marshaller((byte) MessageType.DUPLICATE_FILE, "test").getBytes());

        //fs.deleteFile(fs.duplicateFile("test"));


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
