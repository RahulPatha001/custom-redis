package Components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

@Component
public class TCPServer {

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    public void startWorking(){

        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = 6379;
        try {
            serverSocket = new ServerSocket(port);
            // Since the tester restarts your program quite often, setting SO_REUSEADDR
            // ensures that we don't run into 'Address already in use' errors
            serverSocket.setReuseAddress(true);
            // Wait for connection from client.
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
            System.out.println("IOException: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }

    public void handleClient(Client client) throws IOException {

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
//        Scanner sc = new Scanner(client.inputStream);
//
//        while(sc.hasNextLine()){
//            String nextLine = sc.nextLine();
//            if(nextLine.contains("PING")){
//                outputStream.write("+PONG\r\n".getBytes());
//            }
//            if(nextLine.contains("ECHO")){
//                String respHeader = sc.nextLine();
//                String respBody = sc.nextLine();
//                String response = respHeader+ "\r\n" + respBody + "\r\n";
//                outputStream.write((response).getBytes());
//            }
//        }
    }

    public void handleCommand(String[] command, Client client) throws IOException{
        String res = "";

        switch (command[0]){
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "SET":
                res = commandHandler.set(command);
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
        }
        if(res != null && !res.equals(""))
            client.outputStream.write(res.getBytes());
    }
}
