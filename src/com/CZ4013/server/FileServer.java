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

/**
 * Starts the file server thread
 */
public class FileServer
{
    public FileServer() throws IOException
    {
        new FileServerThread().start();
    }
}



/**
 * The actual file server
 */
class FileServerThread extends Thread
{
    // Somewhat arbitrary limit on UDP packet size
    public static final int MAX_PACKET_BYTES = 60000;

    // For debug usees
    public static final int DEBUG_MASK = 12;

    // An arbitrary timeout period for detecting duplicated messages
    public static int MESSAGE_CACHE_TIMEOUT = 20000;

    private DatagramSocket _socket;

    /**
     *
     * ## Some thoughts on design ##
     *
     * We can handle multiple users by getting the ip address and port number of every packet, and put it into a list in a hashtable.
     *
     *
     * Path normalisation?? How do we handle it. Do we need to handle it?
     *
     *  We may need to keep hash table of files that are currently being accessed, to lock them.
     *  or we can just throw errors
     */

    // Hashmap for storing the clients monitoring a filename
    final HashMap<String, HashSet<AddressPort>> monitoringMap = new HashMap<>();

    // Hashmap associating an incoming message from a client, to its response
    // This is used for the at-most-once invocation semantics
    final HashMap<RequesterMsg, byte[]> responseCache = new HashMap<>();


    // Initialises the file server with some arbitrary port
    public FileServerThread() throws IOException
    {
        super();

        _socket = new DatagramSocket(4445);

    }

    /**
     * Runs the thread
     */
    @Override
    public void run()
    {
        // Server always serving requests
        while(true)
        {
            byte[] buf = new byte[MAX_PACKET_BYTES];

            DatagramPacket packet = new DatagramPacket(buf, MAX_PACKET_BYTES);

            // Gets a incoming packet
            try
            {
                _socket.receive(packet);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }


            // Gets the data associated with packet
            byte[] packetData = packet.getData();

            // By default we want to use at-most-once semantics
            boolean useAtLeastOnceSemantics = false;

            try
            {
                // We take a peek inside the packet's data
                // If the request type is > 50, then it is defined as an at-least-once version of the original request type
                // We then for this request use at-least-once semantics
                // And we reset the request type byte back to request_byte - 50 such that it can be handled normally
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

            // We try to get a cached response
            // Note that we use sequence numbers such that duplicated requests can be identified
            // And that sending the same request twice (but with different seq nums) will mean its 2 different requests
            byte[] cachedResponse = responseCache.get(new RequesterMsg(requester, packetData));

            // If a cached response exists for the request-client combo, then it means a duplicated request was sent.
            // If we are using at-most-once semantics then we send the cached response back
            // And we do not process that query
            if(cachedResponse != null && !useAtLeastOnceSemantics)
            {
                sendRaw(requester, cachedResponse);
                log("Duplicate request sent from " + requester.toString(), 4);

                continue;
            }

            // No cached response found or using at-most-once semantics then we process the query
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


    /**
     * Processes the query according to its contents
     * @param packet
     * @param query
     * @throws Exception
     * @throws SizeLimitExceededException
     */
    public void processQuery(DatagramPacket packet, byte[] query) throws Exception, SizeLimitExceededException
    {
        UnMarshaller um = new UnMarshaller(query);

        int queryType = (int) um.getNextByte();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        switch (queryType)
        {
            // Reading a file
            case MessageType.READ_FILE:
            {
                String path = (String) um.getNext();
                int offset = (Integer) um.getNext();
                int length = (Integer) um.getNext();

                try
                {
                    byte[] content = readFile(path, offset, length);
                    respond(packet, MessageType.RESPONSE_BYTES, content, query, (int) um.getNext());
                    log("File at " + path + " is read from " + offset + " to " + (offset + length), 4);
                }
                catch (IOException e)
                {
                    respondError(packet, ErrorCodes.IOError, e.getMessage());
                    log("Error: file at " + path + " is not read from " + offset + " to " + (offset + length), 12);
                }
                break;
            }

            // Inserting into a file
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

            // Monitoring a file
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

            // Deleting a file (This is the additional idempotent operation, given no new file with same name was created.... between deletions)
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
                    respondError(packet, ErrorCodes.GENERAL, "Cannot delete file. It may not exist, or you do not have permission");
                    log("File " + path + " is not deleted", 12);
                }

                break;
            }

            // Duplicate a file with random name (Additional non-idempotent operation)
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
            case MessageType.GET_ATTRIBUTES:
            {
                String path = (String) um.getNext();

                try
                {
                    String lastModded = getLastModified(path);
                    respond(packet, MessageType.RESPONSE_ATTRIBUTES, lastModded, query, (int) um.getNext());
                    log("File " + path + " was last modified at " + new Date(Long.parseLong(lastModded)).toLocaleString(), 4);
                }
                catch (IOException e)
                {
                    respondError(packet, ErrorCodes.IOError, e.getMessage());
                    log("Cannot get Last Modified attribute from file " + path, 12);
                }
                break;
            }
            case MessageType.SERVER_SETUP_CACHE_TIMEOUT:
            {
                int timeoutMilli = (int) um.getNext();
                MESSAGE_CACHE_TIMEOUT = timeoutMilli;

                respond(packet, MessageType.RESPONSE_SUCCESS, "Cache timeout set to " + timeoutMilli, query, (int) um.getNext());
                log("Cache timeout set to " + timeoutMilli, 4);

                break;
            }

        }
    }


    /**
     * Responds to client that an error has occurred
     * @param packet
     * @param errorCode
     * @param message
     * @throws SizeLimitExceededException
     */
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

    /**
     * Puts a request and response into the cache
     * Also performs automated removal of the cached response after timeout period
     * @param request
     * @param response
     */
    private void putCache(RequesterMsg request, byte[] response)
    {
        // As we are using a different thread to perform the timed removal, we need to ensure integrity of the hashtable
        synchronized (responseCache)
        {
            responseCache.put(request, response);
        }

        // Use different thread to remove the cached response after the specified timeout
        new Thread(() ->
        {
            try
            {
                Thread.sleep(MESSAGE_CACHE_TIMEOUT);
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

    /**
     * Sends a string response back to the client
     * Also performs caching of the response
     * @param packet
     * @param msgType
     * @param message
     * @param request
     * @param sequenceNum
     * @throws SizeLimitExceededException
     */
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

    /**
     * Sends a byte response back to the client
     * Also performs caching of the response
     * @param packet
     * @param msgType
     * @param bytes
     * @param request
     * @param sequenceNum
     * @throws SizeLimitExceededException
     */
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

    /**
     * Sends an update callback to the monitoring client
     * @param target
     * @param pathname
     * @param updates
     * @param offset
     * @throws SizeLimitExceededException
     */
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

    /**
     * Sends raw bytes to a client
     * This mainly used for sending cached responses
     * @param target
     * @param raw
     */
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


    // An idempotent operation - deletion of file
    // Non-idempotent as long as no new files with same name created between deletions
    public boolean deleteFile(String pathname)
    {
        boolean success = new File(pathname).delete();

        log("File " + pathname + " is deleted", 1);

        return success;
    }

    // A non-idempotent operation - duplication of file into file with random name
    public String duplicateFile(String pathname) throws IOException
    {
        File dupFile = new File(pathname + "_" + Double.toHexString(Math.random()));
        Files.copy(new File(pathname).toPath(), dupFile.toPath());

        log("File " + pathname + " is duplicated as '" + dupFile.getPath() + "'", 1);

        return dupFile.getPath();
    }

    public String getLastModified(String pathname) throws Exception
    {
        File f = new File(pathname);

        log("File " + pathname + "was last modified at: " + new Date(f.lastModified()).toLocaleString(), 1);

        return Long.toString(f.lastModified());
    }

    // Reads a file at a certain location
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

    /**
     * Monitors a file for a certain duration, and for a certain client
     * @param pathname
     * @param monitorLength
     * @param monitoringClient
     * @return
     */
    public boolean monitorFile(String pathname, int monitorLength, AddressPort monitoringClient)
    {
        if(!new File(pathname).exists())
            return false;

        // Adds the monitoring of the file
        // Need synchronisation as removal done by differnt thread
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



    // Inserts a certain number of bytes into a file
    // This requires creating a new file and deleting an old
    // due to the difficulty of inserting bytes at a random location in a file
    public void insertFile(String pathname, int offsetx, byte[] data) throws IOException
    {
        String randomName = Double.toHexString(Math.random());

        File origFile = new File(pathname);
        File tempFile = new File("temp_" + randomName);

        BufferedInputStream bi = new BufferedInputStream(new FileInputStream(origFile));
        BufferedOutputStream bo = new BufferedOutputStream(new FileOutputStream(tempFile));

        final int offset = (offsetx < 0) ? (int)origFile.length() : offsetx;

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

        byte[] newBytes = new byte[(int)tempFile.length()];
        bi = new BufferedInputStream(new FileInputStream(tempFile));
        bi.read(newBytes);
        bi.close();


        if(!(origFile.delete() && tempFile.renameTo(origFile)))
            throw new IOException("Cannot delete original file or rename temporary file. Possibly due to original file still in use");

        log("File " + pathname + " successfully inserted into", 1);

        // Sends callbacks to clients which are monitoring this file
        synchronized (monitoringMap)
        {
            if(monitoringMap.get(pathname) != null)
            {
                monitoringMap.get(pathname).forEach(target -> {
                    try
                    {
                        respondCallback(target, pathname, newBytes, offset);
                    }
                    catch (SizeLimitExceededException e)
                    {
                        e.printStackTrace();
                    }
                });
            }
        }

    }





    // Logs something that could be useful
    private void log(String content, int mask)
    {
        Date d = new Date();


        String logStr = "[" + new SimpleDateFormat("HH:mm:ss.SSS").format(d) + "] ";
        if((mask & DEBUG_MASK) > 0)
            System.out.println(logStr + content);
    }
}
