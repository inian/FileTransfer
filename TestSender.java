import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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
//THINGS TO THINK ABOUT
//sepeate thread for timeout? if it is just a count then no need

public class TestSender {
	static int pkt_size = 1000;
	float sf, sn;
	float sw = (float) (Math.pow(2, 1) - 1);
	Timer mTimer;
	int timeout = 1000;
	byte[] lastSent;

	public class OutThread extends Thread {
		private DatagramSocket sk_out;
		private int dst_port;

		public OutThread(final DatagramSocket sk_out, final int dst_port) {
			this.sk_out = sk_out;
			this.dst_port = dst_port;
			sf = sn = 0; // starting sequence number from 0 then
			ActionListener timeoutAction = new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					mTimer.start();
					// basic resend all the packets till sn
					float temp = sf;
					// FileInputStream in = null;
					File file = new File("input.txt");
					RandomAccessFile in = null;
					try {
						in = new RandomAccessFile(file, "rws");
					} catch (FileNotFoundException e3) {
						e3.printStackTrace();
					}

					byte fileContent[] = new byte[pkt_size];
					int bytesReadPerRound = pkt_size - 9;

					try {
						in.skipBytes(bytesReadPerRound * (int) (sf));
					} catch (IOException e3) {
						e3.printStackTrace();
					}
					InetAddress dst_addr = null;
					try {
						dst_addr = InetAddress.getByName("127.0.0.1");
					} catch (UnknownHostException e1) {
						e1.printStackTrace();
					}
					
					if(temp > sn) {
						System.out.println("hah");
						return;
					}
					while (temp < sn) {
						int temp1 = 0;
						try {
							fileContent[0] = (byte) sf;
							temp1 = in.read(fileContent, 9, bytesReadPerRound);
							ByteBuffer buf = ByteBuffer.wrap(fileContent);
							Checksum checksum = new CRC32();
							checksum.update(fileContent, 9, temp1);
							long crcvalue = checksum.getValue();
							buf.putLong(1, crcvalue);
							fileContent = buf.array();
						} catch (IOException e2) {
							e2.printStackTrace();
						}
						if (temp1 == -1) {
							break;
						}

						// send the packet
//						DatagramPacket out_pkt = new DatagramPacket(
//								fileContent, fileContent.length, dst_addr,
//								dst_port);
						DatagramPacket out_pkt = new DatagramPacket(
								lastSent, lastSent.length, dst_addr,
								dst_port);

						try {
							sk_out.send(out_pkt);
						} catch (IOException e1) {
							e1.printStackTrace();
						}

						System.out.print((new Date().getTime())
								+ ": sender sent " + out_pkt.getLength()
								+ "bytes to " + out_pkt.getAddress().toString()
								+ ":" + out_pkt.getPort() + ". data are ");
						for (int i = 0; i < pkt_size; ++i)
							System.out.print(fileContent[i]);

//						temp++; // not there chkk
						temp = (temp + 1) % 126;
					}
				}
			};
			mTimer = new Timer(timeout, timeoutAction);
		}

		public void run() {
			try {
				sn = 0;
				// byte[] out_data = new byte[pkt_size];
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

				FileInputStream in = new FileInputStream("input.txt");
				File file = new File("input.txt");
				int fileLength = (int) file.length();

				byte fileContent[] = new byte[pkt_size];
				int bytesReadPerRound = pkt_size - 9;
				int bytesRead = 0;

//				System.out.println("file length is " + fileLength);
				while (bytesRead < fileLength) {
//				while(true) {
					if ((fileLength - bytesRead) < bytesReadPerRound) {
						bytesReadPerRound = fileLength - bytesRead;
					}

					int temp = in.read(fileContent, 9, bytesReadPerRound);
					if (temp <= 0) {
						break;
					}
					fileContent[0] = (byte) sn;
					ByteBuffer buf = ByteBuffer.wrap(fileContent);
					Checksum checksum = new CRC32();
					checksum.update(fileContent, 9, temp);
					long crcvalue = checksum.getValue();
//					System.out.println("the crc value is " + crcvalue);
					buf.putLong(1, crcvalue);
					fileContent = buf.array();

					bytesRead += temp;
//					System.out.println("bytes read " + bytesRead
//							+ " and file len is " + fileLength);
					while (sn - sf >= sw || sn==0 && sf ==124) {
						Thread.sleep(10);
						System.out.println("sn "+ sn);
						System.out.println("sf "+ sf);
						System.out.println("sw "+ sw);
					}

					// send the packet

					if ((temp + 9) != fileContent.length) {
						fileContent[temp + 9] = -1;
					}
					lastSent = fileContent.clone();
					DatagramPacket out_pkt = new DatagramPacket(fileContent,
							fileContent.length, dst_addr, dst_port);
					sk_out.send(out_pkt);

					sn = (sn + 1) % 126;
					// print info
					System.out.print((new Date().getTime()) + ": sender sent "
							+ out_pkt.getLength() + "bytes to "
							+ out_pkt.getAddress().toString() + ":"
							+ out_pkt.getPort() + ". data are ");
					for (int i = 0; i < pkt_size; ++i)
						System.out.print(fileContent[i]);
					System.out.println();

					// start timer?
					if (!mTimer.isRunning()) {
						mTimer.start();
					}
				}
				System.exit(-1);
			} catch (SocketException e) {
//				System.out.println("inian herre");
				e.printStackTrace();
				System.exit(-1);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public class InThread extends Thread {
		private DatagramSocket sk_in;

		public InThread(DatagramSocket sk_in) {
			this.sk_in = sk_in;
		}

		public void run() {
			try {
				byte[] in_data = new byte[pkt_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);

				while (true) {
					sk_in.receive(in_pkt);
					// extract the ack from the packet
					int acq = in_data[0];
					System.out.println("acq " + acq);
					System.out.println("sf " + sf);
					System.out.println("sn " + sn);
					//if (!(acq > sf && acq <= (sn)) && sf != 125) {
					if((acq < sf || acq > sn) && sf!=125) {	
					//if(acq != sn) {
//						System.out.println("ignoring acq " + acq);
						//System.exit(-1);
						continue;
					}
					// check if it is a valid ack - between ur sf and sn
					// move the window accordingly , increase sf and all
					// float offset = acq - sf;
					// sf = acq;
					while (sf < acq || (sf==125 && acq ==0)) {
						// sf++;
						sf = (sf + 1) % 126;
						 System.out.print((new Date().getTime())
						 + ": sender received " + in_pkt.getLength()
						 + "bytes from "
						 + in_pkt.getAddress().toString() + ":"
						 + in_pkt.getPort() + ". data are ");
						 for (int i = 0; i < pkt_size; ++i)
						 System.out.print(in_data[i]);
						System.out.println();
						// stop timer()
						mTimer.stop();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public TestSender(int sk1_dst_port, int sk4_dst_port) {
		DatagramSocket sk1, sk4;
		System.out.println("sk1_dst_port=" + sk1_dst_port + ", "
				+ "sk4_dst_port=" + sk4_dst_port + ".");

		try {
			// create sockets
			sk1 = new DatagramSocket();
			sk4 = new DatagramSocket(sk4_dst_port);

			// create threads to process data
			InThread th_in = new InThread(sk4);
			OutThread th_out = new OutThread(sk1, sk1_dst_port);
			th_in.start();
			th_out.start();
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
			new TestSender(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
	}
}
