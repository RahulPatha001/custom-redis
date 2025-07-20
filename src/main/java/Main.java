import Components.Server.RedisConfig;
import Components.Server.TCPServer;
import Config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");
      AnnotationConfigApplicationContext context =
              new AnnotationConfigApplicationContext(AppConfig.class);

      TCPServer app = context.getBean(TCPServer.class);
    RedisConfig redisConfig = context.getBean(RedisConfig.class);

      int port = 6379;
      for(int i = 0; i< args.length; i++){
        if(args[i].equals("--port")){
          port = Integer.parseInt(args[i+1]);
          i++;
        }
      }
      redisConfig.setPort(port);
      redisConfig.setRole("master");

      app.startWorking(port);


  }

}
