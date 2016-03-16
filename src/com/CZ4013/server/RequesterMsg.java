package com.CZ4013.server;

import java.util.Arrays;

public class RequesterMsg extends Object
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
