package com.CZ4013.server;

import java.awt.image.ImagingOpException;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Exchanger;

/**
 *
 */
public class FileServer
{

    /**
     * We can handle multiple users by getting the ip address and port number of every packet, and put it into a list in a hashtable.
     *
     * A data array which stores the incomplete data received so far for a certain client
     * A status array indicating what actions should be taken on the next packet received for that client
     * Once all data has been received clear the hashtable and do stuff with it
     *
     */

    final HashMap<String, LinkedList<String>> monitoringSet = new HashMap<>();



    public FileServer()
    {

    }

    // We may need to keep hash table of files that are currently being accessed, to lock them.

    private byte[] readFile(String pathname, int offset, int length) throws IOException
    {
        byte[] buffer = new byte[length];
        new BufferedInputStream(new FileInputStream(pathname)).read(buffer, offset, length);

        return buffer;
    }

    public void monitorFile(String pathname, int monitorLength, String monitorTarget)
    {
        synchronized (monitoringSet)
        {
            if(monitoringSet.get(pathname) == null)
            {
                monitoringSet.put(pathname, new LinkedList<>());
            }
            monitoringSet.get(pathname).add(monitorTarget);
        }

        new Thread(() -> {
            try
            {
                Thread.sleep(monitorLength);
            }catch (Exception e)
            {
                e.printStackTrace();
            }

            synchronized (monitoringSet)
            {
                LinkedList<String> monitorList = monitoringSet.get(pathname);
                monitorList.remove(monitorTarget);

                if(monitorList.isEmpty())
                    monitoringSet.remove(pathname);

            }
        }).start();
    }


    public void insertFile(String pathname, int offset, byte[] data) throws IOException
    {
        String randomName = Double.toHexString(Math.random());

        File origFile = new File(pathname);
        File tempFile = new File("temp_" + randomName);

        BufferedInputStream bi = new BufferedInputStream(new FileInputStream(origFile));
        BufferedOutputStream bo = new BufferedOutputStream(new FileOutputStream(tempFile));

        for(int pos = 0; pos < offset; pos++)
        {
            bo.write(bi.read());
        }

        bo.write(data);

        int read;
        while((read = bi.read()) != -1)
        {
            bo.write(read);
        }

        bi.close();
        bo.close();

        origFile.delete();
        tempFile.renameTo(origFile);

        if(monitoringSet.get(pathname) != null)
        {
            monitoringSet.get(pathname).forEach(target -> {
                sendUpdates(target, pathname, data, offset);
            });
        }
    }

    private void sendUpdates(String target, String pathname, byte[] updates, int offset)
    {
        System.out.println("To target " + target + ", file " + pathname + " has been updated at " + offset + ": " + new String(updates));
    }
}
