package Components.Service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
class RespSerializerTest {

    private final RespSerializer respSerializer = new RespSerializer();

    @Test
    public void deserializePing(){
        String ping = "*1\r\n$4\r\nPING\r\n";
        List<String[]> commands = respSerializer.deserialize(ping.getBytes(StandardCharsets.UTF_8));

        for(String[] s : commands){
            System.out.println("---d---");
            for (String ss : s){
                System.out.print(ss + " ");
            }
        }
        assertEquals(1, commands.size());
        assertEquals(1, commands.get(0).length);
        assertEquals("PING", commands.get(0)[0]);
    }

    @Test
    public void testMultipleCommands(){
        String multipleCommands = "*2\r\n*3\r\n$3\r\nset\r\n$3\r\nkey\r\n$5\r\nvalue\r\n*3\r\n$3\r\nset\r\n$3\r\nkey\r\n$5\r\nvalue";
        // *2\r\n*3\r\n#3set\r\n#3key\r\n#5value\r\n*3\r\n#3set\r\n#3key\r\n#5value

        List<String[]> commands = respSerializer.deserialize(multipleCommands.getBytes(StandardCharsets.UTF_8));

        for(String[] s : commands){
            System.out.println("---d---");
            for (String ss : s){
                System.out.print(ss + " ");
            }
        }
        assertEquals(2, commands.size());
        assertEquals(3, commands.get(0).length);
        assertEquals(3, commands.get(1).length);
        assertEquals("set", commands.get(0)[0]);
        assertEquals("key", commands.get(0)[1]);
        assertEquals("value", commands.get(0)[2]);
    }
}