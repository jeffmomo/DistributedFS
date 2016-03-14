package com.CZ4013.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Created by mdl94 on 14/03/2016.
 */
public class QuoteClient
{
    int port;
    InetAddress address;
    DatagramSocket socket = null;
    DatagramPacket packet;
    byte[] sendBuf = new byte[256];

    public static void main(String[] args)
    {
                try
        {
            DatagramSocket socket = new DatagramSocket();

            byte[] buf = new byte[256];
            InetAddress address = InetAddress.getByName("127.0.0.1");
            DatagramPacket packet = new DatagramPacket(buf, buf.length,
                    address, 4445);
            socket.send(packet);
///////////////////////////////////////
            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Quote of the Moment: " + received);

        } catch(Exception e){e.printStackTrace();}






    }
}
