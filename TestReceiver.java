import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import javax.swing.Timer;

public class TestReceiver {
	static int pkt_size = 1000;
	int rn;

	public TestReceiver(final int sk2_dst_port, final int sk3_dst_port) {
		Timer mTimer = new Timer(2500, new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// resend the acq
//				System.out.println("acq timeout");
				byte[] in_data = new byte[pkt_size];
				InetAddress dst_addr = null;
				try {
					dst_addr = InetAddress.getByName("127.0.0.1");
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				DatagramPacket out_pkt = new DatagramPacket(in_data,
						in_data.length, dst_addr, sk3_dst_port);

				in_data[0] = (byte) rn;

				DatagramSocket sk3 = null;
				try {
					sk3 = new DatagramSocket();
				} catch (SocketException e) {
					e.printStackTrace();
				}
				System.out.println("acq sent from reciever " + in_data[0]);
				try {
					sk3.send(out_pkt);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		DatagramSocket sk2, sk3;
		System.out.println("sk2_dst_port=" + sk2_dst_port + ", "
				+ "sk3_dst_port=" + sk3_dst_port + ".");
		rn = 0;
		try {
			byte[] in_data = new byte[pkt_size];
			DatagramPacket in_pkt = new DatagramPacket(in_data, in_data.length);
			InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

			// create sockets
			sk2 = new DatagramSocket(sk2_dst_port);
			sk3 = new DatagramSocket();
			File file = new File("output.txt");
			FileOutputStream fout = new FileOutputStream(file);

			if (!file.exists()) {
				file.createNewFile();
			}

			while (true) {
				// receive packet
				sk2.receive(in_pkt);
				int seq_num = in_data[0];

				// extract the range of seq number bits, if it is rn then
				// increase rn , else continue;

				if (seq_num != rn)
					continue;
				for (int j = 8; j < in_data.length; j++) {
					if (in_data[j] == -1) {

						ByteBuffer buf = ByteBuffer.wrap(in_data);
						long recCrc = buf.getLong(1);
						Checksum checksum = new CRC32();
						checksum.update(in_data, 9, j - 9);
						if (recCrc != checksum.getValue()) {
//							System.out.println("corrupted packet");
							continue;
						}

						fout.write(in_data, 9, j - 9);
						 System.exit(-1);
					}
				}

				ByteBuffer buf = ByteBuffer.wrap(in_data);
				long recCrc = buf.getLong(1);
				Checksum checksum = new CRC32();
				checksum.update(in_data, 9, pkt_size - 9);
				if (recCrc != checksum.getValue()) {
//					System.out.println("corrupted packet");
					continue;
				}

				mTimer.stop();
				rn = (rn + 1) % 126;
				mTimer.start();
				// print info
//				System.out.print((new Date().getTime())
//						+ ": receiver received " + in_pkt.getLength()
//						+ "bytes from " + in_pkt.getAddress().toString() + ":"
//						+ in_pkt.getPort() + ". data are ");

				fout.write(in_data, 9, in_pkt.getLength() - 9);
//				for (int i = 0; i < pkt_size; ++i)
//					System.out.print(in_data[i]);
//				System.out.println();

				// send received packet
				DatagramPacket out_pkt = new DatagramPacket(in_data,
						in_data.length, dst_addr, sk3_dst_port);
				// make the 1st byte as the ack..it was seq number u can just
				in_data[0] = (byte) rn;

				System.out.println("acq sent from reciever " + in_data[0]);
				sk3.send(out_pkt);

				// print info - mainly u need to send the ack oly..
				 System.out.print((new Date().getTime()) + ": receiver sent "
				 + out_pkt.getLength() + "bytes to "
				 + out_pkt.getAddress().toString() + ":"
				 + out_pkt.getPort() + ". data are ");
				 for (int i = 0; i < pkt_size; ++i)
				 System.out.print(in_data[i]);
				 System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(String[] args) {
		// parse parameters
		if (args.length != 2)
			System.exit(-1);
		else
			new TestReceiver(Integer.parseInt(args[0]),
					Integer.parseInt(args[1]));
	}
}
