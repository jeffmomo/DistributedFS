package com.CZ4013.server;


import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Created by mdl94 on 16/03/2016.
 */
public class AddressPort extends Object
{

    public InetAddress address;
    public int port;

    public AddressPort(InetAddress address, int port)
    {
        this.address = address;
        this.port = port;
    }

    public AddressPort(DatagramPacket packet)
    {
        this.address = packet.getAddress();
        this.port = packet.getPort();
    }

    @Override
    public int hashCode()
    {
        return (address.toString() + port).hashCode();
    }


    @Override
    public String toString()
    {
        return address.toString() + ":" + port;
    }

    @Override
    public boolean equals(Object obj)
    {
        if(obj.getClass() == this.getClass())
        {
            AddressPort ap = (AddressPort)obj;
            return address.equals(ap.address) && port == ap.port;
        }

        return false;
    }
}
