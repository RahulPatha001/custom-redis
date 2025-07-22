package Components.Server;

import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Service.CommandHandler;
import Components.Service.RespSerializer;
import Components.Infra.Client;
import Components.Service.ResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SlaveTCPServer {
    private static final Logger logger = Logger.getLogger(SlaveTCPServer.class.getName());

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    private ConnectionPool connectionPool;

    public void startWorking(){

        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = redisConfig.getPort();

        try {
            serverSocket = new ServerSocket(port);
            // Since the tester restarts your program quite often, setting SO_REUSEADDR
            // ensures that we don't run into 'Address already in use' errors
            serverSocket.setReuseAddress(true);
            // Wait for connection from client.

            CompletableFuture<Void> slaveConnectionFuture = CompletableFuture.runAsync(this::initiateSlavery);
            slaveConnectionFuture.thenRun(() -> System.out.println("Replication done"));

            int id = 0;

            while (true){
                id++;
                clientSocket = serverSocket.accept();
                Socket finalClientSocket = clientSocket;
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                Client client = new Client(finalClientSocket, inputStream, outputStream, id);
                CompletableFuture.runAsync(() ->{
                    try {
                        handleClient(client);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE,e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE,e.getMessage());
            }
        }
    }

    private void initiateSlavery() {
        try(Socket master = new Socket(redisConfig.getMasterHost(), redisConfig.getMasterPort())){
            InputStream inputStream = master.getInputStream();
            OutputStream outputStream = master.getOutputStream();
            byte[] inputBuffer = new byte[1024];

            // step 1 of handshake
            byte[] data = "*1\r\n$4\r\nPING\r\n".getBytes();
            outputStream.write(data);
            int bytesRead = inputStream.read(inputBuffer, 0,inputBuffer.length);
            String response = new String(inputBuffer,0,bytesRead ,StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            // step 2 of handshake
            int lenListeningPort = (redisConfig.getPort()+"").length();
            int listeningPort = redisConfig.getPort();
            String repliconf = "*3\r\n$8\r\nREPLCONF\r\n$14\r\nlistening-port\r\n$" +
                    (lenListeningPort + "") + "\r\n" + (listeningPort+ "")+ "\r\n";
            data = repliconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer, 0,inputBuffer.length);
            response = new String(inputBuffer,0,bytesRead ,StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            repliconf = "*3\r\n$8\r\nREPLCONF\r\n$4\r\ncapa\r\n$6\r\npsync2\r\n" ;
            data = repliconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer, 0,inputBuffer.length);
            response = new String(inputBuffer,0,bytesRead ,StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            // step 3 of handshake
            String psync = "*3\r\n$5\r\nPSYNC\r\n$1\r\n?\r\n$2\r\n-1\r\n" ;
            data = psync.getBytes();
            outputStream.write(data);
            List<Integer>res =  handlePsyncResponse(inputStream);

            while (master.isConnected()){
                int offSet = 1;
                StringBuilder sb = new StringBuilder();
                List<Byte> bytes = new ArrayList<>();

                while (true){
                    int b = inputStream.read();
                    if(b =='*'){
                        break;
                    }
                    offSet++;
                    bytes.add((byte)b);
                    if(inputStream.available() <= 0){
                        break;
                    }
                }
                for(Byte b : bytes){
                    sb.append((char)(b.byteValue() & 0xFF));
                }
                if(bytes.isEmpty()){
                    continue;
                }
                String command = sb.toString();
                String[] parts = command.split("\r\n");
                if(command.equals("+OK\r\n"))
                    continue;
                String[] commandArray = respSerializer.parseArray(parts);
                String commandResult = handleCommandFromMaster(commandArray, master);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }

    private String handleCommandFromMaster(String[] command, Socket master) {
        String cmd = command[0];
        cmd = cmd.toUpperCase();
        String res = "";
        switch (cmd){
            case "SET":
                commandHandler.set(command);
                CompletableFuture.runAsync(() -> propagate(command));
                break;
        }
        return  res;
    }

    private void propagate(String[] command) {
        String commandRespString = respSerializer.respArray(command);
        try {
            for(Slave s: connectionPool.getSlaves()){
                s.send(commandRespString.getBytes());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Integer> handlePsyncResponse(InputStream inputStream) throws IOException {
        List<Integer> res = new ArrayList<>();
        while(true){
            if(inputStream.available() <= 0)
                continue;
            int b = inputStream.read();
            res.add(b);
            if(b == (int)'*'){
                break;
            }
        }
        return res;
    }

    public void handleClient(Client client) throws IOException {
        connectionPool.addClient(client);
        while(client.socket.isConnected()){
            byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
            int bytesRead = client.inputStream.read(buffer);
            if(bytesRead > 0){
                // bytes parsing into string
                List<String[]>commands =  respSerializer.deserialize(buffer);
                for(String[] command: commands){
                    handleCommand(command, client);
                }
            }
        }
        connectionPool.removeClient(client);
        connectionPool.removeSlave(client);
    }

    public void handleCommand(String[] command, Client client) throws IOException{
        String res = "";
        byte[] data = null;

        switch (command[0]){
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "SET":
                res = "-READONLY you can't write against replica.\r\n";
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
            case "INFO":
                res = commandHandler.info(command);
                break;
            case "REPLCONF":
                res = commandHandler.repelconf(command, client);
                break;
            case "PSYNC":
                ResponseDto resDto = commandHandler.psync(command);
                res = resDto.getResponse();
                data = resDto.getData();
                break;
        }
        client.send(res,data);
    }
}
