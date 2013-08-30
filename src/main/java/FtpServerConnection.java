import java.io.*;
import java.net.*;
import java.util.*;

/**
 * FtpServerConnection class.
 *
 * @author  Mort Yao <mort.yao@gmail.com>
 *
 * @see     FtpServer
 */
public class FtpServerConnection implements Runnable {
    /**
     * FTP home path on the server.
     */
    private final String ftpHome;
    
    /**
     * Current working directory.
     */
    private String workingDir = "/";
    
    /**
     * Transfer mode. Not implemented though.
     */
    private String type = "A";
    
    /**
     * Stores the old pathname specified by the last RNFR.
     */
    private String frPath = null;
    
    /**
     * Socket for FTP.
     */
    private final Socket connection;
    private final BufferedReader in;
    private final OutputStream out;
    private final PrintStream pout;
    
    /**
     * Is data trasmission in passive mode.
     */
    private boolean isPassive = false;
    
    /**
     * Host/port for FTP data. Used only by active mode.
     */
    private String dataHost = null;
    private int dataPort = 0;
    
    /**
     * Socket for FTP data. Used only by passive mode.
     */
    private ServerSocket dataSocket;
    
    /**
     * Has a QUIT command been received.
     */
    private volatile boolean shouldStop = false;
    
    /**
     * Prints a log message on standard error.
     *
     * @param msg  log message
     */
    private void log(String msg) {
        System.err.println(new Date() + " [" +
                           connection.getInetAddress().getHostAddress() + ":" +
                           connection.getPort() + "] " + msg);
    }
    
    /**
     * Sends an FTP response.
     *
     * @param  code         response code
     * @param  msg          response arg
     * @throws IOException
     */
    private void sendResponse(int code, String msg) throws IOException {
        pout.print(code + " " + msg + "\r\n");
        out.flush();
    }
    
    /**
     * Sends FTP data.
     *
     * @param  br           BufferedReader from where to read data
     * @throws IOException
     */
    private void sendData(BufferedReader br) throws IOException {
        Socket dataConnection;
        if (isPassive)
            dataConnection = dataSocket.accept();
        else
            dataConnection = new Socket(dataHost, dataPort);
        OutputStream dout = new BufferedOutputStream(dataConnection.getOutputStream());
        PrintStream pdout = new PrintStream(dout);
        String line;
        while ((line = br.readLine()) != null)
            pdout.print(line + "\r\n");
        dout.flush();
        dataConnection.close();
    }
    
    /**
     * Sends FTP data.
     *
     * @param  data         data as a String object
     * @throws IOException
     */
    private void sendData(String data) throws IOException {
        Socket dataConnection;
        if (isPassive)
            dataConnection = dataSocket.accept();
        else
            dataConnection = new Socket(dataHost, dataPort);
        OutputStream dout = new BufferedOutputStream(dataConnection.getOutputStream());
        PrintStream pdout = new PrintStream(dout);
        pdout.print(data);
        dout.flush();
        dataConnection.close();
    }
    
    /**
     * Sends FTP data.
     *
     * @param  file         data as a File object
     * @throws IOException
     */
    private void sendData(File file) throws IOException {
        Socket dataConnection;
        if (isPassive)
            dataConnection = dataSocket.accept();
        else
            dataConnection = new Socket(dataHost, dataPort);
        InputStream fileStream = new FileInputStream(file);
        OutputStream dout = new BufferedOutputStream(dataConnection.getOutputStream());
        byte[] buffer = new byte[1024];
        while (fileStream.available() > 0)
            dout.write(buffer, 0, fileStream.read(buffer));
        dout.flush();
        dataConnection.close();
    }
    
    /**
     * Receives FTP data and saves to a file.
     *
     * @param  file         file to save
     * @throws IOException
     */
    private void receiveData(File file) throws IOException {
        Socket dataConnection;
        if (isPassive)
            dataConnection = dataSocket.accept();
        else
            dataConnection = new Socket(dataHost, dataPort);
        InputStream dataStream = dataConnection.getInputStream();
        OutputStream fout = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buffer = new byte[1024];
        while (dataStream.available() > 0)
            fout.write(buffer, 0, dataStream.read(buffer));
        fout.flush();
        dataConnection.close();
    }
    
    /**
     * Starts a process and sends the output stream to FTP data.
     *
     * @param  cmdArgs      command name and arguments
     * @throws IOException
     */
    private void callProcess(String... cmdArgs) throws IOException {
        Process process = new ProcessBuilder(cmdArgs).start();
        InputStream is = process.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        sendData(br);
    }
    
    /**
     * Returns the canonical path of a client-side pathname on the server.
     * @param  pathName     pathname
     * @return              server-side canonical path
     * @throws IOException
     */
    private String getPath(String pathName) throws IOException {
        if (pathName.startsWith("/"))
            return new File(ftpHome, pathName).getCanonicalPath();
        else
            return new File(new File(ftpHome, workingDir), pathName).getCanonicalPath();
    }
    
    /**
     * Returns the size of a pathname.
     *
     * @param  pathName     pathname
     * @return              size of the pathname
     * @throws IOException
     */
    private long getSize(String pathName) throws IOException {
        if (pathName.startsWith("/"))
            return new File(ftpHome, pathName).length();
        else
            return new File(new File(ftpHome, workingDir), pathName).length();
    }
    
    /**
     * Returns true if a pathname is accessible.
     *
     * @param  pathName     pathname
     * @return              true if the pathname is in FTP home path
     * @throws IOException
     */
    private boolean isPathAccessible(String pathName) throws IOException {
        return getPath(pathName).startsWith(ftpHome);
    }
    
    /**
     * Returns true if a pathname exists and is readable.
     *
     * @param  pathName     pathname
     * @return              true if the pathname exists and is readable
     * @throws IOException
     */
    private boolean isPathReadable(String pathName) throws IOException {
        return new File(getPath(pathName)).exists() && new File(getPath(pathName)).canRead();
    }
    
    /**
     * Returns true if a pathname exists and is writable.
     *
     * @param  pathName     pathname
     * @return              true if the pathname exists and is writable
     * @throws IOException
     */
    private boolean isPathWritable(String pathName) throws IOException {
        return new File(getPath(pathName)).exists() && new File(getPath(pathName)).canWrite();
    }
    
    /**
     * Deletes a pathname on the server.
     *
     * @param   pathName     pathname
     * @throws  IOException
     */
    private void dele(String pathName) throws IOException {
        new File(getPath(pathName)).delete();
    }
    
    /**
     * Renames the old frName to a new pathname on the server.
     *
     * @param  pathName     new pathname
     * @throws IOException
     */
    private void rnto(String pathName) throws IOException {
        new File(getPath(frPath)).renameTo(new File(getPath(pathName)));
    }
    
    /**
     * Lists contents of the current working directory on the server.
     *
     * @throws IOException
     */
    private void list() throws IOException {
        callProcess("ls", "-l", getPath(workingDir));
    }
    
    /**
     * Lists contents of a pathname on the server.
     *
     * @param  pathName     pathname
     * @throws IOException
     */
    private void list(String pathName) throws IOException {
        callProcess("ls", "-l", getPath(pathName));
    }
    
    /**
     * Retrieves a file from the server.
     *
     * @param  pathName     pathname
     * @throws IOException
     */
    private void retr(String pathName) throws IOException {
        sendData(new File(getPath(pathName)));
    }
    
    /**
     * Stores a file onto the server.
     *
     * @param  pathName     pathname
     * @throws IOException
     */
    private void stor(String pathName) throws IOException {
        receiveData(new File(getPath(pathName)));
    }
    
    /**
     * Handles an FTP request.
     *
     * @param  request      FTP request string
     * @throws IOException
     */
    private void handleRequest(String request) throws IOException {
        String command[] = request.split("\\s+");
        switch (command[0]) {
        case "USER": { // USER <SP> <username> <CRLF>
            sendResponse(331, "Please specify the password.");
            break;
        }
        case "PASS": { // PASS <SP> <password> <CRLF>
            sendResponse(230, "Login successful.");
            break;
        }
        case "SYST": { // SYST <CRLF>
            sendResponse(215, "UNIX Type: L8");
            break;
        }
        case "MODE": { // MODE <SP> <mode-code> <CRLF>
            sendResponse(200, "Mode set to Stream.");
            break;
        }
        case "TYPE": { // TYPE <SP> <type-code> <CRLF>
            sendResponse(200, "Type set to I.");
            break;
        }
        case "STRU": // STRU <SP> <structure-code> <CRLF>
            sendResponse(200, "Stru set to File.");
            break;
        case "PWD": { // PWD <CRLF>
            sendResponse(257, "\"" + workingDir + "\"");
            break;
        }
        case "PORT": { // PORT <SP> <host-port> <CRLF>
            isPassive = false;
            String args[] = command[1].split(",");
            dataHost = args[0] + "." + args[1] + "." + args[2] + "." + args[3];
            dataPort = Integer.parseInt(args[4]) * 256 + Integer.parseInt(args[5]);
            sendResponse(200, "PORT command successful.");
            break;
        }
        case "EPRT": { // EPRT <SP> <D> <net-port> <D> <net-addr> <D> <tcp-port> <D> <CRLF>
            isPassive = false;
            String args[] = command[1].split("\\|");
            dataHost = args[2];
            dataPort = Integer.parseInt(args[3]);
            sendResponse(200, "EPRT command successful.");
            break;
        }
        case "PASV": { // PASV <CRLF>
            isPassive = true;
            dataSocket = new ServerSocket(0);
            String localHost = dataSocket.getInetAddress().getHostAddress();
            int localPort = dataSocket.getLocalPort();
            String addr[] = localHost.split("\\.");
            sendResponse(227, "Entering Passive Mode ("
                         + addr[0] + "," + addr[1] + "," + addr[2] + "," + addr[3] + ","
                         + localPort / 256 + "," + localPort % 256 + ").");
            break;
        }
        case "EPSV": { // EPSV <CRLF>
            isPassive = true;
            dataSocket = new ServerSocket(0);
            int localPort = dataSocket.getLocalPort();
            sendResponse(229, "Entering Extended Passive Mode (|||" + localPort + "|).");
            break;
        }
        case "CWD": { // CWD <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName) && isPathReadable(pathName)) {
                    if (pathName.startsWith("/"))
                        workingDir = pathName;
                    else
                        workingDir = new File(workingDir, pathName).getCanonicalPath();
                    sendResponse(250, "Directory successfully changed.");
                } else {
                    sendResponse(550, "Failed to change directory.");
                }
            }
            break;
        }
        case "SIZE": { // SIZE <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName) && isPathReadable(pathName)) {
                    sendResponse(213, "" + getSize(pathName));
                } else {
                    sendResponse(550, "Could not get file size.");
                }
            }
            break;
        }
        case "DELE": { // DELE <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName) && isPathWritable(pathName)) {
                    dele(pathName);
                    sendResponse(250, "Deleted OK.");
                } else {
                    sendResponse(550, "Deletion failed.");
                }
            }
            break;
        }
        case "RNFR": { // RNFR <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName) && isPathWritable(pathName)) {
                    frPath = pathName;
                    sendResponse(350, "Requested file action pending further information.");
                } else {
                    sendResponse(550, "Rename failed.");
                }
            }
            break;
        }
        case "RNTO": { // RNTO <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (frPath != null) {
                    rnto(pathName);
                    frPath = null;
                    sendResponse(250, "Renamed OK.");
                } else {
                    sendResponse(550, "Rename failed.");
                }
            }
            break;
        }
        case "LIST": { // LIST [<SP> <pathname>] <CRLF>
            if (command.length == 1) {
                sendResponse(150, "Here comes the directory listing.");
                list();
                sendResponse(226, "Directory send OK.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName) && isPathReadable(pathName)) {
                    sendResponse(150, "Here comes the directory listing.");
                    list(pathName);
                    sendResponse(226, "Directory send OK.");
                } else {
                    sendResponse(550, "Requested action not taken. File unavailable.");
                }
            }
            break;
        }
        case "RETR": { // RETR <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName) && isPathReadable(pathName)) {
                    sendResponse(150, "Opening BINARY mode data connection for " + pathName + " (" + getSize(pathName) + " bytes).");
                    retr(pathName);
                    sendResponse(226, "Transfer complete.");
                } else {
                    sendResponse(550, "Requested action not taken. File unavailable.");
                }
            }
            break;
        }
        case "STOR": { // STOR <SP> <pathname> <CRLF>
            if (command.length == 1) {
                sendResponse(501, "Syntax error in parameters or arguments.");
            } else {
                String pathName = command[1];
                if (isPathAccessible(pathName)) {
                    sendResponse(150,  "Opening BINARY mode data connection for " + pathName + ".");
                    stor(pathName);
                    sendResponse(226, "Transfer complete.");
                } else {
                    sendResponse(450, "Requested action not taken.");
                }
            }
            break;
        }
        case "NOOP": { // NOOP <CRLF>
            sendResponse(200, "NOOP command successful.");
            break;
        }
        case "QUIT": { // QUIT <CRLF>
            sendResponse(221, "Goodbye.");
            stop();
            break;
        }
        default:
            sendResponse(202, "Command not implemented, superfluous at this site.");
        }
    }
    
    /**
     * Sets shouldStop flag.
     */
    public void stop() {
        shouldStop = true;
    }
    
    /**
     * FtpServerConnection constructor.
     *
     * @param  ftpHome      FTP home path on the server
     * @param  connection   socket for FTP
     * @throws IOException
     */
    public FtpServerConnection(String ftpHome, Socket connection) throws IOException {
        this.ftpHome = new File(ftpHome).getCanonicalPath();
        
        this.connection = connection;
        this.in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        this.out = new BufferedOutputStream(connection.getOutputStream());
        this.pout = new PrintStream(this.out);
        
        // Service ready for new user
        sendResponse(220, "(my-ftpd 0.0.1)");
    }
    
    /**
     * Thread run method.
     */
    public void run() {
        String request;
        while (!shouldStop) {
            // Reads a request
            try {
                request = in.readLine();
            } catch (IOException e) {
                request = null;
            }
            
            // Handles the request and sends response
            try {
                if (request != null && !request.isEmpty()) {
                    log(request);
                    handleRequest(request);
                }
            } catch (IOException e) {
                System.err.println("FTP error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Thread finalize method.
     */
    protected void finalize() {
        try {
            if (connection != null)
                connection.close();
        } catch (IOException e) {
            System.err.println("FTP error: " + e.getMessage());
        }
    }
}
