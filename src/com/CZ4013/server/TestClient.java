package com.CZ4013.server;

import com.CZ4013.marshalling.Marshaller;
import com.CZ4013.marshalling.UnMarshaller;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TestClient
{
    int port;
    InetAddress address;
    static DatagramSocket socket = null;

    byte[] sendBuf = new byte[256];

    static int sequenceNum = 0;


    static void send(byte[] buf) throws IOException
    {
        // Create a socket directed at self and towards the specific socket 4445.
        // This should be made customisable
        InetAddress address = InetAddress.getByName("127.0.0.1");
        DatagramPacket packet = new DatagramPacket(buf, buf.length,
                address, 4445);
        socket.send(packet);
    }

    public static void main(String[] args)
    {
        try
        {
            socket = new DatagramSocket();


            // Parameters for MONITOR_FILE - PathName, IntervalMilliseconds, SequenceNumber
            send(new Marshaller((byte) MessageType.MONITOR_FILE, "test", 10000, sequenceNum++).getBytes());

            // Parameters for READ_FILE - PathName, Offset, Length (set this to -1 if you want to read to end), SequenceNumber
            send(new Marshaller((byte) MessageType.READ_FILE, "test", 0, -1, sequenceNum++).getBytes());

            // Parameters for INSERT_FILE - PathName, Offset, Bytes, SequenceNumber
            send(new Marshaller((byte) MessageType.INSERT_FILE, "test", 1, "b".getBytes(), sequenceNum).getBytes());
            send(new Marshaller((byte) MessageType.INSERT_FILE, "test", 1, "b".getBytes(), sequenceNum).getBytes());
            send(new Marshaller((byte) MessageType.INSERT_FILE, "test", 1, "b".getBytes(), sequenceNum).getBytes());


            send(new Marshaller((byte) MessageType.AT_LEAST_ONCE_DEMO_INSERT_FILE, "test", 1, "z".getBytes(), -1).getBytes());
            send(new Marshaller((byte) MessageType.AT_LEAST_ONCE_DEMO_INSERT_FILE, "test", 1, "z".getBytes(), -1).getBytes());


            send(new Marshaller((byte) MessageType.READ_FILE, "test", 0, -1, sequenceNum++).getBytes());

            // Parameters for DUPLICATE_FILE - PathName, SequenceNumber
            send(new Marshaller((byte) MessageType.DUPLICATE_FILE, "test", sequenceNum++).getBytes());

            // Parameters for DELETE_FILE - PathName, SequenceNumber
            //send(new Marshaller((byte) MessageType.DELETE_FILE, "test", sequenceNum++).getBytes());




            while(true)
            {
                byte[] recvBuf = new byte[FileServerThread.MAX_PACKET_BYTES];
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(packet);

                UnMarshaller um = new UnMarshaller(packet.getData());
                int resType = (byte) um.getNextByte();

                // Handles responses
                switch (resType)
                {
                    case MessageType.RESPONSE_MSG:
                        // Only normal responses have the sequence numbers embedded
                        System.out.println("Response Received: " + (String) um.getNext() + " seq num = " + um.getNext());
                        break;
                    case MessageType.RESPONSE_BYTES:
                        // Only normal responses have the sequence numbers embedded
                        System.out.println("Bytes received - length: " + ((byte[])um.getNext()).length + " seq num = " + um.getNext());
                        break;
                    case MessageType.CALLBACK:
                        System.out.println("Callback received for " + um.getNext() + " - update length: " + ((byte[]) um.getNext()).length);
                        break;
                    case MessageType.ERROR:
                        System.err.println("Error occured - code " + (int) um.getNext() + ": " + um.getNext());
                        break;
                    case MessageType.RESPONSE_PATH:
                        // Only normal responses have the sequence numbers embedded
                        String path = (String)um.getNext();
                        System.out.println("Duplicated file path recvd: " + path + " seq num = " + um.getNext());
                        // Sends a delete request with the newly gotten file name as the parameter
                        send(new Marshaller((byte) MessageType.DELETE_FILE, path, sequenceNum++).getBytes());
                        break;
                    case MessageType.RESPONSE_SUCCESS:
                        System.out.println(um.getNext() + " seq num = " + um.getNext());
                        break;
                    default:
                        System.err.println("Strange request received" + um.getNext());
                }
            }

        } catch(Exception e){e.printStackTrace();}






    }
}
