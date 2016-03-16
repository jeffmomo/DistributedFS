package com.CZ4013.server;

import com.CZ4013.marshalling.Marshaller;
import com.CZ4013.marshalling.UnMarshaller;

import javax.naming.SizeLimitExceededException;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;


public class FileServer
{
    public FileServer() throws IOException
    {
        new FileServerThread().start();
    }
}



/**
 *
 */
class FileServerThread extends Thread
{
    public static final int MAX_PACKET_BYTES = 60000;
    public static final int DEBUG_MASK = 4;
    public static final int CACHE_TIMEOUT = 20000;

    private DatagramSocket _socket;

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
     *  or we can just throw errors
     */

    final HashMap<String, HashSet<AddressPort>> monitoringMap = new HashMap<>();

    final HashMap<RequesterMsg, byte[]> responseCache = new HashMap<>();


    public FileServerThread() throws IOException
    {
        super();

        _socket = new DatagramSocket(4445);

    }


    @Override
    public void run()
    {
        while(true)
        {
            byte[] buf = new byte[MAX_PACKET_BYTES];

            DatagramPacket packet = new DatagramPacket(buf, MAX_PACKET_BYTES);

            try
            {
                _socket.receive(packet);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }


            byte[] packetData = packet.getData();
            boolean useAtLeastOnceSemantics = false;
            try
            {
                UnMarshaller um = new UnMarshaller(packetData);
                if((int)(um.getNextByte()) > 50)
                {
                    useAtLeastOnceSemantics = true;
                    um.resetPosition();
                    um.modifyByteAt((byte)(um.getNextByte() - 50), 0);
                    packet.setData(um.getBytes());
                }
            }catch(Exception e){e.printStackTrace();}

            AddressPort requester = new AddressPort(packet);
            byte[] cachedResponse = responseCache.get(new RequesterMsg(requester, packetData));
            if(cachedResponse != null && !useAtLeastOnceSemantics)
            {
                sendRaw(requester, cachedResponse);
                log("Duplicate request sent from " + requester.toString(), 4);
                continue;
            }



            try
            {
                processQuery(packet, buf);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }



    }


    public void processQuery(DatagramPacket packet, byte[] query) throws Exception, SizeLimitExceededException
    {
        UnMarshaller um = new UnMarshaller(query);

        int queryType = (int) um.getNextByte();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        switch (queryType)
        {
            case MessageType.READ_FILE:
            {
                String path = (String) um.getNext();
                int offset = (Integer) um.getNext();
                int length = (Integer) um.getNext();

                try
                {
                    byte[] content = readFile(path, offset, length);
                    respond(packet, MessageType.RESPONSE_BYTES, content, query, (int) um.getNext());
                    log("File at " + path + " is read from " + offset + " to " + offset + length, 4);
                }
                catch (IOException e)
                {
                    respondError(packet, ErrorCodes.IOError, e.getMessage());
                    log("Error: file at " + path + " is not read from " + offset + " to " + offset + length, 12);
                }
                break;
            }
            case MessageType.INSERT_FILE:
            {
                String path = (String) um.getNext();
                int offset = (Integer) um.getNext();
                byte[] bytes = (byte[]) um.getNext();

                try
                {
                    insertFile(path, offset, bytes);
                    respond(packet, MessageType.RESPONSE_SUCCESS, "Success: Bytes inserted", query, (int) um.getNext());
                    log("file at " + path + " had " + bytes.length + " bytes inserted at " + offset, 4);
                }
                catch(IOException e)
                {
                    respondError(packet, ErrorCodes.IOError, e.getMessage());
                    log("Error: file at " + path + " had " + bytes.length + " bytes not inserted at " + offset, 12);
                }
                break;
            }
            case MessageType.MONITOR_FILE:
            {
                String path = (String) um.getNext();
                int monitorLength = (Integer) um.getNext();


                if(monitorFile(path, monitorLength, new AddressPort(packet)))
                {
                    respond(packet, MessageType.RESPONSE_SUCCESS, "Success: File monitored", query, (int) um.getNext());
                    log("File " + path + " is monitored", 4);
                }
                else
                {
                    respondError(packet, ErrorCodes.NotFound, "File does not exist");
                    log("File " + path + " is not monitored", 12);
                }


                break;
            }
            case MessageType.DELETE_FILE:
            {
                String path = (String) um.getNext();


                if(deleteFile(path))
                {
                    respond(packet, MessageType.RESPONSE_SUCCESS, "Success: File deleted", query, (int) um.getNext());

                    log("File " + path + " is deleted", 4);
                }
                else
                {
                    respondError(packet, ErrorCodes.GENERAL, "Cannot delete file");
                    log("File " + path + " is not deleted", 12);
                }

                break;
            }
            case MessageType.DUPLICATE_FILE:
            {
                String path = (String) um.getNext();

                try
                {
                    String filename = duplicateFile(path);
                    respond(packet, MessageType.RESPONSE_PATH, filename, query, (int) um.getNext());
                    log("File " + path + " is duplicated as " + filename, 4);
                }
                catch (IOException e)
                {
                    respondError(packet, ErrorCodes.IOError, e.getMessage());
                    log("File " + path + " is not duplicated", 12);
                }
                break;
            }
        }
    }


    private void respondError(DatagramPacket packet, int errorCode, String message) throws SizeLimitExceededException
    {
        byte[] buf = new Marshaller((byte) MessageType.ERROR, errorCode, message).getBytes();

        if(buf.length > MAX_PACKET_BYTES)
            throw new SizeLimitExceededException("Message too large for UDP datagram");

        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        DatagramPacket out = new DatagramPacket(buf, buf.length, address, port);

        try
        {
            _socket.send(out);
        }
        catch(Exception e){e.printStackTrace();}

        log("sent error to client : " + message, 2);
    }

    private void putCache(RequesterMsg request, byte[] response)
    {
        synchronized (responseCache)
        {
            responseCache.put(request, response);
        }

        new Thread(() ->
        {
            try
            {
                Thread.sleep(CACHE_TIMEOUT);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            synchronized (responseCache)
            {
                responseCache.remove(request);
            }
        }).start();
    }

    private void respond(DatagramPacket packet, int msgType, String message, byte[] request, int sequenceNum) throws SizeLimitExceededException
    {

        byte[] buf = new Marshaller((byte) msgType, message, sequenceNum).getBytes();

        if(buf.length > MAX_PACKET_BYTES)
            throw new SizeLimitExceededException("Message too large for UDP datagram");

        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        DatagramPacket out = new DatagramPacket(buf, buf.length, address, port);

        try
        {
            _socket.send(out);
            log("responded to client TEST: " + message, 2);

            putCache(new RequesterMsg(new AddressPort(packet), request), buf);
        }
        catch(Exception e){e.printStackTrace();}

    }

    private void respond(DatagramPacket packet, int msgType, byte[] bytes, byte[] request, int sequenceNum) throws SizeLimitExceededException
    {
        byte[] buf = new Marshaller((byte) msgType, bytes, sequenceNum).getBytes();

        if(buf.length > MAX_PACKET_BYTES)
            throw new SizeLimitExceededException("Message too large for UDP datagram");

        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        DatagramPacket out = new DatagramPacket(buf, buf.length, address, port);

        try
        {
            _socket.send(out);
            log("bytes to client test: length = " + bytes.length, 2);


            putCache(new RequesterMsg(new AddressPort(packet), request), buf);
        }
        catch(Exception e){e.printStackTrace();}

    }

    private void respondCallback(AddressPort target, String pathname, byte[] updates, int offset) throws SizeLimitExceededException
    {
        byte[] buf = new Marshaller((byte) MessageType.CALLBACK, pathname, updates).getBytes();

        if(buf.length > MAX_PACKET_BYTES)
            throw new SizeLimitExceededException("Message too large for UDP datagram");

        InetAddress address = target.address;
        int port = target.port;
        DatagramPacket out = new DatagramPacket(buf, buf.length, address, port);

        try
        {
            _socket.send(out);
            log("Callback to client " + target.toString() + ", file " + pathname + " has been updated at " + offset + ": " + new String(updates), 2);

        }
        catch(Exception e){e.printStackTrace();}

    }

    private void sendRaw(AddressPort target, byte[] raw)
    {
        InetAddress address = target.address;
        int port = target.port;
        DatagramPacket out = new DatagramPacket(raw, raw.length, address, port);

        try
        {
            _socket.send(out);
        }
        catch(Exception e){e.printStackTrace();}

        log("Raw data sent to " + target.toString(), 2);
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

    public boolean monitorFile(String pathname, int monitorLength, AddressPort monitoringClient)
    {
        if(!new File(pathname).exists())
            return false;

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

        return true;

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
                    try
                    {
                        respondCallback(target, pathname, data, offset);
                    }
                    catch (SizeLimitExceededException e)
                    {
                        e.printStackTrace();
                    }
                });
            }
        }

    }





    private void log(String content, int mask)
    {
        Date d = new Date();


        String logStr = "[" + new SimpleDateFormat("HH:mm:ss.SSS").format(d) + "] ";
        if((mask & DEBUG_MASK) > 0)
            System.out.println(logStr + content);
    }
}
