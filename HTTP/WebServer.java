import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

import java.util.Enumeration;

public class WebServer {
	public static void main(String[] args) {
		try {
			ServerSocket server = new ServerSocket(8080);
			displayAllIP4Address(server);
			
			server.setSoTimeout(2000);
			while (true) {
				try { processClient(server.accept()); }
				catch (java.net.SocketTimeoutException e) {}
			}
			//server.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void displayAllIP4Address() {
		try {
			for (Enumeration e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
				NetworkInterface n = (NetworkInterface) e.nextElement();
				for (Enumeration f = n.getInetAddresses(); f.hasMoreElements();) {
					InetAddress i = (InetAddress) f.nextElement();
					String address = i.getHostAddress();
					if (!address.contains(":") && !address.startsWith("0."))
						System.out.println(address);
				}
			}
		} catch(SocketException e) { }
	}
	
	public static void processClient(Socket s) {
		new Thread() {
			private byte[] processGet(String file) {
				try {
					byte[] content = Files.readAllBytes(Paths.get(file));
					
					String respHeader = "HTTP/1.1 200 OK\r\n" +
						"Cache-Control: max-age=0, no-store, must-revalidate\r\n" +
						"Expires: 0\r\n" +
						"Content-type: text/html; charset=UTF-8\r\n" +
						"Content-Length: " + content.length + "\r\n\r\n";
					byte[] b = respHeader.getBytes(StandardCharsets.UTF_8);
					byte[] c = new byte[b.length + content.length];
					System.arraycopy(b, 0, c, 0 , b.length);
					System.arraycopy(content, 0, c, b.length, content.length);
					return c;
				} catch (NoSuchFileException e) {
					String respHeader = "HTTP/1.1 404 Not Found\r\n\r\n";
					return respHeader.getBytes(StandardCharsets.UTF_8);
				} catch (IOException e) {
					String respHeader = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
					return respHeader.getBytes(StandardCharsets.UTF_8);
				}
			}
			
			public void run() {
				try {
					System.out.println("New Connection from: " + s.getInetAddress().getHostAddress());
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
					
					//parse request-line (1-method, 2-URL, 3-http protocol version)
					String line = in.readLine();
					System.out.println("request-line: " + line);
					String[] req = line.split(" ");
					if (req[1].startsWith("/")) req[1] = req[1].substring(1);
					if (req[1].isEmpty()) req[1] = "index.html";
					
					//parse headers
					int ContentLength = 0;
					//Map<String, String> headers = new HashMap<String, String>();
					while ((line = in.readLine()) != null && !line.isEmpty()) {
						String[] part = line.split(":", 2);
						//headers.put(part[0].trim(), part[1].trim());
						if (part[0].trim().equals("Content-Length"))
							ContentLength = Integer.parseInt(part[1].trim());
					}
					
					OutputStream out = s.getOutputStream();
					if (req[0].equals("GET")) {
						out.write(processGet(req[1]));
					} else if (req[0].equals("POST")) {
						line = "HTTP/1.1 200 OK\r\n" +
							"Content-type: application/json; charset=UTF-8\r\n" +
							"Content-Length: " + ContentLength + "\r\n\r\n";
						//read body
						if (ContentLength > 0) {
							char[] content = new char[ContentLength];
							int read, pos = 0;
							while ((read = in.read(content, pos, ContentLength)) != -1 && ContentLength > 0) {
								pos += read;
								ContentLength -= read;
							}
							out.write(line.getBytes(StandardCharsets.UTF_8));
							out.write(new String(content).getBytes(StandardCharsets.UTF_8));
						} else {
							out.write(line.getBytes(StandardCharsets.UTF_8));
						}
					} else {
						line = "HTTP/1.1 501 Not Implemented\r\n\r\n";
						out.write(line.getBytes(StandardCharsets.UTF_8));
					}
					
					out.flush();
					out.close();
					in.close();
					s.close();
				} catch (IOException e) {
				}
			}
		}.start();
	}
}
