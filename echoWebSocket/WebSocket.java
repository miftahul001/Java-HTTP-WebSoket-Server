import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class WebSocket {
	public static void main(String[] args) throws IOException {
		boolean isServerRunning = true;
		ServerSocket server = new ServerSocket(8080);
		try {
			Socket client = new Socket();
			System.out.println("Server has started on 127.0.0.1:8080.\r\nWaiting for a connection...");
			while (!server.isClosed()) {
				client = server.accept();
				System.out.println("Connection from: " + client.getRemoteSocketAddress().toString());
				if (processClient(client)) server.close();
			}
			if (!client.isClosed()) echoWebSocket(client);
			
		} finally {
			server.close();
		}
	}
	private static boolean processClient(Socket s) {
		try {
		boolean isConnected = false;
		InputStream in = s.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		OutputStream out = s.getOutputStream();
			int a = in.read();
			//reject https, only accept method GET, byte 71=G
			if (a != 71) {
				out.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes("UTF-8"));
				out.flush();
				br.close();
				in.close();
				out.close();
				s.close();
				return false;
			}
			byte[] response = {0,0};
			String line = br.readLine();
			while ((line = br.readLine()) != null && !line.isEmpty()) {
				String[] b = line.split(":", 2);
				//WebSocket Handshake
				if (b[0].trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
					try {
						response = ("HTTP/1.1 101 Switching Protocols\r\n"
						+ "Connection: Upgrade\r\n"
						+ "Upgrade: websocket\r\n"
						+ "Sec-WebSocket-Accept: "
						+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((b[1].trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
						+ "\r\n\r\n").getBytes("UTF-8");
						isConnected = true;
					} catch (NoSuchAlgorithmException e) { e.printStackTrace(); }
				}
			}
			if (!isConnected) {
				out.write(readHTML("websocket.html"));
				br.close();
				in.close();
				out.close();
				s.close();
				return false;
			}
			out.write(response);
			out.flush();
			return true;
		} catch (IOException e) { e.printStackTrace(); return false; }
	}
	private static void echoWebSocket(Socket s) {
		try {
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			while (!s.isClosed()) {
				int len = in.read();				
				if (len != 129) { //only accept text frame package
					System.out.println("frame type: " + Integer.toString(len, 2)); //look out frame type
					in.close();
					out.close();
					s.close();
					continue;
				}
				//if second byte < 126, content-length = second byte
				//if second byte == 126, content-length = byte[3] & byte[4]
				//if second byte == 127, content-length = byte[3] until byte[3+8]
				//https://stackoverflow.com/questions/8125507/how-can-i-send-and-receive-websocket-messages-on-the-server-sidehttps://stackoverflow.com/questions/8125507/how-can-i-send-and-receive-websocket-messages-on-the-server-side
				len = in.read(); // read second byte
				len = len & 127;
				
				byte[] response;
				int header = 2;
				if (len < 126) {
					response = new byte[header+len];
					response[1] = (byte) len;
				} else if (len == 126) {
					header = 4;
					len = ((in.read() & 0xFF) << 8);
					len += in.read();
					
					response = new byte[header+len];
					response[1] = (byte) 126;
					response[2] = (byte)((len >> 8 ) & (byte)255);;
					response[3] = (byte)((len ) & (byte)255);;
				} else { //second byte is considered 127
					header = 10;
					byte[] tempLen = new byte[8];
					in.read(tempLen);
                    len = (tempLen[0] & 0xFF) << 56 | (tempLen[1] & 0xFF) << 48 | (tempLen[2] & 0xFF) << 40 | (tempLen[3] & 0xFF) << 32 | (tempLen[4] & 0xFF) << 24 | (tempLen[5] & 0xFF) << 16 | (tempLen[6] & 0xFF) << 8 | (tempLen[7] & 0xFF);
					
					response = new byte[header+len];
					response[1] = (byte) 127;
					System.arraycopy(tempLen, 0, response, 2, tempLen.length);
				}
				response[0] = (byte) 129; //frame type = text frame package
				
				byte[] mask = new byte[4];
				in.read(mask);
				
				in.read(response, header, len);
				//unmask or decode content
				for (int i = header; i < response.length; i++) {
					response[i] = (byte) (response[i] ^ mask[(i-header) & 0x3]);
					//response[i] = (byte) (response[i] ^ mask[(i-header) % 4]); //alternative
				}
				//System.out.println("content: " + new String(response)); //if you want to see the results
				
				out.write(response);
				out.flush();
			}
		} catch (IOException e) { e.printStackTrace(); }
	}
	
    private static byte[] readHTML(String fname) throws IOException {
		File file = new File(fname);
		if (!file.canRead()) throw new IOException("can not read " + fname);
		FileInputStream f = new FileInputStream(file);
		int size = f.available();
		byte[] a = ("HTTP/1.1 200 OK\r\n"
			+ "Cache-Control: max-age=0, no-store, must-revalidate\r\n"
			+ "Expires: 0\r\n"
			+ "Content-type: text/html; charset=UTF-8\r\n"
			+ "Content-Length: " + size + "\r\n\r\n").getBytes("UTF-8");
		byte[] b = new byte[a.length + size];
		System.arraycopy(a, 0, b, 0, a.length);
		int read, pos = a.length;
		while ((read = f.read(b, pos, size)) != -1 && size > 0) {
			pos += read;
			size -= read;
		}
		return b;
	}
}
