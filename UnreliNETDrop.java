
import java.net.*;
import java.util.*;

public class UnreliNETDrop
{
  static int pkt_size = 1000;
  static float drop_pct = 0.05f;
  
  // define thread which is used to handle one-direction of communication
  public class UnreliThread extends Thread
  {
    private DatagramSocket sk_in, sk_out;
    private int dst_port = -1;
    private Random rnd = new Random();
    
    public UnreliThread(DatagramSocket in, DatagramSocket out, int dst_port)
    {
      sk_in = in;
      sk_out = out;
      this.dst_port = dst_port;
    }
    
    public void run()
    {
      try
      {
        byte[] in_data = new byte[pkt_size];
        InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
        DatagramPacket in_pkt = new DatagramPacket(in_data, in_data.length);
        
        while (true)
        {
          // read data from the incoming socket
          sk_in.receive(in_pkt);
          
          // check the length of the packet
          if (in_pkt.getLength() != pkt_size)
          {
            System.err.println("Error: received packet of length "+in_pkt.getLength()+" from "+in_pkt.getAddress().toString()+":"+in_pkt.getPort());
            System.exit(-1);
          }
          
          // decide if to drop the packet or not
          if (rnd.nextFloat() <= drop_pct)
            continue;
          
          // write data to the outgoing socket
          DatagramPacket out_pkt = new DatagramPacket(in_data, in_data.length, dst_addr, dst_port);
          sk_out.send(out_pkt);
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
        System.exit(-1);
      }
    }
  }
  
  public UnreliNETDrop(int sk1_dst_port, int sk2_dst_port, int sk3_dst_port, int sk4_dst_port)
  {
    DatagramSocket sk1, sk2, sk3, sk4;
    System.out.println("sk1_dst_port="+sk1_dst_port+", "+"sk2_dst_port="+sk2_dst_port+", "+"sk3_dst_port="+sk3_dst_port+", "+"sk4_dst_port="+sk4_dst_port+".");
    
    try
    {
      // create sockets
      sk1 = new DatagramSocket(sk1_dst_port);
      sk3 = new DatagramSocket(sk3_dst_port);
      sk2 = new DatagramSocket();
      sk4 = new DatagramSocket();
      
      // create threads to process data
      UnreliThread th1 = new UnreliThread(sk1, sk2, sk2_dst_port);
      UnreliThread th2 = new UnreliThread(sk3, sk4, sk4_dst_port);
      th2.start();
      th1.start();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(-1);
    }
  }
  
  public static void main(String[] args)
  {   
    // parse parameters
    if (args.length != 4)
    {
      System.err.println("Usage: java UnreliNETDrop sk1_dst_port, sk2_dst_port, sk3_dst_port, sk4_dst_port");
      System.exit(-1);
    }
    else
    {
      new UnreliNETDrop(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
    }
  }
}
