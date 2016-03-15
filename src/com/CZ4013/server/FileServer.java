package com.CZ4013.server;

import com.CZ4013.marshalling.UnMarshaller;

import javax.naming.SizeLimitExceededException;
import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;




/**
 *
 */
public class FileServer
{


    public static final int MAX_PACKET_BYTES = 600000;

    public static final int DEBUG_MASK = 2;

    /**
     *
     * ## Thoughts on design ##
     *
     * We can handle multiple users by getting the ip address and port number of every packet, and put it into a list in a hashtable.
     *
     * A data array which stores the incomplete data received so far for a certain client
     * A status array indicating what actions should be taken on the next packet received for that client
     * Once all data has been received clear the hashtable and do stuff with it
     *
     * Path normalisation?? How do we handle it. Do we need to handle it?
     *
     *  We may need to keep hash table of files that are currently being accessed, to lock them.
     */

    final HashMap<String, HashSet<AddressPort>> monitoringMap = new HashMap<>();

    public FileServer()
    {

    }

    public void processQuery(DatagramPacket packet, byte[] query) throws Exception
    {
        UnMarshaller um = new UnMarshaller(query);

        int queryType = (int) um.getNextByte();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        switch (queryType)
        {
            case QueryType.READ_FILE:
            {
                String path = (String) um.getNext();
                int offset = (Integer) um.getNext();
                int length = (Integer) um.getNext();

                try
                {
                    byte[] content = readFile(path, offset, length);
                    respond(packet, content);
                } catch (IOException e)
                {
                    respond(packet, "Error in reading file");
                }
                break;
            }
            case QueryType.INSERT_FILE:
            {
                String path = (String) um.getNext();
                int offset = (Integer) um.getNext();
                byte[] bytes = (byte[]) um.getNext();

                try
                {
                    insertFile(path, offset, bytes);
                    respond(packet, "Success: Bytes inserted");
                }
                catch(Exception e)
                {
                    respond(packet, "Error in file insertion");
                }
                break;
            }
            case QueryType.MONITOR_FILE:
            {
                String path = (String) um.getNext();
                int monitorLength = (Integer) um.getNext();

                try
                {
                    monitorFile(path, monitorLength, new AddressPort(packet));
                    respond(packet, "Success: File monitored");
                } catch (Exception e)
                {
                    respond(packet, "Error in monitoring file");
                }

                break;
            }
            case QueryType.DELETE_FILE:
            {
                String path = (String) um.getNext();

                try
                {
                    if(deleteFile(path))
                        respond(packet, "Success: File deleted");
                    else
                        respond(packet, "Failure: File cannot be deleted");
                }
                catch (Exception e)
                {
                    respond(packet, "Error in deleting file");
                }
                break;
            }
            case QueryType.DUPLICATE_FILE:
            {
                String path = (String) um.getNext();

                try
                {
                    String filename = duplicateFile(path);
                    respond(packet, "Success: File duplicated, new file name is: '" + filename + "'");
                }
                catch (Exception e)
                {
                    respond(packet, "Error: Did not duplicate file");
                }
                break;
            }
        }
    }

    private void respond(DatagramPacket queryPkt, String message)
    {
        log("response to client TEST: " + message, 2);
    }

    private void respond(DatagramPacket queryPkt, byte[] bytes)
    {
        log("bytes to client test: length = " + bytes.length, 2);
    }


    // An idempotent operation
    public boolean deleteFile(String pathname)
    {
        boolean success = new File(pathname).delete();

        log("File " + pathname + " is deleted", 1);

        return success;
    }

    // A non-idempotent operation
    public String duplicateFile(String pathname) throws IOException
    {
        File dupFile = new File(pathname + "_" + Double.toHexString(Math.random()));
        Files.copy(new File(pathname).toPath(), dupFile.toPath());

        log("File " + pathname + " is duplicated as '" + dupFile.getPath() + "'", 1);

        return dupFile.getPath();
    }

    public byte[] readFile(String pathname, int offset, int length) throws IOException
    {
        if(length == -1)
            length = (int) new File(pathname).length();

        byte[] buffer = new byte[length];

        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(pathname));
        inputStream.read(buffer, offset, length);
        inputStream.close();

        log("File " + pathname + " has been read", 1);

        return buffer;
    }

    public void monitorFile(String pathname, int monitorLength, AddressPort monitoringClient)
    {
        // Adds the monitoring of the file
        synchronized (monitoringMap)
        {
            if(monitoringMap.get(pathname) == null)
            {
                monitoringMap.put(pathname, new HashSet<>());
            }
            monitoringMap.get(pathname).add(monitoringClient);
        }

        // Create a new thread which removes the monitoring after the interval
        new Thread(() -> {
            try
            {
                Thread.sleep(monitorLength);
            }catch (Exception e)
            {
                e.printStackTrace();
            }

            synchronized (monitoringMap)
            {
                HashSet<AddressPort> clientSet = monitoringMap.get(pathname);
                if(clientSet == null)
                    return;

                clientSet.remove(monitoringClient);

                if(clientSet.isEmpty())
                    monitoringMap.remove(pathname);
            }
        }).start();

        log("File " + pathname + " has been monitored by client at " + monitoringClient.toString(), 1);
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

        bi.close(); bo.close();


        if(!(origFile.delete() && tempFile.renameTo(origFile)))
            throw new IOException("Cannot delete original file or rename temporary file. Possibly due to original file still in use");

        log("File " + pathname + " successfully inserted into", 1);

        synchronized (monitoringMap)
        {
            if(monitoringMap.get(pathname) != null)
            {
                monitoringMap.get(pathname).forEach(target -> {
                    sendUpdates(target, pathname, data, offset);
                });
            }
        }

    }

    private void sendUpdates(AddressPort target, String pathname, byte[] updates, int offset)
    {
        log("To target " + "T E S T" + ", file " + pathname + " has been updated at " + offset + ": " + new String(updates), 1);
    }

    private void sendBytes(byte[] bytes, InetAddress address, int port) throws SizeLimitExceededException
    {
        if(bytes.length > MAX_PACKET_BYTES)
            throw new SizeLimitExceededException("Sequence of bytes too big for a UDP datagram");
    }


    private void log(String content, int mask)
    {
        if((mask & DEBUG_MASK) > 0)
            System.out.println(content);
    }
}
