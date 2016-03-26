package com.CZ4013.server;


import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Stores the uniquely identifiable address + port information from client
 */
public class AddressPort
{

    public InetAddress address;
    public int port;

    public AddressPort(InetAddress address, int port)
    {
        this.address = address;
        this.port = port;
    }

    /**
     * Initialises with a datagrampacket
     * @param packet
     */
    public AddressPort(DatagramPacket packet)
    {
        this(packet.getAddress(), packet.getPort());
    }

    // Below are methods to allow use in a hashtable


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
