package com.CZ4013.server;

import java.util.Arrays;

/**
 * Stores a request message, associated with a requester
 * This class used for checking for duplicated requests
 */
public class RequesterMsg
{
    public AddressPort requester;
    public byte[] request;
    public int messageType;


    public RequesterMsg(AddressPort requester, byte[] request)
    {
        this.requester = requester;
        this.request = request;
        this.messageType = messageType;
    }

    // Bottom methods allows use in hashtable

    public int hashCode()
    {
        return Integer.hashCode(messageType) + requester.hashCode() + Arrays.hashCode(request);
    }

    public boolean equals(Object o)
    {
        if(o.getClass() == this.getClass())
        {
            RequesterMsg sm = (RequesterMsg)o;

            return (requester.equals(sm.requester) && Arrays.equals(request, sm.request));
        }
        return false;
    }

}
