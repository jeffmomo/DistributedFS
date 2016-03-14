package com.CZ4013.server;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class QuoteServerThread extends Thread
{
    DatagramSocket socket;


    public QuoteServerThread() throws IOException {
        this("QuoteServer");
    }

    public QuoteServerThread(String name) throws IOException
    {
        super(name);
        socket = new DatagramSocket(4445);

    }

    public void run()
    {
        while(true)
        {

            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try
            {
                socket.receive(packet);
                System.out.println("Packet Received");
            } catch (Exception e)
            {
                e.printStackTrace();
            }

            String dString = "default response";
            buf = dString.getBytes();

            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            packet = new DatagramPacket(buf, buf.length, address, port);

            try
            {
                socket.send(packet);
                System.out.println("Data sent");
            }
            catch(Exception e){e.printStackTrace();}
        }
    }

}
